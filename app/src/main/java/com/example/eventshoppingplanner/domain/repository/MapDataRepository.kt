package com.example.eventshoppingplanner.domain.repository

import com.example.eventshoppingplanner.domain.model.DayMapData
import kotlinx.coroutines.flow.Flow

interface MapDataRepository {
    fun getMapDataByEventId(eventId: String): Flow<Map<String, DayMapData>>
    suspend fun getMapDataByEventIdOnce(eventId: String): Map<String, DayMapData>
    suspend fun saveMapData(eventId: String, mapDataList: Map<String, DayMapData>)
    suspend fun updateMapData(mapData: DayMapData)
    suspend fun deleteMapDataByEventId(eventId: String)
}