package com.example.eventshoppingplanner.data.repository

import android.util.Base64
import android.util.Log
import com.example.eventshoppingplanner.data.local.dao.MapDataDao
import com.example.eventshoppingplanner.data.local.dao.MapDataMeta
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
    private val tag = "MapDataRepo"
    private val jsonChunkSize = 200_000

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

    /**
     * CursorWindow 上限回避のため jsonData を分割取得する
     */
    private suspend fun readJsonDataByChunks(id: String): String? {
        val totalLength = mapDataDao.getJsonDataLength(id) ?: return null
        if (totalLength <= 0) return ""

        val builder = StringBuilder(totalLength)
        var start = 1 // SQLite SUBSTR は 1-indexed

        while (start <= totalLength) {
            val length = minOf(jsonChunkSize, totalLength - start + 1)
            val chunk = mapDataDao.getJsonDataChunk(id, start, length) ?: return null
            if (chunk.isEmpty()) break
            builder.append(chunk)
            start += chunk.length
        }

        return builder.toString()
    }

    private fun parseDayMapData(entityId: String, storedJsonData: String): DayMapData {
        val jsonData = if (isCompressed(storedJsonData)) {
            decompress(storedJsonData)
        } else {
            storedJsonData
        }
        val mapData = gson.fromJson(jsonData, DayMapData::class.java)
        return if (mapData.id.isNotEmpty() && !mapData.id.startsWith("00000000")) {
            mapData
        } else {
            mapData.copy(id = entityId)
        }
    }

    private suspend fun loadMapDataByMeta(meta: MapDataMeta): DayMapData? {
        val stored = readJsonDataByChunks(meta.id)
        if (stored == null) {
            Log.e(tag, "loadMapDataByMeta: failed to read jsonData, id=${meta.id}")
            return null
        }
        return try {
            parseDayMapData(meta.id, stored)
        } catch (e: Exception) {
            Log.e(tag, "loadMapDataByMeta: failed to parse, id=${meta.id}, dayName=${meta.dayName}", e)
            null
        }
    }

    override fun getMapDataByEventId(eventId: String): Flow<Map<String, DayMapData>> {
        return mapDataDao.getMapDataMetaByEventIdFlow(eventId).map { metas ->
            Log.d(tag, "getMapDataByEventId: found ${metas.size} meta rows")
            buildMap {
                metas.forEach { meta ->
                    val mapData = loadMapDataByMeta(meta)
                    if (mapData != null) {
                        put(meta.dayName, mapData)
                    }
                }
            }
        }
    }

    override suspend fun getMapDataByEventIdOnce(eventId: String): Map<String, DayMapData> {
        Log.d(tag, "getMapDataByEventIdOnce: eventId=$eventId")
        val metas = mapDataDao.getMapDataMetaByEventId(eventId)
        Log.d(tag, "getMapDataByEventIdOnce: found ${metas.size} meta rows")
        return buildMap {
            metas.forEach { meta ->
                val mapData = loadMapDataByMeta(meta)
                if (mapData != null) {
                    Log.d(tag, "  - dayName=${meta.dayName}, id=${meta.id}, mapData.id=${mapData.id}")
                    put(meta.dayName, mapData)
                } else {
                    Log.w(tag, "  - skipped unreadable map data: id=${meta.id}, dayName=${meta.dayName}")
                }
            }
        }
    }

    override suspend fun saveMapData(eventId: String, mapDataList: Map<String, DayMapData>) {
        Log.d(tag, "saveMapData: eventId=$eventId, count=${mapDataList.size}")
        val entities = mapDataList.map { (dayName, mapData) ->
            val json = gson.toJson(mapData)
            val compressed = compress(json)
            // mapData.idを使用して、XlsxMapParserで設定されたIDと一致させる
            Log.d(tag, "  - dayName=$dayName, mapData.id=${mapData.id}, original=${json.length}, compressed=${compressed.length}")
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
        Log.d(tag, "saveMapData: insertAll completed")
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
