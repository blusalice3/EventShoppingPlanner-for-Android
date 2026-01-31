package com.example.eventshoppingplanner.di

import android.content.Context
import androidx.room.Room
import com.example.eventshoppingplanner.data.local.dao.EventDao
import com.example.eventshoppingplanner.data.local.dao.HallDefinitionDao
import com.example.eventshoppingplanner.data.local.dao.HallOrderDao
import com.example.eventshoppingplanner.data.local.dao.MapDataDao
import com.example.eventshoppingplanner.data.local.dao.ShoppingItemDao
import com.example.eventshoppingplanner.data.local.dao.VisitListDao
import com.example.eventshoppingplanner.data.local.database.AppDatabase
import com.example.eventshoppingplanner.data.repository.EventRepositoryImpl
import com.example.eventshoppingplanner.data.repository.MapDataRepositoryImpl
import com.example.eventshoppingplanner.data.repository.ShoppingItemRepositoryImpl
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.MapDataRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "event_shopping_planner.db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideEventDao(database: AppDatabase): EventDao {
        return database.eventDao()
    }

    @Provides
    @Singleton
    fun provideShoppingItemDao(database: AppDatabase): ShoppingItemDao {
        return database.shoppingItemDao()
    }

    @Provides
    @Singleton
    fun provideMapDataDao(database: AppDatabase): MapDataDao {
        return database.mapDataDao()
    }

    @Provides
    @Singleton
    fun provideHallDefinitionDao(database: AppDatabase): HallDefinitionDao {
        return database.hallDefinitionDao()
    }

    @Provides
    @Singleton
    fun provideVisitListDao(database: AppDatabase): VisitListDao {
        return database.visitListDao()
    }

    @Provides
    @Singleton
    fun provideHallOrderDao(database: AppDatabase): HallOrderDao {
        return database.hallOrderDao()
    }

    @Provides
    @Singleton
    fun provideEventRepository(
        eventDao: EventDao
    ): EventRepository {
        return EventRepositoryImpl(eventDao)
    }

    @Provides
    @Singleton
    fun provideShoppingItemRepository(
        shoppingItemDao: ShoppingItemDao
    ): ShoppingItemRepository {
        return ShoppingItemRepositoryImpl(shoppingItemDao)
    }

    @Provides
    @Singleton
    fun provideMapDataRepository(
        mapDataDao: MapDataDao
    ): MapDataRepository {
        return MapDataRepositoryImpl(mapDataDao)
    }
}