package com.example.eventshoppingplanner.domain.repository

import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

interface ShoppingItemRepository {
    fun getItemsByEventId(eventId: String): Flow<List<ShoppingItem>>
    fun getItemsByEventIdAndDate(eventId: String, eventDate: String): Flow<List<ShoppingItem>>
    fun getExecuteListItems(eventId: String): Flow<List<ShoppingItem>>
    suspend fun getItemById(itemId: String): ShoppingItem?
    fun getEventDates(eventId: String): Flow<List<String>>
    fun getBlocks(eventId: String): Flow<List<String>>
    suspend fun insertItem(item: ShoppingItem)
    suspend fun insertItems(items: List<ShoppingItem>)
    suspend fun updateItem(item: ShoppingItem)
    suspend fun updatePurchaseStatus(itemId: String, status: PurchaseStatus)
    suspend fun updateSortOrder(itemId: String, sortOrder: Int)
    suspend fun updateExecuteListStatus(itemId: String, isInExecuteList: Boolean)
    suspend fun deleteItem(item: ShoppingItem)
    suspend fun deleteItemById(itemId: String)
    fun searchItems(eventId: String, query: String): Flow<List<ShoppingItem>>
    suspend fun getItemCount(eventId: String): Int
    suspend fun getPurchasedCount(eventId: String): Int
    suspend fun getRemainingTotal(eventId: String): Int
}