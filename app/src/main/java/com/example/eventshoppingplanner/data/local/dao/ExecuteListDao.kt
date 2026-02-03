package com.example.eventshoppingplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.eventshoppingplanner.data.local.entity.ExecuteListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecuteListDao {

    @Query("SELECT * FROM execute_lists WHERE eventId = :eventId")
    fun getExecuteListsByEventId(eventId: String): Flow<List<ExecuteListEntity>>

    @Query("SELECT * FROM execute_lists WHERE eventId = :eventId")
    suspend fun getExecuteListsByEventIdOnce(eventId: String): List<ExecuteListEntity>

    @Query("SELECT * FROM execute_lists WHERE eventId = :eventId AND eventDate = :eventDate")
    suspend fun getExecuteList(eventId: String, eventDate: String): ExecuteListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecuteList(entity: ExecuteListEntity)

    @Query("DELETE FROM execute_lists WHERE eventId = :eventId AND eventDate = :eventDate")
    suspend fun deleteExecuteList(eventId: String, eventDate: String)

    @Query("DELETE FROM execute_lists WHERE eventId = :eventId")
    suspend fun deleteExecuteListsByEventId(eventId: String)
}