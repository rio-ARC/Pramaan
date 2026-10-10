package app.pramaan.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AthleteDao {
    @Query("SELECT * FROM athletes ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<AthleteEntity>>

    @Query("SELECT * FROM athletes ORDER BY createdAt DESC")
    suspend fun getAll(): List<AthleteEntity>

    @Query("SELECT * FROM athletes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AthleteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(athlete: AthleteEntity)

    @Update
    suspend fun update(athlete: AthleteEntity)

    @Delete
    suspend fun delete(athlete: AthleteEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE athleteId = :athleteId ORDER BY createdAt DESC")
    fun getByAthleteIdFlow(athleteId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE synced = 0")
    fun getPendingSyncCountFlow(): Flow<Int>

    @Query("SELECT * FROM sessions WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getPendingSessions(): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)
}
