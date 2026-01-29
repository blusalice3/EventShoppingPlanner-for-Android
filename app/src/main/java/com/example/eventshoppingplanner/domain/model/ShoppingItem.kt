package com.example.eventshoppingplanner.domain.model

data class ShoppingItem(
    val id: String,
    val eventId: String,
    val circle: String,
    val eventDate: String,
    val block: String,
    val number: String,
    val title: String,
    val price: Int?,
    val purchaseStatus: PurchaseStatus,
    val quantity: Int = 1,
    val remarks: String = "",
    val url: String? = null,
    val priorityLevel: PriorityLevel = PriorityLevel.NONE,
    val protectionLevel: ProtectionLevel = ProtectionLevel.NONE,
    val source: ItemSource = ItemSource.APP,
    val sortOrder: Int = 0,
    val isInExecuteList: Boolean = false
) {
    val locationDisplay: String
        get() = "$block-$number"

    val priceDisplay: String
        get() = price?.let { "¥$it" } ?: "---"
}

enum class PriorityLevel {
    NONE, PRIORITY, HIGHEST
}

enum class ProtectionLevel {
    FULL, DELETABLE, NONE
}

enum class ItemSource {
    SPREADSHEET, APP
}