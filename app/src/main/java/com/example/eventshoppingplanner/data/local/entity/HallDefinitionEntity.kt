package com.example.eventshoppingplanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ホール定義のEntity
 * 頂点データはJSON形式で保存
 */
@Entity(
    tableName = "hall_definitions",
    foreignKeys = [
        ForeignKey(
            entity = MapDataEntity::class,
            parentColumns = ["id"],
            childColumns = ["mapDataId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mapDataId")]
)
data class HallDefinitionEntity(
    @PrimaryKey
    val id: String,
    val mapDataId: String,  // map_data.id への外部キー
    val name: String,
    val verticesJson: String,  // List<Vertex>をJSON化
    val color: Long,
    val updatedAt: Long = System.currentTimeMillis()
)