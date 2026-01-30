package com.example.eventshoppingplanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.eventshoppingplanner.data.local.dao.EventDao
import com.example.eventshoppingplanner.data.local.dao.HallDefinitionDao
import com.example.eventshoppingplanner.data.local.dao.MapDataDao
import com.example.eventshoppingplanner.data.local.dao.ShoppingItemDao
import com.example.eventshoppingplanner.data.local.entity.EventEntity
import com.example.eventshoppingplanner.data.local.entity.HallDefinitionEntity
import com.example.eventshoppingplanner.data.local.entity.MapDataEntity
import com.example.eventshoppingplanner.data.local.entity.ShoppingItemEntity

@Database(
    entities = [
        EventEntity::class,
        ShoppingItemEntity::class,
        MapDataEntity::class,
        HallDefinitionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun mapDataDao(): MapDataDao
    abstract fun hallDefinitionDao(): HallDefinitionDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS hall_definitions (
                        id TEXT PRIMARY KEY NOT NULL,
                        mapDataId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        verticesJson TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (mapDataId) REFERENCES map_data(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_hall_definitions_mapDataId ON hall_definitions(mapDataId)")
            }
        }
    }
}