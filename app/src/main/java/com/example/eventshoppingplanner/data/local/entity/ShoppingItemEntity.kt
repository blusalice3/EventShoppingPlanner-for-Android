package com.example.eventshoppingplanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_items",
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
        Index(value = ["eventId", "eventDate"]),
        Index(value = ["eventId", "block"])
    ]
)
data class ShoppingItemEntity(
    @PrimaryKey
    val id: String,
    val eventId: String,
    val circle: String,
    val eventDate: String,
    val block: String,
    val number: String,
    val title: String,
    val price: Int?,
    val purchaseStatus: String,
    val quantity: Int,
    val remarks: String,
    val url: String?,
    val priorityLevel: String,
    val protectionLevel: String,
    val source: String,
    val sortOrder: Int,
    val isInExecuteList: Boolean
)