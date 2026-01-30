package com.example.eventshoppingplanner.presentation.screens.eventlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventshoppingplanner.domain.model.Event
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class EventWithStats(
    val event: Event,
    val itemCount: Int,
    val purchasedCount: Int
)

data class EventListUiState(
    val events: List<EventWithStats> = emptyList(),
    val isLoading: Boolean = true,
    val selectedEvent: Event? = null,
    val showDeleteDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showCreateDialog: Boolean = false
)

@HiltViewModel
class EventListViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val itemRepository: ShoppingItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventListUiState())
    val uiState: StateFlow<EventListUiState> = _uiState.asStateFlow()

    init {
        loadEvents()
    }

    private fun loadEvents() {
        Log.d("EventListVM", "loadEvents: START")
        viewModelScope.launch {
            try {
                eventRepository.getAllEvents()
                    .collect { events ->
                        Log.d("EventListVM", "loadEvents: received ${events.size} events")
                        val eventsWithStats = events.map { event ->
                            EventWithStats(
                                event = event,
                                itemCount = itemRepository.getItemCount(event.id),
                                purchasedCount = itemRepository.getPurchasedCount(event.id)
                            )
                        }
                        _uiState.update {
                            it.copy(events = eventsWithStats, isLoading = false)
                        }
                        Log.d("EventListVM", "loadEvents: isLoading set to false")
                    }
            } catch (e: Exception) {
                Log.e("EventListVM", "loadEvents: error", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun showCreateDialog() {
        Log.d("EventListVM", "showCreateDialog: called")
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun createEvent(name: String) {
        Log.d("EventListVM", "createEvent: name=$name")
        viewModelScope.launch {
            val event = Event(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            eventRepository.insertEvent(event)
            Log.d("EventListVM", "createEvent: inserted event id=${event.id}")
            hideCreateDialog()
        }
    }

    fun selectEvent(event: Event) {
        _uiState.update { it.copy(selectedEvent = event) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedEvent = null) }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, selectedEvent = null) }
    }

    fun deleteSelectedEvent() {
        viewModelScope.launch {
            _uiState.value.selectedEvent?.let { event ->
                eventRepository.deleteEvent(event)
            }
            hideDeleteDialog()
        }
    }

    fun showRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = true) }
    }

    fun hideRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false, selectedEvent = null) }
    }

    fun renameSelectedEvent(newName: String) {
        viewModelScope.launch {
            _uiState.value.selectedEvent?.let { event ->
                val updated = event.copy(name = newName, updatedAt = Instant.now())
                eventRepository.updateEvent(updated)
            }
            hideRenameDialog()
        }
    }
}