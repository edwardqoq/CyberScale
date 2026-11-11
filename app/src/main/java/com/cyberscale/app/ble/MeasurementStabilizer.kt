package com.cyberscale.app.ble

import kotlin.math.abs

/** 一次"已锁定"的称重结果，只有它会被写入数据库。 */
data class StableMeasurement(
    val weightKg: Double,
    val seq: Int,
    val rssi: Int,
    val impedance: Float,
    val rawHex: String,
    val mac: String,
    val timestamp: Long
)

/**
 * 把高频广播帧收敛成一条记录。
 *
 * 参考工程 MainActivity.onBroadcastWeight() 的做法是
 * `seq 变化 || |Δweight| > 0.05` 就直接 insert，实时波动会写进大量重复记录。
 * 本实现的规则：
 * 1. 实时帧（bit0=0）只用于 UI 动效，绝不落库；
 * 2. 最终帧（bit0=1）进入 2.5 秒滑窗，窗口内同 seq 至少 2 条且极差 ≤ 0.05 kg 才判定稳定；
 * 3. 同一个 seq 只记录一次（秤锁定后会重复广播同一帧几十次）；
 * 4. 兜底去重：20 秒内相同体重（±0.05）不再记录，防秤重启导致 seq 回绕。
 */
class MeasurementStabilizer {

    companion object {
        private const val WINDOW_MS = 2_500L
        private const val MIN_SAMPLES = 2
        private const val TOLERANCE_KG = 0.05
        private const val DEDUP_MS = 20_000L
    }

    private data class Sample(val at: Long, val seq: Int, val weight: Double)

    private val samples = ArrayDeque<Sample>()
    private var lastSavedSeq = -1
    private var lastSavedAt = 0L
    private var lastSavedWeight = Double.NaN

    /** 返回非 null 表示"这一帧触发了一次可保存的测量"。 */
    @Synchronized
    fun onFrame(frame: ScaleFrame): StableMeasurement? {
        if (!frame.isValidWeight) return null
        if (!frame.isFinal) return null
        if (frame.seq == lastSavedSeq) return null

        val now = System.currentTimeMillis()
        samples.addLast(Sample(now, frame.seq, frame.weightKg))
        while (samples.isNotEmpty() && now - samples.first().at > WINDOW_MS) {
            samples.removeFirst()
        }

        val sameSeq = samples.filter { it.seq == frame.seq }
        if (sameSeq.size < MIN_SAMPLES) return null
        val min = sameSeq.minOf { it.weight }
        val max = sameSeq.maxOf { it.weight }
        if (max - min > TOLERANCE_KG) return null

        val weight = sameSeq.map { it.weight }.average()
        if (now - lastSavedAt < DEDUP_MS &&
            !lastSavedWeight.isNaN() &&
            abs(weight - lastSavedWeight) < TOLERANCE_KG
        ) {
            return null
        }

        lastSavedSeq = frame.seq
        lastSavedAt = now
        lastSavedWeight = weight
        samples.clear()

        return StableMeasurement(
            weightKg = weight,
            seq = frame.seq,
            rssi = frame.rssi,
            impedance = frame.impedance,
            rawHex = frame.rawHex,
            mac = frame.mac,
            timestamp = now
        )
    }

    /** 用户手动切换测量会话（切换成员 / 清空记录）时重置，避免旧会话污染下一次判定。 */
    @Synchronized
    fun reset() {
        samples.clear()
        lastSavedSeq = -1
        lastSavedAt = 0L
        lastSavedWeight = Double.NaN
    }
}
