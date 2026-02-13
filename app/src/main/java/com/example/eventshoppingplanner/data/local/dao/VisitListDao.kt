package com.example.eventshoppingplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.eventshoppingplanner.data.local.entity.VisitListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitListDao {

    @Query("SELECT * FROM visit_lists WHERE eventId = :eventId AND dayName = :dayName")
    fun getVisitList(eventId: String, dayName: String): Flow<VisitListEntity?>

    @Query("SELECT * FROM visit_lists WHERE eventId = :eventId AND dayName = :dayName")
    suspend fun getVisitListOnce(eventId: String, dayName: String): VisitListEntity?

    @Query("SELECT * FROM visit_lists WHERE eventId = :eventId")
    fun getVisitListsByEventId(eventId: String): Flow<List<VisitListEntity>>

    @Query("SELECT * FROM visit_lists WHERE eventId = :eventId")
    suspend fun getVisitListsByEventIdOnce(eventId: String): List<VisitListEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisitList(visitList: VisitListEntity)

    @Query("DELETE FROM visit_lists WHERE eventId = :eventId AND dayName = :dayName")
    suspend fun deleteVisitList(eventId: String, dayName: String)

    @Query("DELETE FROM visit_lists WHERE eventId = :eventId")
    suspend fun deleteVisitListsByEventId(eventId: String)
}