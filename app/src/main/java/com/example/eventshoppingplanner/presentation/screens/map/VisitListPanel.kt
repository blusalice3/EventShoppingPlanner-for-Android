package com.example.eventshoppingplanner.presentation.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.eventshoppingplanner.domain.model.*
import com.example.eventshoppingplanner.util.HallUtils
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 訪問先リストの表示モード
 */
enum class VisitListDisplayMode {
    SIDE_LEFT,   // サイドパネル（左）
    SIDE_RIGHT,  // サイドパネル（右）
    BOTTOM_SHEET // ボトムシート
}

/**
 * 訪問先リストの選択モード
 */
enum class VisitListSelectionMode {
    NORMAL,       // 通常モード
    RANGE_SELECT  // 範囲選択モード
}

/**
 * 訪問先リストパネル
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitListPanel(
    isOpen: Boolean,
    visitListItemIds: List<String>,
    items: List<ShoppingItem>,
    halls: List<HallDefinition>,
    blocks: List<BlockDefinition>,
    currentDayName: String,
    displayMode: VisitListDisplayMode,
    panelWidth: Float,  // dp単位
    selectionMode: VisitListSelectionMode,
    rangeStart: String?,
    rangeEnd: String?,
    onClose: () -> Unit,
    onRemoveFromVisitList: (String) -> Unit,
    onChangePriority: (String, PriorityLevel) -> Unit,
    onChangeDisplayMode: (VisitListDisplayMode) -> Unit,
    onChangePanelWidth: (Float) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onMoveItemUp: (String) -> Unit,
    onMoveItemDown: (String) -> Unit,
    onSetSelectionMode: (VisitListSelectionMode) -> Unit,
    onSetRangeStart: (String?) -> Unit,
    onSetRangeEnd: (String?) -> Unit,
    onReverseRange: () -> Unit
) {
    if (!isOpen) return

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()

    // 現在の日付のアイテムのみをフィルタリング
    val dayItems = items.filter { it.eventDate == currentDayName }

    // 訪問先リストに含まれるアイテムを順序を保持して取得
    val visitListItems = remember(visitListItemIds, dayItems) {
        visitListItemIds.mapNotNull { id ->
            dayItems.find { it.id == id }
        }
    }

    // グループ化（ホール×優先度）- 順序保持
    val groupedItems = remember(visitListItems, halls, blocks) {
        groupItemsByHallAndPriorityOrdered(visitListItems, halls, blocks)
    }

    when (displayMode) {
        VisitListDisplayMode.SIDE_LEFT, VisitListDisplayMode.SIDE_RIGHT -> {
            SidePanel(
                isLeft = displayMode == VisitListDisplayMode.SIDE_LEFT,
                width = panelWidth,
                screenWidth = screenWidth,
                groupedItems = groupedItems,
                allItemIds = visitListItemIds,
                selectionMode = selectionMode,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                onClose = onClose,
                onRemoveFromVisitList = onRemoveFromVisitList,
                onChangePriority = onChangePriority,
                onToggleSide = {
                    val newMode = if (displayMode == VisitListDisplayMode.SIDE_LEFT) {
                        VisitListDisplayMode.SIDE_RIGHT
                    } else {
                        VisitListDisplayMode.SIDE_LEFT
                    }
                    onChangeDisplayMode(newMode)
                },
                onChangePanelWidth = onChangePanelWidth,
                onMoveItem = onMoveItem,
                onMoveItemUp = onMoveItemUp,
                onMoveItemDown = onMoveItemDown,
                onSetSelectionMode = onSetSelectionMode,
                onSetRangeStart = onSetRangeStart,
                onSetRangeEnd = onSetRangeEnd,
                onReverseRange = onReverseRange
            )
        }
        VisitListDisplayMode.BOTTOM_SHEET -> {
            BottomSheetPanel(
                groupedItems = groupedItems,
                allItemIds = visitListItemIds,
                selectionMode = selectionMode,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                onClose = onClose,
                onRemoveFromVisitList = onRemoveFromVisitList,
                onChangePriority = onChangePriority,
                onMoveItem = onMoveItem,
                onMoveItemUp = onMoveItemUp,
                onMoveItemDown = onMoveItemDown,
                onSetSelectionMode = onSetSelectionMode,
                onSetRangeStart = onSetRangeStart,
                onSetRangeEnd = onSetRangeEnd,
                onReverseRange = onReverseRange
            )
        }
    }
}

/**
 * アイテムをホール×優先度でグループ化（既存互換）
 */
private fun groupItemsByHallAndPriority(
    items: List<ShoppingItem>,
    halls: List<HallDefinition>,
    blocks: List<BlockDefinition>
): List<VisitGroup> {
    return groupItemsByHallAndPriorityOrdered(items, halls, blocks)
}

/**
 * アイテムをホール×優先度でグループ化（順序保持版）
 * visitListItemIdsの順序を維持しながらグループ化
 */
private fun groupItemsByHallAndPriorityOrdered(
    items: List<ShoppingItem>,  // 既にvisitListItemIdsの順序で並んでいる
    halls: List<HallDefinition>,
    blocks: List<BlockDefinition>
): List<VisitGroup> {
    // ホールごとのブロック名マップを作成
    val hallBlockMap = mutableMapOf<String, Set<String>>()
    halls.forEach { hall ->
        val blocksInHall = HallUtils.getBlocksInHall(hall, blocks)
        hallBlockMap[hall.id] = blocksInHall.map { it.name }.toSet()
    }

    // ブロック名→ホールIDマップ
    val blockToHallMap = mutableMapOf<String, String?>()
    halls.forEach { hall ->
        hallBlockMap[hall.id]?.forEach { blockName ->
            blockToHallMap[blockName] = hall.id
        }
    }

    // アイテムをグループ化（順序保持）
    val groupMap = mutableMapOf<String, MutableList<ShoppingItem>>()

    items.forEach { item ->
        val hallId = blockToHallMap[item.block]
        val groupId = createGroupId(hallId, item.priorityLevel)
        groupMap.getOrPut(groupId) { mutableListOf() }.add(item)
    }

    // 結果リストを作成（ホール定義順 × 優先度順）
    val groups = mutableListOf<VisitGroup>()
    val priorityOrder = listOf(PriorityLevel.HIGHEST, PriorityLevel.PRIORITY, PriorityLevel.NONE)

    for (hall in halls) {
        for (priority in priorityOrder) {
            val groupId = createGroupId(hall.id, priority)
            val groupItems = groupMap[groupId]
            if (groupItems != null && groupItems.isNotEmpty()) {
                groups.add(
                    VisitGroup(
                        groupId = groupId,
                        hallId = hall.id,
                        hallName = hall.name,
                        priorityLevel = priority,
                        items = groupItems
                    )
                )
            }
        }
    }

    // ホール未定義のアイテム
    for (priority in priorityOrder) {
        val groupId = createGroupId(null, priority)
        val groupItems = groupMap[groupId]
        if (groupItems != null && groupItems.isNotEmpty()) {
            groups.add(
                VisitGroup(
                    groupId = groupId,
                    hallId = null,
                    hallName = "ホール未定義",
                    priorityLevel = priority,
                    items = groupItems
                )
            )
        }
    }

    return groups
}

/**
 * サイドパネル
 */
@Composable
private fun SidePanel(
    isLeft: Boolean,
    width: Float,
    screenWidth: Float,
    groupedItems: List<VisitGroup>,
    allItemIds: List<String>,
    selectionMode: VisitListSelectionMode,
    rangeStart: String?,
    rangeEnd: String?,
    onClose: () -> Unit,
    onRemoveFromVisitList: (String) -> Unit,
    onChangePriority: (String, PriorityLevel) -> Unit,
    onToggleSide: () -> Unit,
    onChangePanelWidth: (Float) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onMoveItemUp: (String) -> Unit,
    onMoveItemDown: (String) -> Unit,
    onSetSelectionMode: (VisitListSelectionMode) -> Unit,
    onSetRangeStart: (String?) -> Unit,
    onSetRangeEnd: (String?) -> Unit,
    onReverseRange: () -> Unit
) {
    var currentWidth by remember(width) { mutableStateOf(width) }
    val minWidth = 200f
    val maxWidth = screenWidth * 0.8f

    // 範囲選択の有効性チェック
    val canReverse = rangeStart != null && rangeEnd != null && rangeStart != rangeEnd

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 背景（タップで閉じる）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable { onClose() }
        )

        // パネル本体
        Surface(
            modifier = Modifier
                .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .width(currentWidth.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ヘッダー
                PanelHeaderWithMode(
                    isLeft = isLeft,
                    totalItems = groupedItems.sumOf { it.items.size },
                    selectionMode = selectionMode,
                    canReverse = canReverse,
                    onClose = onClose,
                    onToggleSide = onToggleSide,
                    onSetSelectionMode = onSetSelectionMode,
                    onReverseRange = onReverseRange
                )

                HorizontalDivider()

                // コンテンツ
                if (groupedItems.isEmpty()) {
                    EmptyState()
                } else {
                    val lazyListState = rememberLazyListState()
                    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        // ドラッグ＆ドロップ時の処理
                        val fromIndex = allItemIds.indexOf(from.key as? String ?: "")
                        val toIndex = allItemIds.indexOf(to.key as? String ?: "")
                        if (fromIndex >= 0 && toIndex >= 0) {
                            onMoveItem(fromIndex, toIndex)
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedItems.forEach { group ->
                            // グループヘッダー
                            item(key = "header_${group.groupId}") {
                                GroupHeader(group)
                            }

                            // グループ内アイテム
                            group.items.forEach { item ->
                                item(key = item.id) {
                                    ReorderableItem(reorderableLazyListState, key = item.id) {
                                        val isRangeStart = item.id == rangeStart
                                        val isRangeEnd = item.id == rangeEnd
                                        val isInRange = remember(allItemIds, rangeStart, rangeEnd, item.id) {
                                            if (rangeStart == null || rangeEnd == null) false
                                            else {
                                                val startIdx = allItemIds.indexOf(rangeStart)
                                                val endIdx = allItemIds.indexOf(rangeEnd)
                                                val itemIdx = allItemIds.indexOf(item.id)
                                                if (startIdx >= 0 && endIdx >= 0 && itemIdx >= 0) {
                                                    val min = minOf(startIdx, endIdx)
                                                    val max = maxOf(startIdx, endIdx)
                                                    itemIdx in min..max
                                                } else false
                                            }
                                        }

                                        VisitListItemRowWithReorder(
                                            item = item,
                                            allItemIds = allItemIds,
                                            selectionMode = selectionMode,
                                            isRangeStart = isRangeStart,
                                            isRangeEnd = isRangeEnd,
                                            isInRange = isInRange,
                                            reorderableScope = this,
                                            onRemove = { onRemoveFromVisitList(item.id) },
                                            onChangePriority = { priority ->
                                                onChangePriority(item.id, priority)
                                            },
                                            onMoveUp = { onMoveItemUp(item.id) },
                                            onMoveDown = { onMoveItemDown(item.id) },
                                            onRangeSelect = {
                                                if (rangeStart == null) {
                                                    onSetRangeStart(item.id)
                                                } else if (rangeEnd == null) {
                                                    onSetRangeEnd(item.id)
                                                } else {
                                                    // 再選択: リセットして新しい開始点に
                                                    onSetRangeStart(item.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // リサイズハンドル
        Box(
            modifier = Modifier
                .align(if (isLeft) Alignment.CenterEnd else Alignment.CenterStart)
                .offset(x = if (isLeft) (currentWidth - 8).dp else (-currentWidth + 8).dp)
                .width(16.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            onChangePanelWidth(currentWidth)
                        }
                    ) { _, dragAmount ->
                        val delta = if (isLeft) dragAmount else -dragAmount
                        currentWidth = (currentWidth + delta / density).coerceIn(minWidth, maxWidth)
                    }
                }
        ) {
            // リサイズインジケーター
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

/**
 * パネルヘッダー
 */
@Composable
private fun PanelHeader(
    isLeft: Boolean,
    totalItems: Int,
    onClose: () -> Unit,
    onToggleSide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "訪問先リスト",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${totalItems}件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row {
            // 左右切り替えボタン
            IconButton(onClick = onToggleSide) {
                Icon(
                    imageVector = if (isLeft) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                    contentDescription = if (isLeft) "右に移動" else "左に移動"
                )
            }

            // 閉じるボタン
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "閉じる"
                )
            }
        }
    }
}

/**
 * パネルヘッダー（選択モード対応版）
 */
@Composable
private fun PanelHeaderWithMode(
    isLeft: Boolean,
    totalItems: Int,
    selectionMode: VisitListSelectionMode,
    canReverse: Boolean,
    onClose: () -> Unit,
    onToggleSide: () -> Unit,
    onSetSelectionMode: (VisitListSelectionMode) -> Unit,
    onReverseRange: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "訪問先リスト",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${totalItems}件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                // 左右切り替えボタン
                IconButton(onClick = onToggleSide) {
                    Icon(
                        imageVector = if (isLeft) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                        contentDescription = if (isLeft) "右に移動" else "左に移動"
                    )
                }

                // 閉じるボタン
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "閉じる"
                    )
                }
            }
        }

        // 選択モード切り替えバー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 範囲選択モードボタン
            FilterChip(
                selected = selectionMode == VisitListSelectionMode.RANGE_SELECT,
                onClick = {
                    val newMode = if (selectionMode == VisitListSelectionMode.RANGE_SELECT) {
                        VisitListSelectionMode.NORMAL
                    } else {
                        VisitListSelectionMode.RANGE_SELECT
                    }
                    onSetSelectionMode(newMode)
                },
                label = { Text("範囲選択", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.height(32.dp)
            )

            // 反転ボタン（範囲選択モード時のみ有効）
            if (selectionMode == VisitListSelectionMode.RANGE_SELECT) {
                Button(
                    onClick = onReverseRange,
                    enabled = canReverse,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("区間反転", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * グループヘッダー
 */
@Composable
private fun GroupHeader(group: VisitGroup) {
    val priorityColor = when (group.priorityLevel) {
        PriorityLevel.HIGHEST -> Color(0xFFE53935)  // 赤
        PriorityLevel.PRIORITY -> Color(0xFFFF9800) // オレンジ
        PriorityLevel.NONE -> Color(0xFF1E88E5)     // 青
    }

    val priorityText = when (group.priorityLevel) {
        PriorityLevel.HIGHEST -> "最優先"
        PriorityLevel.PRIORITY -> "優先"
        PriorityLevel.NONE -> "通常"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 優先度インジケーター
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(priorityColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${group.hallName} - $priorityText",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "(${group.items.size}件)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 訪問先アイテム行
 */
@Composable
private fun VisitListItemRow(
    item: ShoppingItem,
    onRemove: () -> Unit,
    onChangePriority: (PriorityLevel) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val priorityColor = when (item.priorityLevel) {
        PriorityLevel.HIGHEST -> Color(0xFFFFEBEE)  // 薄い赤
        PriorityLevel.PRIORITY -> Color(0xFFFFF3E0) // 薄いオレンジ
        PriorityLevel.NONE -> Color(0xFFE3F2FD)     // 薄い青
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = priorityColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // アイテム情報
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.circle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.locationDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.title.isNotBlank()) {
                        Text(
                            text = " / ${item.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = item.priceDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // メニューボタン
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "メニュー",
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // 優先度変更
                    Text(
                        text = "優先度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFE53935), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("最優先")
                            }
                        },
                        onClick = {
                            onChangePriority(PriorityLevel.HIGHEST)
                            showMenu = false
                        },
                        enabled = item.priorityLevel != PriorityLevel.HIGHEST
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFFF9800), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("優先")
                            }
                        },
                        onClick = {
                            onChangePriority(PriorityLevel.PRIORITY)
                            showMenu = false
                        },
                        enabled = item.priorityLevel != PriorityLevel.PRIORITY
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF1E88E5), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("通常")
                            }
                        },
                        onClick = {
                            onChangePriority(PriorityLevel.NONE)
                            showMenu = false
                        },
                        enabled = item.priorityLevel != PriorityLevel.NONE
                    )

                    HorizontalDivider()

                    // 削除
                    DropdownMenuItem(
                        text = {
                            Text(
                                "訪問先から削除",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onRemove()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 訪問先アイテム行（並び替え対応版）
 */
@Composable
private fun VisitListItemRowWithReorder(
    item: ShoppingItem,
    allItemIds: List<String>,
    selectionMode: VisitListSelectionMode,
    isRangeStart: Boolean,
    isRangeEnd: Boolean,
    isInRange: Boolean,
    reorderableScope: ReorderableCollectionItemScope,
    onRemove: () -> Unit,
    onChangePriority: (PriorityLevel) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRangeSelect: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val priorityColor = when (item.priorityLevel) {
        PriorityLevel.HIGHEST -> Color(0xFFFFEBEE)  // 薄い赤
        PriorityLevel.PRIORITY -> Color(0xFFFFF3E0) // 薄いオレンジ
        PriorityLevel.NONE -> Color(0xFFE3F2FD)     // 薄い青
    }

    // 範囲選択時のハイライト
    val borderModifier = when {
        isRangeStart || isRangeEnd -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
        isInRange -> Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        else -> Modifier
    }

    // アイテムの現在位置を取得
    val currentIndex = allItemIds.indexOf(item.id)
    val isFirst = currentIndex == 0
    val isLast = currentIndex == allItemIds.size - 1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .then(
                if (selectionMode == VisitListSelectionMode.RANGE_SELECT) {
                    Modifier.clickable { onRangeSelect() }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = priorityColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ドラッグハンドル（通常モード時）
            if (selectionMode == VisitListSelectionMode.NORMAL) {
                with(reorderableScope) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "ドラッグして並び替え",
                        modifier = Modifier
                            .size(24.dp)
                            .draggableHandle(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // アイテム情報
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.circle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.locationDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.title.isNotBlank()) {
                        Text(
                            text = " / ${item.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = item.priceDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ▲▼ボタン（通常モード時）
            if (selectionMode == VisitListSelectionMode.NORMAL) {
                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "上に移動",
                            modifier = Modifier.size(18.dp),
                            tint = if (isFirst) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "下に移動",
                            modifier = Modifier.size(18.dp),
                            tint = if (isLast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // メニューボタン
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "メニュー",
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // 優先度変更
                    Text(
                        text = "優先度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFE53935), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("最優先")
                            }
                        },
                        onClick = {
                            onChangePriority(PriorityLevel.HIGHEST)
                            showMenu = false
                        },
                        enabled = item.priorityLevel != PriorityLevel.HIGHEST
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFFF9800), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("優先")
                            }
                        },
                        onClick = {
                            onChangePriority(PriorityLevel.PRIORITY)
                            showMenu = false
                        },
                        enabled = item.priorityLevel != PriorityLevel.PRIORITY
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF1E88E5), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("通常")
                            }
                        },
                        onClick = {
                            onChangePriority(PriorityLevel.NONE)
                            showMenu = false
                        },
                        enabled = item.priorityLevel != PriorityLevel.NONE
                    )

                    HorizontalDivider()

                    // 削除
                    DropdownMenuItem(
                        text = {
                            Text(
                                "訪問先から削除",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onRemove()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 空状態表示
 */
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "訪問先がありません",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "マップ上のセルをタップして\nアイテムを追加してください",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * ボトムシートパネル（簡易実装）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetPanel(
    groupedItems: List<VisitGroup>,
    allItemIds: List<String>,
    selectionMode: VisitListSelectionMode,
    rangeStart: String?,
    rangeEnd: String?,
    onClose: () -> Unit,
    onRemoveFromVisitList: (String) -> Unit,
    onChangePriority: (String, PriorityLevel) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onMoveItemUp: (String) -> Unit,
    onMoveItemDown: (String) -> Unit,
    onSetSelectionMode: (VisitListSelectionMode) -> Unit,
    onSetRangeStart: (String?) -> Unit,
    onSetRangeEnd: (String?) -> Unit,
    onReverseRange: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val canReverse = rangeStart != null && rangeEnd != null && rangeStart != rangeEnd

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "訪問先リスト",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${groupedItems.sumOf { it.items.size }}件)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 選択モード切り替え
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectionMode == VisitListSelectionMode.RANGE_SELECT,
                    onClick = {
                        val newMode = if (selectionMode == VisitListSelectionMode.RANGE_SELECT) {
                            VisitListSelectionMode.NORMAL
                        } else {
                            VisitListSelectionMode.RANGE_SELECT
                        }
                        onSetSelectionMode(newMode)
                    },
                    label = { Text("範囲選択", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.height(32.dp)
                )

                if (selectionMode == VisitListSelectionMode.RANGE_SELECT) {
                    Button(
                        onClick = onReverseRange,
                        enabled = canReverse,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("区間反転", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (groupedItems.isEmpty()) {
                EmptyState()
            } else {
                val lazyListState = rememberLazyListState()
                val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val fromIndex = allItemIds.indexOf(from.key as? String ?: "")
                    val toIndex = allItemIds.indexOf(to.key as? String ?: "")
                    if (fromIndex >= 0 && toIndex >= 0) {
                        onMoveItem(fromIndex, toIndex)
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedItems.forEach { group ->
                        item(key = "header_${group.groupId}") {
                            GroupHeader(group)
                        }

                        group.items.forEach { item ->
                            item(key = item.id) {
                                ReorderableItem(reorderableLazyListState, key = item.id) {
                                    val isRangeStart = item.id == rangeStart
                                    val isRangeEnd = item.id == rangeEnd
                                    val isInRange = remember(allItemIds, rangeStart, rangeEnd, item.id) {
                                        if (rangeStart == null || rangeEnd == null) false
                                        else {
                                            val startIdx = allItemIds.indexOf(rangeStart)
                                            val endIdx = allItemIds.indexOf(rangeEnd)
                                            val itemIdx = allItemIds.indexOf(item.id)
                                            if (startIdx >= 0 && endIdx >= 0 && itemIdx >= 0) {
                                                val min = minOf(startIdx, endIdx)
                                                val max = maxOf(startIdx, endIdx)
                                                itemIdx in min..max
                                            } else false
                                        }
                                    }

                                    VisitListItemRowWithReorder(
                                        item = item,
                                        allItemIds = allItemIds,
                                        selectionMode = selectionMode,
                                        isRangeStart = isRangeStart,
                                        isRangeEnd = isRangeEnd,
                                        isInRange = isInRange,
                                        reorderableScope = this,
                                        onRemove = { onRemoveFromVisitList(item.id) },
                                        onChangePriority = { priority ->
                                            onChangePriority(item.id, priority)
                                        },
                                        onMoveUp = { onMoveItemUp(item.id) },
                                        onMoveDown = { onMoveItemDown(item.id) },
                                        onRangeSelect = {
                                            if (rangeStart == null) {
                                                onSetRangeStart(item.id)
                                            } else if (rangeEnd == null) {
                                                onSetRangeEnd(item.id)
                                            } else {
                                                onSetRangeStart(item.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}