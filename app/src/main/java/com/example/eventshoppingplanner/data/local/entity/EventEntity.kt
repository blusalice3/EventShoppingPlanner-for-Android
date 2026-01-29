package com.example.eventshoppingplanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val spreadsheetUrl: String?,
    val spreadsheetSheetName: String?,
    val lastImportDate: Long?,
    val createdAt: Long,
    val updatedAt: Long
)