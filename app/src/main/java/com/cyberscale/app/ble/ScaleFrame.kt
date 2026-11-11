package com.cyberscale.app.ble

import android.bluetooth.le.ScanRecord

/**
 * 云康 / 优渥（OKOK Chipsea 2.0）**纯广播**体重秤帧解析。
 *
 * 字节布局与参考工程 youwo_app/java/com/youwo/scale/YunkangBroadcast.java 一一对应。
 * 完整 manufacturer AD 数据（含 2 字节公司 ID 0x20CA，小端 `CA 20`），共 21 字节：
 *
 * ```
 *  idx 0      CA    公司 ID 低字节 / 帧头
 *  idx 1      20    公司 ID 高字节 / version（本机 0x20）
 *  idx 2      0B    dataLength
 *  idx 3..4   1212  productId（大端）
 *  idx 5..6   1212  保留
 *  idx 7      01    deviceTypeFlag  0=体脂秤 1=体重秤
 *  idx 8      05    scaleProperty   bit4-3=单位 bit2-1=小数位 bit0=最终值标志
 *  idx 9      25    measureSeqNo    每次称重 +1
 *  idx 10..11 1D74  weight（uint16 大端原始值）
 *  idx 12..13 03E8  r1（uint16 大端 / 1000，本机恒为 1000）
 *  idx 14     E1    checksum（参考工程未做校验，此处仅记录，不参与过滤）
 *  idx 15..20 ...   秤自身 MAC
 * ```
 *
 * 实测样例（末尾 6 字节 MAC 已替换为示例值，避免公开真实设备地址）：
 * `CA 20 0B 12 12 12 12 01 05 25 1D 74 03 E8 E1 AA BB CC DD EE FF`
 * → 0x1D74 = 7540，digitBits=2 → 7540/100 = **75.40 kg**，与秤面一致。
 *
 * 关键语义：`scaleProperty` 的 **bit0 = 最终值/锁定标志**。
 * `0x05`=最终值 2 位小数、`0x04`=实时 2 位小数、`0x01`=最终值 1 位小数、`0x00`=实时 1 位小数。
 * 参考工程只在 bit0=1 时解析体重；本实现把实时帧也解析出来，用于驱动数字跳动与波纹动效，
 * 但只有最终帧才会进入 [MeasurementStabilizer] 落库。
 */
data class ScaleFrame(
    val mac: String,
    val rssi: Int,
    val version: Int,
    val productId: Int,
    val deviceType: Int,
    val scaleProperty: Int,
    val seq: Int,
    val isFinal: Boolean,
    val weightKg: Double,
    val impedance: Float,
    val checksum: Int,
    val rawHex: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** 参考工程 hasWeight() 的过滤条件。 */
    val isValidWeight: Boolean get() = weightKg > 0.5 && weightKg < 300.0

    val unitBits: Int get() = (scaleProperty shr 3) and 0x03
    val digitBits: Int get() = (scaleProperty shr 1) and 0x03

    val unitLabel: String
        get() = when (unitBits) {
            1 -> "JIN"
            2 -> "LB"
            3 -> "ST"
            else -> "KG"
        }

    companion object {
        private const val HEADER = 0xCA
        private const val MIN_LENGTH = 21

        /**
         * 从 ScanRecord 解析。
         *
         * 优先用 [ScanRecord.getManufacturerSpecificData]（它已剥掉 2 字节公司 ID，
         * 所以要把 companyId 按小端补回去，还原成 21 字节完整布局）；
         * 部分国产 ROM 该 SparseArray 为空，则退化到手工遍历原始 AD structure，
         * 与参考工程 YunkangBroadcast.fromAdv() 的做法一致。
         */
        fun fromScanRecord(record: ScanRecord?, fallbackMac: String, rssi: Int): ScaleFrame? {
            if (record == null) return null

            val sparse = record.manufacturerSpecificData
            for (i in 0 until sparse.size()) {
                val companyId = sparse.keyAt(i)
                val payload = sparse.valueAt(i) ?: continue
                val full = ByteArray(2 + payload.size)
                full[0] = (companyId and 0xFF).toByte()
                full[1] = ((companyId shr 8) and 0xFF).toByte()
                System.arraycopy(payload, 0, full, 2, payload.size)
                parse(full, fallbackMac, rssi)?.let { return it }
            }

            val raw = record.bytes ?: return null
            val mfg = extractManufacturer(raw) ?: return null
            return parse(mfg, fallbackMac, rssi)
        }

        /** 手工遍历 AD structure，返回 type=0xFF 的整段（含公司 ID）。 */
        private fun extractManufacturer(adv: ByteArray): ByteArray? {
            var i = 0
            while (i + 1 < adv.size) {
                val len = adv[i].toInt() and 0xFF
                if (len == 0) break
                if (i + 1 + len > adv.size) break
                val type = adv[i + 1].toInt() and 0xFF
                if (type == 0xFF) {
                    val size = len - 1
                    if (size <= 0) return null
                    val out = ByteArray(size)
                    System.arraycopy(adv, i + 2, out, 0, size)
                    return out
                }
                i += 1 + len
            }
            return null
        }

        /** 解析完整 21 字节（含公司 ID）。不是云康帧返回 null。 */
        fun parse(b: ByteArray, fallbackMac: String, rssi: Int): ScaleFrame? {
            if (b.size < MIN_LENGTH) return null
            if ((b[0].toInt() and 0xFF) != HEADER) return null

            val version = b[1].toInt() and 0xFF
            val dataLength = b[2].toInt() and 0xFF
            if (dataLength <= 0 || dataLength > 64) return null

            val productId = ((b[3].toInt() and 0xFF) shl 8) or (b[4].toInt() and 0xFF)
            val deviceType = if ((b[7].toInt() and 0xFF) != 0) 1 else 0
            val sp = b[8].toInt() and 0xFF
            val seq = b[9].toInt() and 0xFF

            val rawWeight = ((b[10].toInt() and 0xFF) shl 8) or (b[11].toInt() and 0xFF)
            val unitBits = (sp shr 3) and 0x03
            val digitBits = (sp shr 1) and 0x03
            val weightKg = decodeWeight(rawWeight, unitBits, digitBits)

            val r1Raw = ((b[12].toInt() and 0xFF) shl 8) or (b[13].toInt() and 0xFF)
            val frameMac = formatMac(b, 15)
            val mac = if (frameMac.length == 17) frameMac else fallbackMac

            return ScaleFrame(
                mac = mac,
                rssi = rssi,
                version = version,
                productId = productId,
                deviceType = deviceType,
                scaleProperty = sp,
                seq = seq,
                isFinal = (sp and 0x01) != 0,
                weightKg = weightKg,
                impedance = r1Raw / 1000.0f,
                checksum = b[14].toInt() and 0xFF,
                rawHex = toHex(b)
            )
        }

        /**
         * 完整复刻参考工程 YunkangBroadcast.parseWeight()：
         * digitBits==1 → 不除；==2 → ÷100；其余 → ÷1000。
         * 单位统一换算成 kg：市斤 ×0.5、磅 ×0.4535924、英石 ×6.35029318。
         */
        private fun decodeWeight(raw: Int, unitBits: Int, digitBits: Int): Double {
            var w = when (digitBits) {
                1 -> raw.toDouble()
                2 -> raw / 100.0
                else -> raw / 1000.0
            }
            when (unitBits) {
                1 -> w *= 0.5
                2 -> w *= 0.4535924
                3 -> w *= 6.35029318
            }
            return w
        }

        private fun formatMac(b: ByteArray, off: Int): String {
            if (off + 6 > b.size) return ""
            return (0 until 6).joinToString(":") {
                "%02X".format(b[off + it].toInt() and 0xFF)
            }
        }

        private fun toHex(b: ByteArray): String =
            b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }
}
