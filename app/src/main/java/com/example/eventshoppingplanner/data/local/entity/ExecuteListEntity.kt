package com.example.eventshoppingplanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 実行列エンティティ
 * イベント×参加日ごとに実行列アイテムIDを順序付きで保存
 * VisitListEntityと相互同期される
 */
@Entity(
    tableName = "execute_lists",
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
data class ExecuteListEntity(
    @PrimaryKey
    val id: String,           // "${eventId}_${eventDate}"
    val eventId: String,
    val eventDate: String,    // "1日目" 等
    val itemIdsJson: String,  // 実行列アイテムIDの順序付きJSON配列
    val updatedAt: Long = System.currentTimeMillis()
)