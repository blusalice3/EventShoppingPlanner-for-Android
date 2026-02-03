package com.example.eventshoppingplanner.presentation.screens.shoppinglist

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventshoppingplanner.data.preferences.AppPreference
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.presentation.components.ShoppingItemCard
import com.example.eventshoppingplanner.util.CsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    viewModel: ShoppingListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    val isEditMode = uiState.currentMode == "edit"
    val cardScale = uiState.cardScale

    // ピンチズーム用の一時スケール
    var tempScale by remember { mutableFloatStateOf(cardScale) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.event?.name ?: "読み込み中...") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearGroup()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    // 範囲選択モードボタン（editモードのみ）
                    if (isEditMode) {
                        IconButton(onClick = { viewModel.toggleRangeSelectionMode() }) {
                            Icon(
                                imageVector = if (uiState.isRangeSelectionMode)
                                    Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (uiState.isRangeSelectionMode) "範囲選択解除" else "範囲選択",
                                tint = if (uiState.isRangeSelectionMode)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // マップボタン
                    IconButton(onClick = {
                        viewModel.clearGroup()
                        onNavigateToMap()
                    }) {
                        Icon(Icons.Default.Map, contentDescription = "マップ")
                    }

                    // executeモード時：ステータストグルフィルタ
                    if (!isEditMode) {
                        StatusFilterButton(
                            currentFilter = uiState.executeStatusFilter,
                            onClick = { viewModel.cycleExecuteStatusFilter() }
                        )
                    }

                    // モード切替ボタン
                    IconButton(onClick = { viewModel.toggleCurrentDayMode() }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.PlayArrow else Icons.Default.Edit,
                            contentDescription = if (isEditMode) "実行モード" else "編集モード"
                        )
                    }

                    // メニュー
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("CSVエクスポート") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        exportToCsv(
                                            context = context,
                                            viewModel = viewModel,
                                            eventName = uiState.event?.name ?: "export"
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            SummaryBar(
                purchasedCount = uiState.purchasedCount,
                totalCount = uiState.totalCount,
                remainingAmount = uiState.remainingAmount
            )
        },
        floatingActionButton = {
            if (isEditMode) {
                FloatingActionButton(
                    onClick = { viewModel.showAddItemDialog() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "アイテム追加")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 参加日タブ（件数バッジ+長押しモード切替対応）
            if (uiState.eventDates.isNotEmpty()) {
                DateTabsWithBadge(
                    dates = uiState.eventDates,
                    selectedDate = uiState.selectedDate,
                    itemCountByDate = uiState.itemCountByDate,
                    dayModes = uiState.dayModes,
                    onDateSelected = { viewModel.selectDate(it) },
                    onDateLongPress = { viewModel.toggleDayMode(it) }
                )
            }

            // editモード：検索バー+ブロックフィルタ
            if (isEditMode) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) }
                )

                if (uiState.candidateBlocks.isNotEmpty()) {
                    BlockFilter(
                        blocks = uiState.candidateBlocks,
                        selectedBlock = uiState.selectedBlock,
                        onBlockSelected = { viewModel.selectBlock(it) },
                        hallBlocks = uiState.hallBlocksMap,
                        onHallBulkAdd = { hallName -> viewModel.bulkAddByHall(hallName) }
                    )
                }
            }

            // メインコンテンツ（ピンチジェスチャー対応）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val newScale = (tempScale * zoom).coerceIn(
                                AppPreference.MIN_CARD_SCALE,
                                AppPreference.MAX_CARD_SCALE
                            )
                            tempScale = newScale
                            viewModel.setCardScale(newScale)
                        }
                    }
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.currentTabItems.isEmpty() -> {
                        EmptyStateView(
                            isEditMode = isEditMode,
                            onAddItem = { viewModel.showAddItemDialog() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    isEditMode -> {
                        // 2列レイアウト（実行列+候補リスト）
                        Box(modifier = Modifier.fillMaxSize()) {
                            TwoColumnLayout(
                                executeItems = uiState.executeColumnItems,
                                candidateItems = uiState.candidateColumnItems,
                                duplicateSpaceItemIds = uiState.duplicateSpaceItemIds,
                                cardScale = cardScale,
                                groupLabels = uiState.executeGroupLabels,
                                isRangeSelectionMode = uiState.isRangeSelectionMode,
                                rangeSelectedItemIds = uiState.rangeSelectedItemIds,
                                groupedItemIdSet = uiState.groupedItemIdSet,
                                rangeStartId = uiState.rangeSelectionStartId,
                                rangeEndId = uiState.rangeSelectionEndId,
                                onExecuteItemTap = { item ->
                                    if (uiState.isRangeSelectionMode && uiState.groupedItemIds.isEmpty()) {
                                        val error = viewModel.onRangeSelectionTap(item)
                                        if (error != null) {
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        viewModel.toggleExecuteList(item)
                                    }
                                },
                                onCandidateItemTap = { viewModel.toggleExecuteList(it) },
                                onItemLongPress = { viewModel.showEditItemDialog(it) },
                                onStatusClick = { viewModel.cycleItemStatus(it) },
                                onPriceChange = { id, price -> viewModel.updateItemPrice(id, price) },
                                onMoveExecuteItem = { from, to -> viewModel.moveExecuteItem(from, to) },
                                onDeleteItem = { viewModel.deleteItem(it) }
                            )

                            // 範囲選択アクションバー
                            if (uiState.isRangeSelectionMode && uiState.rangeSelectedItemIds.size >= 2) {
                                RangeSelectionActionBar(
                                    selectedCount = uiState.rangeSelectedItemIds.size,
                                    hasGroup = uiState.groupedItemIds.isNotEmpty(),
                                    onMoveToCandidate = { viewModel.bulkMoveToCandidate() },
                                    onGroup = { viewModel.groupSelectedItems() },
                                    onClearSelection = { viewModel.clearRangeSelection() },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            } else if (uiState.groupedItemIds.isNotEmpty() && uiState.rangeSelectedItemIds.size < 2) {
                                // グループ解除バー（グループ存在時、範囲選択中でない場合）
                                GroupInfoBar(
                                    groupSize = uiState.groupedItemIds.size,
                                    onClearGroup = { viewModel.clearGroup() },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                    else -> {
                        // executeモード：1列（フィルタ適用済み実行列）
                        ExecuteModeList(
                            items = uiState.filteredExecuteItems,
                            duplicateSpaceItemIds = uiState.duplicateSpaceItemIds,
                            cardScale = cardScale,
                            dragEnabled = uiState.executeModeItemDragEnabled,
                            groupLabels = uiState.executeGroupLabels,
                            onItemTap = { viewModel.showStatusDialog(it) },
                            onItemLongPress = { viewModel.showEditItemDialog(it) },
                            onStatusClick = { viewModel.cycleItemStatus(it) },
                            onPriceChange = { id, price -> viewModel.updateItemPrice(id, price) },
                            onDeleteItem = { viewModel.deleteItem(it) },
                            onMoveItem = { from, to -> viewModel.moveExecuteItem(from, to) }
                        )
                    }
                }
            }
        }
    }

    // ステータス選択ダイアログ
    if (uiState.showStatusDialog && uiState.selectedItem != null) {
        StatusSelectDialog(
            currentStatus = uiState.selectedItem!!.purchaseStatus,
            onStatusSelected = { viewModel.updateItemStatus(it) },
            onDismiss = { viewModel.hideStatusDialog() }
        )
    }

    // アイテム追加ダイアログ
    if (uiState.showAddItemDialog) {
        AddEditItemDialog(
            item = null,
            eventDates = uiState.eventDates,
            onDismiss = { viewModel.hideAddItemDialog() },
            onSave = { item -> viewModel.addItem(item) }
        )
    }

    // アイテム編集ダイアログ
    if (uiState.showEditItemDialog && uiState.editingItem != null) {
        AddEditItemDialog(
            item = uiState.editingItem,
            eventDates = uiState.eventDates,
            onDismiss = { viewModel.hideEditItemDialog() },
            onSave = { item -> viewModel.updateItem(item) },
            onDelete = { viewModel.deleteItem(uiState.editingItem!!) }
        )
    }
}

// ==================== 参加日タブ（件数バッジ+長押し対応） ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateTabsWithBadge(
    dates: List<String>,
    selectedDate: String?,
    itemCountByDate: Map<String, Int>,
    dayModes: Map<String, String>,
    onDateSelected: (String) -> Unit,
    onDateLongPress: (String) -> Unit
) {
    val selectedIndex = dates.indexOf(selectedDate).coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp
    ) {
        dates.forEachIndexed { index, date ->
            val count = itemCountByDate[date] ?: 0
            val mode = dayModes[date] ?: "edit"
            val isExecuteMode = mode == "execute"
            val isSelected = date == selectedDate

            // Tabの代わりにBoxでカスタムタブを実装（長押し対応のため）
            Box(
                modifier = Modifier
                    .combinedClickable(
                        onClick = { onDateSelected(date) },
                        onLongClick = { onDateLongPress(date) }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isExecuteMode) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "実行モード",
                            modifier = Modifier.size(14.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$date ($count)",
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

// ==================== ステータストグルフィルタボタン ====================

@Composable
private fun StatusFilterButton(
    currentFilter: ExecuteStatusFilter,
    onClick: () -> Unit
) {
    val (icon, tint) = when (currentFilter) {
        ExecuteStatusFilter.ALL -> Icons.Default.FilterList to MaterialTheme.colorScheme.onSurface
        ExecuteStatusFilter.POSTPONE -> Icons.Default.PauseCircle to Color(0xFF9C27B0)
        ExecuteStatusFilter.LATE -> Icons.Default.Schedule to Color(0xFF2196F3)
        ExecuteStatusFilter.ABSENT -> Icons.Default.RemoveCircle to Color(0xFFFF9800)
        ExecuteStatusFilter.SOLD_OUT -> Icons.Default.Cancel to Color(0xFFF44336)
        ExecuteStatusFilter.NONE -> Icons.Default.RadioButtonUnchecked to Color.Gray
    }

    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = "フィルタ: ${currentFilter.displayName}",
            tint = tint
        )
    }
}

// ==================== 2列レイアウト ====================

@Composable
private fun TwoColumnLayout(
    executeItems: List<ShoppingItem>,
    candidateItems: List<ShoppingItem>,
    duplicateSpaceItemIds: Set<String>,
    cardScale: Float,
    groupLabels: Map<String, String>,
    isRangeSelectionMode: Boolean,
    rangeSelectedItemIds: Set<String>,
    groupedItemIdSet: Set<String>,
    rangeStartId: String?,
    rangeEndId: String?,
    onExecuteItemTap: (ShoppingItem) -> Unit,
    onCandidateItemTap: (ShoppingItem) -> Unit,
    onItemLongPress: (ShoppingItem) -> Unit,
    onStatusClick: (ShoppingItem) -> Unit,
    onPriceChange: (String, Int?) -> Unit,
    onMoveExecuteItem: (Int, Int) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // 左列：実行列
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ColumnHeader(
                title = "▶ 実行列",
                count = executeItems.size,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            ExecuteColumnList(
                items = executeItems,
                duplicateSpaceItemIds = duplicateSpaceItemIds,
                cardScale = cardScale,
                groupLabels = groupLabels,
                isRangeSelectionMode = isRangeSelectionMode,
                rangeSelectedItemIds = rangeSelectedItemIds,
                groupedItemIdSet = groupedItemIdSet,
                rangeStartId = rangeStartId,
                rangeEndId = rangeEndId,
                onItemTap = onExecuteItemTap,
                onItemLongPress = onItemLongPress,
                onStatusClick = onStatusClick,
                onPriceChange = onPriceChange,
                onMoveBlock = onMoveExecuteItem,
                onDeleteItem = onDeleteItem
            )
        }

        // 右列：候補リスト
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ColumnHeader(
                title = "候補リスト",
                count = candidateItems.size,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                textColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            CandidateColumnList(
                items = candidateItems,
                duplicateSpaceItemIds = duplicateSpaceItemIds,
                cardScale = cardScale,
                onItemTap = onCandidateItemTap,
                onItemLongPress = onItemLongPress,
                onStatusClick = onStatusClick,
                onPriceChange = onPriceChange,
                onDeleteItem = onDeleteItem
            )
        }
    }
}

@Composable
private fun ColumnHeader(
    title: String,
    count: Int,
    backgroundColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

// ==================== 実行列リスト（ドラッグ&ドロップ対応） ====================

/**
 * ディスプレイブロック: グループをLazyColumnの1アイテムとして扱うための中間表現
 */
private sealed class DisplayBlock {
    data class Single(val item: ShoppingItem) : DisplayBlock()
    data class Group(val items: List<ShoppingItem>) : DisplayBlock()

    val key: String
        get() = when (this) {
            is Single -> item.id
            is Group -> "group_${items.first().id}"
        }
}

/**
 * 実行列アイテムからディスプレイブロックリストを構築
 */
private fun buildDisplayBlocks(
    items: List<ShoppingItem>,
    groupedItemIdSet: Set<String>
): List<DisplayBlock> {
    if (groupedItemIdSet.isEmpty()) {
        return items.map { DisplayBlock.Single(it) }
    }
    val result = mutableListOf<DisplayBlock>()
    var i = 0
    while (i < items.size) {
        if (groupedItemIdSet.contains(items[i].id)) {
            val groupItems = mutableListOf<ShoppingItem>()
            while (i < items.size && groupedItemIdSet.contains(items[i].id)) {
                groupItems.add(items[i])
                i++
            }
            result.add(DisplayBlock.Group(groupItems))
        } else {
            result.add(DisplayBlock.Single(items[i]))
            i++
        }
    }
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExecuteColumnList(
    items: List<ShoppingItem>,
    duplicateSpaceItemIds: Set<String>,
    cardScale: Float,
    groupLabels: Map<String, String>,
    isRangeSelectionMode: Boolean,
    rangeSelectedItemIds: Set<String>,
    groupedItemIdSet: Set<String>,
    rangeStartId: String?,
    rangeEndId: String?,
    onItemTap: (ShoppingItem) -> Unit,
    onItemLongPress: (ShoppingItem) -> Unit,
    onStatusClick: (ShoppingItem) -> Unit,
    onPriceChange: (String, Int?) -> Unit,
    onMoveBlock: (Int, Int) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit
) {
    // ディスプレイブロック構築
    val displayBlocks = remember(items, groupedItemIdSet) {
        buildDisplayBlocks(items, groupedItemIdSet)
    }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMoveBlock(from.index, to.index)
    }

    // 始点選択済み＆終点未選択時のみドラッグ無効
    val isDragDisabled = isRangeSelectionMode && groupedItemIdSet.isEmpty() &&
            rangeStartId != null && rangeEndId == null

    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "実行列にアイテムが\nありません\n\n候補リストのアイテムを\nタップして追加",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(displayBlocks, key = { it.key }) { block ->
            ReorderableItem(reorderableLazyListState, key = block.key) {
                when (block) {
                    is DisplayBlock.Single -> {
                        // ---- 単一アイテム ----
                        val item = block.item
                        val itemIndex = items.indexOf(item)
                        val currentGroup = groupLabels[item.id] ?: ""
                        val prevGroup = if (itemIndex > 0) groupLabels[items[itemIndex - 1].id] ?: "" else ""
                        val showGroupHeader = currentGroup.isNotEmpty() && currentGroup != prevGroup

                        val isRangeSelected = rangeSelectedItemIds.contains(item.id)
                        val isStart = item.id == rangeStartId
                        val isEnd = item.id == rangeEndId

                        val highlightColor: Color? = when {
                            isStart || isEnd -> Color(0xFF1565C0)
                            isRangeSelected -> Color(0xFF42A5F5)
                            else -> null
                        }

                        Column {
                            if (showGroupHeader) {
                                GroupSeparator(label = currentGroup)
                            }

                            var showContextMenu by remember { mutableStateOf(false) }
                            Box {
                                ShoppingItemCard(
                                    item = item,
                                    scale = cardScale,
                                    onStatusClick = { onStatusClick(item) },
                                    onItemClick = { onItemTap(item) },
                                    showDragHandle = !isDragDisabled,
                                    reorderableScope = if (!isDragDisabled) this@ReorderableItem else null,
                                    onPriceChange = { price -> onPriceChange(item.id, price) },
                                    isDuplicateCircle = duplicateSpaceItemIds.contains(item.id),
                                    onLongClick = { if (!isRangeSelectionMode) showContextMenu = true },
                                    highlightBorderColor = highlightColor
                                )

                                ItemContextMenu(
                                    expanded = showContextMenu,
                                    onDismiss = { showContextMenu = false },
                                    onEdit = {
                                        showContextMenu = false
                                        onItemLongPress(item)
                                    },
                                    onDelete = {
                                        showContextMenu = false
                                        onDeleteItem(item)
                                    }
                                )
                            }
                        }
                    }
                    is DisplayBlock.Group -> {
                        // ---- グループブロック ----
                        val groupItems = block.items
                        val firstItem = groupItems.first()
                        val firstItemIndex = items.indexOf(firstItem)
                        val currentGroup = groupLabels[firstItem.id] ?: ""
                        val prevGroup = if (firstItemIndex > 0) groupLabels[items[firstItemIndex - 1].id] ?: "" else ""
                        val showGroupSeparator = currentGroup.isNotEmpty() && currentGroup != prevGroup

                        Column {
                            if (showGroupSeparator) {
                                GroupSeparator(label = currentGroup)
                            }

                            // グループヘッダー（ドラッグハンドル付き）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .background(
                                        Color(0xFF7E57C2).copy(alpha = 0.15f),
                                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ドラッグハンドル（this@ReorderableItem スコープ内で直接使用）
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "グループをドラッグして移動",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .draggableHandle(),
                                    tint = Color(0xFF7E57C2)
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF7E57C2)
                                )
                                Text(
                                    text = " グループ (${groupItems.size}件)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF7E57C2),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // グループ内アイテム（個別ドラッグなし、紫ボーダー）
                            groupItems.forEach { item ->
                                var showContextMenu by remember { mutableStateOf(false) }
                                Box {
                                    ShoppingItemCard(
                                        item = item,
                                        scale = cardScale,
                                        onStatusClick = { onStatusClick(item) },
                                        onItemClick = { onItemTap(item) },
                                        showDragHandle = false,
                                        reorderableScope = null,
                                        onPriceChange = { price -> onPriceChange(item.id, price) },
                                        isDuplicateCircle = duplicateSpaceItemIds.contains(item.id),
                                        onLongClick = { showContextMenu = true },
                                        highlightBorderColor = Color(0xFF7E57C2).copy(alpha = 0.5f)
                                    )

                                    ItemContextMenu(
                                        expanded = showContextMenu,
                                        onDismiss = { showContextMenu = false },
                                        onEdit = {
                                            showContextMenu = false
                                            onItemLongPress(item)
                                        },
                                        onDelete = {
                                            showContextMenu = false
                                            onDeleteItem(item)
                                        }
                                    )
                                }
                            }

                            // グループ末尾線
                            Divider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = Color(0xFF7E57C2).copy(alpha = 0.5f),
                                thickness = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 候補リスト ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CandidateColumnList(
    items: List<ShoppingItem>,
    duplicateSpaceItemIds: Set<String>,
    cardScale: Float,
    onItemTap: (ShoppingItem) -> Unit,
    onItemLongPress: (ShoppingItem) -> Unit,
    onStatusClick: (ShoppingItem) -> Unit,
    onPriceChange: (String, Int?) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "候補リストに\nアイテムがありません",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(items, key = { it.id }) { item ->
            var showContextMenu by remember { mutableStateOf(false) }

            Box {
                ShoppingItemCard(
                    item = item,
                    scale = cardScale,
                    onStatusClick = { onStatusClick(item) },
                    onItemClick = { onItemTap(item) },
                    showDragHandle = false,
                    onPriceChange = { price -> onPriceChange(item.id, price) },
                    isDuplicateCircle = duplicateSpaceItemIds.contains(item.id),
                    onLongClick = { showContextMenu = true }
                )

                ItemContextMenu(
                    expanded = showContextMenu,
                    onDismiss = { showContextMenu = false },
                    onEdit = {
                        showContextMenu = false
                        onItemLongPress(item)
                    },
                    onDelete = {
                        showContextMenu = false
                        onDeleteItem(item)
                    }
                )
            }
        }
    }
}

// ==================== executeモードリスト ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExecuteModeList(
    items: List<ShoppingItem>,
    duplicateSpaceItemIds: Set<String>,
    cardScale: Float,
    dragEnabled: Boolean,
    groupLabels: Map<String, String>,
    onItemTap: (ShoppingItem) -> Unit,
    onItemLongPress: (ShoppingItem) -> Unit,
    onStatusClick: (ShoppingItem) -> Unit,
    onPriceChange: (String, Int?) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    onMoveItem: (Int, Int) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "実行列にアイテムがありません",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    if (dragEnabled) {
        // ドラッグ有効時
        val lazyListState = rememberLazyListState()
        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            onMoveItem(from.index, to.index)
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                ReorderableItem(reorderableLazyListState, key = item.id) {
                    val itemIndex = items.indexOf(item)
                    val currentGroup = groupLabels[item.id] ?: ""
                    val prevGroup = if (itemIndex > 0) groupLabels[items[itemIndex - 1].id] ?: "" else ""
                    val showGroupHeader = currentGroup.isNotEmpty() && currentGroup != prevGroup

                    Column {
                        if (showGroupHeader) {
                            GroupSeparator(label = currentGroup)
                        }

                        var showContextMenu by remember { mutableStateOf(false) }
                        Box {
                            ShoppingItemCard(
                                item = item,
                                scale = cardScale,
                                onStatusClick = { onStatusClick(item) },
                                onItemClick = { onItemTap(item) },
                                showDragHandle = true,
                                reorderableScope = this@ReorderableItem,
                                onPriceChange = { price -> onPriceChange(item.id, price) },
                                isDuplicateCircle = duplicateSpaceItemIds.contains(item.id),
                                onLongClick = { showContextMenu = true }
                            )

                            ItemContextMenu(
                                expanded = showContextMenu,
                                onDismiss = { showContextMenu = false },
                                onEdit = {
                                    showContextMenu = false
                                    onItemLongPress(item)
                                },
                                onDelete = {
                                    showContextMenu = false
                                    onDeleteItem(item)
                                }
                            )
                        }
                    }  // Column end
                }
            }
        }
    } else {
        // ドラッグ無効時
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                val itemIndex = items.indexOf(item)
                val currentGroup = groupLabels[item.id] ?: ""
                val prevGroup = if (itemIndex > 0) groupLabels[items[itemIndex - 1].id] ?: "" else ""
                val showGroupHeader = currentGroup.isNotEmpty() && currentGroup != prevGroup

                Column {
                    if (showGroupHeader) {
                        GroupSeparator(label = currentGroup)
                    }

                    var showContextMenu by remember { mutableStateOf(false) }
                    Box {
                        ShoppingItemCard(
                            item = item,
                            scale = cardScale,
                            onStatusClick = { onStatusClick(item) },
                            onItemClick = { onItemTap(item) },
                            showDragHandle = false,
                            onPriceChange = { price -> onPriceChange(item.id, price) },
                            isDuplicateCircle = duplicateSpaceItemIds.contains(item.id),
                            onLongClick = { showContextMenu = true }
                        )

                        ItemContextMenu(
                            expanded = showContextMenu,
                            onDismiss = { showContextMenu = false },
                            onEdit = {
                                showContextMenu = false
                                onItemLongPress(item)
                            },
                            onDelete = {
                                showContextMenu = false
                                onDeleteItem(item)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==================== コンテキストメニュー ====================

@Composable
private fun ItemContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("編集") },
            onClick = onEdit,
            leadingIcon = {
                Icon(Icons.Default.Edit, contentDescription = null)
            }
        )
        DropdownMenuItem(
            text = { Text("削除", color = MaterialTheme.colorScheme.error) },
            onClick = onDelete,
            leadingIcon = {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        )
    }
}

// ==================== 空状態 ====================

@Composable
private fun EmptyStateView(
    isEditMode: Boolean,
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "アイテムがありません",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isEditMode) {
            TextButton(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("アイテムを追加")
            }
        }
    }
}

// ==================== 検索バー ====================

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("検索...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "検索") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "クリア")
                }
            }
        },
        singleLine = true
    )
}

// ==================== ブロックフィルタ ====================

@Composable
private fun BlockFilter(
    blocks: List<String>,
    selectedBlock: String?,
    onBlockSelected: (String?) -> Unit,
    hallBlocks: Map<String, List<String>> = emptyMap(),
    onHallBulkAdd: ((String) -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedBlock == null,
            onClick = { onBlockSelected(null) },
            label = { Text("全て") }
        )
        blocks.forEach { block ->
            FilterChip(
                selected = block == selectedBlock,
                onClick = { onBlockSelected(block) },
                label = { Text(block) }
            )
        }

        // ホール一括追加ボタン（ホール定義がある場合のみ）
        if (hallBlocks.isNotEmpty() && onHallBulkAdd != null) {
            Spacer(modifier = Modifier.width(8.dp))
            hallBlocks.forEach { (hallName, _) ->
                FilterChip(
                    selected = false,
                    onClick = { onHallBulkAdd(hallName) },
                    label = { Text("⊕$hallName") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

// ==================== 範囲選択アクションバー ====================

@Composable
private fun RangeSelectionActionBar(
    selectedCount: Int,
    hasGroup: Boolean,
    onMoveToCandidate: () -> Unit,
    onGroup: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${selectedCount}件選択中",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onMoveToCandidate) {
                    Icon(Icons.Default.RemoveCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("候補に戻す", style = MaterialTheme.typography.labelMedium)
                }
                if (!hasGroup) {
                    TextButton(onClick = onGroup) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("グループ化", style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "選択解除", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ==================== グループ情報バー ====================

@Composable
private fun GroupInfoBar(
    groupSize: Int,
    onClearGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    text = " グループ: ${groupSize}件",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onClearGroup) {
                Text("解除", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ==================== グループ境界セパレータ ====================

@Composable
private fun GroupSeparator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Divider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Divider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )
    }
}

// ==================== SummaryBar ====================

@Composable
private fun SummaryBar(
    purchasedCount: Int,
    totalCount: Int,
    remainingAmount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "購入: $purchasedCount / $totalCount 件",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "残り: ¥%,d".format(remainingAmount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ==================== ダイアログ群 ====================

@Composable
private fun StatusSelectDialog(
    currentStatus: PurchaseStatus,
    onStatusSelected: (PurchaseStatus) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("購入状態を選択") },
        text = {
            Column {
                PurchaseStatus.entries.forEach { status ->
                    TextButton(
                        onClick = { onStatusSelected(status) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${if (status == currentStatus) "✓ " else "   "}${status.displayName}",
                            color = Color(status.colorHex)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditItemDialog(
    item: ShoppingItem?,
    eventDates: List<String>,
    onDismiss: () -> Unit,
    onSave: (ShoppingItem) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var circle by remember { mutableStateOf(item?.circle ?: "") }
    var eventDate by remember { mutableStateOf(item?.eventDate ?: eventDates.firstOrNull() ?: "1日目") }
    var isCustomEventDate by remember { mutableStateOf(false) }
    var block by remember { mutableStateOf(item?.block ?: "") }
    var number by remember { mutableStateOf(item?.number ?: "") }
    var title by remember { mutableStateOf(item?.title ?: "") }
    var price by remember { mutableStateOf(item?.price?.toString() ?: "") }
    var quantity by remember { mutableStateOf(item?.quantity ?: 1) }
    var remarks by remember { mutableStateOf(item?.remarks ?: "") }
    var url by remember { mutableStateOf(item?.url ?: "") }

    var eventDateExpanded by remember { mutableStateOf(false) }
    var priceExpanded by remember { mutableStateOf(false) }
    var quantityExpanded by remember { mutableStateOf(false) }

    val defaultDates = listOf("1日目", "2日目", "3日目", "4日目")
    val allEventDates = (eventDates + defaultDates).distinct().sorted()

    if (item != null && !allEventDates.contains(item.eventDate)) {
        isCustomEventDate = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "アイテム追加" else "アイテム編集") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = circle,
                    onValueChange = { circle = it },
                    label = { Text("サークル名 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isCustomEventDate) {
                    OutlinedTextField(
                        value = eventDate,
                        onValueChange = { eventDate = it },
                        label = { Text("参加日 *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { isCustomEventDate = false }) {
                                Text("選択", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = eventDateExpanded,
                        onExpandedChange = { eventDateExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = eventDate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("参加日 *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eventDateExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = eventDateExpanded,
                            onDismissRequest = { eventDateExpanded = false }
                        ) {
                            allEventDates.forEach { date ->
                                DropdownMenuItem(
                                    text = { Text(date) },
                                    onClick = {
                                        eventDate = date
                                        eventDateExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("任意の値を入力...") },
                                onClick = {
                                    isCustomEventDate = true
                                    eventDateExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = block,
                        onValueChange = { block = it },
                        label = { Text("ブロック *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("ナンバー *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = if (price.isEmpty()) "価格未定" else price,
                        onValueChange = {
                            val filtered = it.filter { c -> c.isDigit() }
                            price = filtered
                        },
                        label = { Text("価格") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        suffix = { if (price.isNotEmpty()) Text("円") },
                        textStyle = if (price.isEmpty()) {
                            MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge
                        }
                    )

                    ExposedDropdownMenuBox(
                        expanded = priceExpanded,
                        onExpandedChange = { priceExpanded = it }
                    ) {
                        TextButton(
                            onClick = { priceExpanded = true },
                            modifier = Modifier.menuAnchor()
                        ) {
                            Text("選択")
                        }
                        ExposedDropdownMenu(
                            expanded = priceExpanded,
                            onDismissRequest = { priceExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text("価格未定", color = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    price = ""
                                    priceExpanded = false
                                }
                            )
                            (0..100).forEach { i ->
                                val p = i * 100
                                DropdownMenuItem(
                                    text = { Text("${p}円") },
                                    onClick = {
                                        price = p.toString()
                                        priceExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = quantityExpanded,
                    onExpandedChange = { quantityExpanded = it }
                ) {
                    OutlinedTextField(
                        value = quantity.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("数量") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = quantityExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = quantityExpanded,
                        onDismissRequest = { quantityExpanded = false }
                    ) {
                        (1..10).forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q.toString()) },
                                onClick = {
                                    quantity = q
                                    quantityExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("備考") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://...") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newItem = ShoppingItem(
                        id = item?.id ?: java.util.UUID.randomUUID().toString(),
                        eventId = item?.eventId ?: "",
                        circle = circle,
                        eventDate = eventDate,
                        block = block,
                        number = number,
                        title = title,
                        price = price.toIntOrNull(),
                        purchaseStatus = item?.purchaseStatus ?: PurchaseStatus.NONE,
                        quantity = quantity,
                        remarks = remarks,
                        url = url.ifBlank { null },
                        sortOrder = item?.sortOrder ?: 0,
                        isInExecuteList = item?.isInExecuteList ?: false
                    )
                    onSave(newItem)
                },
                enabled = circle.isNotBlank() && eventDate.isNotBlank() && block.isNotBlank() && number.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("削除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
            }
        }
    )
}

// ==================== CSVエクスポート ====================

private suspend fun exportToCsv(
    context: android.content.Context,
    viewModel: ShoppingListViewModel,
    eventName: String
) {
    withContext(Dispatchers.IO) {
        try {
            val exportData = viewModel.getExportData()
            if (exportData.items.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "エクスポートするアイテムがありません", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            val fileName = "${eventName.replace(Regex("[^a-zA-Z0-9ぁ-んァ-ン一-龥]"), "_")}_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)

            file.outputStream().use { outputStream ->
                CsvParser.exportToOutputStream(outputStream, exportData.items, exportData.executeListItemIds)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "CSVをエクスポート"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "エクスポートに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}