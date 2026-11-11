package com.cyberscale.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    suspend fun allOnce(): List<UserEntity>

    @Query("SELECT * FROM users WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<UserEntity?>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Delete
    suspend fun delete(user: UserEntity)

    @Query("UPDATE users SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE users SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)
}

@Dao
interface RecordDao {

    @Query("SELECT * FROM records WHERE userId = :userId ORDER BY ts DESC")
    fun observeByUser(userId: Long): Flow<List<WeightRecordEntity>>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getById(id: Long): WeightRecordEntity?

    @Insert
    suspend fun insert(record: WeightRecordEntity): Long

    @Update
    suspend fun update(record: WeightRecordEntity)

    @Delete
    suspend fun delete(record: WeightRecordEntity)

    /** v2：档案补全后，按当前档案为旧记录补算体成分指标。 */
    @Query("SELECT * FROM records WHERE userId = :userId AND bmi IS NULL ORDER BY ts DESC")
    suspend fun withoutMetrics(userId: Long): List<WeightRecordEntity>

    @Query("SELECT COUNT(*) FROM records WHERE userId = :userId AND bmi IS NULL")
    suspend fun countWithoutMetrics(userId: Long): Int

    @Query("DELETE FROM records WHERE userId = :userId")
    suspend fun clearByUser(userId: Long)
}
