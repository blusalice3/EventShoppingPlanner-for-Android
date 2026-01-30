package com.example.eventshoppingplanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.eventshoppingplanner.data.local.dao.EventDao
import com.example.eventshoppingplanner.data.local.dao.MapDataDao
import com.example.eventshoppingplanner.data.local.dao.ShoppingItemDao
import com.example.eventshoppingplanner.data.local.entity.EventEntity
import com.example.eventshoppingplanner.data.local.entity.MapDataEntity
import com.example.eventshoppingplanner.data.local.entity.ShoppingItemEntity

@Database(
    entities = [
        EventEntity::class,
        ShoppingItemEntity::class,
        MapDataEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun mapDataDao(): MapDataDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS map_data (
                        id TEXT PRIMARY KEY NOT NULL,
                        eventId TEXT NOT NULL,
                        dayName TEXT NOT NULL,
                        sheetName TEXT NOT NULL,
                        jsonData TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}