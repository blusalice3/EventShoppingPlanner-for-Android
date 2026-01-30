package com.example.eventshoppingplanner.data.local.dao

import androidx.room.*
import com.example.eventshoppingplanner.data.local.entity.MapDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MapDataDao {

    @Query("SELECT * FROM map_data WHERE eventId = :eventId")
    fun getMapDataByEventId(eventId: String): Flow<List<MapDataEntity>>

    @Query("SELECT * FROM map_data WHERE eventId = :eventId")
    suspend fun getMapDataByEventIdOnce(eventId: String): List<MapDataEntity>

    @Query("SELECT * FROM map_data WHERE id = :id")
    suspend fun getMapDataById(id: String): MapDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapData: MapDataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mapDataList: List<MapDataEntity>)

    @Update
    suspend fun update(mapData: MapDataEntity)

    @Delete
    suspend fun delete(mapData: MapDataEntity)

    @Query("DELETE FROM map_data WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("DELETE FROM map_data WHERE id = :id")
    suspend fun deleteById(id: String)
}