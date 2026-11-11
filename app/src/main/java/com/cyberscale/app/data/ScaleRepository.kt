package com.cyberscale.app.data

import androidx.room.withTransaction
import com.cyberscale.app.ble.StableMeasurement
import kotlinx.coroutines.flow.Flow

class ScaleRepository(private val db: AppDatabase) {

    private val users = db.userDao()
    private val records = db.recordDao()

    fun observeUsers(): Flow<List<UserEntity>> = users.observeAll()
    fun observeActiveUser(): Flow<UserEntity?> = users.observeActive()
    fun observeRecords(userId: Long): Flow<List<WeightRecordEntity>> = records.observeByUser(userId)

    /** 首次启动写入一个默认成员，保证界面不会落在空壳状态。 */
    suspend fun ensureDefaultUser() {
        if (users.count() == 0) {
            users.insert(
                UserEntity(
                    name = "我",
                    colorArgb = 0xFF00F0FF.toInt(),
                    heightCm = 170,
                    birthYear = 0,
                    gender = 0,
                    goalWeightKg = 0.0,
                    startWeightKg = 0.0,
                    createdAt = System.currentTimeMillis(),
                    isActive = true,
                    birthDate = ""
                )
            )
        }
    }

    suspend fun activate(userId: Long) = db.withTransaction {
        users.clearActive()
        users.setActive(userId)
    }

    suspend fun addUser(user: UserEntity): Long = users.insert(user)

    suspend fun updateUser(user: UserEntity) = users.update(user)

    /** 删除成员时同步删掉其记录（外键 CASCADE），并保证仍有一个激活成员。 */
    suspend fun deleteUser(user: UserEntity) = db.withTransaction {
        records.clearByUser(user.id)
        users.delete(user)
        if (user.isActive) {
            users.allOnce().firstOrNull()?.let { next ->
                users.clearActive()
                users.setActive(next.id)
            }
        }
    }

    /** 把公式估算结果贴到一条记录上；档案不全时所有指标保持 null。 */
    private fun WeightRecordEntity.withMetrics(m: BodyMetrics?): WeightRecordEntity = copy(
        bmi = m?.bmi,
        bmiLevel = m?.level?.name,
        bodyFatPct = m?.bodyFatPct,
        bmrKcal = m?.bmrKcal,
        ibwKg = m?.ibwKg,
        lbmKg = m?.lbmKg,
        fmKg = m?.fmKg,
        relativeWeightIndex = m?.relativeWeightIndex,
        obesityDegreePct = m?.obesityDegreePct
    )

    /** 一次稳定称重 → 一条记录，同时按当时的档案算出 8 项指标一并落库。 */
    suspend fun saveMeasurement(m: StableMeasurement, user: UserEntity): Long = db.withTransaction {
        val metrics = BodyComposition.compute(
            weightKg = m.weightKg,
            profile = BodyComposition.profileOf(user),
            atMillis = m.timestamp
        )
        val id = records.insert(
            WeightRecordEntity(
                userId = user.id,
                ts = m.timestamp,
                weightKg = m.weightKg,
                seq = m.seq,
                rssi = m.rssi,
                impedance = m.impedance,
                rawHex = m.rawHex,
                mac = m.mac
            ).withMetrics(metrics)
        )
        if (user.startWeightKg <= 0.0) {
            users.update(user.copy(startWeightKg = m.weightKg))
        }
        id
    }

    /**
     * 档案编辑保存后调用：为 v1 遗留的、尚无指标的历史记录按当前档案补算。
     * 年龄仍按每条记录自己的时间戳计算，但身高/性别使用当前档案（历史身高不可考）。
     * 返回实际补算条数；档案不完整时不改动任何记录。
     */
    suspend fun backfillMetrics(userId: Long): Int = db.withTransaction {
        val user = users.getById(userId) ?: return@withTransaction 0
        val profile = BodyComposition.profileOf(user)
        var done = 0
        records.withoutMetrics(userId).forEach { record ->
            val metrics = BodyComposition.compute(record.weightKg, profile, record.ts)
                ?: return@forEach
            records.update(record.withMetrics(metrics))
            done++
        }
        done
    }

    suspend fun missingMetricsCount(userId: Long): Int = records.countWithoutMetrics(userId)

    suspend fun deleteRecord(record: WeightRecordEntity) = records.delete(record)

    suspend fun clearRecords(userId: Long) = records.clearByUser(userId)

    /** 改归属时按新成员的档案重算指标，避免 A 的记录挂着 B 的体成分数据。 */
    suspend fun reassign(recordId: Long, newUserId: Long) = db.withTransaction {
        val record = records.getById(recordId) ?: return@withTransaction
        val newUser = users.getById(newUserId) ?: return@withTransaction
        val metrics = BodyComposition.compute(
            record.weightKg,
            BodyComposition.profileOf(newUser),
            record.ts
        )
        records.update(record.copy(userId = newUserId).withMetrics(metrics))
    }

    /** records 由 DAO 保证按 ts DESC 返回。 */
    fun stats(list: List<WeightRecordEntity>): RecordStats {
        if (list.isEmpty()) return RecordStats()
        val values = list.map { it.weightKg }
        val latest = values.first()
        val previous = values.getOrNull(1)
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        return RecordStats(
            count = list.size,
            latest = latest,
            previous = previous,
            average = values.average(),
            min = values.minOrNull(),
            max = values.maxOrNull(),
            best30d = list.filter { it.ts >= cutoff }.minOfOrNull { it.weightKg },
            delta = previous?.let { latest - it }
        )
    }

    fun trendPoints(list: List<WeightRecordEntity>, range: TrendRange): List<WeightRecordEntity> {
        val cutoff = System.currentTimeMillis() - range.days.toLong() * 24 * 60 * 60 * 1000
        return list.filter { it.ts >= cutoff }.sortedBy { it.ts }
    }

    /**
     * 目标进度 = (起始 - 当前) / (起始 - 目标)。
     * 参考工程用的是 weight/goal×100，超重时恒为 100%，这里改为基于起始体重的真实进度。
     */
    fun goalProgress(user: UserEntity?, latest: Double?): Float {
        if (user == null || latest == null || user.goalWeightKg <= 0.0) return 0f
        val start = if (user.startWeightKg > 0.0) user.startWeightKg else latest
        val total = start - user.goalWeightKg
        if (total <= 0.0) return if (latest <= user.goalWeightKg) 1f else 0f
        return ((start - latest) / total).coerceIn(0.0, 1.0).toFloat()
    }
}
