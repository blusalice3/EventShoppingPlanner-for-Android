package com.example.eventshoppingplanner.presentation.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
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

/**
 * 訪問先リストの表示モード
 */
enum class VisitListDisplayMode {
    SIDE_LEFT,   // サイドパネル（左）
    SIDE_RIGHT,  // サイドパネル（右）
    BOTTOM_SHEET // ボトムシート
}

/**
 * 訪問先リストパネル
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitListPanel(
    isOpen: Boolean,
    visitListItemIds: Set<String>,
    items: List<ShoppingItem>,
    halls: List<HallDefinition>,
    blocks: List<BlockDefinition>,
    currentDayName: String,
    displayMode: VisitListDisplayMode,
    panelWidth: Float,  // dp単位
    onClose: () -> Unit,
    onRemoveFromVisitList: (String) -> Unit,
    onChangePriority: (String, PriorityLevel) -> Unit,
    onChangeDisplayMode: (VisitListDisplayMode) -> Unit,
    onChangePanelWidth: (Float) -> Unit
) {
    if (!isOpen) return

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()

    // 現在の日付のアイテムのみをフィルタリング
    val dayItems = items.filter { it.eventDate == currentDayName }

    // 訪問先リストに含まれるアイテムを取得
    val visitListItems = dayItems.filter { visitListItemIds.contains(it.id) }

    // グループ化（ホール×優先度）
    val groupedItems = groupItemsByHallAndPriority(visitListItems, halls, blocks)

    when (displayMode) {
        VisitListDisplayMode.SIDE_LEFT, VisitListDisplayMode.SIDE_RIGHT -> {
            SidePanel(
                isLeft = displayMode == VisitListDisplayMode.SIDE_LEFT,
                width = panelWidth,
                screenWidth = screenWidth,
                groupedItems = groupedItems,
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
                onChangePanelWidth = onChangePanelWidth
            )
        }
        VisitListDisplayMode.BOTTOM_SHEET -> {
            BottomSheetPanel(
                groupedItems = groupedItems,
                onClose = onClose,
                onRemoveFromVisitList = onRemoveFromVisitList,
                onChangePriority = onChangePriority
            )
        }
    }
}

/**
 * アイテムをホール×優先度でグループ化
 */
private fun groupItemsByHallAndPriority(
    items: List<ShoppingItem>,
    halls: List<HallDefinition>,
    blocks: List<BlockDefinition>
): List<VisitGroup> {
    // ホールごとのブロック名マップを作成
    val hallBlockMap = mutableMapOf<String, Set<String>>()
    halls.forEach { hall ->
        val blocksInHall = HallUtils.getBlocksInHall(hall, blocks)
        hallBlockMap[hall.id] = blocksInHall.map { it.name }.toSet()
    }

    // アイテムをグループ化
    val groups = mutableListOf<VisitGroup>()

    // ホール定義順 × 優先度順（最優先→優先→通常）
    val priorityOrder = listOf(PriorityLevel.HIGHEST, PriorityLevel.PRIORITY, PriorityLevel.NONE)

    for (hall in halls) {
        val blockNames = hallBlockMap[hall.id] ?: emptySet()

        for (priority in priorityOrder) {
            val groupItems = items.filter { item ->
                blockNames.contains(item.block) && item.priorityLevel == priority
            }

            if (groupItems.isNotEmpty()) {
                groups.add(
                    VisitGroup(
                        groupId = createGroupId(hall.id, priority),
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
    val definedBlockNames = hallBlockMap.values.flatten().toSet()

    for (priority in priorityOrder) {
        val undefinedItems = items.filter { item ->
            !definedBlockNames.contains(item.block) && item.priorityLevel == priority
        }

        if (undefinedItems.isNotEmpty()) {
            groups.add(
                VisitGroup(
                    groupId = createGroupId(null, priority),
                    hallId = null,
                    hallName = "ホール未定義",
                    priorityLevel = priority,
                    items = undefinedItems
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
    onClose: () -> Unit,
    onRemoveFromVisitList: (String) -> Unit,
    onChangePriority: (String, PriorityLevel) -> Unit,
    onToggleSide: () -> Unit,
    onChangePanelWidth: (Float) -> Unit
) {
    var currentWidth by remember(width) { mutableStateOf(width) }
    val minWidth = 200f
    val maxWidth = screenWidth * 0.8f

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
                PanelHeader(
                    isLeft = isLeft,
                    totalItems = groupedItems.sumOf { it.items.size },
                    onClose = onClose,
                    onToggleSide = onToggleSide
                )

                HorizontalDivider()

                // コンテンツ
                if (groupedItems.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedItems.forEach { group ->
                            // グループヘッダー
                            item(key = "header_${group.groupId}") {
                                GroupHeader(group)
                            }

                            // グループ内アイテム
                            items(
                                items = group.items,
                                key = { it.id }
                            ) { item ->
                                VisitListItemRow(
                                    item = item,
                                    onRemove = { onRemoveFromVisitList(item.id) },
                                    onChangePriority = { priority ->
                                        onChangePriority(item.id, priority)
                                    }
                                )
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
    onClose: () -> Unit,
    onRemoveFromVisitList: (String) -> Unit,
    onChangePriority: (String, PriorityLevel) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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

            Spacer(modifier = Modifier.height(16.dp))

            if (groupedItems.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedItems.forEach { group ->
                        item(key = "header_${group.groupId}") {
                            GroupHeader(group)
                        }

                        items(
                            items = group.items,
                            key = { it.id }
                        ) { item ->
                            VisitListItemRow(
                                item = item,
                                onRemove = { onRemoveFromVisitList(item.id) },
                                onChangePriority = { priority ->
                                    onChangePriority(item.id, priority)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}