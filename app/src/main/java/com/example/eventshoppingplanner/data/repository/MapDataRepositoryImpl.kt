package com.example.eventshoppingplanner.data.repository

import android.util.Base64
import android.util.Log
import com.example.eventshoppingplanner.data.local.dao.MapDataDao
import com.example.eventshoppingplanner.data.local.entity.MapDataEntity
import com.example.eventshoppingplanner.domain.model.DayMapData
import com.example.eventshoppingplanner.domain.repository.MapDataRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

class MapDataRepositoryImpl @Inject constructor(
    private val mapDataDao: MapDataDao
) : MapDataRepository {

    private val gson: Gson = GsonBuilder().create()

    /**
     * JSONをGZIP圧縮してBase64エンコード
     */
    private fun compress(data: String): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use { gzip ->
            gzip.write(data.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Base64デコードしてGZIP解凍
     */
    private fun decompress(compressed: String): String {
        val bytes = Base64.decode(compressed, Base64.NO_WRAP)
        val byteArrayInputStream = ByteArrayInputStream(bytes)
        GZIPInputStream(byteArrayInputStream).use { gzip ->
            return gzip.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    /**
     * データが圧縮されているかどうかを判定
     * （JSON は { で始まるため、それ以外は圧縮データと判断）
     */
    private fun isCompressed(data: String): Boolean {
        return !data.trimStart().startsWith("{")
    }

    override fun getMapDataByEventId(eventId: String): Flow<Map<String, DayMapData>> {
        return mapDataDao.getMapDataByEventId(eventId).map { entities ->
            Log.d("MapDataRepo", "getMapDataByEventId: found ${entities.size} entities")
            entities.associate { entity ->
                val jsonData = if (isCompressed(entity.jsonData)) {
                    decompress(entity.jsonData)
                } else {
                    entity.jsonData
                }
                val mapData = gson.fromJson(jsonData, DayMapData::class.java)
                // mapData.idがない（古いデータ）場合はentity.idを使用
                val mapDataWithId = if (mapData.id.isNotEmpty() && !mapData.id.startsWith("00000000")) {
                    mapData
                } else {
                    mapData.copy(id = entity.id)
                }
                entity.dayName to mapDataWithId
            }
        }
    }

    override suspend fun getMapDataByEventIdOnce(eventId: String): Map<String, DayMapData> {
        Log.d("MapDataRepo", "getMapDataByEventIdOnce: eventId=$eventId")
        val entities = mapDataDao.getMapDataByEventIdOnce(eventId)
        Log.d("MapDataRepo", "getMapDataByEventIdOnce: found ${entities.size} entities")
        return entities.associate { entity ->
            val jsonData = if (isCompressed(entity.jsonData)) {
                decompress(entity.jsonData)
            } else {
                entity.jsonData
            }
            val mapData = gson.fromJson(jsonData, DayMapData::class.java)
            // mapData.idがない（古いデータ）場合はentity.idを使用
            val mapDataWithId = if (mapData.id.isNotEmpty() && !mapData.id.startsWith("00000000")) {
                mapData
            } else {
                mapData.copy(id = entity.id)
            }
            Log.d("MapDataRepo", "  - dayName=${entity.dayName}, entity.id=${entity.id}, mapData.id=${mapDataWithId.id}")
            entity.dayName to mapDataWithId
        }
    }

    override suspend fun saveMapData(eventId: String, mapDataList: Map<String, DayMapData>) {
        Log.d("MapDataRepo", "saveMapData: eventId=$eventId, count=${mapDataList.size}")
        val entities = mapDataList.map { (dayName, mapData) ->
            val json = gson.toJson(mapData)
            val compressed = compress(json)
            // mapData.idを使用して、XlsxMapParserで設定されたIDと一致させる
            Log.d("MapDataRepo", "  - dayName=$dayName, mapData.id=${mapData.id}, original=${json.length}, compressed=${compressed.length}")
            MapDataEntity(
                id = mapData.id,  // mapData.idをそのまま使用
                eventId = eventId,
                dayName = dayName,
                sheetName = mapData.sheetName,
                jsonData = compressed,
                updatedAt = System.currentTimeMillis()
            )
        }
        mapDataDao.insertAll(entities)
        Log.d("MapDataRepo", "saveMapData: insertAll completed")
    }

    override suspend fun updateMapData(mapData: DayMapData) {
        val json = gson.toJson(mapData)
        val compressed = compress(json)
        val entity = MapDataEntity(
            id = mapData.id,  // mapData.idをそのまま使用
            eventId = mapData.eventId,
            dayName = mapData.dayName,
            sheetName = mapData.sheetName,
            jsonData = compressed,
            updatedAt = System.currentTimeMillis()
        )
        mapDataDao.update(entity)
    }

    override suspend fun deleteMapDataByEventId(eventId: String) {
        mapDataDao.deleteByEventId(eventId)
    }
}