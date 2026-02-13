package com.example.eventshoppingplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.eventshoppingplanner.data.local.entity.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items WHERE eventId = :eventId ORDER BY sortOrder ASC")
    fun getItemsByEventId(eventId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE eventId = :eventId ORDER BY sortOrder ASC")
    suspend fun getItemsByEventIdOnce(eventId: String): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE eventId = :eventId AND eventDate = :eventDate ORDER BY sortOrder ASC")
    fun getItemsByEventIdAndDate(eventId: String, eventDate: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE eventId = :eventId AND isInExecuteList = 1 ORDER BY sortOrder ASC")
    fun getExecuteListItems(eventId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE id = :itemId")
    suspend fun getItemById(itemId: String): ShoppingItemEntity?

    @Query("SELECT DISTINCT eventDate FROM shopping_items WHERE eventId = :eventId")
    fun getEventDates(eventId: String): Flow<List<String>>

    @Query("SELECT DISTINCT block FROM shopping_items WHERE eventId = :eventId")
    fun getBlocks(eventId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Update
    suspend fun updateItem(item: ShoppingItemEntity)

    @Query("UPDATE shopping_items SET purchaseStatus = :status WHERE id = :itemId")
    suspend fun updatePurchaseStatus(itemId: String, status: String)

    @Query("UPDATE shopping_items SET sortOrder = :sortOrder WHERE id = :itemId")
    suspend fun updateSortOrder(itemId: String, sortOrder: Int)

    @Query("UPDATE shopping_items SET isInExecuteList = :isInExecuteList WHERE id = :itemId")
    suspend fun updateExecuteListStatus(itemId: String, isInExecuteList: Boolean)

    @Delete
    suspend fun deleteItem(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: String)

    @Query("DELETE FROM shopping_items WHERE eventId = :eventId")
    suspend fun deleteItemsByEventId(eventId: String)

    @Query("""
        SELECT * FROM shopping_items 
        WHERE eventId = :eventId 
        AND (circle LIKE '%' || :query || '%' 
             OR title LIKE '%' || :query || '%' 
             OR remarks LIKE '%' || :query || '%')
        ORDER BY sortOrder ASC
    """)
    fun searchItems(eventId: String, query: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT COUNT(*) FROM shopping_items WHERE eventId = :eventId")
    suspend fun getItemCount(eventId: String): Int

    @Query("SELECT COUNT(*) FROM shopping_items WHERE eventId = :eventId AND purchaseStatus = 'PURCHASED'")
    suspend fun getPurchasedCount(eventId: String): Int

    @Query("SELECT SUM(price * quantity) FROM shopping_items WHERE eventId = :eventId AND purchaseStatus NOT IN ('PURCHASED', 'SOLD_OUT', 'ABSENT')")
    suspend fun getRemainingTotal(eventId: String): Int?
}