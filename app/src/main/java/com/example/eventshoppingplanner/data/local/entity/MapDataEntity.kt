package com.example.eventshoppingplanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * マップデータのEntity
 * 複雑なマップデータ構造はJSON形式で保存
 */
@Entity(tableName = "map_data")
data class MapDataEntity(
    @PrimaryKey
    val id: String,  // eventId + "_" + dayName
    val eventId: String,
    val dayName: String,
    val sheetName: String,
    val jsonData: String,  // DayMapDataをJSON化したもの
    val updatedAt: Long = System.currentTimeMillis()
)