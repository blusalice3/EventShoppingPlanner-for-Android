package com.example.eventshoppingplanner.presentation.screens.shoppinglist

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventshoppingplanner.data.preferences.AppPreference
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.presentation.components.ShoppingItemCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


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

    val isEditMode = uiState.currentMode == "edit"
    val isFocusMode = uiState.currentMode == "focus"
    val cardScale = uiState.cardScale

    // SP+マップ表示中はTopAppBar/タブバーを非表示にしてマップ領域を確保
    val hideTopBars = isFocusMode && uiState.focusMapVisible &&
            uiState.focusLayoutMode == FocusLayoutMode.SMARTPHONE &&
            uiState.focusCurrentMapData != null && !uiState.focusCompleted

    // ピンチズーム用の一時スケール
    var tempScale by remember { mutableFloatStateOf(cardScale) }

    Scaffold(
        topBar = {
            if (!hideTopBars) {
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
                        if (!isEditMode && !isFocusMode) {
                            StatusFilterButton(
                                currentFilter = uiState.executeStatusFilter,
                                onClick = { viewModel.cycleExecuteStatusFilter() }
                            )
                        }

                        // 集中モードボタン（実行列にアイテムがある場合のみ表示）
                        if (uiState.currentExecuteIds.isNotEmpty() && !isFocusMode) {
                            IconButton(onClick = { viewModel.enterFocusMode() }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "集中モード",
                                    tint = Color(0xFF9C27B0)
                                )
                            }
                        }

                        // モード切替ボタン
                        IconButton(onClick = { viewModel.toggleCurrentDayMode() }) {
                            Icon(
                                imageVector = when {
                                    isFocusMode -> Icons.Default.Edit
                                    isEditMode -> Icons.Default.PlayArrow
                                    else -> Icons.Default.Edit
                                },
                                contentDescription = when {
                                    isFocusMode -> "編集モード"
                                    isEditMode -> "実行モード"
                                    else -> "編集モード"
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isFocusMode) {
                SummaryBar(
                    purchasedCount = uiState.purchasedCount,
                    totalCount = uiState.totalCount,
                    remainingAmount = uiState.remainingAmount
                )
            }
        },
        floatingActionButton = {
            if (isEditMode && !isFocusMode) {
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
            if (uiState.eventDates.isNotEmpty() && !hideTopBars) {
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
                    isFocusMode -> {
                        FocusModeContent(
                            uiState = uiState,
                            viewModel = viewModel
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
            val isFocusModeTab = mode == "focus"
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
                    if (isFocusModeTab) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "集中モード",
                            modifier = Modifier.size(14.dp),
                            tint = if (isSelected) Color(0xFF9C27B0)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isExecuteMode) {
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

// ==================== 集中モード ====================

@Composable
private fun FocusModeContent(
    uiState: ShoppingListUiState,
    viewModel: ShoppingListViewModel
) {
    val isTabletMode = uiState.focusLayoutMode == FocusLayoutMode.TABLET
    val isMapVisible = uiState.focusMapVisible && uiState.focusCurrentMapData != null && !uiState.focusCompleted

    // 自動スキップ処理
    LaunchedEffect(uiState.focusIsAutoAdvancing) {
        if (uiState.focusIsAutoAdvancing) {
            viewModel.focusAutoSkip()
        }
    }

    // 空状態
    if (uiState.focusAllVisits.isEmpty()) {
        FocusEmptyState(onEditMode = { viewModel.exitFocusMode("edit") })
        return
    }

    // 自動スキップ中ローディング
    if (uiState.focusIsAutoAdvancing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⏳", fontSize = 40.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("次の訪問先を探しています...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // 完了画面
    if (uiState.focusCompleted) {
        FocusCompletedScreen(
            isTabletMode = isTabletMode,
            onPrev = { viewModel.focusPrev() },
            onEditMode = { viewModel.exitFocusMode("edit") },
            onExecuteMode = { viewModel.exitFocusMode("execute") }
        )
        return
    }

    // ===== マップ表示モード分岐 =====
    if (isMapVisible && isTabletMode) {
        // タブレット + マップ ON: 左右50:50分割
        FocusModeWithMapTablet(uiState = uiState, viewModel = viewModel)
    } else if (isMapVisible && !isTabletMode) {
        // SP + マップ ON: 上下分割
        FocusModeWithMapSmartphone(uiState = uiState, viewModel = viewModel)
    } else {
        // マップ OFF: Phase 1と同じレイアウト
        FocusModeNoMap(uiState = uiState, viewModel = viewModel)
    }

    // フェーズ切替ダイアログ
    if (uiState.showPhaseChangeDialog && uiState.phaseChangeTargetPhase != null) {
        FocusPhaseChangeDialog(
            uiState = uiState,
            onFromStart = { viewModel.focusExecutePhaseChange(fromStart = true) },
            onFromSaved = { viewModel.focusExecutePhaseChange(fromStart = false) },
            onCancel = { viewModel.focusCancelPhaseChange() }
        )
    }

    // ステータス選択ダイアログ
    if (uiState.showStatusDialog && uiState.selectedItem != null) {
        StatusSelectDialog(
            currentStatus = uiState.selectedItem!!.purchaseStatus,
            onStatusSelected = {
                viewModel.focusUpdateItemStatus(uiState.selectedItem!!, it)
                viewModel.hideStatusDialog()
            },
            onDismiss = { viewModel.hideStatusDialog() }
        )
    }

    // セルタップポップアップ
    if (uiState.focusCellPopup != null) {
        FocusCellPopupDialog(
            popup = uiState.focusCellPopup!!,
            onDismiss = { viewModel.closeFocusCellPopup() },
            onAddNewItem = { viewModel.openFocusAddItemDialog() }
        )
    }

    // 新規アイテム追加ダイアログ
    if (uiState.focusAddItemDialog != null) {
        FocusAddItemDialogComposable(
            dialog = uiState.focusAddItemDialog!!,
            onDismiss = { viewModel.closeFocusAddItemDialog() },
            onSubmit = { circle, title, price, quantity, status, remarks, url ->
                viewModel.submitFocusAddItem(circle, title, price, quantity, status, remarks, url)
            }
        )
    }
}

/** 集中モード: 購入ステータスの次の値を返す */
private fun nextFocusStatus(current: PurchaseStatus): PurchaseStatus {
    return when (current) {
        PurchaseStatus.NONE -> PurchaseStatus.PURCHASED
        PurchaseStatus.PURCHASED -> PurchaseStatus.SOLD_OUT
        PurchaseStatus.SOLD_OUT -> PurchaseStatus.ABSENT
        PurchaseStatus.ABSENT -> PurchaseStatus.POSTPONE
        PurchaseStatus.POSTPONE -> PurchaseStatus.LATE
        PurchaseStatus.LATE -> PurchaseStatus.NONE
    }
}

// ==================== 集中モード: マップOFF ====================

@Composable
private fun FocusModeNoMap(
    uiState: ShoppingListUiState,
    viewModel: ShoppingListViewModel
) {
    val isTabletMode = uiState.focusLayoutMode == FocusLayoutMode.TABLET

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FocusHeaderBar(uiState = uiState, onPhaseChange = { viewModel.focusRequestPhaseChange(it) })

            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!isTabletMode) {
                            Modifier.pointerInput(Unit) {
                                var totalDragX = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDragX = 0f },
                                    onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount },
                                    onDragEnd = {
                                        if (totalDragX > 50f) viewModel.focusPrev()
                                        else if (totalDragX < -50f) viewModel.focusNext()
                                    }
                                )
                            }
                        } else Modifier
                    )
            ) {
                FocusItemList(
                    items = uiState.focusCurrentVisitDisplayItems,
                    blinkingPriceItemIds = uiState.blinkingPriceItemIds,
                    onStatusClick = { item -> viewModel.focusUpdateItemStatus(item, nextFocusStatus(item.purchaseStatus)) },
                    onPriceChange = { id, price -> viewModel.focusUpdateItemPrice(id, price) },
                    onItemTap = { item -> viewModel.showStatusDialog(item) }
                )
            }

            FocusFooterBar(
                uiState = uiState,
                isTabletMode = isTabletMode,
                onToggleLayout = { viewModel.toggleFocusLayoutMode() },
                onToggleMap = if (uiState.focusHasMapData) {
                    { viewModel.toggleFocusMapVisible() }
                } else null,
                isMapVisible = uiState.focusMapVisible
            )
        }

        // タブレットモード: ナビゲーションボタン
        if (isTabletMode) {
            FocusNavButtons(
                uiState = uiState,
                onPrev = { viewModel.focusPrev() },
                onNext = { viewModel.focusNext() }
            )
        }

        FocusOverlayNotifications(uiState = uiState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

// ==================== 集中モード: タブレット+マップ ====================

@Composable
private fun FocusModeWithMapTablet(
    uiState: ShoppingListUiState,
    viewModel: ShoppingListViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左側: マップ
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                FocusMapCanvas(
                    mapData = uiState.focusCurrentMapData!!,
                    cellStates = uiState.focusCellStates,
                    currentCellCoords = uiState.focusCurrentCellCoords,
                    nextCellCoords = uiState.focusNextCellCoords,
                    routeSegments = uiState.focusRouteSegments,
                    routeBounds = uiState.focusRouteBounds,
                    currentPhase = uiState.focusPhase,
                    zoomLevel = uiState.focusMapZoomLevel,
                    rotationDegrees = uiState.focusMapRotationDegrees,
                    selectedHall = uiState.focusSelectedHall,
                    onZoomChange = { viewModel.setFocusMapZoomLevel(it) },
                    onCellTap = { block, num, items -> viewModel.openFocusCellPopup(block, num, items) }
                )

                // フローティングコントロール
                FocusMapFloatingControls(
                    uiState = uiState,
                    onHallChange = { viewModel.setFocusSelectedHallId(it) },
                    onZoomChange = { viewModel.setFocusMapZoomLevel(it) },
                    onRotateLeft = { viewModel.rotateFocusMapBy(-45f) },
                    onRotateRight = { viewModel.rotateFocusMapBy(45f) },
                    onResetRotation = { viewModel.resetFocusMapRotation() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }

            // 右側: リスト + ◀▶ボタン
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    FocusHeaderBar(uiState = uiState, onPhaseChange = { viewModel.focusRequestPhaseChange(it) })

                    Box(modifier = Modifier.weight(1f)) {
                        FocusItemList(
                            items = uiState.focusCurrentVisitDisplayItems,
                            blinkingPriceItemIds = uiState.blinkingPriceItemIds,
                            onStatusClick = { item -> viewModel.focusUpdateItemStatus(item, nextFocusStatus(item.purchaseStatus)) },
                            onPriceChange = { id, price -> viewModel.focusUpdateItemPrice(id, price) },
                            onItemTap = { item -> viewModel.showStatusDialog(item) }
                        )
                    }

                    FocusFooterBar(
                        uiState = uiState,
                        isTabletMode = true,
                        onToggleLayout = { viewModel.toggleFocusLayoutMode() },
                        onToggleMap = { viewModel.toggleFocusMapVisible() },
                        isMapVisible = true
                    )
                }

                // ◀▶ボタン（右パネル内の左右端）
                FocusNavButtons(
                    uiState = uiState,
                    onPrev = { viewModel.focusPrev() },
                    onNext = { viewModel.focusNext() }
                )
            }
        }

        FocusOverlayNotifications(uiState = uiState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

// ==================== 集中モード: スマホ+マップ ====================

@Composable
private fun FocusModeWithMapSmartphone(
    uiState: ShoppingListUiState,
    viewModel: ShoppingListViewModel
) {
    val splitRatio = uiState.focusSplitRatio

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // マップエリア
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(splitRatio)
                    .clipToBounds()
            ) {
                FocusMapCanvas(
                    mapData = uiState.focusCurrentMapData!!,
                    cellStates = uiState.focusCellStates,
                    currentCellCoords = uiState.focusCurrentCellCoords,
                    nextCellCoords = uiState.focusNextCellCoords,
                    routeSegments = uiState.focusRouteSegments,
                    routeBounds = uiState.focusRouteBounds,
                    currentPhase = uiState.focusPhase,
                    zoomLevel = uiState.focusMapZoomLevel,
                    rotationDegrees = uiState.focusMapRotationDegrees,
                    selectedHall = uiState.focusSelectedHall,
                    onZoomChange = { viewModel.setFocusMapZoomLevel(it) },
                    onCellTap = { block, num, items -> viewModel.openFocusCellPopup(block, num, items) }
                )

                // フローティングコントロール
                FocusMapFloatingControls(
                    uiState = uiState,
                    onHallChange = { viewModel.setFocusSelectedHallId(it) },
                    onZoomChange = { viewModel.setFocusMapZoomLevel(it) },
                    onRotateLeft = { viewModel.rotateFocusMapBy(-45f) },
                    onRotateRight = { viewModel.rotateFocusMapBy(45f) },
                    onResetRotation = { viewModel.resetFocusMapRotation() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }

            // 分割線ドラッグハンドル
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        var startY = 0f
                        var startRatio = 0f
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                startY = offset.y
                                startRatio = splitRatio
                            },
                            onVerticalDrag = { _, dragAmount ->
                                val viewportHeight = size.height.toFloat()
                                if (viewportHeight > 0) {
                                    // 全体の高さに対する比率変更
                                    val deltaRatio = dragAmount / (viewportHeight * 8f)  // 感度調整
                                    viewModel.setFocusSplitRatio(splitRatio + deltaRatio)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            // リストエリア（スワイプ対応）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f - splitRatio)
                    .pointerInput(Unit) {
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount },
                            onDragEnd = {
                                if (totalDragX > 50f) viewModel.focusPrev()
                                else if (totalDragX < -50f) viewModel.focusNext()
                            }
                        )
                    }
            ) {
                FocusHeaderBar(uiState = uiState, onPhaseChange = { viewModel.focusRequestPhaseChange(it) })

                Box(modifier = Modifier.weight(1f)) {
                    FocusItemList(
                        items = uiState.focusCurrentVisitDisplayItems,
                        blinkingPriceItemIds = uiState.blinkingPriceItemIds,
                        onStatusClick = { item -> viewModel.focusUpdateItemStatus(item, nextFocusStatus(item.purchaseStatus)) },
                        onPriceChange = { id, price -> viewModel.focusUpdateItemPrice(id, price) },
                        onItemTap = { item -> viewModel.showStatusDialog(item) }
                    )
                }

                FocusFooterBar(
                    uiState = uiState,
                    isTabletMode = false,
                    onToggleLayout = { viewModel.toggleFocusLayoutMode() },
                    onToggleMap = { viewModel.toggleFocusMapVisible() },
                    isMapVisible = true
                )
            }
        }

        FocusOverlayNotifications(uiState = uiState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

// ==================== 集中モード: 共通オーバーレイ ====================

@Composable
private fun FocusOverlayNotifications(uiState: ShoppingListUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(visible = uiState.focusNotification != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF1976D2), shadowElevation = 8.dp) {
                Text(
                    text = uiState.focusNotification ?: "",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 14.sp
                )
            }
        }
        if (uiState.autoAdvanceCountdown != null) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFF9800), shadowElevation = 8.dp) {
                Text(
                    text = "${uiState.autoAdvanceCountdown}秒後に次の訪問先へ移動します...",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ==================== 集中モード: ナビゲーションボタン ====================

@Composable
private fun BoxScope.FocusNavButtons(
    uiState: ShoppingListUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    // 前へボタン（左端）
    Surface(
        onClick = onPrev,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 8.dp)
            .size(56.dp),
        shape = RoundedCornerShape(50),
        color = Color(0xFF546E7A),
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("◀", fontSize = 24.sp, color = Color.White)
        }
    }

    // 次へボタン（右端）
    val nextButtonColor = when {
        uiState.focusHasUndefinedPricePurchased -> Color(0xFFF44336)
        uiState.focusIsNextButtonBlinking -> Color(0xFF4CAF50)
        else -> Color(0xFF1976D2)
    }
    Surface(
        onClick = onNext,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 8.dp)
            .size(56.dp),
        shape = RoundedCornerShape(50),
        color = nextButtonColor,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("▶", fontSize = 24.sp, color = Color.White)
        }
    }
}

// ==================== 集中モード: マップフローティングコントロール ====================

@Composable
private fun FocusMapFloatingControls(
    uiState: ShoppingListUiState,
    onHallChange: (String) -> Unit,
    onZoomChange: (Int) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onResetRotation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hallExpanded by remember { mutableStateOf(false) }
    var zoomExpanded by remember { mutableStateOf(false) }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // ホール選択
        Box {
            Surface(
                onClick = { hallExpanded = true },
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                val displayText = if (uiState.focusSelectedHallId == "follow") "追随ON"
                else uiState.hallDefinitions.find { it.id == uiState.focusSelectedHallId }?.name ?: "全体"
                Text(
                    text = displayText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            DropdownMenu(expanded = hallExpanded, onDismissRequest = { hallExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("追随モードON", fontSize = 13.sp) },
                    onClick = { onHallChange("follow"); hallExpanded = false }
                )
                uiState.hallDefinitions.forEach { hall ->
                    DropdownMenuItem(
                        text = { Text(hall.name, fontSize = 13.sp) },
                        onClick = { onHallChange(hall.id); hallExpanded = false }
                    )
                }
            }
        }

        // ズームレベル
        Box {
            Surface(
                onClick = { zoomExpanded = true },
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "${uiState.focusMapZoomLevel}%",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            DropdownMenu(expanded = zoomExpanded, onDismissRequest = { zoomExpanded = false }) {
                listOf(30, 40, 50, 60, 70, 80, 90, 100).forEach { level ->
                    DropdownMenuItem(
                        text = { Text("$level%", fontSize = 13.sp) },
                        onClick = { onZoomChange(level); zoomExpanded = false }
                    )
                }
            }
        }

        // 回転コントロール
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onRotateLeft,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                        contentDescription = "左回転",
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "${uiState.focusMapRotationDegrees.toInt()}°",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = onResetRotation,
                    enabled = uiState.focusMapRotationDegrees != 0f,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("R", fontSize = 11.sp)
                }
                IconButton(
                    onClick = onRotateRight,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "右回転",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==================== 集中モード: セルタップポップアップ ====================

@Composable
private fun FocusCellPopupDialog(
    popup: FocusCellPopup,
    onDismiss: () -> Unit,
    onAddNewItem: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // ヘッダー
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${popup.blockName}-${popup.number}" +
                                if (popup.items.isNotEmpty()) "（${popup.items.size}件）" else "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "閉じる",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 新規アイテム追加ボタン
                Button(
                    onClick = onAddNewItem,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF43A047)
                    )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新規アイテム追加")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // アイテム一覧
                if (popup.items.isEmpty()) {
                    Text(
                        "このセルにはアイテムがありません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        popup.items.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (item.purchaseStatus) {
                                        PurchaseStatus.PURCHASED -> Color(0xFFE8F5E9)
                                        PurchaseStatus.SOLD_OUT -> Color(0xFFFFEBEE)
                                        PurchaseStatus.ABSENT -> Color(0xFFFFF3E0)
                                        PurchaseStatus.POSTPONE -> Color(0xFFF3E5F5)
                                        PurchaseStatus.LATE -> Color(0xFFE3F2FD)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // ステータスアイコン
                                    val statusIcon = when (item.purchaseStatus) {
                                        PurchaseStatus.PURCHASED -> "✅"
                                        PurchaseStatus.SOLD_OUT -> "❌"
                                        PurchaseStatus.ABSENT -> "🚫"
                                        PurchaseStatus.POSTPONE -> "⏸"
                                        PurchaseStatus.LATE -> "⏰"
                                        else -> "⬜"
                                    }
                                    Text(statusIcon, fontSize = 16.sp)

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.circle,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.title.isNotBlank()) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (item.price != null && item.price > 0) {
                                        Text(
                                            "¥${"%,d".format(item.price)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 集中モード: 新規アイテム追加ダイアログ ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusAddItemDialogComposable(
    dialog: FocusAddItemDialog,
    onDismiss: () -> Unit,
    onSubmit: (
        circle: String,
        title: String,
        price: Int?,
        quantity: Int,
        purchaseStatus: PurchaseStatus,
        remarks: String,
        url: String
    ) -> Unit
) {
    var circle by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var number by remember { mutableStateOf(dialog.number) }
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(1) }
    var purchaseStatus by remember { mutableStateOf(PurchaseStatus.PURCHASED) }
    var remarks by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    var priceExpanded by remember { mutableStateOf(false) }
    var quantityExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // ヘッダー
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "新規アイテム追加",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${dialog.eventDate} ${dialog.block}-${dialog.number}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "閉じる",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // フォーム（スクロール可能）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // サークル名（必須）
                    OutlinedTextField(
                        value = circle,
                        onValueChange = { circle = it },
                        label = { Text("サークル名 *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // タイトル
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("タイトル") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 参加日・ブロック（読み取り専用）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dialog.eventDate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("参加日") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            enabled = false
                        )
                        OutlinedTextField(
                            value = dialog.block,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("ブロック") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            enabled = false
                        )
                    }

                    // ナンバー（編集可能）
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("ナンバー") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 価格（テキスト入力 + クイック選択）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = if (price.isEmpty()) "価格未定" else price,
                            onValueChange = { newVal ->
                                price = newVal.filter { it.isDigit() }
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
                                        Text(
                                            "価格未定",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        price = ""
                                        priceExpanded = false
                                    }
                                )
                                listOf(0, 100, 200, 300, 500, 1000, 1500, 2000, 3000, 5000).forEach { p ->
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

                    // 数量・購入状態（横並び）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 数量（半分幅）
                        ExposedDropdownMenuBox(
                            expanded = quantityExpanded,
                            onExpandedChange = { quantityExpanded = it },
                            modifier = Modifier.weight(1f)
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

                        // 購入状態（半分幅）
                        ExposedDropdownMenuBox(
                            expanded = statusExpanded,
                            onExpandedChange = { statusExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = when (purchaseStatus) {
                                    PurchaseStatus.PURCHASED -> "購入済"
                                    PurchaseStatus.POSTPONE -> "後回し"
                                    PurchaseStatus.LATE -> "遅参"
                                    else -> ""
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("購入状態") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false }
                            ) {
                                listOf(
                                    PurchaseStatus.PURCHASED to "購入済",
                                    PurchaseStatus.POSTPONE to "後回し",
                                    PurchaseStatus.LATE to "遅参"
                                ).forEach { (status, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            purchaseStatus = status
                                            statusExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 備考
                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("備考") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // URL
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://...") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ボタン
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("キャンセル")
                    }
                    Button(
                        onClick = {
                            onSubmit(
                                circle,
                                title,
                                price.toIntOrNull(),
                                quantity,
                                purchaseStatus,
                                remarks,
                                url
                            )
                        },
                        enabled = circle.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF43A047)
                        )
                    ) {
                        Text("リストに追加")
                    }
                }
            }
        }
    }
}

// ==================== 集中モード: ヘッダー ====================

@Composable
private fun FocusHeaderBar(
    uiState: ShoppingListUiState,
    onPhaseChange: (FocusPhase) -> Unit
) {
    val priceInfo = uiState.focusCurrentVisitPriceInfo
    val (nextSpaceInfo, nextCircleName) = uiState.focusNextVisitInfo

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF5C6BC0), Color(0xFF7E57C2))
                    )
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 左: 訪問先情報
                Column {
                    Text("訪問先", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                    Text(
                        text = uiState.focusSpaceInfo,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiState.focusCircleName, fontSize = 14.sp, color = Color.White)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${uiState.focusCheckedCount}/${uiState.focusCurrentVisitDisplayItems.size}",
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 中央: 総額
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("総額", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                    when {
                        priceInfo.allUndefined -> Text("価格未定", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF9A9A))
                        priceInfo.undefinedCount > 0 -> Row {
                            Text("¥${"%,d".format(priceInfo.totalPrice)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("+未定${priceInfo.undefinedCount}件", fontSize = 12.sp, color = Color(0xFFEF9A9A))
                        }
                        else -> Text("¥${"%,d".format(priceInfo.totalPrice)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // 右: フェーズ + 次の訪問先
                Column(horizontalAlignment = Alignment.End) {
                    Text("フェーズ", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                    // ドロップダウンフェーズ選択
                    var phaseExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { phaseExpanded = true },
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = uiState.focusPhase.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(" ▼", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        DropdownMenu(
                            expanded = phaseExpanded,
                            onDismissRequest = { phaseExpanded = false }
                        ) {
                            FocusPhase.entries.forEach { phase ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = phase.displayName,
                                            fontWeight = if (phase == uiState.focusPhase) FontWeight.Bold else FontWeight.Normal,
                                            color = if (phase == uiState.focusPhase)
                                                Color(0xFF5C6BC0) else Color.Unspecified
                                        )
                                    },
                                    onClick = {
                                        phaseExpanded = false
                                        if (phase != uiState.focusPhase) onPhaseChange(phase)
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "次: $nextSpaceInfo $nextCircleName",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// ==================== 集中モード: アイテムリスト ====================

@Composable
private fun FocusItemList(
    items: List<ShoppingItem>,
    blinkingPriceItemIds: Set<String>,
    onStatusClick: (ShoppingItem) -> Unit,
    onPriceChange: (String, Int?) -> Unit,
    onItemTap: (ShoppingItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.id }) { item ->
            val isBorderBlinking = blinkingPriceItemIds.contains(item.id)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = if (isBorderBlinking) BorderStroke(2.dp, Color(0xFFF44336)) else null
            ) {
                ShoppingItemCard(
                    item = item,
                    onItemClick = { onItemTap(item) },
                    onStatusClick = { onStatusClick(item) },
                    onPriceChange = { price -> onPriceChange(item.id, price) },
                    isDuplicateCircle = false
                )
            }
        }
    }
}

// ==================== 集中モード: フッター ====================

@Composable
private fun FocusFooterBar(
    uiState: ShoppingListUiState,
    isTabletMode: Boolean,
    onToggleLayout: () -> Unit,
    onToggleMap: (() -> Unit)? = null,
    isMapVisible: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // フェーズ進捗
            Column {
                Text(
                    text = "${uiState.focusPhase.displayName}: ${uiState.focusPhaseIndex + 1}/${uiState.focusCurrentPhaseVisits.size}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF5C6BC0)
                )
                Text(
                    text = "(${uiState.focusCurrentVisitNumber}/${uiState.focusTotalVisits})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 購入件数 + 残り金額 + ボタン
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.focusPurchasedCount}/${uiState.focusExecuteItems.size}件",
                    fontSize = 14.sp
                )
                Text(
                    text = "¥${"%,d".format(uiState.focusRemainingCost)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1976D2)
                )

                // マップトグルボタン
                if (onToggleMap != null) {
                    IconButton(
                        onClick = onToggleMap,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isMapVisible) Color(0xFF1976D2) else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(6.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = if (isMapVisible) "マップ非表示" else "マップ表示",
                            modifier = Modifier.size(20.dp),
                            tint = if (isMapVisible) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // レイアウト切替ボタン
                IconButton(onClick = onToggleLayout, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isTabletMode) Icons.Default.PhoneAndroid else Icons.Default.Tablet,
                        contentDescription = if (isTabletMode) "スマートフォンモード" else "タブレットモード",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ==================== 集中モード: 完了画面 ====================

@Composable
private fun FocusCompletedScreen(
    isTabletMode: Boolean,
    onPrev: () -> Unit,
    onEditMode: () -> Unit,
    onExecuteMode: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "全ての訪問先を確認しました",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("お疲れ様でした！", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    onClick = onEditMode,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF546E7A)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📝", fontSize = 16.sp)
                        Text("編集モードへ", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(
                    onClick = onExecuteMode,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1976D2)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏃", fontSize = 16.sp)
                        Text("実行モードへ", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (!isTabletMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("← 右スワイプで前の訪問先へ戻る", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // タブレットモード: 前へボタン
        if (isTabletMode) {
            Surface(
                onClick = onPrev,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(56.dp),
                shape = RoundedCornerShape(50),
                color = Color(0xFF546E7A),
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("◀", fontSize = 24.sp, color = Color.White)
                }
            }
        }
    }
}

// ==================== 集中モード: 空状態 ====================

@Composable
private fun FocusEmptyState(onEditMode: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📋", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "訪問先がありません",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("実行列にアイテムを追加してください", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                onClick = onEditMode,
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1976D2)
            ) {
                Text(
                    "編集モードへ",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ==================== 集中モード: フェーズ切替ダイアログ ====================

@Composable
private fun FocusPhaseChangeDialog(
    uiState: ShoppingListUiState,
    onFromStart: () -> Unit,
    onFromSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val targetPhase = uiState.phaseChangeTargetPhase ?: return
    val targetVisits = uiState.focusVisitsByPhase[targetPhase] ?: emptyList()
    val hasSavedIndex = uiState.focusPhaseChangeHasSavedIndex
    val savedIndex = uiState.focusSavedPhaseIndices[targetPhase] ?: 0
    val savedVisit = if (hasSavedIndex) targetVisits.getOrNull(savedIndex) else null

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("フェーズを切り替えますか？")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${targetPhase.displayName}フェーズに移動します")
                Text("${targetPhase.displayName}フェーズには ${targetVisits.size} 件の訪問先があります。")

                // 最初から開始ボタン
                Surface(
                    onClick = onFromStart,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1976D2)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("最初から開始", color = Color.White, fontWeight = FontWeight.Medium)
                        targetVisits.firstOrNull()?.items?.firstOrNull()?.let { item ->
                            Text(
                                "${item.block}-${item.number} ${item.circle}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // 途中から再開ボタン
                if (hasSavedIndex && savedVisit != null) {
                    Surface(
                        onClick = onFromSaved,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF4CAF50)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("途中から再開", color = Color.White, fontWeight = FontWeight.Medium)
                            savedVisit.items.firstOrNull()?.let { item ->
                                Text(
                                    "${item.block}-${item.number} ${item.circle} (${savedIndex + 1}/${targetVisits.size})",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("キャンセル")
            }
        }
    )
}
