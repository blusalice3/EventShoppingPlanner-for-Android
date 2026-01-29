package com.example.eventshoppingplanner.data.mapper

import com.example.eventshoppingplanner.data.local.entity.EventEntity
import com.example.eventshoppingplanner.data.local.entity.ShoppingItemEntity
import com.example.eventshoppingplanner.domain.model.Event
import com.example.eventshoppingplanner.domain.model.ItemSource
import com.example.eventshoppingplanner.domain.model.PriorityLevel
import com.example.eventshoppingplanner.domain.model.ProtectionLevel
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import java.time.Instant

// Event Mappers
fun EventEntity.toDomain(): Event {
    return Event(
        id = id,
        name = name,
        spreadsheetUrl = spreadsheetUrl,
        spreadsheetSheetName = spreadsheetSheetName,
        lastImportDate = lastImportDate?.let { Instant.ofEpochMilli(it) },
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )
}

fun Event.toEntity(): EventEntity {
    return EventEntity(
        id = id,
        name = name,
        spreadsheetUrl = spreadsheetUrl,
        spreadsheetSheetName = spreadsheetSheetName,
        lastImportDate = lastImportDate?.toEpochMilli(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )
}

// ShoppingItem Mappers
fun ShoppingItemEntity.toDomain(): ShoppingItem {
    return ShoppingItem(
        id = id,
        eventId = eventId,
        circle = circle,
        eventDate = eventDate,
        block = block,
        number = number,
        title = title,
        price = price,
        purchaseStatus = PurchaseStatus.fromString(purchaseStatus),
        quantity = quantity,
        remarks = remarks,
        url = url,
        priorityLevel = PriorityLevel.valueOf(priorityLevel),
        protectionLevel = ProtectionLevel.valueOf(protectionLevel),
        source = ItemSource.valueOf(source),
        sortOrder = sortOrder,
        isInExecuteList = isInExecuteList
    )
}

fun ShoppingItem.toEntity(): ShoppingItemEntity {
    return ShoppingItemEntity(
        id = id,
        eventId = eventId,
        circle = circle,
        eventDate = eventDate,
        block = block,
        number = number,
        title = title,
        price = price,
        purchaseStatus = purchaseStatus.name,
        quantity = quantity,
        remarks = remarks,
        url = url,
        priorityLevel = priorityLevel.name,
        protectionLevel = protectionLevel.name,
        source = source.name,
        sortOrder = sortOrder,
        isInExecuteList = isInExecuteList
    )
}

fun List<EventEntity>.toDomainList(): List<Event> = map { it.toDomain() }
fun List<ShoppingItemEntity>.toItemDomainList(): List<ShoppingItem> = map { it.toDomain() }