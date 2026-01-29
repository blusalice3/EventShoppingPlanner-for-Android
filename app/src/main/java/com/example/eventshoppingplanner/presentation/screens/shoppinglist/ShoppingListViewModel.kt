package com.example.eventshoppingplanner.presentation.screens.shoppinglist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventshoppingplanner.domain.model.Event
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingListUiState(
    val event: Event? = null,
    val items: List<ShoppingItem> = emptyList(),
    val allItems: List<ShoppingItem> = emptyList(),
    val eventDates: List<String> = emptyList(),
    val selectedDate: String? = null,
    val blocks: List<String> = emptyList(),
    val selectedBlock: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val purchasedCount: Int = 0,
    val totalCount: Int = 0,
    val remainingAmount: Int = 0,
    val showStatusDialog: Boolean = false,
    val selectedItem: ShoppingItem? = null,
    val showAddItemDialog: Boolean = false,
    val showEditItemDialog: Boolean = false,
    val editingItem: ShoppingItem? = null,
    val duplicateSpaceItemIds: Set<String> = emptySet()
)

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
    private val itemRepository: ShoppingItemRepository
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    // ドラッグ終了後の保存用
    private var saveOrderJob: Job? = null

    init {
        loadEvent()
        loadData()
    }

    private fun loadEvent() {
        viewModelScope.launch {
            val event = eventRepository.getEventById(eventId)
            _uiState.update { it.copy(event = event) }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            itemRepository.getItemsByEventId(eventId).collect { items ->
                // 参加日リストを取得
                val dates = items.map { it.eventDate }.distinct().sorted()

                // ブロックリストを取得
                val blocks = items.map { it.block }.distinct().sorted()

                // 現在選択中の日付（未設定なら最初の日付）
                val currentState = _uiState.value
                val effectiveDate = if (currentState.selectedDate != null && dates.contains(currentState.selectedDate)) {
                    currentState.selectedDate
                } else {
                    dates.firstOrNull()
                }

                // フィルタリング
                val filtered = filterItemsInternal(items, effectiveDate, currentState.selectedBlock, currentState.searchQuery)

                // 複数種アイテムIDを計算（同じスペースに複数アイテムがあるもの）
                val duplicateIds = calculateDuplicateSpaceItemIds(items, effectiveDate)

                // 統計計算
                val purchased = items.count { it.purchaseStatus == PurchaseStatus.PURCHASED }
                val remaining = items
                    .filter { it.purchaseStatus !in listOf(PurchaseStatus.PURCHASED, PurchaseStatus.SOLD_OUT, PurchaseStatus.ABSENT) }
                    .sumOf { (it.price ?: 0) * it.quantity }

                _uiState.update {
                    it.copy(
                        items = filtered,
                        allItems = items,
                        eventDates = dates,
                        selectedDate = effectiveDate,
                        blocks = blocks,
                        isLoading = false,
                        purchasedCount = purchased,
                        totalCount = items.size,
                        remainingAmount = remaining,
                        duplicateSpaceItemIds = duplicateIds
                    )
                }
            }
        }
    }

    /**
     * ナンバーから数値部分を抽出する
     * 例: "01a" → "01", "12b" → "12", "123" → "123", "a01" → ""
     */
    private fun extractNumberPart(number: String): String {
        // アルファベットが最初に出現する位置を探す
        val firstAlphaIndex = number.indexOfFirst { it.isLetter() }
        return if (firstAlphaIndex == -1) {
            // アルファベットがない場合は全体を返す
            number.trim()
        } else if (firstAlphaIndex == 0) {
            // 最初がアルファベットの場合は空文字
            ""
        } else {
            // アルファベットより前の部分を返す
            number.substring(0, firstAlphaIndex).trim()
        }
    }

    /**
     * スペースキーを生成（参加日 + ブロック + ナンバーの数値部分）
     */
    private fun getSpaceKey(item: ShoppingItem): String {
        val numberPart = extractNumberPart(item.number)
        return "${item.eventDate}|${item.block.trim()}|$numberPart"
    }

    /**
     * 同じスペース（参加日・ブロック・ナンバー数値部分が一致）に
     * 複数アイテムがある場合のアイテムIDセットを計算
     */
    private fun calculateDuplicateSpaceItemIds(items: List<ShoppingItem>, selectedDate: String?): Set<String> {
        if (selectedDate == null) return emptySet()

        // 選択された日付のアイテムのみを対象
        val itemsForDate = items.filter { it.eventDate == selectedDate }

        // スペースキーごとにアイテムをグループ化
        val spaceItemsMap = mutableMapOf<String, MutableList<String>>()
        itemsForDate.forEach { item ->
            val spaceKey = getSpaceKey(item)
            if (spaceKey.isNotEmpty()) {
                spaceItemsMap.getOrPut(spaceKey) { mutableListOf() }.add(item.id)
            }
        }

        // 2つ以上あるスペースのアイテムIDを収集
        val duplicateIds = mutableSetOf<String>()
        spaceItemsMap.forEach { (_, itemIds) ->
            if (itemIds.size > 1) {
                duplicateIds.addAll(itemIds)
            }
        }

        return duplicateIds
    }

    private fun filterItemsInternal(
        items: List<ShoppingItem>,
        selectedDate: String?,
        selectedBlock: String?,
        searchQuery: String
    ): List<ShoppingItem> {
        return items.filter { item ->
            val dateMatch = selectedDate == null || item.eventDate == selectedDate
            val blockMatch = selectedBlock == null || item.block == selectedBlock
            val searchMatch = searchQuery.isEmpty() ||
                    item.circle.contains(searchQuery, ignoreCase = true) ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.remarks.contains(searchQuery, ignoreCase = true)
            dateMatch && blockMatch && searchMatch
        }.sortedBy { it.sortOrder }
    }

    fun selectDate(date: String?) {
        _uiState.update { it.copy(selectedDate = date) }
        refreshFilteredItems()
        // 日付変更時に複数種判定も更新
        updateDuplicateSpaceItemIds()
    }

    fun selectBlock(block: String?) {
        _uiState.update { it.copy(selectedBlock = block) }
        refreshFilteredItems()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshFilteredItems()
    }

    private fun refreshFilteredItems() {
        val state = _uiState.value
        val filtered = filterItemsInternal(state.allItems, state.selectedDate, state.selectedBlock, state.searchQuery)
        _uiState.update { it.copy(items = filtered) }
    }

    private fun updateDuplicateSpaceItemIds() {
        val state = _uiState.value
        val duplicateIds = calculateDuplicateSpaceItemIds(state.allItems, state.selectedDate)
        _uiState.update { it.copy(duplicateSpaceItemIds = duplicateIds) }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun showStatusDialog(item: ShoppingItem) {
        _uiState.update { it.copy(showStatusDialog = true, selectedItem = item) }
    }

    fun hideStatusDialog() {
        _uiState.update { it.copy(showStatusDialog = false, selectedItem = null) }
    }

    fun updateItemStatus(status: PurchaseStatus) {
        viewModelScope.launch {
            _uiState.value.selectedItem?.let { item ->
                itemRepository.updatePurchaseStatus(item.id, status)
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
            itemRepository.updatePurchaseStatus(item.id, nextStatus)
        }
    }

    // アイテム追加ダイアログ
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

    // アイテム編集ダイアログ
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
            hideEditItemDialog()
        }
    }

    // ドラッグ&ドロップで並び替え
    fun moveItem(fromIndex: Int, toIndex: Int) {
        val currentItems = _uiState.value.items.toMutableList()
        if (fromIndex < 0 || fromIndex >= currentItems.size || toIndex < 0 || toIndex >= currentItems.size) {
            return
        }

        val item = currentItems.removeAt(fromIndex)
        currentItems.add(toIndex, item)

        // UI更新
        _uiState.update { it.copy(items = currentItems) }

        // 並び順をDBに保存（debounce: 300ms後に保存）
        saveOrderJob?.cancel()
        saveOrderJob = viewModelScope.launch {
            delay(300)
            saveSortOrder(currentItems)
        }
    }

    private suspend fun saveSortOrder(items: List<ShoppingItem>) {
        val state = _uiState.value
        val allItems = state.allItems.toMutableList()

        val newOrderMap = items.mapIndexed { index, item -> item.id to index }.toMap()

        allItems.forEach { item ->
            newOrderMap[item.id]?.let { newOrder ->
                val baseOrder = allItems
                    .filter { it.eventDate == item.eventDate && it.id != item.id }
                    .count { newOrderMap[it.id]?.let { o -> o < newOrder } ?: (it.sortOrder < newOrder) }

                itemRepository.updateSortOrder(item.id, newOrder)
            }
        }
    }

    // エクスポート用
    suspend fun getAllItemsForExport(): List<ShoppingItem> {
        return itemRepository.getItemsByEventId(eventId).first()
    }

    // 価格を直接更新
    fun updateItemPrice(itemId: String, price: Int?) {
        viewModelScope.launch {
            val item = itemRepository.getItemById(itemId)
            item?.let {
                itemRepository.updateItem(it.copy(price = price))
            }
        }
    }
}