package com.example.eventshoppingplanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.eventshoppingplanner.data.local.dao.DayModeDao
import com.example.eventshoppingplanner.data.local.dao.EventDao
import com.example.eventshoppingplanner.data.local.dao.ExecuteListDao
import com.example.eventshoppingplanner.data.local.dao.HallDefinitionDao
import com.example.eventshoppingplanner.data.local.dao.HallOrderDao
import com.example.eventshoppingplanner.data.local.dao.MapDataDao
import com.example.eventshoppingplanner.data.local.dao.ShoppingItemDao
import com.example.eventshoppingplanner.data.local.dao.VisitListDao
import com.example.eventshoppingplanner.data.local.entity.DayModeEntity
import com.example.eventshoppingplanner.data.local.entity.EventEntity
import com.example.eventshoppingplanner.data.local.entity.ExecuteListEntity
import com.example.eventshoppingplanner.data.local.entity.HallDefinitionEntity
import com.example.eventshoppingplanner.data.local.entity.HallOrderEntity
import com.example.eventshoppingplanner.data.local.entity.MapDataEntity
import com.example.eventshoppingplanner.data.local.entity.ShoppingItemEntity
import com.example.eventshoppingplanner.data.local.entity.VisitListEntity

@Database(
    entities = [
        EventEntity::class,
        ShoppingItemEntity::class,
        MapDataEntity::class,
        HallDefinitionEntity::class,
        VisitListEntity::class,
        HallOrderEntity::class,
        ExecuteListEntity::class,
        DayModeEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun mapDataDao(): MapDataDao
    abstract fun hallDefinitionDao(): HallDefinitionDao
    abstract fun visitListDao(): VisitListDao
    abstract fun hallOrderDao(): HallOrderDao
    abstract fun executeListDao(): ExecuteListDao
    abstract fun dayModeDao(): DayModeDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 訪問先リストテーブル
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS visit_lists (
                        id TEXT PRIMARY KEY NOT NULL,
                        eventId TEXT NOT NULL,
                        dayName TEXT NOT NULL,
                        itemIdsJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (eventId) REFERENCES events(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_visit_lists_eventId ON visit_lists(eventId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_visit_lists_eventId_dayName ON visit_lists(eventId, dayName)")

                // ホール順序テーブル
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS hall_orders (
                        id TEXT PRIMARY KEY NOT NULL,
                        eventId TEXT NOT NULL,
                        dayName TEXT NOT NULL,
                        groupOrderJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (eventId) REFERENCES events(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_hall_orders_eventId ON hall_orders(eventId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_hall_orders_eventId_dayName ON hall_orders(eventId, dayName)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 実行列テーブル
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS execute_lists (
                        id TEXT PRIMARY KEY NOT NULL,
                        eventId TEXT NOT NULL,
                        eventDate TEXT NOT NULL,
                        itemIdsJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (eventId) REFERENCES events(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_execute_lists_eventId ON execute_lists(eventId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_execute_lists_eventId_eventDate ON execute_lists(eventId, eventDate)")

                // 日ごとモードテーブル
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS day_modes (
                        id TEXT PRIMARY KEY NOT NULL,
                        eventId TEXT NOT NULL,
                        eventDate TEXT NOT NULL,
                        viewMode TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (eventId) REFERENCES events(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_day_modes_eventId ON day_modes(eventId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_day_modes_eventId_eventDate ON day_modes(eventId, eventDate)")
            }
        }
    }
}