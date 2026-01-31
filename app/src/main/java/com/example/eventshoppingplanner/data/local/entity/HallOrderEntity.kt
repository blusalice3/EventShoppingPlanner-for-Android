package com.example.eventshoppingplanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ホール順序（グループ順序）のエンティティ
 * イベント×日付ごとにホール×優先度の順序を保存
 */
@Entity(
    tableName = "hall_orders",
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
data class HallOrderEntity(
    @PrimaryKey
    val id: String,  // "${eventId}_${dayName}"
    val eventId: String,
    val dayName: String,  // 日付名（例："1日目"）
    val groupOrderJson: String,  // グループID（"hallId_priorityLevel"）の配列をJSON形式で保存
    val updatedAt: Long = System.currentTimeMillis()
)