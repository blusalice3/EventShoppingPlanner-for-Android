package com.example.eventshoppingplanner.data.repository

import com.example.eventshoppingplanner.data.local.dao.EventDao
import com.example.eventshoppingplanner.data.mapper.toDomain
import com.example.eventshoppingplanner.data.mapper.toDomainList
import com.example.eventshoppingplanner.data.mapper.toEntity
import com.example.eventshoppingplanner.domain.model.Event
import com.example.eventshoppingplanner.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao
) : EventRepository {

    override fun getAllEvents(): Flow<List<Event>> {
        return eventDao.getAllEvents().map { it.toDomainList() }
    }

    override suspend fun getEventById(eventId: String): Event? {
        return eventDao.getEventById(eventId)?.toDomain()
    }

    override suspend fun insertEvent(event: Event) {
        eventDao.insertEvent(event.toEntity())
    }

    override suspend fun updateEvent(event: Event) {
        eventDao.updateEvent(event.toEntity())
    }

    override suspend fun deleteEvent(event: Event) {
        eventDao.deleteEvent(event.toEntity())
    }

    override suspend fun deleteEventById(eventId: String) {
        eventDao.deleteEventById(eventId)
    }
}