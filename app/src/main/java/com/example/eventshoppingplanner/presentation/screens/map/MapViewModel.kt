package com.example.eventshoppingplanner.presentation.screens.map

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventshoppingplanner.domain.model.*
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.MapDataRepository
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
    val cellItemsMap: Map<String, List<ShoppingItem>> = emptyMap(),
    // ブロック編集関連
    val isBlockDefinitionPanelOpen: Boolean = false,
    val cellSelectionMode: CellSelectionMode = CellSelectionMode.NONE,
    val selectedCells: List<Pair<Int, Int>> = emptyList()
)

/**
 * セル選択モード
 */
enum class CellSelectionMode {
    NONE,           // 通常モード
    CORNER_SELECT   // 4角選択モード
}

@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val eventRepository: EventRepository,
    private val itemRepository: ShoppingItemRepository,
    private val mapDataRepository: MapDataRepository
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadEvent()
        loadItems()
        loadSavedMapData()
    }

    private fun loadEvent() {
        viewModelScope.launch {
            val event = eventRepository.getEventById(eventId)
            _uiState.update { it.copy(event = event) }
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

    /**
     * 保存されたマップデータをDBから読み込む
     */
    private fun loadSavedMapData() {
        viewModelScope.launch {
            try {
                Log.d("MapViewModel", "loadSavedMapData: eventId=$eventId")
                val savedMapData = mapDataRepository.getMapDataByEventIdOnce(eventId)
                Log.d("MapViewModel", "loadSavedMapData: found ${savedMapData.size} maps")
                if (savedMapData.isNotEmpty()) {
                    val firstMapName = savedMapData.keys.first()
                    Log.d("MapViewModel", "loadSavedMapData: firstMapName=$firstMapName")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            mapDataList = savedMapData,
                            selectedMapName = firstMapName,
                            currentMapData = savedMapData[firstMapName]
                        )
                    }
                    updateCellItemsMap()
                } else {
                    Log.d("MapViewModel", "loadSavedMapData: no saved map data found")
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "loadSavedMapData: error", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun importMapFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                Log.d("MapViewModel", "importMapFile: START - eventId=$eventId")

                val result = withContext(Dispatchers.IO) {
                    XlsxMapParser.parseMapFile(context, uri, eventId)
                }

                if (result != null && result.isNotEmpty()) {
                    val firstMapName = result.keys.first()
                    Log.d("MapViewModel", "importMapFile: parsed ${result.size} maps, first=$firstMapName")

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            mapDataList = result,
                            selectedMapName = firstMapName,
                            currentMapData = result[firstMapName]
                        )
                    }
                    updateCellItemsMap()

                    // DBに保存（同じコルーチン内で同期的に実行）
                    try {
                        Log.d("MapViewModel", "importMapFile: saving to DB, eventId=$eventId, count=${result.size}")
                        result.forEach { (key, data) ->
                            Log.d("MapViewModel", "importMapFile: saving key=$key, sheetName=${data.sheetName}")
                        }
                        mapDataRepository.saveMapData(eventId, result)
                        Log.d("MapViewModel", "importMapFile: DB save completed")

                        // 保存確認
                        val verify = mapDataRepository.getMapDataByEventIdOnce(eventId)
                        Log.d("MapViewModel", "importMapFile: VERIFY found ${verify.size} maps in DB")
                        if (verify.isEmpty()) {
                            Log.e("MapViewModel", "importMapFile: VERIFY FAILED - no data saved!")
                        }
                    } catch (saveError: Exception) {
                        Log.e("MapViewModel", "importMapFile: DB save error", saveError)
                    }
                } else {
                    Log.w("MapViewModel", "importMapFile: result is null or empty")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "マップデータを読み込めませんでした。\n「○日目」という名前のシートが必要です。"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "importMapFile: error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "読み込みエラー: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * マップデータをDBに保存
     */
    private fun saveMapDataToDb(mapDataList: Map<String, DayMapData>) {
        viewModelScope.launch {
            try {
                Log.d("MapViewModel", "saveMapDataToDb: START - saving ${mapDataList.size} maps for eventId=$eventId")
                mapDataList.forEach { (key, _) ->
                    Log.d("MapViewModel", "saveMapDataToDb: key=$key")
                }
                mapDataRepository.saveMapData(eventId, mapDataList)
                Log.d("MapViewModel", "saveMapDataToDb: save completed, now verifying...")

                // 保存確認
                val verify = mapDataRepository.getMapDataByEventIdOnce(eventId)
                Log.d("MapViewModel", "saveMapDataToDb: VERIFY - found ${verify.size} maps after save")
                verify.forEach { (key, _) ->
                    Log.d("MapViewModel", "saveMapDataToDb: VERIFY key=$key")
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "saveMapDataToDb: error", e)
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

    fun updateItem(item: ShoppingItem) {
        viewModelScope.launch {
            itemRepository.updateItem(item)
        }
    }

    fun updateItemStatus(itemId: String, status: PurchaseStatus) {
        viewModelScope.launch {
            val item = _uiState.value.items.find { it.id == itemId }
            item?.let {
                itemRepository.updateItem(it.copy(purchaseStatus = status))
            }
        }
    }

    // ===== ブロック定義パネル関連 =====

    fun openBlockDefinitionPanel() {
        _uiState.update { it.copy(isBlockDefinitionPanelOpen = true) }
    }

    fun closeBlockDefinitionPanel() {
        _uiState.update {
            it.copy(
                isBlockDefinitionPanelOpen = false,
                cellSelectionMode = CellSelectionMode.NONE,
                selectedCells = emptyList()
            )
        }
    }

    fun startCellSelection() {
        _uiState.update {
            it.copy(
                cellSelectionMode = CellSelectionMode.CORNER_SELECT,
                selectedCells = emptyList()
            )
        }
    }

    fun cancelCellSelection() {
        _uiState.update {
            it.copy(
                cellSelectionMode = CellSelectionMode.NONE,
                selectedCells = emptyList()
            )
        }
    }

    fun addSelectedCell(row: Int, col: Int) {
        val currentCells = _uiState.value.selectedCells.toMutableList()
        val newCell = Pair(row, col)

        // 既に選択済みの場合は削除
        if (currentCells.contains(newCell)) {
            currentCells.remove(newCell)
        } else if (currentCells.size < 4) {
            currentCells.add(newCell)
        }

        _uiState.update { it.copy(selectedCells = currentCells) }
    }

    fun updateBlocks(blocks: List<BlockDefinition>) {
        val currentMapName = _uiState.value.selectedMapName ?: return
        val currentMapData = _uiState.value.currentMapData ?: return

        // 現在のマップデータを更新
        val updatedMapData = currentMapData.copy(blocks = blocks)
        val updatedMapDataList = _uiState.value.mapDataList.toMutableMap()
        updatedMapDataList[currentMapName] = updatedMapData

        _uiState.update {
            it.copy(
                mapDataList = updatedMapDataList,
                currentMapData = updatedMapData
            )
        }

        // cellItemsMapを再計算
        updateCellItemsMap()

        // DBに保存
        saveMapDataToDb(updatedMapDataList)
    }

    fun addBlock(block: BlockDefinition) {
        val currentBlocks = _uiState.value.currentMapData?.blocks ?: return
        updateBlocks(currentBlocks + block)
    }

    fun updateBlock(updatedBlock: BlockDefinition) {
        val currentBlocks = _uiState.value.currentMapData?.blocks ?: return
        val newBlocks = currentBlocks.map {
            if (it.id == updatedBlock.id) updatedBlock else it
        }
        updateBlocks(newBlocks)
    }

    fun deleteBlock(blockId: String) {
        val currentBlocks = _uiState.value.currentMapData?.blocks ?: return
        val newBlocks = currentBlocks.filter { it.id != blockId }
        updateBlocks(newBlocks)
    }

    fun deleteAllBlocks() {
        updateBlocks(emptyList())
    }
}