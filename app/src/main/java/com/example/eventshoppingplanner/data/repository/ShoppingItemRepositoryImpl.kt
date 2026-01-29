package com.example.eventshoppingplanner.data.repository

import com.example.eventshoppingplanner.data.local.dao.ShoppingItemDao
import com.example.eventshoppingplanner.data.mapper.toDomain
import com.example.eventshoppingplanner.data.mapper.toEntity
import com.example.eventshoppingplanner.data.mapper.toItemDomainList
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ShoppingItemRepositoryImpl @Inject constructor(
    private val shoppingItemDao: ShoppingItemDao
) : ShoppingItemRepository {

    override fun getItemsByEventId(eventId: String): Flow<List<ShoppingItem>> {
        return shoppingItemDao.getItemsByEventId(eventId).map { it.toItemDomainList() }
    }

    override fun getItemsByEventIdAndDate(eventId: String, eventDate: String): Flow<List<ShoppingItem>> {
        return shoppingItemDao.getItemsByEventIdAndDate(eventId, eventDate).map { it.toItemDomainList() }
    }

    override fun getExecuteListItems(eventId: String): Flow<List<ShoppingItem>> {
        return shoppingItemDao.getExecuteListItems(eventId).map { it.toItemDomainList() }
    }

    override suspend fun getItemById(itemId: String): ShoppingItem? {
        return shoppingItemDao.getItemById(itemId)?.toDomain()
    }

    override fun getEventDates(eventId: String): Flow<List<String>> {
        return shoppingItemDao.getEventDates(eventId)
    }

    override fun getBlocks(eventId: String): Flow<List<String>> {
        return shoppingItemDao.getBlocks(eventId)
    }

    override suspend fun insertItem(item: ShoppingItem) {
        shoppingItemDao.insertItem(item.toEntity())
    }

    override suspend fun insertItems(items: List<ShoppingItem>) {
        shoppingItemDao.insertItems(items.map { it.toEntity() })
    }

    override suspend fun updateItem(item: ShoppingItem) {
        shoppingItemDao.updateItem(item.toEntity())
    }

    override suspend fun updatePurchaseStatus(itemId: String, status: PurchaseStatus) {
        shoppingItemDao.updatePurchaseStatus(itemId, status.name)
    }

    override suspend fun updateSortOrder(itemId: String, sortOrder: Int) {
        shoppingItemDao.updateSortOrder(itemId, sortOrder)
    }

    override suspend fun updateExecuteListStatus(itemId: String, isInExecuteList: Boolean) {
        shoppingItemDao.updateExecuteListStatus(itemId, isInExecuteList)
    }

    override suspend fun deleteItem(item: ShoppingItem) {
        shoppingItemDao.deleteItem(item.toEntity())
    }

    override suspend fun deleteItemById(itemId: String) {
        shoppingItemDao.deleteItemById(itemId)
    }

    override fun searchItems(eventId: String, query: String): Flow<List<ShoppingItem>> {
        return shoppingItemDao.searchItems(eventId, query).map { it.toItemDomainList() }
    }

    override suspend fun getItemCount(eventId: String): Int {
        return shoppingItemDao.getItemCount(eventId)
    }

    override suspend fun getPurchasedCount(eventId: String): Int {
        return shoppingItemDao.getPurchasedCount(eventId)
    }

    override suspend fun getRemainingTotal(eventId: String): Int {
        return shoppingItemDao.getRemainingTotal(eventId) ?: 0
    }
}