package com.example.eventshoppingplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.eventshoppingplanner.data.local.entity.DayModeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayModeDao {

    @Query("SELECT * FROM day_modes WHERE eventId = :eventId")
    fun getDayModesByEventId(eventId: String): Flow<List<DayModeEntity>>

    @Query("SELECT * FROM day_modes WHERE eventId = :eventId")
    suspend fun getDayModesByEventIdOnce(eventId: String): List<DayModeEntity>

    @Query("SELECT * FROM day_modes WHERE eventId = :eventId AND eventDate = :eventDate")
    suspend fun getDayMode(eventId: String, eventDate: String): DayModeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayMode(entity: DayModeEntity)

    @Query("DELETE FROM day_modes WHERE eventId = :eventId")
    suspend fun deleteDayModesByEventId(eventId: String)
}