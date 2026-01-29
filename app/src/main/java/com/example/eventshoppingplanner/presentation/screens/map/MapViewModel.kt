package com.example.eventshoppingplanner.presentation.screens.map

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventshoppingplanner.domain.model.*
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import com.example.eventshoppingplanner.util.XlsxMapParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MapUiState(
    val event: Event? = null,
    val isLoading: Boolean = true,
    val mapDataList: Map<String, DayMapData> = emptyMap(),
    val selectedMapName: String? = null,
    val currentMapData: DayMapData? = null,
    val items: List<ShoppingItem> = emptyList(),
    val zoomLevel: ZoomLevel = ZoomLevel.ZOOM_100,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val errorMessage: String? = null,
    val cellItemsMap: Map<String, List<ShoppingItem>> = emptyMap()
)

@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val eventRepository: EventRepository,
    private val itemRepository: ShoppingItemRepository
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadEvent()
        loadItems()
    }

    private fun loadEvent() {
        viewModelScope.launch {
            val event = eventRepository.getEventById(eventId)
            _uiState.update { it.copy(event = event, isLoading = false) }
        }
    }

    private fun loadItems() {
        viewModelScope.launch {
            itemRepository.getItemsByEventId(eventId).collect { items ->
                _uiState.update { it.copy(items = items) }
                updateCellItemsMap()
            }
        }
    }

    fun importMapFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val result = withContext(Dispatchers.IO) {
                    XlsxMapParser.parseMapFile(context, uri, eventId)
                }

                if (result != null && result.isNotEmpty()) {
                    val firstMapName = result.keys.first()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            mapDataList = result,
                            selectedMapName = firstMapName,
                            currentMapData = result[firstMapName]
                        )
                    }
                    updateCellItemsMap()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "マップデータを読み込めませんでした。\n「○日目」という名前のシートが必要です。"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "読み込みエラー: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectMap(mapName: String) {
        val mapData = _uiState.value.mapDataList[mapName]
        _uiState.update {
            it.copy(
                selectedMapName = mapName,
                currentMapData = mapData,
                offsetX = 0f,
                offsetY = 0f
            )
        }
        updateCellItemsMap()
    }

    fun setZoomLevel(level: ZoomLevel) {
        _uiState.update { it.copy(zoomLevel = level) }
    }

    fun zoomIn() {
        val currentIndex = ZoomLevel.entries.indexOf(_uiState.value.zoomLevel)
        if (currentIndex < ZoomLevel.entries.size - 1) {
            _uiState.update { it.copy(zoomLevel = ZoomLevel.entries[currentIndex + 1]) }
        }
    }

    fun zoomOut() {
        val currentIndex = ZoomLevel.entries.indexOf(_uiState.value.zoomLevel)
        if (currentIndex > 0) {
            _uiState.update { it.copy(zoomLevel = ZoomLevel.entries[currentIndex - 1]) }
        }
    }

    fun pan(deltaX: Float, deltaY: Float) {
        _uiState.update {
            it.copy(
                offsetX = it.offsetX + deltaX,
                offsetY = it.offsetY + deltaY
            )
        }
    }

    fun setOffset(x: Float, y: Float) {
        _uiState.update { it.copy(offsetX = x, offsetY = y) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun updateCellItemsMap() {
        val mapData = _uiState.value.currentMapData ?: return
        val items = _uiState.value.items
        val selectedMapName = _uiState.value.selectedMapName ?: return

        val dayName = selectedMapName.replace("マップ", "")

        val cellItemsMap = mutableMapOf<String, MutableList<ShoppingItem>>()

        items.forEach { item ->
            val position = XlsxMapParser.matchItemToCell(item, mapData, dayName)
            if (position != null) {
                val key = "${position.first}-${position.second}"
                cellItemsMap.getOrPut(key) { mutableListOf() }.add(item)
            }
        }

        _uiState.update { it.copy(cellItemsMap = cellItemsMap) }
    }

    fun getItemsForCell(row: Int, col: Int): List<ShoppingItem> {
        return _uiState.value.cellItemsMap["$row-$col"] ?: emptyList()
    }
}