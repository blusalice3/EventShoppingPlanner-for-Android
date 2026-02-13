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
import com.example.eventshoppingplanner.domain.model.DayMapData
import com.example.eventshoppingplanner.domain.model.PriorityLevel
import com.example.eventshoppingplanner.domain.model.RouteSegment
import com.example.eventshoppingplanner.domain.model.ItemSource
import com.example.eventshoppingplanner.domain.model.ProtectionLevel
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.domain.model.Vertex
import com.example.eventshoppingplanner.domain.model.createGroupId
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.MapDataRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import com.example.eventshoppingplanner.util.HallUtils
import com.example.eventshoppingplanner.util.PathfindingUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * 集中モードのフェーズ
 */
enum class FocusPhase(val displayName: String) {
    NORMAL("通常"),
    POSTPONED("後回し"),
    LATE("遅参")
}

/**
 * 集中モードのレイアウトモード
 */
enum class FocusLayoutMode(val displayName: String) {
    SMARTPHONE("スマートフォン"),
    TABLET("タブレット")
}

/**
 * 集中モード: セルタップポップアップ情報
 */
data class FocusCellPopup(
    val blockName: String,
    val number: Int,
    val items: List<ShoppingItem>
)

/**
 * 集中モード: 新規アイテム追加ダイアログ情報
 */
data class FocusAddItemDialog(
    val eventDate: String,
    val block: String,
    val number: String
)

/**
 * 集中モード: マップセルの状態
 */
data class FocusCellState(
    val hasItems: Boolean,
    val items: List<ShoppingItem>,
    val visitKeys: Set<String>,
    val isCurrentPosition: Boolean,
    val isNextDestination: Boolean,
    val allNone: Boolean,
    val allProcessed: Boolean,
    val hasPostponed: Boolean,
    val hasLate: Boolean,
    val isVisited: Boolean
)

/**
 * 集中モード: ルート範囲
 */
data class FocusRouteBounds(
    val minRow: Int, val maxRow: Int,
    val minCol: Int, val maxCol: Int
)

/**
 * 集中モードの訪問先
 */
data class FocusVisit(
    val key: String,
    val items: List<ShoppingItem>
) {
    companion object {
        /** ナンバーからベース部分を抽出（"12a" → "12a", "12ab" → "12a"） */
        fun extractBaseNumber(number: String): String {
            val match = Regex("^(\\d+[a-zA-Z])").find(number)
            return match?.groupValues?.get(1)?.lowercase() ?: number.lowercase()
        }

        /** 訪問先キー生成（参加日 + ブロック + ベースナンバー） */
        fun getVisitKey(item: ShoppingItem): String {
            val baseNumber = extractBaseNumber(item.number)
            return "${item.eventDate}-${item.block}-$baseNumber"
        }
    }
}

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
    val dayHallNamesMap: Map<String, Map<String, String>> = emptyMap(),  // dayName → (hallId → hallName)

    // ========== 集中モード状態 ==========
    val focusPhase: FocusPhase = FocusPhase.NORMAL,
    val focusPhaseIndex: Int = 0,
    val focusCompleted: Boolean = false,
    val focusLastInteractedItemId: String? = null,
    val postponedPhaseItemIds: Set<String> = emptySet(),
    val latePhaseItemIds: Set<String> = emptySet(),
    val focusSavedPhaseIndices: Map<FocusPhase, Int> = mapOf(
        FocusPhase.NORMAL to 0, FocusPhase.POSTPONED to 0, FocusPhase.LATE to 0
    ),
    val focusNotification: String? = null,
    val autoAdvanceCountdown: Int? = null,
    val blinkingPriceItemIds: Set<String> = emptySet(),
    val showPhaseChangeDialog: Boolean = false,
    val phaseChangeTargetPhase: FocusPhase? = null,
    val focusLayoutMode: FocusLayoutMode = FocusLayoutMode.SMARTPHONE,

    // ========== 集中モード マップ連動 ==========
    val focusMapVisible: Boolean = false,
    val focusMapZoomLevel: Int = 100,               // 30-100（10刻み）
    val focusMapRotationByMapName: Map<String, Float> = emptyMap(), // mapName -> 角度（0-359）
    val focusSelectedHallId: String = "follow",     // "follow" = 追随モード, ホールID
    val focusSplitRatio: Float = 0.5f,              // SP+マップ時の上下分割比率（0.2-0.8）
    val focusMapDataList: Map<String, DayMapData> = emptyMap(),
    val focusCellPopup: FocusCellPopup? = null,
    val focusAddItemDialog: FocusAddItemDialog? = null
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

    // ========== 集中モード算出プロパティ ==========

    /** 集中モード: 実行列アイテム（順序保持） */
    val focusExecuteItems: List<ShoppingItem>
        get() {
            val executeIds = currentExecuteIds
            val itemsMap = allItems.associateBy { it.id }
            return executeIds.mapNotNull { itemsMap[it] }
        }

    /** 集中モード: 全訪問先リスト（実行列順序でグループ化） */
    val focusAllVisits: List<FocusVisit>
        get() {
            val visitKeyOrder = mutableListOf<String>()
            val visitMap = mutableMapOf<String, MutableList<ShoppingItem>>()
            focusExecuteItems.forEach { item ->
                val key = FocusVisit.getVisitKey(item)
                if (!visitMap.containsKey(key)) {
                    visitMap[key] = mutableListOf()
                    visitKeyOrder.add(key)
                }
                visitMap[key]!!.add(item)
            }
            return visitKeyOrder.map { key -> FocusVisit(key, visitMap[key]!!) }
        }

    /** 現時点のPostpone/LateアイテムIDセット（通常フェーズ中の動的参照用） */
    private val currentPostponedItemIds: Set<String>
        get() = focusExecuteItems.filter { it.purchaseStatus == PurchaseStatus.POSTPONE }.map { it.id }.toSet()
    private val currentLateItemIds: Set<String>
        get() = focusExecuteItems.filter { it.purchaseStatus == PurchaseStatus.LATE }.map { it.id }.toSet()

    /** フェーズごとの訪問先リスト */
    val focusVisitsByPhase: Map<FocusPhase, List<FocusVisit>>
        get() {
            val normal = mutableListOf<FocusVisit>()
            val postponed = mutableListOf<FocusVisit>()
            val late = mutableListOf<FocusVisit>()

            focusAllVisits.forEach { visit ->
                normal.add(visit)

                // 後回しフェーズ
                if (focusPhase == FocusPhase.NORMAL) {
                    if (visit.items.any { currentPostponedItemIds.contains(it.id) }) postponed.add(visit)
                } else {
                    if (visit.items.any { postponedPhaseItemIds.contains(it.id) }) postponed.add(visit)
                }

                // 遅参フェーズ
                if (focusPhase == FocusPhase.NORMAL || focusPhase == FocusPhase.POSTPONED) {
                    if (visit.items.any { currentLateItemIds.contains(it.id) }) late.add(visit)
                } else {
                    if (visit.items.any { latePhaseItemIds.contains(it.id) }) late.add(visit)
                }
            }

            return mapOf(FocusPhase.NORMAL to normal, FocusPhase.POSTPONED to postponed, FocusPhase.LATE to late)
        }

    /** 現在フェーズの訪問先リスト */
    val focusCurrentPhaseVisits: List<FocusVisit>
        get() = focusVisitsByPhase[focusPhase] ?: emptyList()

    /** 現在の訪問先 */
    val focusCurrentVisit: FocusVisit?
        get() {
            if (focusCurrentPhaseVisits.isEmpty()) return null
            val safeIndex = focusPhaseIndex.coerceAtMost(focusCurrentPhaseVisits.size - 1)
            return focusCurrentPhaseVisits.getOrNull(safeIndex)
        }

    /** 次の訪問先 */
    val focusNextVisit: FocusVisit?
        get() {
            val visits = focusCurrentPhaseVisits
            val nextIndex = focusPhaseIndex + 1
            if (nextIndex < visits.size) return visits[nextIndex]
            val byPhase = focusVisitsByPhase
            if (focusPhase == FocusPhase.NORMAL) {
                byPhase[FocusPhase.POSTPONED]?.firstOrNull()?.let { return it }
            }
            if (focusPhase == FocusPhase.NORMAL || focusPhase == FocusPhase.POSTPONED) {
                byPhase[FocusPhase.LATE]?.firstOrNull()?.let { return it }
            }
            return null
        }

    /** 現在の訪問先で表示すべきアイテム */
    val focusCurrentVisitDisplayItems: List<ShoppingItem>
        get() {
            val visit = focusCurrentVisit ?: return emptyList()
            return when (focusPhase) {
                FocusPhase.NORMAL -> visit.items
                FocusPhase.POSTPONED -> visit.items.filter { postponedPhaseItemIds.contains(it.id) }
                FocusPhase.LATE -> visit.items.filter { latePhaseItemIds.contains(it.id) }
            }
        }

    /** 価格未定かつ購入済みのアイテムがあるか */
    val focusHasUndefinedPricePurchased: Boolean
        get() = focusCurrentVisitDisplayItems.any {
            it.purchaseStatus == PurchaseStatus.PURCHASED && (it.price == null || it.price == -1)
        }

    /** 現在の訪問先チェック済み数 */
    val focusCheckedCount: Int
        get() = focusCurrentVisitDisplayItems.count { it.purchaseStatus != PurchaseStatus.NONE }

    /** 全フェーズ通算の訪問先合計数 */
    val focusTotalVisits: Int
        get() {
            val byPhase = focusVisitsByPhase
            return (byPhase[FocusPhase.NORMAL]?.size ?: 0) +
                    (byPhase[FocusPhase.POSTPONED]?.size ?: 0) +
                    (byPhase[FocusPhase.LATE]?.size ?: 0)
        }

    /** 現在の訪問先番号（全フェーズ通算） */
    val focusCurrentVisitNumber: Int
        get() {
            val byPhase = focusVisitsByPhase
            var number = focusPhaseIndex + 1
            if (focusPhase == FocusPhase.POSTPONED) number += byPhase[FocusPhase.NORMAL]?.size ?: 0
            if (focusPhase == FocusPhase.LATE) {
                number += (byPhase[FocusPhase.NORMAL]?.size ?: 0) + (byPhase[FocusPhase.POSTPONED]?.size ?: 0)
            }
            return number
        }

    /** 訪問先の総額情報 */
    data class FocusPriceInfo(val totalPrice: Int, val undefinedCount: Int, val allUndefined: Boolean)
    val focusCurrentVisitPriceInfo: FocusPriceInfo
        get() {
            var totalPrice = 0
            var undefinedCount = 0
            focusCurrentVisitDisplayItems.forEach { item ->
                if (item.price == null || item.price == -1) {
                    undefinedCount += item.quantity
                } else {
                    totalPrice += item.price * item.quantity
                }
            }
            val allUndefined = undefinedCount > 0 && totalPrice == 0 &&
                    focusCurrentVisitDisplayItems.all { it.price == null || it.price == -1 }
            return FocusPriceInfo(totalPrice, undefinedCount, allUndefined)
        }

    /** 残りの合計金額（未購入+後回し+遅参） */
    val focusRemainingCost: Int
        get() = focusExecuteItems.sumOf { item ->
            val isPurchasable = item.purchaseStatus in listOf(PurchaseStatus.NONE, PurchaseStatus.POSTPONE, PurchaseStatus.LATE)
            if (!isPurchasable) return@sumOf 0
            val price = if (item.price != null && item.price > 0) item.price else 0
            price * item.quantity
        }

    /** 購入済み件数 */
    val focusPurchasedCount: Int
        get() = focusExecuteItems.count { it.purchaseStatus == PurchaseStatus.PURCHASED }

    /** 次の訪問先情報テキスト */
    val focusNextVisitInfo: Pair<String, String>
        get() {
            val next = focusNextVisit ?: return "最終" to ""
            val item = next.items.firstOrNull() ?: return "最終" to ""
            val baseNumber = FocusVisit.extractBaseNumber(item.number)
            return "${item.block}-${baseNumber.uppercase()}" to (item.circle)
        }

    /** 現在の訪問先スペース情報 */
    val focusSpaceInfo: String
        get() {
            val item = focusCurrentVisit?.items?.firstOrNull() ?: return ""
            return "${item.block}-${FocusVisit.extractBaseNumber(item.number).uppercase()}"
        }

    /** 現在の訪問先サークル名 */
    val focusCircleName: String
        get() = focusCurrentVisit?.items?.firstOrNull()?.circle ?: ""

    /** 自動スキップ中かどうか */
    val focusIsAutoAdvancing: Boolean
        get() = !focusCompleted && focusAllVisits.isNotEmpty() &&
                focusCurrentVisitDisplayItems.isEmpty() && focusCurrentPhaseVisits.isNotEmpty()

    /** 全アイテムが後回し/遅参かどうか（自動進行チェック用） */
    val focusAllPostponedOrLate: Boolean
        get() = focusCurrentVisitDisplayItems.isNotEmpty() &&
                focusCurrentVisitDisplayItems.all {
                    it.purchaseStatus == PurchaseStatus.POSTPONE || it.purchaseStatus == PurchaseStatus.LATE
                }

    /** 次へボタンが点滅すべきか（全アイテム処理済み） */
    val focusIsNextButtonBlinking: Boolean
        get() = !focusHasUndefinedPricePurchased &&
                focusCurrentVisitDisplayItems.isNotEmpty() &&
                focusCurrentVisitDisplayItems.none { it.purchaseStatus == PurchaseStatus.NONE }

    /** フェーズ切替ダイアログ: 保存済みインデックスがあるか */
    val focusPhaseChangeHasSavedIndex: Boolean
        get() {
            val target = phaseChangeTargetPhase ?: return false
            val saved = focusSavedPhaseIndices[target] ?: 0
            val targetVisits = focusVisitsByPhase[target] ?: emptyList()
            return saved > 0 && saved < targetVisits.size
        }

    // ========== 集中モード マップ連動算出プロパティ ==========

    /** マップデータが利用可能か */
    val focusHasMapData: Boolean
        get() = focusMapDataList.isNotEmpty()

    /** 現在のマップ名 */
    val focusCurrentMapName: String?
        get() {
            val visit = focusCurrentVisit ?: return null
            val eventDate = visit.items.firstOrNull()?.eventDate ?: return null
            return "${eventDate}マップ"
        }

    /** 現在のマップデータ */
    val focusCurrentMapData: DayMapData?
        get() {
            val mapName = focusCurrentMapName ?: return null
            return focusMapDataList[mapName]
        }

    /** 現在マップの回転角度 */
    val focusMapRotationDegrees: Float
        get() {
            val mapName = focusCurrentMapName ?: return 0f
            val angle = focusMapRotationByMapName[mapName] ?: 0f
            val normalized = angle % 360f
            return if (normalized < 0f) normalized + 360f else normalized
        }

    /** 追随モード時のホール（現在訪問先のセルが属するホール） */
    val focusFollowHall: HallDefinition?
        get() {
            if (hallDefinitions.isEmpty()) return null
            val visit = focusCurrentVisit ?: return null
            val mapData = focusCurrentMapData ?: return null
            val currentItem = visit.items.firstOrNull() ?: return null

            val block = mapData.blocks.find { it.name == currentItem.block } ?: return null
            val numStr = Regex("^(\\d+)").find(currentItem.number)?.groupValues?.get(1) ?: return null
            val num = numStr.toIntOrNull() ?: return null
            val cell = block.numberCells.find { it.value == num } ?: return null

            for (hall in hallDefinitions) {
                if (hall.vertices.size >= 3 &&
                    HallUtils.isPointInPolygon(cell.row, cell.col, hall.vertices)) {
                    return hall
                }
            }
            return null
        }

    /** 実際に選択中のホール */
    val focusSelectedHall: HallDefinition?
        get() {
            if (focusSelectedHallId == "follow") return focusFollowHall
            return hallDefinitions.find { it.id == focusSelectedHallId }
        }

    /** セル状態マップ（マップ描画用） */
    val focusCellStates: Map<String, FocusCellState>
        get() {
            val mapData = focusCurrentMapData ?: return emptyMap()
            val mapName = focusCurrentMapName ?: return emptyMap()
            val dayName = mapName.removeSuffix("マップ")
            val currentVisitKey = focusCurrentVisit?.key
            val nextVisitKey = focusNextVisit?.key
            val states = mutableMapOf<String, FocusCellState>()

            // アイテムからセル状態を構築
            val dayItems = allItems.filter { it.eventDate == dayName }
            for (item in dayItems) {
                val block = mapData.blocks.find { it.name.equals(item.block, ignoreCase = true) } ?: continue
                val numStr = Regex("^(\\d+)").find(item.number)?.groupValues?.get(1) ?: continue
                val num = numStr.toIntOrNull() ?: continue
                val cell = block.numberCells.find { it.value == num } ?: continue
                val key = "${cell.row}-${cell.col}"
                val visitKey = FocusVisit.getVisitKey(item)

                val existing = states[key]
                val items = (existing?.items ?: emptyList()) + item
                val visitKeys = (existing?.visitKeys ?: emptySet()) + visitKey
                val allNone = if (item.purchaseStatus == PurchaseStatus.NONE) (existing?.allNone ?: true) else false
                val allProcessed = if (item.purchaseStatus == PurchaseStatus.NONE) false else (existing?.allProcessed ?: true)
                val hasPostponed = (existing?.hasPostponed ?: false) || item.purchaseStatus == PurchaseStatus.POSTPONE
                val hasLate = (existing?.hasLate ?: false) || item.purchaseStatus == PurchaseStatus.LATE

                states[key] = FocusCellState(
                    hasItems = true,
                    items = items,
                    visitKeys = visitKeys,
                    isCurrentPosition = false,
                    isNextDestination = false,
                    allNone = allNone,
                    allProcessed = allProcessed,
                    hasPostponed = hasPostponed,
                    hasLate = hasLate,
                    isVisited = false
                )
            }

            // 訪問済み・現在位置・次の目的地を設定
            for ((key, state) in states) {
                val hasFinalStatus = state.items.any {
                    it.purchaseStatus == PurchaseStatus.PURCHASED ||
                            it.purchaseStatus == PurchaseStatus.SOLD_OUT ||
                            it.purchaseStatus == PurchaseStatus.ABSENT
                }
                val onlyPostponedOrLate = state.items.all {
                    it.purchaseStatus == PurchaseStatus.POSTPONE || it.purchaseStatus == PurchaseStatus.LATE
                }
                val isVisited = !state.allNone && (hasFinalStatus || (!state.allNone && !onlyPostponedOrLate))
                val isCurrentPosition = currentVisitKey != null && state.visitKeys.contains(currentVisitKey)
                val isNextDestination = nextVisitKey != null && state.visitKeys.contains(nextVisitKey)

                states[key] = state.copy(
                    isVisited = isVisited,
                    isCurrentPosition = isCurrentPosition,
                    isNextDestination = isNextDestination
                )
            }
            return states
        }

    /** 現在位置のセル座標 */
    val focusCurrentCellCoords: Pair<Int, Int>?
        get() = focusCellStates.entries.firstOrNull { it.value.isCurrentPosition }
            ?.let { val (r, c) = it.key.split("-").map(String::toInt); r to c }

    /** 次の目的地のセル座標 */
    val focusNextCellCoords: Pair<Int, Int>?
        get() = focusCellStates.entries.firstOrNull { it.value.isNextDestination }
            ?.let { val (r, c) = it.key.split("-").map(String::toInt); r to c }

    /** ルートセグメント（現在位置→次の目的地） */
    val focusRouteSegments: List<RouteSegment>
        get() {
            val mapData = focusCurrentMapData ?: return emptyList()
            val current = focusCurrentCellCoords ?: return emptyList()
            val next = focusNextCellCoords ?: return emptyList()
            if (current == next) return emptyList()

            val blockNameCells = PathfindingUtils.generateBlockNameCells(mapData)

            val path = PathfindingUtils.findPath(
                mapData, current.first, current.second, next.first, next.second, blockNameCells
            )
            val simplifiedPath = PathfindingUtils.simplifyPath(path)

            if (simplifiedPath.size < 2) return emptyList()

            // 現在訪問先のアイテムからPriorityLevel取得
            val currentVisit = focusCurrentVisit
            val fromPriority = currentVisit?.items?.firstOrNull()?.let {
                focusExecuteItems.find { ei -> ei.id == it.id }?.priorityLevel
            } ?: PriorityLevel.NONE
            val nextVisit = focusNextVisit
            val toPriority = nextVisit?.items?.firstOrNull()?.let {
                focusExecuteItems.find { ei -> ei.id == it.id }?.priorityLevel
            } ?: PriorityLevel.NONE

            return listOf(
                RouteSegment(
                    fromRow = current.first,
                    fromCol = current.second,
                    toRow = next.first,
                    toCol = next.second,
                    path = simplifiedPath,
                    fromPriority = fromPriority,
                    toPriority = toPriority
                )
            )
        }

    /** ルート範囲 */
    val focusRouteBounds: FocusRouteBounds?
        get() {
            val current = focusCurrentCellCoords ?: return null
            var minRow = current.first
            var maxRow = current.first
            var minCol = current.second
            var maxCol = current.second

            val next = focusNextCellCoords
            if (next != null) {
                minRow = minOf(minRow, next.first)
                maxRow = maxOf(maxRow, next.first)
                minCol = minOf(minCol, next.second)
                maxCol = maxOf(maxCol, next.second)
            }

            val margin = 3
            return FocusRouteBounds(
                minRow = maxOf(1, minRow - margin),
                maxRow = maxRow + margin,
                minCol = maxOf(1, minCol - margin),
                maxCol = maxCol + margin
            )
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

    // 集中モード: 自動進行タイマー / 通知タイマー
    private var autoAdvanceJob: Job? = null
    private var notificationJob: Job? = null

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
        val newMode = when (currentMode) {
            "edit" -> "execute"
            else -> "edit"  // "execute" and "focus" → "edit"
        }
        if (currentMode == "focus") {
            cancelAutoAdvance()
            cancelNotification()
        }
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
        if ((currentMode == "execute" || currentMode == "focus") && (purchaseStatusChanged || priceChanged)) {
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

    // ========== 集中モード ==========

    /**
     * 集中モードに入る
     */
    fun enterFocusMode() {
        val state = _uiState.value
        val currentDate = state.selectedDate ?: return
        val executeIds = state.currentExecuteIds
        if (executeIds.isEmpty()) return

        cancelAutoAdvance()
        cancelNotification()

        _uiState.update {
            it.copy(
                focusPhase = FocusPhase.NORMAL,
                focusPhaseIndex = 0,
                focusCompleted = false,
                focusLastInteractedItemId = null,
                postponedPhaseItemIds = emptySet(),
                latePhaseItemIds = emptySet(),
                focusSavedPhaseIndices = mapOf(
                    FocusPhase.NORMAL to 0, FocusPhase.POSTPONED to 0, FocusPhase.LATE to 0
                ),
                focusNotification = null,
                autoAdvanceCountdown = null,
                blinkingPriceItemIds = emptySet(),
                showPhaseChangeDialog = false,
                phaseChangeTargetPhase = null,
                isRangeSelectionMode = false,
                rangeSelectionStartId = null,
                rangeSelectionEndId = null,
                groupedItemIds = emptyList(),
                // マップ連動状態リセット
                focusMapVisible = false,
                focusMapZoomLevel = 100,
                focusSelectedHallId = "follow",
                focusSplitRatio = 0.5f,
                focusCellPopup = null,
                focusAddItemDialog = null
            )
        }
        setDayMode(currentDate, "focus")
        loadFocusMapData()
    }

    /**
     * 集中モードを抜ける
     */
    fun exitFocusMode(targetMode: String) {
        val state = _uiState.value
        val currentDate = state.selectedDate ?: return
        cancelAutoAdvance()
        cancelNotification()
        setDayMode(currentDate, targetMode)
    }

    /**
     * 集中モード: レイアウトモード切替
     */
    fun toggleFocusLayoutMode() {
        _uiState.update {
            val newMode = if (it.focusLayoutMode == FocusLayoutMode.SMARTPHONE)
                FocusLayoutMode.TABLET else FocusLayoutMode.SMARTPHONE
            it.copy(focusLayoutMode = newMode)
        }
    }

    /**
     * 集中モード: 次の訪問先へ
     */
    fun focusNext() {
        cancelAutoAdvance()
        val state = _uiState.value

        // 価格未定チェック
        if (state.focusHasUndefinedPricePurchased) {
            showFocusNotification("価格未定のアイテムがあります。価格を入力してください。")
            val undefinedPriceIds = state.focusCurrentVisitDisplayItems
                .filter { it.purchaseStatus == PurchaseStatus.PURCHASED && (it.price == null || it.price == -1) }
                .map { it.id }.toSet()
            _uiState.update { it.copy(blinkingPriceItemIds = undefinedPriceIds) }
            return
        }

        val hasUncheckedItems = state.focusCurrentVisitDisplayItems.any {
            it.purchaseStatus == PurchaseStatus.NONE
        }

        // インデックスを保存
        _uiState.update {
            it.copy(focusSavedPhaseIndices = it.focusSavedPhaseIndices + (it.focusPhase to it.focusPhaseIndex))
        }

        val nextIndex = state.focusPhaseIndex + 1
        if (nextIndex < state.focusCurrentPhaseVisits.size) {
            _uiState.update {
                it.copy(focusPhaseIndex = nextIndex, blinkingPriceItemIds = emptySet())
            }
        } else {
            advanceToNextPhase()
        }

        if (hasUncheckedItems) {
            showFocusNotification("前のサークルでチェック漏れがあります")
        }
    }

    /**
     * 集中モード: 前の訪問先へ
     */
    fun focusPrev() {
        cancelAutoAdvance()
        val state = _uiState.value

        if (state.focusCompleted) {
            _uiState.update { it.copy(focusCompleted = false) }
            val byPhase = state.focusVisitsByPhase
            when {
                (byPhase[FocusPhase.LATE]?.size ?: 0) > 0 -> _uiState.update {
                    it.copy(focusPhase = FocusPhase.LATE, focusPhaseIndex = (byPhase[FocusPhase.LATE]?.size ?: 1) - 1)
                }
                (byPhase[FocusPhase.POSTPONED]?.size ?: 0) > 0 -> _uiState.update {
                    it.copy(focusPhase = FocusPhase.POSTPONED, focusPhaseIndex = (byPhase[FocusPhase.POSTPONED]?.size ?: 1) - 1)
                }
                (byPhase[FocusPhase.NORMAL]?.size ?: 0) > 0 -> _uiState.update {
                    it.copy(focusPhase = FocusPhase.NORMAL, focusPhaseIndex = (byPhase[FocusPhase.NORMAL]?.size ?: 1) - 1)
                }
            }
            return
        }

        if (state.focusPhaseIndex > 0) {
            _uiState.update { it.copy(focusPhaseIndex = it.focusPhaseIndex - 1, blinkingPriceItemIds = emptySet()) }
        } else {
            val byPhase = state.focusVisitsByPhase
            when (state.focusPhase) {
                FocusPhase.POSTPONED -> {
                    if ((byPhase[FocusPhase.NORMAL]?.size ?: 0) > 0) {
                        _uiState.update {
                            it.copy(focusPhase = FocusPhase.NORMAL,
                                focusPhaseIndex = (byPhase[FocusPhase.NORMAL]?.size ?: 1) - 1,
                                blinkingPriceItemIds = emptySet())
                        }
                    } else showFocusNotification("最初の訪問サークル・スペースです")
                }
                FocusPhase.LATE -> {
                    if (state.postponedPhaseItemIds.isNotEmpty()) {
                        _uiState.update {
                            it.copy(focusPhase = FocusPhase.POSTPONED,
                                focusPhaseIndex = (byPhase[FocusPhase.POSTPONED]?.size ?: 1) - 1,
                                blinkingPriceItemIds = emptySet())
                        }
                    } else if ((byPhase[FocusPhase.NORMAL]?.size ?: 0) > 0) {
                        _uiState.update {
                            it.copy(focusPhase = FocusPhase.NORMAL,
                                focusPhaseIndex = (byPhase[FocusPhase.NORMAL]?.size ?: 1) - 1,
                                blinkingPriceItemIds = emptySet())
                        }
                    } else showFocusNotification("最初の訪問サークル・スペースです")
                }
                FocusPhase.NORMAL -> showFocusNotification("最初の訪問サークル・スペースです")
            }
        }
    }

    /**
     * 集中モード: 次のフェーズへ自動遷移
     */
    private fun advanceToNextPhase() {
        val state = _uiState.value
        val executeItems = state.focusExecuteItems

        when (state.focusPhase) {
            FocusPhase.NORMAL -> {
                val postponedIds = executeItems.filter { it.purchaseStatus == PurchaseStatus.POSTPONE }.map { it.id }.toSet()
                val lateIds = executeItems.filter { it.purchaseStatus == PurchaseStatus.LATE }.map { it.id }.toSet()
                _uiState.update { it.copy(postponedPhaseItemIds = postponedIds, latePhaseItemIds = lateIds) }

                when {
                    postponedIds.isNotEmpty() -> {
                        showFocusNotification("後回しアイテムの巡回を開始します")
                        _uiState.update { it.copy(focusPhase = FocusPhase.POSTPONED, focusPhaseIndex = 0, blinkingPriceItemIds = emptySet()) }
                    }
                    lateIds.isNotEmpty() -> {
                        showFocusNotification("遅参アイテムの巡回を開始します")
                        _uiState.update { it.copy(focusPhase = FocusPhase.LATE, focusPhaseIndex = 0, blinkingPriceItemIds = emptySet()) }
                    }
                    else -> _uiState.update { it.copy(focusCompleted = true) }
                }
            }
            FocusPhase.POSTPONED -> {
                val currentLateIds = state.latePhaseItemIds.toMutableSet()
                executeItems.forEach { if (it.purchaseStatus == PurchaseStatus.LATE) currentLateIds.add(it.id) }
                _uiState.update { it.copy(latePhaseItemIds = currentLateIds) }
                if (currentLateIds.isNotEmpty()) {
                    showFocusNotification("遅参アイテムの巡回を開始します")
                    _uiState.update { it.copy(focusPhase = FocusPhase.LATE, focusPhaseIndex = 0, blinkingPriceItemIds = emptySet()) }
                } else _uiState.update { it.copy(focusCompleted = true) }
            }
            FocusPhase.LATE -> _uiState.update { it.copy(focusCompleted = true) }
        }
    }

    /**
     * 集中モード: アイテムのステータス更新
     */
    fun focusUpdateItemStatus(item: ShoppingItem, status: PurchaseStatus) {
        viewModelScope.launch {
            val updatedItem = applyProtectionLevelAutoChange(item, purchaseStatusChanged = true)
                .copy(purchaseStatus = status)
            itemRepository.updateItem(updatedItem)
            _uiState.update { it.copy(focusLastInteractedItemId = item.id) }

            if (status != PurchaseStatus.POSTPONE && status != PurchaseStatus.LATE) {
                cancelAutoAdvance()
                return@launch
            }
            if (_uiState.value.focusPhase != FocusPhase.NORMAL) return@launch

            delay(100)
            if (_uiState.value.focusAllPostponedOrLate) startAutoAdvance()
        }
    }

    /**
     * 集中モード: アイテムの価格更新
     */
    fun focusUpdateItemPrice(itemId: String, price: Int?) {
        viewModelScope.launch {
            val item = itemRepository.getItemById(itemId)
            item?.let {
                val priceChanged = it.price != price
                val updatedItem = applyProtectionLevelAutoChange(it, priceChanged = priceChanged).copy(price = price)
                itemRepository.updateItem(updatedItem)
                _uiState.update { s -> s.copy(focusLastInteractedItemId = itemId) }
            }
        }
    }

    /**
     * 集中モード: フェーズ切替ダイアログを開く
     */
    fun focusRequestPhaseChange(targetPhase: FocusPhase) {
        val state = _uiState.value
        if (targetPhase == state.focusPhase) return
        val targetVisits = state.focusVisitsByPhase[targetPhase] ?: emptyList()
        if (targetVisits.isEmpty()) {
            showFocusNotification("${targetPhase.displayName}フェーズに該当するアイテムがありません")
            return
        }
        _uiState.update { it.copy(showPhaseChangeDialog = true, phaseChangeTargetPhase = targetPhase) }
    }

    /**
     * 集中モード: フェーズ切替実行
     */
    fun focusExecutePhaseChange(fromStart: Boolean) {
        val state = _uiState.value
        val targetPhase = state.phaseChangeTargetPhase ?: return
        val executeItems = state.focusExecuteItems

        cancelAutoAdvance()
        _uiState.update {
            it.copy(focusSavedPhaseIndices = it.focusSavedPhaseIndices + (it.focusPhase to it.focusPhaseIndex))
        }

        if (state.focusPhase == FocusPhase.NORMAL && (targetPhase == FocusPhase.POSTPONED || targetPhase == FocusPhase.LATE)) {
            val postponedIds = executeItems.filter { it.purchaseStatus == PurchaseStatus.POSTPONE }.map { it.id }.toSet()
            val lateIds = executeItems.filter { it.purchaseStatus == PurchaseStatus.LATE }.map { it.id }.toSet()
            _uiState.update { it.copy(postponedPhaseItemIds = postponedIds, latePhaseItemIds = lateIds) }
        } else if (state.focusPhase == FocusPhase.POSTPONED && targetPhase == FocusPhase.LATE) {
            val currentLateIds = state.latePhaseItemIds.toMutableSet()
            executeItems.forEach { if (it.purchaseStatus == PurchaseStatus.LATE) currentLateIds.add(it.id) }
            _uiState.update { it.copy(latePhaseItemIds = currentLateIds) }
        }

        val newIndex = if (fromStart) 0 else (state.focusSavedPhaseIndices[targetPhase] ?: 0)
        val message = if (fromStart) "${targetPhase.displayName}フェーズを最初から開始します"
        else "${targetPhase.displayName}フェーズを途中から再開します"

        _uiState.update {
            it.copy(focusPhase = targetPhase, focusPhaseIndex = newIndex, focusCompleted = false,
                blinkingPriceItemIds = emptySet(), showPhaseChangeDialog = false, phaseChangeTargetPhase = null)
        }
        showFocusNotification(message)
    }

    fun focusCancelPhaseChange() {
        _uiState.update { it.copy(showPhaseChangeDialog = false, phaseChangeTargetPhase = null) }
    }

    private fun startAutoAdvance() {
        cancelAutoAdvance()
        autoAdvanceJob = viewModelScope.launch {
            _uiState.update { it.copy(autoAdvanceCountdown = 3) }
            delay(1000)
            _uiState.update { it.copy(autoAdvanceCountdown = 2) }
            delay(1000)
            _uiState.update { it.copy(autoAdvanceCountdown = 1) }
            delay(1000)
            _uiState.update { it.copy(autoAdvanceCountdown = null) }
            focusNext()
        }
    }

    fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
        _uiState.update { it.copy(autoAdvanceCountdown = null) }
    }

    private fun showFocusNotification(message: String) {
        notificationJob?.cancel()
        _uiState.update { it.copy(focusNotification = message) }
        notificationJob = viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(focusNotification = null) }
        }
    }

    private fun cancelNotification() {
        notificationJob?.cancel()
        notificationJob = null
        _uiState.update { it.copy(focusNotification = null) }
    }

    fun focusAutoSkip() {
        val state = _uiState.value
        if (state.focusCompleted || state.focusAllVisits.isEmpty()) return
        if (state.focusCurrentVisitDisplayItems.isNotEmpty()) return
        if (state.focusCurrentPhaseVisits.isEmpty()) return

        for (i in state.focusPhaseIndex + 1 until state.focusCurrentPhaseVisits.size) {
            val visit = state.focusCurrentPhaseVisits[i]
            val hasItems = when (state.focusPhase) {
                FocusPhase.NORMAL -> visit.items.isNotEmpty()
                FocusPhase.POSTPONED -> visit.items.any { state.postponedPhaseItemIds.contains(it.id) }
                FocusPhase.LATE -> visit.items.any { state.latePhaseItemIds.contains(it.id) }
            }
            if (hasItems) {
                _uiState.update { it.copy(focusPhaseIndex = i) }
                return
            }
        }
        advanceToNextPhase()
    }

    // ========== 集中モード: マップ連動 ==========

    /**
     * マップデータを読み込む
     */
    private fun loadFocusMapData() {
        viewModelScope.launch {
            try {
                val mapDataMap = mapDataRepository.getMapDataByEventIdOnce(eventId)
                _uiState.update { it.copy(focusMapDataList = mapDataMap) }
                Log.d(TAG, "loadFocusMapData: ${mapDataMap.size} maps loaded")
            } catch (e: Exception) {
                Log.e(TAG, "loadFocusMapData: error", e)
            }
        }
    }

    /**
     * マップ表示ON/OFF切替
     */
    fun toggleFocusMapVisible() {
        _uiState.update { it.copy(focusMapVisible = !it.focusMapVisible) }
    }

    /**
     * マップズームレベル設定
     */
    fun setFocusMapZoomLevel(level: Int) {
        val clamped = level.coerceIn(30, 100)
        _uiState.update { it.copy(focusMapZoomLevel = clamped) }
    }

    /**
     * 現在マップの回転角度を設定
     */
    fun setFocusMapRotation(angle: Float) {
        val state = _uiState.value
        val mapName = state.focusCurrentMapName ?: return
        val normalized = normalizeRotation(angle)
        _uiState.update {
            it.copy(
                focusMapRotationByMapName = it.focusMapRotationByMapName.toMutableMap().apply {
                    this[mapName] = normalized
                }
            )
        }
    }

    /**
     * 現在マップを相対回転
     */
    fun rotateFocusMapBy(deltaDegrees: Float) {
        val current = _uiState.value.focusMapRotationDegrees
        setFocusMapRotation(current + deltaDegrees)
    }

    /**
     * 現在マップ回転をリセット
     */
    fun resetFocusMapRotation() {
        setFocusMapRotation(0f)
    }

    /**
     * ホール選択変更
     */
    fun setFocusSelectedHallId(id: String) {
        _uiState.update { it.copy(focusSelectedHallId = id) }
    }

    /**
     * SP+マップ時の分割比率変更
     */
    fun setFocusSplitRatio(ratio: Float) {
        _uiState.update { it.copy(focusSplitRatio = ratio.coerceIn(0.2f, 0.8f)) }
    }

    private fun normalizeRotation(degrees: Float): Float {
        val normalized = degrees % 360f
        return if (normalized < 0f) normalized + 360f else normalized
    }

    /**
     * セルタップポップアップ表示
     */
    fun openFocusCellPopup(blockName: String, number: Int, items: List<ShoppingItem>) {
        _uiState.update { it.copy(focusCellPopup = FocusCellPopup(blockName, number, items)) }
    }

    /**
     * セルタップポップアップ閉じる
     */
    fun closeFocusCellPopup() {
        _uiState.update { it.copy(focusCellPopup = null) }
    }

    /**
     * 集中モード: 新規アイテム追加ダイアログを開く
     * セルポップアップのブロック/ナンバー情報を引き継ぐ
     */
    fun openFocusAddItemDialog() {
        val state = _uiState.value
        val popup = state.focusCellPopup ?: return
        val visit = state.focusCurrentVisit ?: return
        val eventDate = visit.items.firstOrNull()?.eventDate ?: return

        _uiState.update {
            it.copy(
                focusCellPopup = null,  // ポップアップを閉じる
                focusAddItemDialog = FocusAddItemDialog(
                    eventDate = eventDate,
                    block = popup.blockName,
                    number = popup.number.toString()
                )
            )
        }
    }

    /**
     * 集中モード: 新規アイテム追加ダイアログを閉じる
     */
    fun closeFocusAddItemDialog() {
        _uiState.update { it.copy(focusAddItemDialog = null) }
    }

    /**
     * 集中モード: 新規アイテム追加を実行
     * - Purchased → 候補リストのみに追加（実行列には追加しない）
     * - Postpone/Late → 実行列の同フェーズアイテム間で最短経路位置に挿入
     */
    fun submitFocusAddItem(
        circle: String,
        title: String,
        price: Int?,
        quantity: Int,
        purchaseStatus: PurchaseStatus,
        remarks: String,
        url: String
    ) {
        val state = _uiState.value
        val dialog = state.focusAddItemDialog ?: return

        viewModelScope.launch {
            val maxSortOrder = state.allItems.maxOfOrNull { it.sortOrder } ?: -1
            val newItem = ShoppingItem(
                id = java.util.UUID.randomUUID().toString(),
                eventId = eventId,
                circle = circle,
                eventDate = dialog.eventDate,
                block = dialog.block,
                number = dialog.number,
                title = title,
                price = price,
                purchaseStatus = purchaseStatus,
                quantity = quantity,
                remarks = remarks,
                url = url.ifBlank { null },
                sortOrder = maxSortOrder + 1,
                isInExecuteList = false,
                source = ItemSource.APP,
                protectionLevel = ProtectionLevel.FULL
            )

            // DBに保存
            itemRepository.insertItem(newItem)

            // 購入済は候補リストのみ（実行列には追加しない）
            if (purchaseStatus == PurchaseStatus.PURCHASED) {
                _uiState.update { it.copy(focusAddItemDialog = null) }
                return@launch
            }

            // 後回し・遅参: 実行列の最短経路位置に挿入
            val dayName = dialog.eventDate
            val currentIds = (state.executeListItemIds[dayName] ?: emptyList()).toMutableList()
            val allItemsMap = state.allItems.associateBy { it.id }.toMutableMap()
            allItemsMap[newItem.id] = newItem

            val mapData = state.focusCurrentMapData

            // アイテムのセル座標を取得するヘルパー
            fun getItemPosition(itemId: String): Pair<Int, Int>? {
                val item = allItemsMap[itemId] ?: return null
                if (mapData == null) return null
                val blockName = item.block.trim()
                val block = mapData.blocks.find { it.name == blockName }
                    ?: mapData.blocks.find { it.name.equals(blockName, ignoreCase = true) }
                    ?: return null
                val normalizedNumber = item.number.lowercase()
                for (nc in block.numberCells) {
                    if (nc.value.toString().lowercase() == normalizedNumber) {
                        return nc.row to nc.col
                    }
                }
                return (block.startRow + block.endRow) / 2 to (block.startCol + block.endCol) / 2
            }

            fun calcDistance(p1: Pair<Int, Int>, p2: Pair<Int, Int>): Int {
                return kotlin.math.abs(p1.first - p2.first) + kotlin.math.abs(p1.second - p2.second)
            }

            // 同じフェーズのアイテムのインデックスを収集
            val samePhaseIndices = mutableListOf<Int>()
            for (i in currentIds.indices) {
                val existingItem = allItemsMap[currentIds[i]]
                if (existingItem != null && existingItem.purchaseStatus == purchaseStatus) {
                    samePhaseIndices.add(i)
                }
            }

            val newItemPos = getItemPosition(newItem.id)

            if (samePhaseIndices.isEmpty() || newItemPos == null) {
                // 同フェーズアイテムなし → 末尾に追加
                currentIds.add(newItem.id)
            } else {
                // 同フェーズアイテム間で最短経路になる挿入位置を探す
                var bestInsertIndex = samePhaseIndices.last() + 1
                var minTotalDistance = Int.MAX_VALUE

                for (insertIdx in 0..samePhaseIndices.size) {
                    var totalDistance = 0

                    // 挿入位置の前のアイテムとの距離
                    if (insertIdx > 0) {
                        val prevId = currentIds[samePhaseIndices[insertIdx - 1]]
                        val prevPos = getItemPosition(prevId)
                        if (prevPos != null) {
                            totalDistance += calcDistance(prevPos, newItemPos)
                        }
                    }

                    // 挿入位置の後のアイテムとの距離
                    if (insertIdx < samePhaseIndices.size) {
                        val nextId = currentIds[samePhaseIndices[insertIdx]]
                        val nextPos = getItemPosition(nextId)
                        if (nextPos != null) {
                            totalDistance += calcDistance(newItemPos, nextPos)
                        }

                        // 元の前後距離を引く
                        if (insertIdx > 0) {
                            val prevId = currentIds[samePhaseIndices[insertIdx - 1]]
                            val prevPos = getItemPosition(prevId)
                            if (prevPos != null && nextPos != null) {
                                totalDistance -= calcDistance(prevPos, nextPos)
                            }
                        }
                    }

                    if (totalDistance < minTotalDistance) {
                        minTotalDistance = totalDistance
                        bestInsertIndex = when {
                            insertIdx == 0 -> samePhaseIndices[0]
                            insertIdx == samePhaseIndices.size -> samePhaseIndices.last() + 1
                            else -> samePhaseIndices[insertIdx]
                        }
                    }
                }

                currentIds.add(bestInsertIndex, newItem.id)
            }

            saveExecuteList(dayName, currentIds)
            syncExecuteToVisitList(dayName, currentIds)
            _uiState.update { it.copy(focusAddItemDialog = null) }
        }
    }

    companion object {
        private const val TAG = "ShoppingListViewModel"
    }
}
