package com.example.eventshoppingplanner.presentation.screens.shoppinglist

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventshoppingplanner.data.local.dao.DayModeDao
import com.example.eventshoppingplanner.data.local.dao.ExecuteListDao
import com.example.eventshoppingplanner.data.local.dao.HallDefinitionDao
import com.example.eventshoppingplanner.data.local.dao.VisitListDao
import com.example.eventshoppingplanner.data.local.entity.DayModeEntity
import com.example.eventshoppingplanner.data.local.entity.ExecuteListEntity
import com.example.eventshoppingplanner.data.local.entity.VisitListEntity
import com.example.eventshoppingplanner.data.preferences.AppPreference
import com.example.eventshoppingplanner.domain.model.Event
import com.example.eventshoppingplanner.domain.model.HallDefinition
import com.example.eventshoppingplanner.domain.model.PriorityLevel
import com.example.eventshoppingplanner.domain.model.ProtectionLevel
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.domain.model.Vertex
import com.example.eventshoppingplanner.domain.model.createGroupId
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.MapDataRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import com.example.eventshoppingplanner.util.HallUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * executeモードのステータストグルフィルタ
 */
enum class ExecuteStatusFilter(val displayName: String, val status: PurchaseStatus?) {
    ALL("巡回順", null),
    POSTPONE("後回し", PurchaseStatus.POSTPONE),
    LATE("遅参", PurchaseStatus.LATE),
    ABSENT("欠席", PurchaseStatus.ABSENT),
    SOLD_OUT("売切", PurchaseStatus.SOLD_OUT),
    NONE("未購入", PurchaseStatus.NONE);

    fun next(): ExecuteStatusFilter {
        val values = entries
        val nextIndex = (values.indexOf(this) + 1) % values.size
        return values[nextIndex]
    }
}

/**
 * エクスポート用データ
 */
data class ExportData(
    val items: List<ShoppingItem>,
    val executeListItemIds: Map<String, List<String>>
)

data class ShoppingListUiState(
    val event: Event? = null,
    val allItems: List<ShoppingItem> = emptyList(),
    val eventDates: List<String> = emptyList(),
    val selectedDate: String? = null,
    val selectedBlock: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,

    // 日ごと独立モード（"edit" / "execute"）
    val dayModes: Map<String, String> = emptyMap(),

    // 実行列データ（eventDate → itemIds）
    val executeListItemIds: Map<String, List<String>> = emptyMap(),

    // 訪問先リストデータ（eventDate → itemIds）
    val visitListItemIds: Map<String, List<String>> = emptyMap(),

    // 統計
    val purchasedCount: Int = 0,
    val totalCount: Int = 0,
    val remainingAmount: Int = 0,

    // ダイアログ
    val showStatusDialog: Boolean = false,
    val selectedItem: ShoppingItem? = null,
    val showAddItemDialog: Boolean = false,
    val showEditItemDialog: Boolean = false,
    val editingItem: ShoppingItem? = null,

    // 重複スペース検出
    val duplicateSpaceItemIds: Set<String> = emptySet(),

    // executeモード ステータストグルフィルタ
    val executeStatusFilter: ExecuteStatusFilter = ExecuteStatusFilter.ALL,

    // 設定項目
    val executeModeItemDragEnabled: Boolean = false,
    val cardScale: Float = AppPreference.DEFAULT_CARD_SCALE,

    // 範囲選択モード（editモード実行列のみ）
    val isRangeSelectionMode: Boolean = false,
    val rangeSelectionStartId: String? = null,
    val rangeSelectionEndId: String? = null,

    // アドホックグループ（解除まで1つのみ）
    val groupedItemIds: List<String> = emptyList(),  // グループ内アイテムIDリスト（順序保持）

    // ホール定義→ブロック名マッピング（Feature6: ホール一括追加用）
    val hallDefinitions: List<HallDefinition> = emptyList(),
    val hallBlocksMap: Map<String, List<String>> = emptyMap(),  // hallName → blockNames
    // 日別 ブロック名→ホールIDマッピング（ドラッグ制約用）
    val dayBlockToHallIdMap: Map<String, Map<String, String>> = emptyMap(),  // dayName → (blockName → hallId)
    // 日別 ホールID→ホール名マッピング（グループ表示用）
    val dayHallNamesMap: Map<String, Map<String, String>> = emptyMap()  // dayName → (hallId → hallName)
) {
    /** 現在タブのモード */
    val currentMode: String
        get() = dayModes[selectedDate] ?: "edit"

    /** 現在タブの全アイテム */
    val currentTabItems: List<ShoppingItem>
        get() = allItems.filter { it.eventDate == selectedDate }

    /** 現在タブの実行列アイテムID */
    val currentExecuteIds: List<String>
        get() = executeListItemIds[selectedDate] ?: emptyList()

    /** 日付ごとのアイテム数マップ */
    val itemCountByDate: Map<String, Int>
        get() = allItems.groupBy { it.eventDate }.mapValues { it.value.size }

    /** 実行列アイテム（検索フィルタ適用、ブロックフィルタ非適用） */
    val executeColumnItems: List<ShoppingItem>
        get() {
            val executeIds = currentExecuteIds
            val idSet = executeIds.toSet()
            val itemsMap = currentTabItems.associateBy { it.id }
            val ordered = executeIds.mapNotNull { itemsMap[it] }
            return if (searchQuery.isEmpty()) ordered
            else ordered.filter { matchesSearch(it) }
        }

    /** 候補リストアイテム（検索・ブロックフィルタ適用） */
    val candidateColumnItems: List<ShoppingItem>
        get() {
            val executeIdSet = currentExecuteIds.toSet()
            var filtered = currentTabItems
                .filter { !executeIdSet.contains(it.id) }
                .sortedBy { it.sortOrder }
            if (selectedBlock != null) {
                filtered = filtered.filter { it.block == selectedBlock }
            }
            if (searchQuery.isNotEmpty()) {
                filtered = filtered.filter { matchesSearch(it) }
            }
            return filtered
        }

    /** 候補リストに存在するブロック値（動的フィルタ用） */
    val candidateBlocks: List<String>
        get() {
            val executeIdSet = currentExecuteIds.toSet()
            return currentTabItems
                .filter { !executeIdSet.contains(it.id) }
                .map { it.block }
                .distinct()
                .sorted()
        }

    /** executeモードのフィルタ適用済み実行列アイテム */
    val filteredExecuteItems: List<ShoppingItem>
        get() {
            return if (executeStatusFilter == ExecuteStatusFilter.ALL) {
                executeColumnItems
            } else {
                executeColumnItems.filter { it.purchaseStatus == executeStatusFilter.status }
            }
        }

    /** 表示中アイテム（SummaryBar用） */
    val visibleItems: List<ShoppingItem>
        get() = if (currentMode == "execute") {
            filteredExecuteItems
        } else {
            executeColumnItems + candidateColumnItems
        }

    /** 現在タブのブロック→ホールIDマップ */
    val currentBlockToHallIdMap: Map<String, String>
        get() = dayBlockToHallIdMap[selectedDate] ?: emptyMap()

    /** 現在タブのホールID→ホール名マップ */
    val currentHallNamesMap: Map<String, String>
        get() = dayHallNamesMap[selectedDate] ?: emptyMap()

    /** 範囲選択されたアイテムIDセット（始点〜終点の全アイテム） */
    val rangeSelectedItemIds: Set<String>
        get() {
            val startId = rangeSelectionStartId ?: return emptySet()
            val endId = rangeSelectionEndId ?: return setOf(startId)
            val executeIds = currentExecuteIds
            val startIndex = executeIds.indexOf(startId)
            val endIndex = executeIds.indexOf(endId)
            if (startIndex < 0 || endIndex < 0) return emptySet()
            val from = minOf(startIndex, endIndex)
            val to = maxOf(startIndex, endIndex)
            return executeIds.subList(from, to + 1).toSet()
        }

    /** グループ化されたアイテムIDセット（高速参照用） */
    val groupedItemIdSet: Set<String>
        get() = groupedItemIds.toSet()

    /**
     * 実行列アイテムのグループラベル（itemId → groupLabel）
     * ホール名 + 優先度記号。グループ境界表示に使用。
     */
    val executeGroupLabels: Map<String, String>
        get() {
            val blockToHallId = currentBlockToHallIdMap
            if (blockToHallId.isEmpty()) return emptyMap()
            val hallNames = currentHallNamesMap
            val itemsMap = allItems.associateBy { it.id }
            return currentExecuteIds.associateWith { itemId ->
                val item = itemsMap[itemId] ?: return@associateWith ""
                val hallId = blockToHallId[item.block]
                val hallName = if (hallId != null) hallNames[hallId] ?: "?" else "未分類"
                val priority = when (item.priorityLevel) {
                    PriorityLevel.HIGHEST -> " ★★"
                    PriorityLevel.PRIORITY -> " ★"
                    PriorityLevel.NONE -> ""
                }
                "$hallName$priority"
            }
        }

    private fun matchesSearch(item: ShoppingItem): Boolean {
        val q = searchQuery.lowercase()
        return item.circle.lowercase().contains(q) ||
                item.title.lowercase().contains(q) ||
                item.remarks.lowercase().contains(q)
    }
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val eventRepository: EventRepository,
    private val itemRepository: ShoppingItemRepository,
    private val visitListDao: VisitListDao,
    private val executeListDao: ExecuteListDao,
    private val dayModeDao: DayModeDao,
    private val hallDefinitionDao: HallDefinitionDao,
    private val mapDataRepository: MapDataRepository
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type
    private val appPreferences = AppPreference(context)

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    // ドラッグ終了後の保存用
    private var saveOrderJob: Job? = null

    // VisitList同期中フラグ（循環更新防止）
    private var isSyncingVisitList = false

    init {
        loadEvent()
        loadDayModes()
        loadExecuteLists()
        loadVisitLists()
        loadData()
        loadPreferences()
        loadHallDefinitions()
    }

    // ========== データロード ==========

    private fun loadEvent() {
        viewModelScope.launch {
            val event = eventRepository.getEventById(eventId)
            _uiState.update { it.copy(event = event) }
        }
    }

    private fun loadDayModes() {
        viewModelScope.launch {
            dayModeDao.getDayModesByEventId(eventId).collect { entities ->
                val modesMap = entities.associate { it.eventDate to it.viewMode }
                _uiState.update { it.copy(dayModes = modesMap) }
            }
        }
    }

    private fun loadExecuteLists() {
        viewModelScope.launch {
            executeListDao.getExecuteListsByEventId(eventId).collect { entities ->
                val executeMap = entities.associate { entity ->
                    val itemIds: List<String> = gson.fromJson(entity.itemIdsJson, listType) ?: emptyList()
                    entity.eventDate to itemIds
                }
                _uiState.update { it.copy(executeListItemIds = executeMap) }
            }
        }
    }

    private fun loadVisitLists() {
        viewModelScope.launch {
            try {
                visitListDao.getVisitListsByEventId(eventId).collect { entities ->
                    val visitListMap = entities.associate { entity ->
                        val itemIds: List<String> = gson.fromJson(entity.itemIdsJson, listType) ?: emptyList()
                        entity.dayName to itemIds
                    }
                    val oldVisitListMap = _uiState.value.visitListItemIds
                    _uiState.update { it.copy(visitListItemIds = visitListMap) }

                    // VisitList→ExecuteList同期（MapScreen側からの変更を反映）
                    if (!isSyncingVisitList) {
                        syncVisitListToExecuteList(oldVisitListMap, visitListMap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadVisitLists: error", e)
            }
        }
    }

    /**
     * VisitList→ExecuteList同期
     * VisitListに追加されたアイテムをExecuteListにも追加、除去されたものはExecuteListからも除去
     * 並び替えのみの場合もExecuteListの順序を更新する
     */
    private fun syncVisitListToExecuteList(
        oldVisitListMap: Map<String, List<String>>,
        newVisitListMap: Map<String, List<String>>
    ) {
        viewModelScope.launch {
            try {
                // ExecuteListがまだDBからロードされていない場合はスキップ
                // （loadExecuteListsのFlowが先に発火してから再度同期される）
                val currentExecuteMap = _uiState.value.executeListItemIds
                if (currentExecuteMap.isEmpty() && newVisitListMap.isNotEmpty()) {
                    // ExecuteListをDBから直接読み込んで使用
                    val dbEntities = executeListDao.getExecuteListsByEventIdOnce(eventId)
                    val dbExecuteMap = dbEntities.associate { entity ->
                        val itemIds: List<String> = gson.fromJson(entity.itemIdsJson, listType) ?: emptyList()
                        entity.eventDate to itemIds
                    }
                    if (dbExecuteMap.isNotEmpty()) {
                        doSyncVisitToExecute(oldVisitListMap, newVisitListMap, dbExecuteMap.toMutableMap())
                    }
                    return@launch
                }

                doSyncVisitToExecute(oldVisitListMap, newVisitListMap, currentExecuteMap.toMutableMap())
            } catch (e: Exception) {
                Log.e(TAG, "syncVisitListToExecuteList: error", e)
            }
        }
    }

    /**
     * VisitList→ExecuteList同期の実処理
     */
    private suspend fun doSyncVisitToExecute(
        oldVisitListMap: Map<String, List<String>>,
        newVisitListMap: Map<String, List<String>>,
        currentExecuteMap: MutableMap<String, List<String>>
    ) {
        var needsSave = false
        val changedDates = mutableSetOf<String>()

        val allDates = (oldVisitListMap.keys + newVisitListMap.keys).toSet()

        for (eventDate in allDates) {
            val oldVisitIds = oldVisitListMap[eventDate] ?: emptyList()
            val newVisitIds = newVisitListMap[eventDate] ?: emptyList()

            if (oldVisitIds == newVisitIds) continue
            changedDates.add(eventDate)

            val currentExecuteIds = (currentExecuteMap[eventDate] ?: emptyList()).toMutableList()
            val oldVisitSet = oldVisitIds.toSet()
            val newVisitSet = newVisitIds.toSet()

            // VisitListに追加されたアイテムをExecuteListにも追加
            val addedIds = newVisitSet - oldVisitSet
            for (id in addedIds) {
                if (!currentExecuteIds.contains(id)) {
                    currentExecuteIds.add(id)
                }
            }

            // VisitListから除去されたアイテムをExecuteListからも除去
            val removedIds = oldVisitSet - newVisitSet
            currentExecuteIds.removeAll(removedIds)

            // VisitListの並び順をExecuteListにも反映
            val visitOrderMap = newVisitIds.withIndex().associate { it.value to it.index }
            val before = currentExecuteIds.toList()
            currentExecuteIds.sortBy { visitOrderMap[it] ?: Int.MAX_VALUE }
            currentExecuteMap[eventDate] = currentExecuteIds.toList()

            // 追加・削除・並び替えのいずれかがあったら保存対象
            if (addedIds.isNotEmpty() || removedIds.isNotEmpty() || before != currentExecuteIds) {
                needsSave = true
                Log.d(TAG, "doSyncVisitToExecute: date=$eventDate added=${addedIds.size} removed=${removedIds.size} reordered=${before != currentExecuteIds}")
            }
        }

        if (needsSave) {
            // UIを更新
            _uiState.update { it.copy(executeListItemIds = currentExecuteMap.toMap()) }

            // DBに保存（変更があった日のみ）
            for (eventDate in changedDates) {
                val itemIds = currentExecuteMap[eventDate] ?: emptyList()
                executeListDao.insertExecuteList(
                    ExecuteListEntity(
                        id = "${eventId}_${eventDate}",
                        eventId = eventId,
                        eventDate = eventDate,
                        itemIdsJson = gson.toJson(itemIds)
                    )
                )
            }
            Log.d(TAG, "doSyncVisitToExecute: synced changes for ${changedDates.size} dates")
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            itemRepository.getItemsByEventId(eventId).collect { items ->
                val dates = sortEventDates(items.map { it.eventDate }.distinct())

                val currentState = _uiState.value
                val effectiveDate = if (currentState.selectedDate != null && dates.contains(currentState.selectedDate)) {
                    currentState.selectedDate
                } else {
                    dates.firstOrNull()
                }

                val duplicateIds = calculateDuplicateSpaceItemIds(items, effectiveDate)

                // 統計は全アイテム（現在タブ）から計算
                val tabItems = items.filter { it.eventDate == effectiveDate }
                val purchased = tabItems.count { it.purchaseStatus == PurchaseStatus.PURCHASED }
                val remaining = tabItems
                    .filter { it.purchaseStatus !in listOf(PurchaseStatus.PURCHASED, PurchaseStatus.SOLD_OUT, PurchaseStatus.ABSENT) }
                    .sumOf { (it.price ?: 0) * it.quantity }

                _uiState.update {
                    it.copy(
                        allItems = items,
                        eventDates = dates,
                        selectedDate = effectiveDate,
                        isLoading = false,
                        purchasedCount = purchased,
                        totalCount = tabItems.size,
                        remainingAmount = remaining,
                        duplicateSpaceItemIds = duplicateIds
                    )
                }
            }
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            appPreferences.executeModeDragEnabled.collect { enabled ->
                _uiState.update { it.copy(executeModeItemDragEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.cardScale.collect { scale ->
                _uiState.update { it.copy(cardScale = scale) }
            }
        }
    }

    // ========== ホール定義読み込み（Feature6: ホール一括追加用） ==========

    /**
     * イベントに紐づくマップデータからホール定義を読み込み、
     * ホール名→ブロック名リストのマッピングを構築する。
     */
    private fun loadHallDefinitions() {
        viewModelScope.launch {
            try {
                val mapDataMap = mapDataRepository.getMapDataByEventIdOnce(eventId)
                if (mapDataMap.isEmpty()) return@launch

                val allHalls = mutableListOf<HallDefinition>()
                val hallBlocksMap = mutableMapOf<String, List<String>>()
                val dayBlockToHallIdMap = mutableMapOf<String, Map<String, String>>()
                val dayHallNamesMap = mutableMapOf<String, Map<String, String>>()

                for ((dayName, dayMapData) in mapDataMap) {
                    val entities = hallDefinitionDao.getHallsByMapDataIdOnce(dayMapData.id)
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
                    allHalls.addAll(halls)

                    val perDayBlockToHall = mutableMapOf<String, String>()
                    val perDayHallNames = mutableMapOf<String, String>()

                    // ホールごとにブロック名を取得
                    for (hall in halls) {
                        perDayHallNames[hall.id] = hall.name
                        val blocksInHall = HallUtils.getBlocksInHall(hall, dayMapData.blocks)
                        val blockNames = blocksInHall.map { it.name }
                        // hallBlocksMap（全日まとめ、一括追加ボタン用）
                        val existing = hallBlocksMap[hall.name] ?: emptyList()
                        hallBlocksMap[hall.name] = (existing + blockNames).distinct()
                        // 日別マッピング（ドラッグ制約用）
                        blockNames.forEach { blockName ->
                            perDayBlockToHall[blockName] = hall.id
                        }
                    }

                    dayBlockToHallIdMap[dayName] = perDayBlockToHall
                    dayHallNamesMap[dayName] = perDayHallNames
                }

                _uiState.update {
                    it.copy(
                        hallDefinitions = allHalls,
                        hallBlocksMap = hallBlocksMap,
                        dayBlockToHallIdMap = dayBlockToHallIdMap,
                        dayHallNamesMap = dayHallNamesMap
                    )
                }
                Log.d(TAG, "loadHallDefinitions: ${allHalls.size} halls, days=${dayBlockToHallIdMap.keys}")
            } catch (e: Exception) {
                Log.e(TAG, "loadHallDefinitions: error", e)
            }
        }
    }

    /**
     * ホール名を指定して、そのホール内の全ブロックの候補アイテムを実行列に一括追加
     */
    fun bulkAddByHall(hallName: String) {
        val state = _uiState.value
        val eventDate = state.selectedDate ?: return
        val blockNames = state.hallBlocksMap[hallName] ?: return
        val blockNameSet = blockNames.toSet()

        // 現在の候補アイテムからホール内ブロックに属するものを抽出
        val executeIdSet = state.currentExecuteIds.toSet()
        val targetItems = state.currentTabItems
            .filter { !executeIdSet.contains(it.id) && blockNameSet.contains(it.block) }

        if (targetItems.isEmpty()) return

        // 実行列に一括追加
        val currentIds = (state.executeListItemIds[eventDate] ?: emptyList()).toMutableList()
        targetItems.forEach { item ->
            if (!currentIds.contains(item.id)) {
                currentIds.add(item.id)
            }
        }

        saveExecuteList(eventDate, currentIds)
        syncExecuteToVisitList(eventDate, currentIds)
        Log.d(TAG, "bulkAddByHall: added ${targetItems.size} items from hall=$hallName")
    }

    // ========== 参加日ソート（WEB版準拠） ==========

    private fun sortEventDates(dates: List<String>): List<String> {
        return dates.sortedWith(compareBy<String> {
            val match = Regex("\\d+").find(it)
            match?.value?.toIntOrNull() ?: 0
        }.thenBy { it })
    }

    // ========== 重複スペース検出 ==========

    private fun extractNumberPart(number: String): String {
        val firstAlphaIndex = number.indexOfFirst { it.isLetter() }
        return if (firstAlphaIndex == -1) number.trim()
        else if (firstAlphaIndex == 0) ""
        else number.substring(0, firstAlphaIndex).trim()
    }

    private fun getSpaceKey(item: ShoppingItem): String {
        val numberPart = extractNumberPart(item.number)
        return "${item.eventDate}|${item.block.trim()}|$numberPart"
    }

    private fun calculateDuplicateSpaceItemIds(items: List<ShoppingItem>, selectedDate: String?): Set<String> {
        if (selectedDate == null) return emptySet()
        val itemsForDate = items.filter { it.eventDate == selectedDate }
        val spaceItemsMap = mutableMapOf<String, MutableList<String>>()
        itemsForDate.forEach { item ->
            val spaceKey = getSpaceKey(item)
            if (spaceKey.isNotEmpty()) {
                spaceItemsMap.getOrPut(spaceKey) { mutableListOf() }.add(item.id)
            }
        }
        val duplicateIds = mutableSetOf<String>()
        spaceItemsMap.forEach { (_, itemIds) ->
            if (itemIds.size > 1) duplicateIds.addAll(itemIds)
        }
        return duplicateIds
    }

    // ========== タブ選択・フィルタ ==========

    fun selectDate(date: String?) {
        _uiState.update {
            it.copy(
                selectedDate = date,
                selectedBlock = null,  // タブ変更時にブロックフィルタをリセット
                executeStatusFilter = ExecuteStatusFilter.ALL,  // ステータスフィルタもリセット
                isRangeSelectionMode = false,
                rangeSelectionStartId = null,
                rangeSelectionEndId = null,
                groupedItemIds = emptyList()
            )
        }
        updateStats()
        updateDuplicateSpaceItemIds()
    }

    fun selectBlock(block: String?) {
        _uiState.update { it.copy(selectedBlock = block) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ========== モード管理 ==========

    /**
     * 現在選択中のタブのモードを切替（TopAppBarボタン用）
     */
    fun toggleCurrentDayMode() {
        val state = _uiState.value
        val currentDate = state.selectedDate ?: return
        val currentMode = state.dayModes[currentDate] ?: "edit"
        val newMode = if (currentMode == "edit") "execute" else "edit"
        setDayMode(currentDate, newMode)
    }

    /**
     * 特定の参加日のモードを切替（タブ長押し用）
     */
    fun toggleDayMode(eventDate: String) {
        val currentMode = _uiState.value.dayModes[eventDate] ?: "edit"
        val newMode = if (currentMode == "edit") "execute" else "edit"
        setDayMode(eventDate, newMode)
    }

    private fun setDayMode(eventDate: String, mode: String) {
        // UIを即座に更新（モード切替時にグループ・範囲選択もクリア）
        _uiState.update {
            it.copy(
                dayModes = it.dayModes + (eventDate to mode),
                executeStatusFilter = ExecuteStatusFilter.ALL,  // モード切替時にフィルタリセット
                isRangeSelectionMode = false,
                rangeSelectionStartId = null,
                rangeSelectionEndId = null,
                groupedItemIds = emptyList()
            )
        }
        // DBに永続化
        viewModelScope.launch {
            try {
                dayModeDao.insertDayMode(
                    DayModeEntity(
                        id = "${eventId}_${eventDate}",
                        eventId = eventId,
                        eventDate = eventDate,
                        viewMode = mode
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "setDayMode: error", e)
            }
        }
    }

    // ========== 実行列管理 ==========

    /**
     * アイテムを実行列に追加（候補→実行列）
     */
    fun addToExecuteList(item: ShoppingItem) {
        val state = _uiState.value
        val eventDate = state.selectedDate ?: return
        val currentIds = (state.executeListItemIds[eventDate] ?: emptyList()).toMutableList()
        if (currentIds.contains(item.id)) return

        currentIds.add(item.id)
        saveExecuteList(eventDate, currentIds)
        syncExecuteToVisitList(eventDate, currentIds)
    }

    /**
     * アイテムを実行列から除去（実行列→候補）
     */
    fun removeFromExecuteList(item: ShoppingItem) {
        val state = _uiState.value
        val eventDate = state.selectedDate ?: return
        val currentIds = (state.executeListItemIds[eventDate] ?: emptyList()).toMutableList()
        if (!currentIds.contains(item.id)) return

        currentIds.remove(item.id)
        saveExecuteList(eventDate, currentIds)
        syncExecuteToVisitList(eventDate, currentIds)
    }

    /**
     * アイテムの実行列所属をトグル
     */
    fun toggleExecuteList(item: ShoppingItem) {
        val state = _uiState.value
        val eventDate = state.selectedDate ?: return
        val currentIds = state.executeListItemIds[eventDate] ?: emptyList()
        if (currentIds.contains(item.id)) {
            removeFromExecuteList(item)
        } else {
            addToExecuteList(item)
        }
    }

    private fun saveExecuteList(eventDate: String, itemIds: List<String>) {
        // UI即座に更新
        _uiState.update {
            it.copy(executeListItemIds = it.executeListItemIds + (eventDate to itemIds))
        }
        // DB保存
        viewModelScope.launch {
            try {
                executeListDao.insertExecuteList(
                    ExecuteListEntity(
                        id = "${eventId}_${eventDate}",
                        eventId = eventId,
                        eventDate = eventDate,
                        itemIdsJson = gson.toJson(itemIds)
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "saveExecuteList: error", e)
            }
        }
    }

    /**
     * 実行列→訪問先リスト同期
     * 実行列の並び順をVisitListにも反映する。
     * ExecuteListにのみ存在するアイテムは追加、ExecuteListにないアイテムは除去。
     */
    private fun syncExecuteToVisitList(eventDate: String, executeIds: List<String>) {
        if (isSyncingVisitList) return
        isSyncingVisitList = true

        viewModelScope.launch {
            try {
                // ExecuteListの順序をそのままVisitListに反映
                // （ExecuteListとVisitListのアイテム集合は同一であるべき）
                val newVisitIds = executeIds.toList()

                visitListDao.insertVisitList(
                    VisitListEntity(
                        id = "${eventId}_${eventDate}",
                        eventId = eventId,
                        dayName = eventDate,
                        itemIdsJson = gson.toJson(newVisitIds)
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "syncExecuteToVisitList: error", e)
            } finally {
                isSyncingVisitList = false
            }
        }
    }

    // ========== 範囲選択モード ==========

    /**
     * 範囲選択モードのトグル
     * OFF時にグループ・範囲選択状態をクリア
     */
    fun toggleRangeSelectionMode() {
        val current = _uiState.value.isRangeSelectionMode
        _uiState.update {
            if (current) {
                // OFF時: 範囲選択・グループをクリア
                it.copy(
                    isRangeSelectionMode = false,
                    rangeSelectionStartId = null,
                    rangeSelectionEndId = null,
                    groupedItemIds = emptyList()
                )
            } else {
                it.copy(
                    isRangeSelectionMode = true,
                    rangeSelectionStartId = null,
                    rangeSelectionEndId = null
                )
            }
        }
    }

    /**
     * 範囲選択モードでのアイテムタップ
     * 始点未設定→始点設定、始点設定済み→終点設定（同グループ検証）
     */
    fun onRangeSelectionTap(item: ShoppingItem): String? {
        val state = _uiState.value
        if (!state.isRangeSelectionMode) return null
        val executeIds = state.currentExecuteIds
        if (!executeIds.contains(item.id)) return null  // 実行列外のアイテムは無視

        if (state.rangeSelectionStartId == null) {
            // 始点設定
            _uiState.update { it.copy(rangeSelectionStartId = item.id, rangeSelectionEndId = null) }
            return null
        } else {
            // 終点設定: 始点と同じグループか検証
            val itemsMap = state.allItems.associateBy { it.id }
            val startItem = itemsMap[state.rangeSelectionStartId]
            if (startItem != null && state.currentBlockToHallIdMap.isNotEmpty()) {
                val startGroup = getItemGroupId(startItem)
                val endGroup = getItemGroupId(item)
                if (startGroup != endGroup) {
                    // 異なるグループ → エラー
                    return "異なるホール・優先度グループのアイテムは範囲選択できません"
                }
            }
            _uiState.update { it.copy(rangeSelectionEndId = item.id) }
            return null
        }
    }

    /**
     * 範囲選択をリセット（始点・終点クリア、グループは維持）
     */
    fun clearRangeSelection() {
        _uiState.update {
            it.copy(rangeSelectionStartId = null, rangeSelectionEndId = null)
        }
    }

    // ========== アドホックグループ ==========

    /**
     * 範囲選択されたアイテムをグループ化
     */
    fun groupSelectedItems() {
        val state = _uiState.value
        val selectedIds = state.rangeSelectedItemIds
        if (selectedIds.size < 2) return

        // 実行列上の順序を保持してグループIDリスト作成
        val executeIds = state.currentExecuteIds
        val ordered = executeIds.filter { selectedIds.contains(it) }

        _uiState.update {
            it.copy(
                groupedItemIds = ordered,
                rangeSelectionStartId = null,
                rangeSelectionEndId = null
            )
        }
        Log.d(TAG, "groupSelectedItems: grouped ${ordered.size} items")
    }

    /**
     * 範囲選択されたアイテムを候補リストに一括移動
     */
    fun bulkMoveToCandidate() {
        val state = _uiState.value
        val eventDate = state.selectedDate ?: return
        val selectedIds = state.rangeSelectedItemIds
        if (selectedIds.isEmpty()) return

        val currentIds = (state.executeListItemIds[eventDate] ?: emptyList()).toMutableList()
        currentIds.removeAll(selectedIds)

        // グループも解除（移動対象がグループ内にあれば）
        val remainingGroupIds = state.groupedItemIds.filter { !selectedIds.contains(it) }

        _uiState.update {
            it.copy(
                rangeSelectionStartId = null,
                rangeSelectionEndId = null,
                groupedItemIds = if (remainingGroupIds.size < 2) emptyList() else remainingGroupIds
            )
        }

        saveExecuteList(eventDate, currentIds)
        syncExecuteToVisitList(eventDate, currentIds)
        Log.d(TAG, "bulkMoveToCandidate: moved ${selectedIds.size} items")
    }

    /**
     * グループ解除
     */
    fun clearGroup() {
        _uiState.update { it.copy(groupedItemIds = emptyList()) }
    }

    // ========== ドラッグ&ドロップ（実行列の並び替え） ==========

    /**
     * アイテムのグループID（ホールID×優先度）を取得
     * 同じグループ内でのみドラッグ入れ替えを許可する
     */
    private fun getItemGroupId(item: ShoppingItem): String {
        val blockToHallId = _uiState.value.currentBlockToHallIdMap
        val hallId = blockToHallId[item.block]
        return createGroupId(hallId, item.priorityLevel)
    }

    /**
     * ディスプレイブロック単位で実行列を並び替え。
     * Screen側のDisplayBlockに対応するインデックスで呼ばれる。
     * グループは1ブロックとして扱われるため、グループ全体が自然に移動する。
     */
    fun moveExecuteItem(fromBlockIndex: Int, toBlockIndex: Int) {
        val state = _uiState.value
        val eventDate = state.selectedDate ?: return
        val executeIds = (state.executeListItemIds[eventDate] ?: emptyList())
        val groupIdSet = state.groupedItemIdSet

        // ブロック構築（Screen側のbuildDisplayBlocksと同じロジック）
        val blocks = buildBlocks(executeIds, groupIdSet)

        if (fromBlockIndex < 0 || fromBlockIndex >= blocks.size ||
            toBlockIndex < 0 || toBlockIndex >= blocks.size) return
        if (fromBlockIndex == toBlockIndex) return

        // ホール×優先度制約チェック
        if (state.currentBlockToHallIdMap.isNotEmpty()) {
            val itemsMap = state.allItems.associateBy { it.id }
            val fromBlock = blocks[fromBlockIndex]
            val toBlock = blocks[toBlockIndex]
            val fromItem = itemsMap[fromBlock.first()]
            val toItem = itemsMap[toBlock.first()]
            if (fromItem != null && toItem != null) {
                val fromGroup = getItemGroupId(fromItem)
                val toGroup = getItemGroupId(toItem)
                if (fromGroup != toGroup) return  // 異なるグループ → ドラッグ拒否
            }
        }

        // ブロック単位で入れ替え
        val mutableBlocks = blocks.toMutableList()
        val block = mutableBlocks.removeAt(fromBlockIndex)
        mutableBlocks.add(toBlockIndex, block)

        // フラットに展開
        val newExecuteIds = mutableBlocks.flatten()

        // UI即座に更新
        _uiState.update {
            it.copy(executeListItemIds = it.executeListItemIds + (eventDate to newExecuteIds))
        }

        // debounce保存
        saveOrderJob?.cancel()
        saveOrderJob = viewModelScope.launch {
            delay(300)
            saveExecuteList(eventDate, newExecuteIds)
            syncExecuteToVisitList(eventDate, newExecuteIds)
        }
    }

    /**
     * 実行列IDリストからブロック（グループ=1ブロック、非グループ=個別ブロック）を構築
     */
    private fun buildBlocks(executeIds: List<String>, groupIdSet: Set<String>): List<List<String>> {
        if (groupIdSet.isEmpty()) {
            return executeIds.map { listOf(it) }
        }
        val blocks = mutableListOf<List<String>>()
        var i = 0
        while (i < executeIds.size) {
            if (groupIdSet.contains(executeIds[i])) {
                val groupBlock = mutableListOf<String>()
                while (i < executeIds.size && groupIdSet.contains(executeIds[i])) {
                    groupBlock.add(executeIds[i])
                    i++
                }
                blocks.add(groupBlock)
            } else {
                blocks.add(listOf(executeIds[i]))
                i++
            }
        }
        return blocks
    }

    // ========== executeモード ステータストグルフィルタ ==========

    fun cycleExecuteStatusFilter() {
        _uiState.update {
            it.copy(executeStatusFilter = it.executeStatusFilter.next())
        }
    }

    // ========== ステータス・アイテム操作 ==========

    fun showStatusDialog(item: ShoppingItem) {
        _uiState.update { it.copy(showStatusDialog = true, selectedItem = item) }
    }

    fun hideStatusDialog() {
        _uiState.update { it.copy(showStatusDialog = false, selectedItem = null) }
    }

    fun updateItemStatus(status: PurchaseStatus) {
        viewModelScope.launch {
            _uiState.value.selectedItem?.let { item ->
                val updatedItem = applyProtectionLevelAutoChange(item, purchaseStatusChanged = true)
                    .copy(purchaseStatus = status)
                itemRepository.updateItem(updatedItem)
            }
            hideStatusDialog()
        }
    }

    fun cycleItemStatus(item: ShoppingItem) {
        viewModelScope.launch {
            val nextStatus = when (item.purchaseStatus) {
                PurchaseStatus.NONE -> PurchaseStatus.PURCHASED
                PurchaseStatus.PURCHASED -> PurchaseStatus.SOLD_OUT
                PurchaseStatus.SOLD_OUT -> PurchaseStatus.ABSENT
                PurchaseStatus.ABSENT -> PurchaseStatus.POSTPONE
                PurchaseStatus.POSTPONE -> PurchaseStatus.LATE
                PurchaseStatus.LATE -> PurchaseStatus.NONE
            }
            val updatedItem = applyProtectionLevelAutoChange(item, purchaseStatusChanged = true)
                .copy(purchaseStatus = nextStatus)
            itemRepository.updateItem(updatedItem)
        }
    }

    /**
     * executeモード時に購入状態または価格が変更されたら、
     * protectionLevel が NONE の場合は DELETABLE に自動変更
     */
    private fun applyProtectionLevelAutoChange(
        item: ShoppingItem,
        purchaseStatusChanged: Boolean = false,
        priceChanged: Boolean = false
    ): ShoppingItem {
        val currentMode = _uiState.value.currentMode
        if (currentMode == "execute" && (purchaseStatusChanged || priceChanged)) {
            if (item.protectionLevel == ProtectionLevel.NONE) {
                return item.copy(protectionLevel = ProtectionLevel.DELETABLE)
            }
        }
        return item
    }

    fun showAddItemDialog() {
        _uiState.update { it.copy(showAddItemDialog = true) }
    }

    fun hideAddItemDialog() {
        _uiState.update { it.copy(showAddItemDialog = false) }
    }

    fun addItem(item: ShoppingItem) {
        viewModelScope.launch {
            val maxSortOrder = _uiState.value.allItems.maxOfOrNull { it.sortOrder } ?: -1
            val newItem = item.copy(
                eventId = eventId,
                sortOrder = maxSortOrder + 1
            )
            itemRepository.insertItem(newItem)
            hideAddItemDialog()
        }
    }

    fun showEditItemDialog(item: ShoppingItem) {
        _uiState.update { it.copy(showEditItemDialog = true, editingItem = item) }
    }

    fun hideEditItemDialog() {
        _uiState.update { it.copy(showEditItemDialog = false, editingItem = null) }
    }

    fun updateItem(item: ShoppingItem) {
        viewModelScope.launch {
            itemRepository.updateItem(item)
            hideEditItemDialog()
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch {
            itemRepository.deleteItem(item)
            // 実行列からも除去
            val state = _uiState.value
            val eventDate = item.eventDate
            val currentIds = (state.executeListItemIds[eventDate] ?: emptyList()).toMutableList()
            if (currentIds.remove(item.id)) {
                saveExecuteList(eventDate, currentIds)
                syncExecuteToVisitList(eventDate, currentIds)
            }
            hideEditItemDialog()
        }
    }

    fun updateItemPrice(itemId: String, price: Int?) {
        viewModelScope.launch {
            val item = itemRepository.getItemById(itemId)
            item?.let {
                val originalPrice = it.price
                val priceChanged = originalPrice != price
                val updatedItem = applyProtectionLevelAutoChange(it, priceChanged = priceChanged)
                    .copy(price = price)
                itemRepository.updateItem(updatedItem)
            }
        }
    }

    // ========== カードスケール（ピンチズーム） ==========

    fun setCardScale(scale: Float) {
        val clampedScale = scale.coerceIn(AppPreference.MIN_CARD_SCALE, AppPreference.MAX_CARD_SCALE)
        _uiState.update { it.copy(cardScale = clampedScale) }
        viewModelScope.launch {
            appPreferences.setCardScale(clampedScale)
        }
    }

    // ========== 統計更新 ==========

    private fun updateStats() {
        val state = _uiState.value
        val tabItems = state.allItems.filter { it.eventDate == state.selectedDate }
        val purchased = tabItems.count { it.purchaseStatus == PurchaseStatus.PURCHASED }
        val remaining = tabItems
            .filter { it.purchaseStatus !in listOf(PurchaseStatus.PURCHASED, PurchaseStatus.SOLD_OUT, PurchaseStatus.ABSENT) }
            .sumOf { (it.price ?: 0) * it.quantity }
        _uiState.update {
            it.copy(
                purchasedCount = purchased,
                totalCount = tabItems.size,
                remainingAmount = remaining
            )
        }
    }

    private fun updateDuplicateSpaceItemIds() {
        val state = _uiState.value
        val duplicateIds = calculateDuplicateSpaceItemIds(state.allItems, state.selectedDate)
        _uiState.update { it.copy(duplicateSpaceItemIds = duplicateIds) }
    }

    // ========== エクスポート ==========

    suspend fun getExportData(): ExportData {
        val items = itemRepository.getItemsByEventId(eventId).first()
        val executeListItemIds = _uiState.value.executeListItemIds
        return ExportData(items, executeListItemIds)
    }

    companion object {
        private const val TAG = "ShoppingListViewModel"
    }
}