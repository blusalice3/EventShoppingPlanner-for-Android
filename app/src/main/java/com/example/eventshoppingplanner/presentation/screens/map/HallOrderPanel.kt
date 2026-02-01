package com.example.eventshoppingplanner.presentation.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.eventshoppingplanner.domain.model.*

/**
 * ホール順序パネル（モーダルダイアログ）
 * WEB版 HallOrderPanel.tsx に対応
 *
 * グループ（ホール×優先度）の順序を一覧表示し、
 * ▲/▼ボタンで順序変更、「実行列を並び替え」ボタンで訪問先リストを自動並び替え
 */
@Composable
fun HallOrderPanel(
    isOpen: Boolean,
    groupOrder: List<String>,
    halls: List<HallDefinition>,
    onClose: () -> Unit,
    onSave: (List<String>) -> Unit,
    onReorderExecuteList: () -> Unit,
    getGroupItemCount: (String) -> Int
) {
    if (!isOpen) return

    // ローカル編集用の順序（保存前の一時状態）
    var localOrder by remember(groupOrder) { mutableStateOf(groupOrder) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ヘッダー
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ホール間移動順序",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる")
                    }
                }

                HorizontalDivider()

                // コンテンツ
                if (localOrder.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ホールが定義されていません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // 訪問先ありグループとなしグループに分離
                    val groupsWithItems = localOrder.filter { getGroupItemCount(it) > 0 }
                    val groupsWithoutItems = localOrder.filter { getGroupItemCount(it) == 0 }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        // 訪問先があるグループ
                        if (groupsWithItems.isNotEmpty()) {
                            item {
                                Text(
                                    text = "訪問先があるグループ（この順序で回ります）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            var displayIndex = 0
                            localOrder.forEachIndexed { actualIndex, groupId ->
                                val itemCount = getGroupItemCount(groupId)
                                if (itemCount > 0) {
                                    val currentDisplayIndex = displayIndex
                                    item(key = "active_$groupId") {
                                        GroupOrderItem(
                                            groupId = groupId,
                                            halls = halls,
                                            displayIndex = currentDisplayIndex + 1,
                                            itemCount = itemCount,
                                            isFirst = actualIndex == 0,
                                            isLast = actualIndex == localOrder.size - 1,
                                            onMoveUp = {
                                                if (actualIndex > 0) {
                                                    val newOrder = localOrder.toMutableList()
                                                    val temp = newOrder[actualIndex - 1]
                                                    newOrder[actualIndex - 1] = newOrder[actualIndex]
                                                    newOrder[actualIndex] = temp
                                                    localOrder = newOrder
                                                }
                                            },
                                            onMoveDown = {
                                                if (actualIndex < localOrder.size - 1) {
                                                    val newOrder = localOrder.toMutableList()
                                                    val temp = newOrder[actualIndex + 1]
                                                    newOrder[actualIndex + 1] = newOrder[actualIndex]
                                                    newOrder[actualIndex] = temp
                                                    localOrder = newOrder
                                                }
                                            }
                                        )
                                    }
                                    displayIndex++
                                }
                            }
                        }

                        // 訪問先がないグループ
                        if (groupsWithoutItems.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "訪問先がないグループ（スキップされます）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            groupsWithoutItems.forEach { groupId ->
                                item(key = "inactive_$groupId") {
                                    InactiveGroupItem(
                                        groupId = groupId,
                                        halls = halls
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // フッター
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 実行列並び替えボタン
                    val hasActiveGroups = localOrder.any { getGroupItemCount(it) > 0 }
                    if (hasActiveGroups) {
                        Button(
                            onClick = {
                                // 保存してから並び替え
                                onSave(localOrder)
                                onReorderExecuteList()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B)  // amber
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("実行列を並び替え", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onClose) {
                            Text("キャンセル")
                        }
                        Button(
                            onClick = {
                                onSave(localOrder)
                                onClose()
                            }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 訪問先ありグループの行
 */
@Composable
private fun GroupOrderItem(
    groupId: String,
    halls: List<HallDefinition>,
    displayIndex: Int,
    itemCount: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val (hallId, priority) = parseGroupId(groupId)
    val displayName = getGroupDisplayName(groupId, halls)
    val badgeColor = getGroupBadgeColor(priority, hallId, halls)
    val bgColor = when (priority) {
        PriorityLevel.HIGHEST -> Color(0xFFFEF2F2)  // red-50
        PriorityLevel.PRIORITY -> Color(0xFFFFF7ED)  // orange-50
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    val borderColor = when (priority) {
        PriorityLevel.HIGHEST -> Color(0xFFFECACA)  // red-200
        PriorityLevel.PRIORITY -> Color(0xFFFED7AA)  // orange-200
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 順番バッジ
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$displayIndex",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // グループ名と件数
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${itemCount}件の訪問先",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ▲▼ボタン
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "上へ",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "下へ",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 訪問先なしグループの行（グレーアウト）
 */
@Composable
private fun InactiveGroupItem(
    groupId: String,
    halls: List<HallDefinition>
) {
    val (hallId, priority) = parseGroupId(groupId)
    val displayName = getGroupDisplayName(groupId, halls)
    val badgeColor = getGroupBadgeColor(priority, hallId, halls)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.5f)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * グループの表示名を取得
 */
private fun getGroupDisplayName(groupId: String, halls: List<HallDefinition>): String {
    val (hallId, priority) = parseGroupId(groupId)
    val hallName = if (hallId != null) {
        halls.find { it.id == hallId }?.name ?: "ホール未定義"
    } else {
        "ホール未定義"
    }

    return when (priority) {
        PriorityLevel.HIGHEST -> "${hallName} - 最優先"
        PriorityLevel.PRIORITY -> "${hallName} - 優先"
        PriorityLevel.NONE -> hallName
    }
}

/**
 * グループのバッジ色を取得
 */
private fun getGroupBadgeColor(
    priority: PriorityLevel,
    hallId: String?,
    halls: List<HallDefinition>
): Color {
    return when (priority) {
        PriorityLevel.HIGHEST -> Color(0xFFEF4444)  // 赤
        PriorityLevel.PRIORITY -> Color(0xFFF97316)  // オレンジ
        PriorityLevel.NONE -> {
            val hall = halls.find { it.id == hallId }
            if (hall != null) {
                try {
                    Color(hall.color)
                } catch (e: Exception) {
                    Color(0xFF9CA3AF)  // グレー
                }
            } else {
                Color(0xFF9CA3AF)  // グレー
            }
        }
    }
}