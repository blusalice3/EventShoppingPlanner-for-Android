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

    @Query("SELECT id FROM map_data WHERE eventId = :eventId")
    suspend fun getMapDataIdsByEventId(eventId: String): List<String>

    @Query("SELECT * FROM map_data WHERE id = :id")
    suspend fun getMapDataById(id: String): MapDataEntity?

    /**
     * jsonData を除く軽量メタ取得（CursorWindow 上限回避用）
     */
    @Query("SELECT id, eventId, dayName, sheetName, updatedAt FROM map_data WHERE eventId = :eventId")
    suspend fun getMapDataMetaByEventId(eventId: String): List<MapDataMeta>

    @Query("SELECT id, eventId, dayName, sheetName, updatedAt FROM map_data WHERE eventId = :eventId")
    fun getMapDataMetaByEventIdFlow(eventId: String): Flow<List<MapDataMeta>>

    /**
     * jsonData の文字数を取得
     */
    @Query("SELECT LENGTH(jsonData) FROM map_data WHERE id = :id")
    suspend fun getJsonDataLength(id: String): Int?

    /**
     * jsonData を SUBSTR で部分取得（CursorWindow 上限回避用）
     * start は 1-indexed（SQL の SUBSTR 仕様）
     */
    @Query("SELECT SUBSTR(jsonData, :start, :length) FROM map_data WHERE id = :id")
    suspend fun getJsonDataChunk(id: String, start: Int, length: Int): String?

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
