package com.cyberscale.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** App 需要动态申请的权限。API 31 起不能把 ACCESS_FINE_LOCATION 放进数组（已声明 maxSdkVersion=30）。 */
fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

data class BleUiState(
    val scanning: Boolean = false,
    val bluetoothOn: Boolean = true,
    val permissionGranted: Boolean = false,
    val lastFrameAt: Long = 0L,
    val lastRssi: Int = 0,
    val lastMac: String = "--",
    val lastHex: String = "",
    val lastSeq: Int = -1,
    val message: String = "等待信号"
)

/**
 * 纯广播 BLE 扫描控制器。
 *
 * 与参考工程 BleManager.startMonitor() 一致：只扫描、**不建立 GATT 连接、不配对**。
 * 改进点：
 * - 用协程循环做 25s 扫描 + 1s 空档，规避系统对长时间扫描的静默限流；
 * - 不按设备名硬过滤，能否解析出云康帧就是唯一判据，兼容改名 / 匿名广播的秤；
 * - MAC 优先取帧内 [15..20]，不依赖 device.address（API 31+ 读它要 BLUETOOTH_CONNECT）。
 */
class BleScanController(private val context: Context) {

    companion object {
        private const val SCAN_WINDOW_MS = 25_000L
        private const val SCAN_GAP_MS = 1_000L
        private const val RETRY_MS = 2_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    @Volatile
    private var scanning = false

    private val _frames = MutableSharedFlow<ScaleFrame>(extraBufferCapacity = 64)
    val frames: SharedFlow<ScaleFrame> = _frames.asSharedFlow()

    private val _state = MutableStateFlow(BleUiState())
    val state: StateFlow<BleUiState> = _state.asStateFlow()

    private val adapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val systemMac = try {
                result.device?.address.orEmpty()
            } catch (e: SecurityException) {
                ""
            }
            val frame = ScaleFrame.fromScanRecord(result.scanRecord, systemMac, result.rssi)
                ?: return

            _frames.tryEmit(frame)
            _state.value = _state.value.copy(
                lastFrameAt = System.currentTimeMillis(),
                lastRssi = frame.rssi,
                lastMac = frame.mac,
                lastHex = frame.rawHex,
                lastSeq = frame.seq,
                message = if (frame.isFinal) "LOCKED · 最终值" else "STREAM · 实时数据"
            )
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _state.value = _state.value.copy(scanning = false, message = errorText(errorCode))
        }
    }

    private fun errorText(code: Int): String = when (code) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "扫描已在运行"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "扫描注册失败，请重启蓝牙"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "蓝牙内部错误，请确认定位服务已开启"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "设备不支持 BLE 扫描"
        else -> "扫描失败 code=$code，请确认定位服务已开启"
    }

    fun hasPermission(): Boolean {
        val needed = requiredBlePermissions()
        return needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** 幂等启动。权限/蓝牙状态变化会在循环里自动重试，不需要外部重启。 */
    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                val granted = hasPermission()
                val bt = adapter
                when {
                    !granted -> {
                        _state.value = _state.value.copy(
                            scanning = false, permissionGranted = false, message = "缺少蓝牙扫描权限"
                        )
                        delay(RETRY_MS)
                    }
                    bt == null || !bt.isEnabled -> {
                        _state.value = _state.value.copy(
                            scanning = false, permissionGranted = true,
                            bluetoothOn = false, message = "请先开启系统蓝牙"
                        )
                        delay(RETRY_MS)
                    }
                    else -> {
                        startOnce(bt.bluetoothLeScanner)
                        delay(SCAN_WINDOW_MS)
                        stopOnce(bt.bluetoothLeScanner)
                        delay(SCAN_GAP_MS)
                    }
                }
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        adapter?.bluetoothLeScanner?.let { stopOnce(it) }
        _state.value = _state.value.copy(scanning = false, message = "监听已暂停")
    }

    @SuppressLint("MissingPermission")
    private fun startOnce(scanner: BluetoothLeScanner?) {
        if (scanner == null) {
            _state.value = _state.value.copy(message = "无法获取 BLE 扫描器")
            return
        }
        if (scanning) return
        try {
            // 不设 ScanFilter：部分 ROM 对 manufacturer data 过滤支持不一致，回调里按帧头判定更稳
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L)
                .build()
            scanner.startScan(null, settings, scanCallback)
            scanning = true
            _state.value = _state.value.copy(
                scanning = true, bluetoothOn = true, permissionGranted = true,
                message = "SCANNING · 请站上体重秤"
            )
        } catch (e: SecurityException) {
            scanning = false
            _state.value = _state.value.copy(
                scanning = false, permissionGranted = false, message = "缺少蓝牙扫描权限"
            )
        } catch (e: IllegalStateException) {
            scanning = false
            _state.value = _state.value.copy(scanning = false, message = "蓝牙已关闭")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopOnce(scanner: BluetoothLeScanner?) {
        if (!scanning) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // 权限被系统回收时忽略，循环下一轮会重新检查
        } catch (e: IllegalStateException) {
            // 蓝牙已关闭
        }
        scanning = false
    }
}
