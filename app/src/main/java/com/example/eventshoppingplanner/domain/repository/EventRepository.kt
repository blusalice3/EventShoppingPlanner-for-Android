package com.example.eventshoppingplanner.domain.repository

import com.example.eventshoppingplanner.domain.model.Event
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun getAllEvents(): Flow<List<Event>>
    suspend fun getEventById(eventId: String): Event?
    suspend fun insertEvent(event: Event)
    suspend fun updateEvent(event: Event)
    suspend fun deleteEvent(event: Event)
    suspend fun deleteEventById(eventId: String)
}