package com.example.eventshoppingplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.eventshoppingplanner.data.local.entity.HallDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HallDefinitionDao {

    @Query("SELECT * FROM hall_definitions WHERE mapDataId = :mapDataId ORDER BY name ASC")
    fun getHallsByMapDataId(mapDataId: String): Flow<List<HallDefinitionEntity>>

    @Query("SELECT * FROM hall_definitions WHERE mapDataId = :mapDataId ORDER BY name ASC")
    suspend fun getHallsByMapDataIdOnce(mapDataId: String): List<HallDefinitionEntity>

    @Query("SELECT * FROM hall_definitions WHERE id = :id")
    suspend fun getHallById(id: String): HallDefinitionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHall(hall: HallDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHalls(halls: List<HallDefinitionEntity>)

    @Delete
    suspend fun deleteHall(hall: HallDefinitionEntity)

    @Query("DELETE FROM hall_definitions WHERE id = :id")
    suspend fun deleteHallById(id: String)

    @Query("DELETE FROM hall_definitions WHERE mapDataId = :mapDataId")
    suspend fun deleteHallsByMapDataId(mapDataId: String)
}