package com.example.eventshoppingplanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.eventshoppingplanner.data.local.dao.EventDao
import com.example.eventshoppingplanner.data.local.dao.ShoppingItemDao
import com.example.eventshoppingplanner.data.local.entity.EventEntity
import com.example.eventshoppingplanner.data.local.entity.ShoppingItemEntity

@Database(
    entities = [
        EventEntity::class,
        ShoppingItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun shoppingItemDao(): ShoppingItemDao
}