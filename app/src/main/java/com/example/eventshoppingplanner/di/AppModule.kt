package com.example.eventshoppingplanner.di

import android.content.Context
import androidx.room.Room
import com.example.eventshoppingplanner.data.local.dao.EventDao
import com.example.eventshoppingplanner.data.local.dao.ShoppingItemDao
import com.example.eventshoppingplanner.data.local.database.AppDatabase
import com.example.eventshoppingplanner.data.repository.EventRepositoryImpl
import com.example.eventshoppingplanner.data.repository.ShoppingItemRepositoryImpl
import com.example.eventshoppingplanner.domain.repository.EventRepository
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
        ).build()
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
}