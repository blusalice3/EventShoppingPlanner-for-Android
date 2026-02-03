package com.example.eventshoppingplanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日ごとのビューモードエンティティ
 * イベント×参加日ごとに edit/execute モードを永続化
 */
@Entity(
    tableName = "day_modes",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["eventId", "eventDate"], unique = true)
    ]
)
data class DayModeEntity(
    @PrimaryKey
    val id: String,           // "${eventId}_${eventDate}"
    val eventId: String,
    val eventDate: String,    // "1日目" 等
    val viewMode: String,     // "edit" or "execute"
    val updatedAt: Long = System.currentTimeMillis()
)