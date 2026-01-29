package com.example.eventshoppingplanner.domain.model

import java.time.Instant

data class Event(
    val id: String,
    val name: String,
    val spreadsheetUrl: String? = null,
    val spreadsheetSheetName: String? = null,
    val lastImportDate: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)