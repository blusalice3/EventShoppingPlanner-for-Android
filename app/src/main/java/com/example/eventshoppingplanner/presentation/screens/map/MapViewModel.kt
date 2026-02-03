package com.example.eventshoppingplanner.presentation.screens.map

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventshoppingplanner.data.local.dao.HallDefinitionDao
import com.example.eventshoppingplanner.data.local.dao.HallOrderDao
import com.example.eventshoppingplanner.data.local.dao.VisitListDao
import com.example.eventshoppingplanner.data.local.entity.HallDefinitionEntity
import com.example.eventshoppingplanner.data.local.entity.HallOrderEntity
import com.example.eventshoppingplanner.data.local.entity.VisitListEntity
import com.example.eventshoppingplanner.domain.model.*
import com.example.eventshoppingplanner.domain.model.createGroupId
import com.example.eventshoppingplanner.domain.model.parseGroupId
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.MapDataRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import com.example.eventshoppingplanner.util.HallUtils
import com.example.eventshoppingplanner.util.PathfindingUtils
import com.example.eventshoppingplanner.util.XlsxMapParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    // ピンチズーム対応（連続的スケール）
    val scale: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val errorMessage: String? = null,
    val cellItemsMap: Map<String, List<ShoppingItem>> = emptyMap(),
    // ブロック編集関連
    val isBlockDefinitionPanelOpen: Boolean = false,  // パネルが論理的に開いているか（状態保持）
    val isBlockDefinitionPanelVisible: Boolean = false,  // パネルが実際に表示されているか
    val cellSelectionMode: CellSelectionMode = CellSelectionMode.NONE,
    val selectedCells: List<Pair<Int, Int>> = emptyList(),
    val currentSelectionType: CellSelectionType? = null,
    // セル選択中に保持する編集状態
    val pendingEditState: BlockEditState? = null,
    // ホール定義関連
    val halls: List<HallDefinition> = emptyList(),
    val selectedHallId: String? = null,  // null = "all"（全ホール表示）
    val isHallSelectorOpen: Boolean = false,  // ホール選択ドロップダウンの表示状態
    val hallItemCounts: Map<String, HallItemCount> = emptyMap(),  // ホールごとのアイテム数
    val isHallDefinitionPanelOpen: Boolean = false,
    val isHallDefinitionPanelVisible: Boolean = false,
    // マーカーによる頂点選択モード
    val hallVertexSelectionMode: HallVertexSelectionMode = HallVertexSelectionMode.NONE,
    val hallMarkers: List<HallMarker> = emptyList(),  // 設置済みマーカーリスト
    val isPlacingMarker: Boolean = false,  // 新規マーカー配置中か
    val selectedHallVertices: List<Vertex> = emptyList(),  // 確定済み頂点（後方互換用）
    val editingHallId: String? = null,  // 編集中のホールID（新規はnull）
    val pendingHallEditState: HallEditState? = null,  // 頂点選択中に保持する編集状態
    // 訪問先リスト関連
    val visitListItemIds: List<String> = emptyList(),  // 訪問先リストに追加されたアイテムID（訪問順）
    val isVisitListPanelOpen: Boolean = false,  // 訪問先リストパネルの表示状態
    val visitListDisplayMode: VisitListDisplayMode = VisitListDisplayMode.SIDE_RIGHT,  // 表示モード
    val visitListPanelWidth: Float = 300f,  // パネル幅（dp）
    val isRouteVisible: Boolean = true,  // ルート表示のON/OFF
    val visitListSelectionMode: VisitListSelectionMode = VisitListSelectionMode.NORMAL,  // 選択モード
    val visitListRangeStart: String? = null,  // 範囲選択の開始アイテムID
    val visitListRangeEnd: String? = null,  // 範囲選択の終了アイテムID
    // ルート描画関連
    val visitPoints: List<VisitPoint> = emptyList(),  // 訪問ポイント（描画用）
    val routeSegments: List<RouteSegment> = emptyList(),  // ルートセグメント（描画用）
    // グループ順序関連
    val groupOrder: List<String> = emptyList(),  // グループID（"hallId_PRIORITY"等）の順序
    val isHallOrderPanelOpen: Boolean = false  // ホール順序パネルの表示状態
)

/**
 * ホールごとのアイテム数
 */
data class HallItemCount(
    val executeCount: Int,  // 訪問先リストに追加されたアイテム数
    val totalCount: Int     // ホール内の全アイテム数
)

/**
 * ホール定義用マーカー
 */
data class HallMarker(
    val id: String = java.util.UUID.randomUUID().toString(),
    val row: Int,
    val col: Int
)

/**
 * ホール編集状態（頂点選択中に保持）
 */
data class HallEditState(
    val editingHall: HallDefinition?,
    val isAddingNew: Boolean,
    val currentHalls: List<HallDefinition>
)

/**
 * ホール頂点選択モード
 */
enum class HallVertexSelectionMode {
    NONE,
    SELECTING
}

/**
 * ブロック編集状態（セル選択中に保持）
 */
data class BlockEditState(
    val editingBlock: BlockDefinition?,
    val isAddingNew: Boolean,
    val editMode: EditMode,
    val wallCellGroups: List<CellGroup>,
    val multiRanges: List<MultiRange>,
    val currentBlocks: List<BlockDefinition>
)

/**
 * セル選択モード
 */
enum class CellSelectionMode {
    NONE,           // 通常モード
    CORNER_SELECT,  // 4角選択モード
    RANGE_SELECT,   // 範囲選択モード（2点）
    INDIVIDUAL_SELECT // 個別セル選択モード
}

@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val eventRepository: EventRepository,
    private val itemRepository: ShoppingItemRepository,
    private val mapDataRepository: MapDataRepository,
    private val hallDefinitionDao: HallDefinitionDao,
    private val visitListDao: VisitListDao,
    private val hallOrderDao: HallOrderDao
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val gson = Gson()

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
                    // ホール定義を読み込み
                    loadHallsForCurrentMap()
                    // 訪問先リストを読み込み
                    loadVisitListForCurrentMap()
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
     * 既存マップデータをDBに更新保存（ブロック定義変更等）
     *
     * 注意: saveMapData() は insertAll(REPLACE) を使用するが、
     * Room の REPLACE は内部的に DELETE → INSERT で実装されている。
     * hall_definitions テーブルには map_data への ForeignKey(CASCADE) があるため、
     * REPLACE 時に hall_definitions の子レコードが全削除されてしまう。
     *
     * 既存レコードの更新には updateMapData(@Update) を使用することで回避する。
     */
    private fun saveMapDataToDb(mapDataList: Map<String, DayMapData>) {
        viewModelScope.launch {
            try {
                Log.d("MapViewModel", "saveMapDataToDb: START - updating ${mapDataList.size} maps for eventId=$eventId")
                mapDataList.forEach { (_, mapData) ->
                    mapDataRepository.updateMapData(mapData)
                }
                Log.d("MapViewModel", "saveMapDataToDb: update completed")
            } catch (e: Exception) {
                Log.e("MapViewModel", "saveMapDataToDb: error", e)
            }
        }
    }

    fun selectMap(mapName: String) {
        val mapData = _uiState.value.mapDataList[mapName]
        Log.d("MapViewModel", "selectMap: mapName=$mapName")
        Log.d("MapViewModel", "selectMap: mapData?.id=${mapData?.id}")
        Log.d("MapViewModel", "selectMap: mapData?.eventId=${mapData?.eventId}")
        Log.d("MapViewModel", "selectMap: mapData?.dayName=${mapData?.dayName}")

        _uiState.update {
            it.copy(
                selectedMapName = mapName,
                currentMapData = mapData,
                // スケールとオフセットを初期値にリセット
                scale = 1.0f,
                offsetX = 0f,
                offsetY = 0f,
                // ホール関連をリセット
                selectedHallId = null,
                halls = emptyList(),
                // 訪問先リストをリセット（すぐに読み込む）
                visitListItemIds = emptyList(),
                // グループ順序をリセット
                groupOrder = emptyList()
            )
        }
        updateCellItemsMap()
        // ホール定義を読み込み（内部でgroupOrderも読み込み・同期する）
        loadHallsForCurrentMap()
        // 訪問先リストを読み込み
        loadVisitListForCurrentMap()
    }

    // ========================================
    // 訪問先リスト関連
    // ========================================

    /**
     * 現在のマップの訪問先リストをDBから読み込む
     */
    private fun loadVisitListForCurrentMap() {
        val dayName = _uiState.value.currentMapData?.dayName ?: return

        viewModelScope.launch {
            try {
                val entity = visitListDao.getVisitListOnce(eventId, dayName)
                val itemIds: List<String> = if (entity != null) {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson(entity.itemIdsJson, type) ?: emptyList()
                } else {
                    emptyList()
                }
                _uiState.update { it.copy(visitListItemIds = itemIds) }
                Log.d("MapViewModel", "loadVisitListForCurrentMap: loaded ${itemIds.size} items for dayName=$dayName")
                // ホールアイテム数を更新
                updateHallItemCounts()
                // ルートデータを生成
                generateRouteData()
            } catch (e: Exception) {
                Log.e("MapViewModel", "loadVisitListForCurrentMap: error", e)
            }
        }
    }

    /**
     * 訪問先リストをDBに保存
     */
    private fun saveVisitList() {
        val dayName = _uiState.value.currentMapData?.dayName ?: return
        val itemIds = _uiState.value.visitListItemIds

        viewModelScope.launch {
            try {
                val entity = VisitListEntity(
                    id = "${eventId}_${dayName}",
                    eventId = eventId,
                    dayName = dayName,
                    itemIdsJson = gson.toJson(itemIds)
                )
                visitListDao.insertVisitList(entity)
                Log.d("MapViewModel", "saveVisitList: saved ${itemIds.size} items for dayName=$dayName")
            } catch (e: Exception) {
                Log.e("MapViewModel", "saveVisitList: error", e)
            }
        }
    }

    // ========================================
    // グループ順序関連
    // ========================================

    /**
     * 現在のマップのグループ順序をDBから読み込み、ホール定義と同期する
     * loadHallsForCurrentMap() 内からホール読み込み完了後に呼ばれる（順序保証）
     */
    private suspend fun loadAndSyncGroupOrder(halls: List<HallDefinition>) {
        val dayName = _uiState.value.currentMapData?.dayName ?: return

        try {
            val entity = hallOrderDao.getHallOrderOnce(eventId, dayName)
            val savedOrder: List<String> = if (entity != null) {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(entity.groupOrderJson, type) ?: emptyList()
            } else {
                emptyList()
            }

            if (savedOrder.isEmpty()) {
                // DBに保存されていなければデフォルト順序を生成
                generateDefaultGroupOrder()
                Log.d("MapViewModel", "loadAndSyncGroupOrder: generated default order for dayName=$dayName")
            } else {
                // DB読み込み後、現在のホール定義と同期
                _uiState.update { it.copy(groupOrder = savedOrder) }
                syncGroupOrderWithHalls(halls)
                Log.d("MapViewModel", "loadAndSyncGroupOrder: loaded and synced ${_uiState.value.groupOrder.size} groups for dayName=$dayName")
            }
        } catch (e: Exception) {
            Log.e("MapViewModel", "loadAndSyncGroupOrder: error", e)
            generateDefaultGroupOrder()
        }
    }

    /**
     * デフォルトのグループ順序を生成
     * ホール定義順 × (最優先→優先→通常) の全組み合わせ
     */
    fun generateDefaultGroupOrder() {
        val halls = _uiState.value.halls
        val priorityOrder = listOf(PriorityLevel.HIGHEST, PriorityLevel.PRIORITY, PriorityLevel.NONE)

        val order = mutableListOf<String>()

        // ホール定義順 × 優先度順
        for (hall in halls) {
            for (priority in priorityOrder) {
                order.add(createGroupId(hall.id, priority))
            }
        }

        // ホール未定義グループ
        for (priority in priorityOrder) {
            order.add(createGroupId(null, priority))
        }

        _uiState.update { it.copy(groupOrder = order) }
        Log.d("MapViewModel", "generateDefaultGroupOrder: ${order.size} groups")
    }

    /**
     * グループ順序をDBに保存
     */
    private fun saveGroupOrder() {
        val dayName = _uiState.value.currentMapData?.dayName ?: return
        val groupOrder = _uiState.value.groupOrder

        viewModelScope.launch {
            try {
                val entity = HallOrderEntity(
                    id = "${eventId}_${dayName}",
                    eventId = eventId,
                    dayName = dayName,
                    groupOrderJson = gson.toJson(groupOrder)
                )
                hallOrderDao.insertHallOrder(entity)
                Log.d("MapViewModel", "saveGroupOrder: saved ${groupOrder.size} groups for dayName=$dayName")
            } catch (e: Exception) {
                Log.e("MapViewModel", "saveGroupOrder: error", e)
            }
        }
    }

    /**
     * グループ順序を更新（HallOrderPanelから呼ばれる）
     */
    fun updateGroupOrder(newOrder: List<String>) {
        _uiState.update { it.copy(groupOrder = newOrder) }
        saveGroupOrder()
        // ルートは並び替え時にのみ再生成（順序変更だけでは描画に影響しない）
        Log.d("MapViewModel", "updateGroupOrder: ${newOrder.size} groups")
    }

    /**
     * ホール順序パネルを開く
     */
    fun openHallOrderPanel() {
        _uiState.update { it.copy(isHallOrderPanelOpen = true) }
    }

    /**
     * ホール順序パネルを閉じる
     */
    fun closeHallOrderPanel() {
        _uiState.update { it.copy(isHallOrderPanelOpen = false) }
    }

    /**
     * ホール定義の変更後にgroupOrderを同期する
     * - 新しいホールのグループIDを末尾に追加
     * - 削除されたホールのグループIDを除去
     * - 既存の順序は維持
     */
    private fun syncGroupOrderWithHalls(halls: List<HallDefinition>) {
        val currentOrder = _uiState.value.groupOrder
        val priorityOrder = listOf(PriorityLevel.HIGHEST, PriorityLevel.PRIORITY, PriorityLevel.NONE)

        // 現在のホールIDセット
        val currentHallIds = halls.map { it.id }.toSet()

        // 全ホールの全グループIDセット（未定義含む）
        val allValidGroupIds = mutableSetOf<String>()
        halls.forEach { hall ->
            priorityOrder.forEach { priority ->
                allValidGroupIds.add(createGroupId(hall.id, priority))
            }
        }
        priorityOrder.forEach { priority ->
            allValidGroupIds.add(createGroupId(null, priority))
        }

        // 1) 削除されたホールのグループIDを除去（既存順序維持）
        val filteredOrder = currentOrder.filter { groupId ->
            val (hallId, _) = parseGroupId(groupId)
            hallId == null || currentHallIds.contains(hallId)
        }

        // 2) 新しいホールのグループIDを末尾に追加
        val existingGroupIds = filteredOrder.toSet()
        val newGroupIds = mutableListOf<String>()
        halls.forEach { hall ->
            priorityOrder.forEach { priority ->
                val groupId = createGroupId(hall.id, priority)
                if (!existingGroupIds.contains(groupId)) {
                    newGroupIds.add(groupId)
                }
            }
        }
        // 未定義グループも不足があれば追加
        priorityOrder.forEach { priority ->
            val groupId = createGroupId(null, priority)
            if (!existingGroupIds.contains(groupId)) {
                newGroupIds.add(groupId)
            }
        }

        val newOrder = filteredOrder + newGroupIds
        _uiState.update { it.copy(groupOrder = newOrder) }
        saveGroupOrder()
        Log.d("MapViewModel", "syncGroupOrderWithHalls: ${currentOrder.size} -> ${newOrder.size} groups (added ${newGroupIds.size}, removed ${currentOrder.size - filteredOrder.size})")
    }

    /**
     * グループ順序に従って訪問先リストを並び替え
     */
    fun reorderVisitListByGroupOrder() {
        val state = _uiState.value
        val groupOrder = state.groupOrder
        val visitListItemIds = state.visitListItemIds
        val items = state.items
        val halls = state.halls
        val mapData = state.currentMapData ?: return
        val blocks = mapData.blocks
        val dayName = mapData.dayName

        if (visitListItemIds.isEmpty() || groupOrder.isEmpty()) return

        val itemsMap = items.associateBy { it.id }

        // ブロック名→ホールIDマップを作成
        val blockToHallMap = mutableMapOf<String, String?>()
        halls.forEach { hall ->
            val blocksInHall = HallUtils.getBlocksInHall(hall, blocks)
            blocksInHall.forEach { block ->
                blockToHallMap[block.name] = hall.id
            }
        }

        // 各アイテムのグループIDを取得
        fun getItemGroupId(itemId: String): String {
            val item = itemsMap[itemId] ?: return createGroupId(null, PriorityLevel.NONE)
            val hallId = blockToHallMap[item.block]
            return createGroupId(hallId, item.priorityLevel)
        }

        // グループごとにアイテムを分類（既存の順序を保持）
        val groupedItems = mutableMapOf<String, MutableList<String>>()
        visitListItemIds.forEach { itemId ->
            val groupId = getItemGroupId(itemId)
            groupedItems.getOrPut(groupId) { mutableListOf() }.add(itemId)
        }

        // グループ順序に従って連結
        val reordered = mutableListOf<String>()
        groupOrder.forEach { groupId ->
            groupedItems[groupId]?.let { reordered.addAll(it) }
            groupedItems.remove(groupId)
        }
        // グループ順序に含まれないアイテム（あれば末尾に追加）
        groupedItems.values.forEach { reordered.addAll(it) }

        _uiState.update { it.copy(visitListItemIds = reordered) }
        saveVisitList()
        updateHallItemCounts()
        generateRouteData()
        Log.d("MapViewModel", "reorderVisitListByGroupOrder: reordered ${reordered.size} items")
    }

    /**
     * グループ内のアイテム数を取得（グループIDで）
     */
    fun getGroupItemCount(groupId: String): Int {
        val state = _uiState.value
        val visitListItemIds = state.visitListItemIds
        val items = state.items
        val halls = state.halls
        val mapData = state.currentMapData ?: return 0
        val blocks = mapData.blocks

        val itemsMap = items.associateBy { it.id }

        // ブロック名→ホールIDマップ
        val blockToHallMap = mutableMapOf<String, String?>()
        halls.forEach { hall ->
            val blocksInHall = HallUtils.getBlocksInHall(hall, blocks)
            blocksInHall.forEach { block ->
                blockToHallMap[block.name] = hall.id
            }
        }

        val (targetHallId, targetPriority) = parseGroupId(groupId)

        return visitListItemIds.count { itemId ->
            val item = itemsMap[itemId] ?: return@count false
            val itemHallId = blockToHallMap[item.block]
            itemHallId == targetHallId && item.priorityLevel == targetPriority
        }
    }

    /**
     * アイテムを訪問先リストに追加
     */
    fun addToVisitList(itemId: String) {
        val currentIds = _uiState.value.visitListItemIds
        if (currentIds.contains(itemId)) return

        _uiState.update { it.copy(visitListItemIds = currentIds + itemId) }
        saveVisitList()
        updateHallItemCounts()
        generateRouteData()
        Log.d("MapViewModel", "addToVisitList: added itemId=$itemId")
    }

    /**
     * アイテムを訪問先リストから削除
     */
    fun removeFromVisitList(itemId: String) {
        val currentIds = _uiState.value.visitListItemIds
        if (!currentIds.contains(itemId)) return

        _uiState.update { it.copy(visitListItemIds = currentIds.filter { it != itemId }) }
        saveVisitList()
        updateHallItemCounts()
        generateRouteData()
        Log.d("MapViewModel", "removeFromVisitList: removed itemId=$itemId")
    }

    /**
     * アイテムが訪問先リストに含まれているかをトグル
     */
    fun toggleVisitListItem(itemId: String) {
        if (_uiState.value.visitListItemIds.contains(itemId)) {
            removeFromVisitList(itemId)
        } else {
            addToVisitList(itemId)
        }
    }

    /**
     * 訪問先リストパネルを開く
     */
    fun openVisitListPanel() {
        _uiState.update { it.copy(isVisitListPanelOpen = true) }
    }

    /**
     * 訪問先リストパネルを閉じる
     */
    fun closeVisitListPanel() {
        _uiState.update { it.copy(isVisitListPanelOpen = false) }
    }

    /**
     * ルート表示のON/OFFをトグル
     */
    fun toggleRouteVisibility() {
        _uiState.update { it.copy(isRouteVisible = !it.isRouteVisible) }
    }

    /**
     * ルートデータを生成（訪問ポイントとルートセグメント）
     */
    private fun generateRouteData() {
        val state = _uiState.value
        val mapData = state.currentMapData ?: return
        val visitListItemIds = state.visitListItemIds
        val items = state.items
        val dayName = mapData.dayName

        if (visitListItemIds.isEmpty()) {
            _uiState.update { it.copy(visitPoints = emptyList(), routeSegments = emptyList()) }
            return
        }

        // アイテムIDからアイテムマップを作成
        val itemsMap = items.associateBy { it.id }

        // visitListItemIdsの順序でVisitPointを生成
        val visitPoints = mutableListOf<VisitPoint>()
        var order = 0

        visitListItemIds.forEach { itemId ->
            val item = itemsMap[itemId] ?: return@forEach
            // 現在の日付のアイテムのみ
            if (item.eventDate != dayName) return@forEach

            // ブロックを検索
            val blockName = item.block.trim()
            val block = mapData.blocks.find { it.name == blockName }
                ?: mapData.blocks.find { it.name.equals(blockName, ignoreCase = true) }
                ?: return@forEach

            // スペース番号からセル座標を取得
            val numberStr = extractNumber(item.number)
            val number = numberStr?.toIntOrNull() ?: return@forEach
            val cell = block.numberCells.find { it.value == number } ?: return@forEach

            visitPoints.add(
                VisitPoint(
                    row = cell.row,
                    col = cell.col,
                    order = order,
                    priorityLevel = item.priorityLevel,
                    itemId = item.id
                )
            )
            order++
        }

        // ルートセグメントを生成
        val routeSegments = if (visitPoints.size >= 2) {
            val blockNameCells = PathfindingUtils.generateBlockNameCells(mapData)
            PathfindingUtils.generateRouteSegments(mapData, visitPoints, blockNameCells)
        } else {
            emptyList()
        }

        _uiState.update {
            it.copy(
                visitPoints = visitPoints,
                routeSegments = routeSegments
            )
        }

        Log.d("MapViewModel", "generateRouteData: ${visitPoints.size} points, ${routeSegments.size} segments")
    }

    /**
     * アイテム番号から数値部分を抽出
     */
    private fun extractNumber(number: String): String? {
        // "12a" -> "12", "12" -> "12", "a12b" -> "12"
        val match = Regex("\\d+").find(number)
        return match?.value
    }

    /**
     * アイテムの優先度を変更
     */
    fun changeItemPriority(itemId: String, priorityLevel: PriorityLevel) {
        viewModelScope.launch {
            try {
                val item = _uiState.value.items.find { it.id == itemId } ?: return@launch
                val updatedItem = item.copy(priorityLevel = priorityLevel)
                itemRepository.updateItem(updatedItem)
                Log.d("MapViewModel", "changeItemPriority: itemId=$itemId, priority=$priorityLevel")
            } catch (e: Exception) {
                Log.e("MapViewModel", "changeItemPriority: error", e)
            }
        }
    }

    /**
     * 訪問先リストの表示モードを変更
     */
    fun changeVisitListDisplayMode(mode: VisitListDisplayMode) {
        _uiState.update { it.copy(visitListDisplayMode = mode) }
    }

    /**
     * 訪問先リストパネルの幅を変更
     */
    fun changeVisitListPanelWidth(width: Float) {
        _uiState.update { it.copy(visitListPanelWidth = width) }
    }

    // ========== 訪問先リスト並び替え関連 ==========

    /**
     * 訪問先リスト内でアイテムを移動（ドラッグ＆ドロップ用）
     * @param fromIndex 移動元インデックス
     * @param toIndex 移動先インデックス
     */
    fun moveItemInVisitList(fromIndex: Int, toIndex: Int) {
        val currentIds = _uiState.value.visitListItemIds.toMutableList()
        if (fromIndex < 0 || fromIndex >= currentIds.size ||
            toIndex < 0 || toIndex >= currentIds.size ||
            fromIndex == toIndex) return

        val item = currentIds.removeAt(fromIndex)
        currentIds.add(toIndex, item)

        _uiState.update { it.copy(visitListItemIds = currentIds) }
        saveVisitList()
        generateRouteData()
        Log.d("MapViewModel", "moveItemInVisitList: from=$fromIndex to=$toIndex")
    }

    /**
     * 訪問先リストでアイテムを上に移動
     * @param itemId 移動するアイテムのID
     */
    fun moveItemUp(itemId: String) {
        val currentIds = _uiState.value.visitListItemIds
        val index = currentIds.indexOf(itemId)
        if (index <= 0) return  // 既に先頭または見つからない

        moveItemInVisitList(index, index - 1)
    }

    /**
     * 訪問先リストでアイテムを下に移動
     * @param itemId 移動するアイテムのID
     */
    fun moveItemDown(itemId: String) {
        val currentIds = _uiState.value.visitListItemIds
        val index = currentIds.indexOf(itemId)
        if (index < 0 || index >= currentIds.size - 1) return  // 既に末尾または見つからない

        moveItemInVisitList(index, index + 1)
    }

    /**
     * 訪問先リストの選択モードを変更
     */
    fun setVisitListSelectionMode(mode: VisitListSelectionMode) {
        _uiState.update {
            it.copy(
                visitListSelectionMode = mode,
                visitListRangeStart = null,
                visitListRangeEnd = null
            )
        }
        Log.d("MapViewModel", "setVisitListSelectionMode: mode=$mode")
    }

    /**
     * 範囲選択の開始点を設定
     */
    fun setVisitListRangeStart(itemId: String?) {
        _uiState.update { it.copy(visitListRangeStart = itemId, visitListRangeEnd = null) }
        Log.d("MapViewModel", "setVisitListRangeStart: itemId=$itemId")
    }

    /**
     * 範囲選択の終了点を設定
     */
    fun setVisitListRangeEnd(itemId: String?) {
        _uiState.update { it.copy(visitListRangeEnd = itemId) }
        Log.d("MapViewModel", "setVisitListRangeEnd: itemId=$itemId")
    }

    /**
     * 範囲選択をクリア
     */
    fun clearVisitListRange() {
        _uiState.update {
            it.copy(
                visitListRangeStart = null,
                visitListRangeEnd = null
            )
        }
    }

    /**
     * 選択範囲の区間を反転
     */
    fun reverseVisitListRange() {
        val startId = _uiState.value.visitListRangeStart ?: return
        val endId = _uiState.value.visitListRangeEnd ?: return
        val currentIds = _uiState.value.visitListItemIds.toMutableList()

        val startIndex = currentIds.indexOf(startId)
        val endIndex = currentIds.indexOf(endId)
        if (startIndex < 0 || endIndex < 0) return

        val actualStart = minOf(startIndex, endIndex)
        val actualEnd = maxOf(startIndex, endIndex)

        // 範囲を反転
        val subList = currentIds.subList(actualStart, actualEnd + 1)
        val reversed = subList.reversed()

        for (i in reversed.indices) {
            currentIds[actualStart + i] = reversed[i]
        }

        _uiState.update {
            it.copy(
                visitListItemIds = currentIds,
                visitListRangeStart = null,
                visitListRangeEnd = null,
                visitListSelectionMode = VisitListSelectionMode.NORMAL
            )
        }
        saveVisitList()
        generateRouteData()
        Log.d("MapViewModel", "reverseVisitListRange: reversed from $actualStart to $actualEnd")
    }

    // ========== 訪問先リスト並び替え関連 ここまで ==========

    /**
     * ピンチズームによるスケールとオフセットの更新
     * @param newScale 新しいスケール値
     * @param newOffsetX 新しいX方向オフセット
     * @param newOffsetY 新しいY方向オフセット
     */
    fun updateScaleAndOffset(newScale: Float, newOffsetX: Float, newOffsetY: Float) {
        // スケールの範囲制限（0.1〜5.0）
        val clampedScale = newScale.coerceIn(0.1f, 5.0f)
        _uiState.update {
            it.copy(
                scale = clampedScale,
                offsetX = newOffsetX,
                offsetY = newOffsetY
            )
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

    // ===== マーカー関連（Google Maps風配置方式） =====

    /**
     * マーカー配置モードを開始
     * 画面中央に🚩を表示し、パン/ズームで位置調整可能に
     */
    fun startPlacingMarker() {
        val markers = _uiState.value.hallMarkers
        if (markers.size >= 6) return  // 最大6個まで

        _uiState.update { it.copy(isPlacingMarker = true) }
    }

    /**
     * マーカー配置を確定
     * 現在の画面中央のセルにマーカーを設置
     */
    fun confirmMarkerPlacement(row: Int, col: Int) {
        val markers = _uiState.value.hallMarkers
        if (markers.size >= 6) return

        val newMarker = HallMarker(row = row, col = col)
        _uiState.update {
            it.copy(
                hallMarkers = markers + newMarker,
                isPlacingMarker = false
            )
        }
    }

    /**
     * マーカー配置をキャンセル
     */
    fun cancelPlacingMarker() {
        _uiState.update { it.copy(isPlacingMarker = false) }
    }

    /**
     * マーカーを削除
     */
    fun removeHallMarker(markerId: String) {
        val markers = _uiState.value.hallMarkers.filter { it.id != markerId }
        _uiState.update { it.copy(hallMarkers = markers) }
    }

    /**
     * マーカーから頂点リストを生成
     */
    private fun markersToVertices(): List<Vertex> {
        return _uiState.value.hallMarkers.map { Vertex(it.row, it.col) }
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

    fun addItem(item: ShoppingItem) {
        viewModelScope.launch {
            // eventIdを設定してアイテムを追加
            val eventId = _uiState.value.event?.id ?: return@launch
            val itemWithEventId = item.copy(eventId = eventId)
            itemRepository.insertItem(itemWithEventId)
        }
    }

    // ===== ブロック定義パネル関連 =====

    fun openBlockDefinitionPanel() {
        _uiState.update {
            it.copy(
                isBlockDefinitionPanelOpen = true,
                isBlockDefinitionPanelVisible = true
            )
        }
    }

    fun closeBlockDefinitionPanel() {
        _uiState.update {
            it.copy(
                isBlockDefinitionPanelOpen = false,
                isBlockDefinitionPanelVisible = false,
                cellSelectionMode = CellSelectionMode.NONE,
                selectedCells = emptyList(),
                currentSelectionType = null,
                pendingEditState = null
            )
        }
    }

    fun startCellSelection(selectionType: CellSelectionType, editState: BlockEditState) {
        val mode = when (selectionType) {
            CellSelectionType.CORNER, CellSelectionType.MULTI_CORNER -> CellSelectionMode.CORNER_SELECT
            CellSelectionType.RANGE_START -> CellSelectionMode.RANGE_SELECT
            CellSelectionType.INDIVIDUAL -> CellSelectionMode.INDIVIDUAL_SELECT
        }
        _uiState.update {
            it.copy(
                isBlockDefinitionPanelOpen = true,  // 状態保持のためtrueを維持
                isBlockDefinitionPanelVisible = false,  // UIは非表示
                cellSelectionMode = mode,
                selectedCells = emptyList(),
                currentSelectionType = selectionType,
                pendingEditState = editState  // 編集状態を保存
            )
        }
    }

    fun cancelCellSelection() {
        _uiState.update {
            it.copy(
                isBlockDefinitionPanelVisible = true,  // パネルを再表示
                cellSelectionMode = CellSelectionMode.NONE,
                selectedCells = emptyList(),
                currentSelectionType = null
                // pendingEditStateは保持（BlockDefinitionPanelで復元に使う）
            )
        }
    }

    fun clearPendingEditState() {
        _uiState.update { it.copy(pendingEditState = null) }
    }

    fun removeSelectedCell(row: Int, col: Int) {
        val currentCells = _uiState.value.selectedCells.toMutableList()
        currentCells.remove(Pair(row, col))
        _uiState.update { it.copy(selectedCells = currentCells) }
    }

    fun addSelectedCell(row: Int, col: Int) {
        val currentCells = _uiState.value.selectedCells.toMutableList()
        val newCell = Pair(row, col)
        val selectionType = _uiState.value.currentSelectionType

        when (selectionType) {
            CellSelectionType.CORNER, CellSelectionType.MULTI_CORNER -> {
                // 4角選択: 最大4つ、同じセルは削除
                if (currentCells.contains(newCell)) {
                    currentCells.remove(newCell)
                } else if (currentCells.size < 4) {
                    currentCells.add(newCell)
                }
                _uiState.update { it.copy(selectedCells = currentCells) }
                // 4つ選択しても自動では戻らない（確定ボタンで戻る）
            }
            CellSelectionType.RANGE_START -> {
                // 範囲選択: 最大2つ
                if (currentCells.contains(newCell)) {
                    currentCells.remove(newCell)
                } else if (currentCells.size < 2) {
                    currentCells.add(newCell)
                }
                _uiState.update { it.copy(selectedCells = currentCells) }
                // 2つ選択しても自動では戻らない（確定ボタンで戻る）
            }
            CellSelectionType.INDIVIDUAL -> {
                // 個別セル選択: 無制限、トグル
                if (currentCells.contains(newCell)) {
                    currentCells.remove(newCell)
                } else {
                    currentCells.add(newCell)
                }
                _uiState.update { it.copy(selectedCells = currentCells) }
            }
            null -> {
                // 旧来の動作（フォールバック）
                if (currentCells.contains(newCell)) {
                    currentCells.remove(newCell)
                } else if (currentCells.size < 4) {
                    currentCells.add(newCell)
                }
                _uiState.update { it.copy(selectedCells = currentCells) }
            }
        }
    }

    fun confirmSelection() {
        // セル選択を確定してパネルを再表示
        _uiState.update {
            it.copy(
                isBlockDefinitionPanelVisible = true,
                cellSelectionMode = CellSelectionMode.NONE
            )
        }
    }

    fun confirmIndividualSelection() {
        // 個別セル選択モードを確定してパネルを再表示
        _uiState.update {
            it.copy(
                isBlockDefinitionPanelVisible = true,
                cellSelectionMode = CellSelectionMode.NONE
            )
        }
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

    // ===== ホール定義関連 =====

    /**
     * 現在のマップのホール定義をDBから読み込む
     */
    fun loadHallsForCurrentMap() {
        val mapDataId = _uiState.value.currentMapData?.id
        Log.d("MapViewModel", "loadHallsForCurrentMap: mapDataId=$mapDataId")

        if (mapDataId == null) {
            Log.w("MapViewModel", "loadHallsForCurrentMap: mapDataId is null, currentMapData=${_uiState.value.currentMapData}")
            return
        }

        viewModelScope.launch {
            try {
                val entities = hallDefinitionDao.getHallsByMapDataIdOnce(mapDataId)
                Log.d("MapViewModel", "loadHallsForCurrentMap: found ${entities.size} halls for mapDataId=$mapDataId")
                val halls = entities.map { entity ->
                    val verticesType = object : TypeToken<List<Vertex>>() {}.type
                    val vertices: List<Vertex> = gson.fromJson(entity.verticesJson, verticesType)
                    HallDefinition(
                        id = entity.id,
                        name = entity.name,
                        vertices = vertices,
                        color = entity.color
                    )
                }
                _uiState.update { it.copy(halls = halls) }
                Log.d("MapViewModel", "loadHallsForCurrentMap: updated state with ${halls.size} halls")

                // ホール読み込み後にgroupOrderをDB読み込み→同期（順序保証）
                loadAndSyncGroupOrder(halls)
            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to load halls", e)
            }
        }
    }

    /**
     * ホール定義パネルを開く
     */
    fun openHallDefinitionPanel() {
        // 注意: loadHallsForCurrentMapは呼ばない
        // 理由: saveHalls()で保存後、uiState.hallsは既に更新されている
        // loadHallsForCurrentMapを呼ぶと非同期でDBから読み込み、
        // mapDataIdの不一致でhallsが空になる可能性がある
        Log.d("MapViewModel", "openHallDefinitionPanel: current halls=${_uiState.value.halls.size}")

        _uiState.update {
            it.copy(
                isHallDefinitionPanelOpen = true,
                isHallDefinitionPanelVisible = true
            )
        }
    }

    /**
     * ホール定義パネルを閉じる
     */
    fun closeHallDefinitionPanel() {
        _uiState.update {
            it.copy(
                isHallDefinitionPanelOpen = false,
                isHallDefinitionPanelVisible = false,
                hallVertexSelectionMode = HallVertexSelectionMode.NONE,
                selectedHallVertices = emptyList(),
                editingHallId = null,
                pendingHallEditState = null
            )
        }
    }

    /**
     * ホール定義パネルを表示（頂点選択後に戻る用）
     */
    fun showHallDefinitionPanel() {
        _uiState.update {
            it.copy(isHallDefinitionPanelVisible = true)
        }
    }

    /**
     * ホール選択ドロップダウンの開閉
     */
    fun toggleHallSelector() {
        _uiState.update { it.copy(isHallSelectorOpen = !it.isHallSelectorOpen) }
    }

    /**
     * ホール選択ドロップダウンを閉じる
     */
    fun closeHallSelector() {
        _uiState.update { it.copy(isHallSelectorOpen = false) }
    }

    /**
     * ホール選択（表示切替）
     */
    fun selectHall(hallId: String?) {
        // nullの場合は全ホール表示
        if (hallId == null) {
            _uiState.update {
                it.copy(
                    selectedHallId = null,
                    isHallSelectorOpen = false
                )
            }
            return
        }

        // 指定されたホールが存在するか確認
        val hall = _uiState.value.halls.find { it.id == hallId }
        if (hall == null) {
            // 存在しないホールIDの場合は全ホール表示に戻す
            _uiState.update {
                it.copy(
                    selectedHallId = null,
                    isHallSelectorOpen = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedHallId = hallId,
                isHallSelectorOpen = false
            )
        }

        // ホール選択時にビューをリセット（ホール内が見える位置に移動）
        centerOnHall(hallId)
    }

    /**
     * 指定ホールを中央に表示
     */
    private fun centerOnHall(hallId: String) {
        val hall = _uiState.value.halls.find { it.id == hallId } ?: return
        val bounds = HallUtils.getHallBounds(hall) ?: return
        val mapData = _uiState.value.currentMapData ?: return

        // 境界チェック
        if (bounds.minRow < 1 || bounds.minCol < 1) return
        if (bounds.maxRow > mapData.maxRow || bounds.maxCol > mapData.maxCol) return

        // ホール中心のセル座標を計算
        val centerRow = (bounds.minRow + bounds.maxRow) / 2
        val centerCol = (bounds.minCol + bounds.maxCol) / 2

        // セル中心のピクセル座標を計算
        var cellX = 0f
        for (c in 1 until centerCol) {
            cellX += mapData.columnWidths[c] ?: mapData.defaultColumnWidth
        }
        cellX += (mapData.columnWidths[centerCol] ?: mapData.defaultColumnWidth) / 2

        var cellY = 0f
        for (r in 1 until centerRow) {
            cellY += mapData.rowHeights[r] ?: mapData.defaultRowHeight
        }
        cellY += (mapData.rowHeights[centerRow] ?: mapData.defaultRowHeight) / 2

        // スケールを調整（ホール全体が見えるように）
        // 現在のスケールを維持しつつ、オフセットを調整
        val currentScale = _uiState.value.scale

        // 仮の画面サイズ（実際の値は取得できないので概算）
        val screenWidth = 1080f
        val screenHeight = 1920f

        val newOffsetX = screenWidth / 2 - cellX * currentScale
        val newOffsetY = screenHeight / 2 - cellY * currentScale

        _uiState.update {
            it.copy(
                offsetX = newOffsetX,
                offsetY = newOffsetY
            )
        }
    }

    /**
     * ホールごとのアイテム数を計算
     */
    fun updateHallItemCounts() {
        val halls = _uiState.value.halls
        val allItems = _uiState.value.items
        val mapData = _uiState.value.currentMapData ?: return
        val blocks = mapData.blocks
        val visitListItemIds = _uiState.value.visitListItemIds

        // 現在のマップの日付に一致するアイテムのみをフィルタリング
        // dayNameは "1日目" の形式、eventDateも "1日目" の形式
        val dayName = mapData.dayName
        val items = allItems.filter { it.eventDate == dayName }

        val counts = mutableMapOf<String, HallItemCount>()

        for (hall in halls) {
            // ホール内のブロック名を取得
            val blocksInHall = HallUtils.getBlocksInHall(hall, blocks)
            val blockNamesInHall = blocksInHall.map { it.name }.toSet()

            // ホール内のアイテムを取得
            val itemsInHall = items.filter { item ->
                blockNamesInHall.contains(item.block)
            }

            // ホール内の全アイテム数
            val totalCount = itemsInHall.size

            // 訪問先リストに追加されたアイテム数
            val executeCount = itemsInHall.count { item ->
                visitListItemIds.contains(item.id)
            }

            counts[hall.id] = HallItemCount(
                executeCount = executeCount,
                totalCount = totalCount
            )
        }

        // ホール未定義のアイテムもカウント
        val definedBlockNames = halls.flatMap { hall ->
            HallUtils.getBlocksInHall(hall, blocks).map { it.name }
        }.toSet()

        val undefinedItems = items.filter { item ->
            !definedBlockNames.contains(item.block)
        }

        if (undefinedItems.isNotEmpty()) {
            val undefinedExecuteCount = undefinedItems.count { item ->
                visitListItemIds.contains(item.id)
            }
            counts["undefined"] = HallItemCount(
                executeCount = undefinedExecuteCount,
                totalCount = undefinedItems.size
            )
        }

        _uiState.update { it.copy(hallItemCounts = counts) }
    }

    /**
     * ホール頂点選択モードを開始（マーカーシステム）
     */
    fun startHallVertexSelection(editingHallId: String? = null, editState: HallEditState? = null) {
        // 既存の頂点があればマーカーとして復元
        val existingVertices = editState?.editingHall?.vertices ?: emptyList()
        val initialMarkers = existingVertices.map { vertex ->
            HallMarker(row = vertex.row, col = vertex.col)
        }

        _uiState.update {
            it.copy(
                hallVertexSelectionMode = HallVertexSelectionMode.SELECTING,
                hallMarkers = initialMarkers,
                isPlacingMarker = false,
                selectedHallVertices = emptyList(),
                editingHallId = editingHallId,
                isHallDefinitionPanelVisible = false,
                pendingHallEditState = editState
            )
        }
    }

    /**
     * ホール頂点を追加（後方互換用）
     */
    fun addHallVertex(row: Int, col: Int) {
        val currentVertices = _uiState.value.selectedHallVertices

        // 同じ頂点が既に選択されている場合は削除
        val existingIndex = currentVertices.indexOfFirst { it.row == row && it.col == col }
        if (existingIndex >= 0) {
            _uiState.update {
                it.copy(selectedHallVertices = currentVertices.filterIndexed { index, _ -> index != existingIndex })
            }
            return
        }

        // 6個まで追加可能
        if (currentVertices.size >= 6) return

        val newVertex = Vertex(row, col)
        _uiState.update { it.copy(selectedHallVertices = currentVertices + newVertex) }
    }

    /**
     * 直前のホール頂点を削除（後方互換用）
     */
    fun removeLastHallVertex() {
        val currentVertices = _uiState.value.selectedHallVertices
        if (currentVertices.isNotEmpty()) {
            _uiState.update { it.copy(selectedHallVertices = currentVertices.dropLast(1)) }
        }
    }

    /**
     * ホール頂点選択を確定（マーカーから頂点を生成）
     */
    fun confirmHallVertexSelection(): List<Vertex> {
        val markers = _uiState.value.hallMarkers
        val vertices = markers.map { Vertex(it.row, it.col) }
        val sortedVertices = HallUtils.computeConvexHull(vertices)

        _uiState.update {
            it.copy(
                hallVertexSelectionMode = HallVertexSelectionMode.NONE,
                hallMarkers = emptyList(),
                isPlacingMarker = false,
                // ソート済み頂点を保持（HallDefinitionPanelで使用）
                selectedHallVertices = sortedVertices,
                isHallDefinitionPanelVisible = true
            )
        }

        return sortedVertices
    }

    /**
     * ホール頂点選択をキャンセル
     */
    fun cancelHallVertexSelection() {
        _uiState.update {
            it.copy(
                hallVertexSelectionMode = HallVertexSelectionMode.NONE,
                hallMarkers = emptyList(),
                isPlacingMarker = false,
                selectedHallVertices = emptyList(),
                editingHallId = null,
                isHallDefinitionPanelVisible = true
            )
        }
    }

    /**
     * ホール定義をDBに保存
     */
    fun saveHalls(halls: List<HallDefinition>) {
        val mapDataId = _uiState.value.currentMapData?.id
        Log.d("MapViewModel", "saveHalls: mapDataId=$mapDataId, hallsCount=${halls.size}")

        if (mapDataId == null) {
            Log.e("MapViewModel", "saveHalls: mapDataId is null, cannot save halls")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("MapViewModel", "saveHalls: deleting existing halls for mapDataId=$mapDataId")
                hallDefinitionDao.deleteHallsByMapDataId(mapDataId)

                val entities = halls.map { hall ->
                    HallDefinitionEntity(
                        id = hall.id,
                        mapDataId = mapDataId,
                        name = hall.name,
                        verticesJson = gson.toJson(hall.vertices),
                        color = hall.color
                    )
                }

                Log.d("MapViewModel", "saveHalls: inserting ${entities.size} halls")
                hallDefinitionDao.insertHalls(entities)
                _uiState.update { it.copy(halls = halls) }
                Log.d("MapViewModel", "saveHalls: completed successfully")

                // ホール変更後にgroupOrderを同期
                syncGroupOrderWithHalls(halls)
            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to save halls", e)
            }
        }
    }

    /**
     * ホール定義を追加
     */
    fun addHall(hall: HallDefinition) {
        val currentHalls = _uiState.value.halls
        saveHalls(currentHalls + hall)
    }

    /**
     * ホール定義を更新
     */
    fun updateHall(updatedHall: HallDefinition) {
        val currentHalls = _uiState.value.halls
        val newHalls = currentHalls.map {
            if (it.id == updatedHall.id) updatedHall else it
        }
        saveHalls(newHalls)
    }

    /**
     * ホール定義を削除
     */
    fun deleteHall(hallId: String) {
        val currentHalls = _uiState.value.halls
        val newHalls = currentHalls.filter { it.id != hallId }
        saveHalls(newHalls)

        // 削除したホールが選択されていた場合はリセット
        if (_uiState.value.selectedHallId == hallId) {
            _uiState.update { it.copy(selectedHallId = null) }
        }
    }

    /**
     * 保留中のホール編集状態を取得してクリア
     */
    fun consumePendingHallEditState(): HallEditState? {
        val state = _uiState.value.pendingHallEditState
        _uiState.update { it.copy(pendingHallEditState = null) }
        return state
    }
}