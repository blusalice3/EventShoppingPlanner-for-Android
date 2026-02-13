package com.example.eventshoppingplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.eventshoppingplanner.data.local.entity.HallOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HallOrderDao {

    @Query("SELECT * FROM hall_orders WHERE eventId = :eventId AND dayName = :dayName")
    fun getHallOrder(eventId: String, dayName: String): Flow<HallOrderEntity?>

    @Query("SELECT * FROM hall_orders WHERE eventId = :eventId AND dayName = :dayName")
    suspend fun getHallOrderOnce(eventId: String, dayName: String): HallOrderEntity?

    @Query("SELECT * FROM hall_orders WHERE eventId = :eventId")
    fun getHallOrdersByEventId(eventId: String): Flow<List<HallOrderEntity>>

    @Query("SELECT * FROM hall_orders WHERE eventId = :eventId")
    suspend fun getHallOrdersByEventIdOnce(eventId: String): List<HallOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHallOrder(hallOrder: HallOrderEntity)

    @Query("DELETE FROM hall_orders WHERE eventId = :eventId AND dayName = :dayName")
    suspend fun deleteHallOrder(eventId: String, dayName: String)

    @Query("DELETE FROM hall_orders WHERE eventId = :eventId")
    suspend fun deleteHallOrdersByEventId(eventId: String)
}