package com.example.eventshoppingplanner.domain.model

enum class PurchaseStatus(val displayName: String, val colorHex: Long) {
    NONE("未購入", 0xFF9E9E9E),
    PURCHASED("購入済", 0xFF4CAF50),
    SOLD_OUT("売切", 0xFFF44336),
    ABSENT("欠席", 0xFFFFEB3B),
    POSTPONE("後回し", 0xFF9C27B0),
    LATE("遅参", 0xFF2196F3);

    companion object {
        fun fromString(value: String): PurchaseStatus {
            return entries.find { it.name == value } ?: NONE
        }
    }
}