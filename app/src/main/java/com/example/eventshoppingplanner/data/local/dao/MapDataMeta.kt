package com.example.eventshoppingplanner.data.local.dao

/**
 * MapDataEntity から jsonData を除いた軽量メタデータ。
 * CursorWindow 上限（2MB）を回避するためのクエリ結果用。
 * Room の部分クエリ結果として使用。
 */
data class MapDataMeta(
    val id: String,
    val eventId: String,
    val dayName: String,
    val sheetName: String,
    val updatedAt: Long
)