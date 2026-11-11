package com.cyberscale.app.data

import android.content.Context
import com.cyberscale.app.ble.BleScanController

/**
 * 极简手动依赖容器：工程只有一个 Activity，不需要引入 Hilt/Koin。
 * 依赖全部是 application 作用域单例，Activity 重建时 BLE 扫描状态不会丢。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.build(appContext)

    val repository: ScaleRepository = ScaleRepository(database)
    val bleController: BleScanController = BleScanController(appContext)
    val settings: SettingsStore = SettingsStore(appContext)
}
