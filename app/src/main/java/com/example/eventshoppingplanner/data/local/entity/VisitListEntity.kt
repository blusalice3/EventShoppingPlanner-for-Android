package com.example.eventshoppingplanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 訪問先リストのエンティティ
 * イベント×日付ごとに訪問先アイテムIDを順序付きで保存
 */
@Entity(
    tableName = "visit_lists",
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
        Index(value = ["eventId", "dayName"], unique = true)
    ]
)
data class VisitListEntity(
    @PrimaryKey
    val id: String,  // "${eventId}_${dayName}"
    val eventId: String,
    val dayName: String,  // 日付名（例："1日目"）
    val itemIdsJson: String,  // アイテムIDの配列をJSON形式で保存（訪問順）
    val updatedAt: Long = System.currentTimeMillis()
)