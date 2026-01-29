package com.example.eventshoppingplanner.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import sh.calvin.reorderable.ReorderableCollectionItemScope

// ブロック別の背景色
private val BlockColors = listOf(
    Color(0xFFE3F2FD),
    Color(0xFFE8F5E9),
    Color(0xFFFFF3E0),
    Color(0xFFF3E5F5),
    Color(0xFFE0F7FA),
    Color(0xFFFBE9E7),
    Color(0xFFF1F8E9),
    Color(0xFFFCE4EC),
    Color(0xFFE8EAF6),
    Color(0xFFFFFDE7),
)

private fun getBlockColor(block: String): Color {
    val index = kotlin.math.abs(block.hashCode()) % BlockColors.size
    return BlockColors[index]
}

// 警告タグの種類
private enum class WarningTag(
    val label: String,
    val backgroundColor: Color,
    val textColor: Color,
    val icon: ImageVector
) {
    MULTIPLE("複数種", Color(0xFF9C27B0), Color.White, Icons.Default.ContentCopy),
    PRIORITY("優先", Color(0xFFFFEB3B), Color.Black, Icons.Default.PriorityHigh),
    NO_CONSIGNMENT("委託無", Color(0xFFF44336), Color.White, Icons.Default.Warning)
}

@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    onStatusClick: () -> Unit,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = false,
    reorderableScope: ReorderableCollectionItemScope? = null,
    onPriceChange: ((Int?) -> Unit)? = null,
    isDuplicateCircle: Boolean = false
) {
    val context = LocalContext.current
    var priceDropdownExpanded by remember { mutableStateOf(false) }

    // 警告タグを判定
    val warningTags = remember(item.remarks, isDuplicateCircle) {
        buildList {
            if (isDuplicateCircle) add(WarningTag.MULTIPLE)
            if (item.remarks.contains("優先")) add(WarningTag.PRIORITY)
            if (item.remarks.contains("委託無")) add(WarningTag.NO_CONSIGNMENT)
        }
    }
    val hasWarnings = warningTags.isNotEmpty()

    val backgroundColor = when (item.purchaseStatus) {
        PurchaseStatus.NONE -> getBlockColor(item.block)
        PurchaseStatus.PURCHASED -> Color(0xFFE8F5E9)
        PurchaseStatus.SOLD_OUT -> Color(0xFFFFEBEE)
        PurchaseStatus.ABSENT -> Color(0xFFFFFDE7)
        PurchaseStatus.POSTPONE -> Color(0xFFF3E5F5)
        PurchaseStatus.LATE -> Color(0xFFE3F2FD)
    }

    val isCompleted = item.purchaseStatus in listOf(
        PurchaseStatus.PURCHASED,
        PurchaseStatus.SOLD_OUT,
        PurchaseStatus.ABSENT
    )

    // 価格表示（nullの場合は「価格未定」を赤色で表示）
    val isPriceUndefined = item.price == null
    val priceText = if (isPriceUndefined) "価格未定" else "¥${item.price}"
    val priceColor = if (isPriceUndefined) Color(0xFFF44336) else Color.Unspecified

    // 価格オプション（0円〜10000円、100円刻み + 価格未定）
    val priceOptions = remember {
        listOf<Int?>(null) + (0..100).map { it * 100 }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(
                if (hasWarnings && !isCompleted) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color(0xFFFF9800),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // 警告タグ表示（上部に横並び）- 完了状態でないときのみ表示
            if (hasWarnings && !isCompleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    warningTags.forEach { tag ->
                        WarningBadge(tag = tag)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .clickable(onClick = onItemClick)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ドラッグハンドル（編集モード時のみ表示）
                if (showDragHandle && reorderableScope != null) {
                    with(reorderableScope) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "ドラッグして並び替え",
                            modifier = Modifier
                                .size(24.dp)
                                .draggableHandle(),
                            tint = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // メインコンテンツ
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // 参加日・場所 + URLリンク
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.eventDate} ${item.locationDisplay}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // URLリンクボタン
                        if (!item.url.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "URLを開く",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF2196F3)
                                )
                            }
                        }
                    }

                    Text(
                        text = item.circle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = item.title.ifEmpty { "（タイトルなし）" },
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCompleted) Color.Gray else Color.Unspecified
                    )

                    if (item.remarks.isNotBlank()) {
                        Text(
                            text = "📝 ${item.remarks}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 価格（ドロップダウン選択可能）
                Box {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        if (onPriceChange != null) {
                            // 価格変更可能（クリックでドロップダウン表示）
                            TextButton(
                                onClick = { priceDropdownExpanded = true }
                            ) {
                                Text(
                                    text = priceText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = priceColor
                                )
                            }
                        } else {
                            // 価格表示のみ
                            Text(
                                text = priceText,
                                style = MaterialTheme.typography.titleMedium,
                                color = priceColor
                            )
                        }

                        // 数量が1より大きい場合は表示
                        if (item.quantity > 1) {
                            Text(
                                text = "×${item.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 価格ドロップダウンメニュー
                    DropdownMenu(
                        expanded = priceDropdownExpanded,
                        onDismissRequest = { priceDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "価格未定",
                                    color = Color(0xFFF44336)
                                )
                            },
                            onClick = {
                                onPriceChange?.invoke(null)
                                priceDropdownExpanded = false
                            }
                        )
                        priceOptions.filterNotNull().forEach { price ->
                            DropdownMenuItem(
                                text = { Text("¥${price}") },
                                onClick = {
                                    onPriceChange?.invoke(price)
                                    priceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 購入状態ボタン
                StatusButton(
                    status = item.purchaseStatus,
                    onClick = onStatusClick
                )
            }
        }
    }
}

@Composable
private fun WarningBadge(tag: WarningTag) {
    Row(
        modifier = Modifier
            .background(
                color = tag.backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = tag.icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tag.textColor
        )
        Text(
            text = tag.label,
            color = tag.textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatusButton(
    status: PurchaseStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (status) {
        PurchaseStatus.NONE -> Icons.Default.RadioButtonUnchecked to Color.Gray
        PurchaseStatus.PURCHASED -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        PurchaseStatus.SOLD_OUT -> Icons.Default.Cancel to Color(0xFFF44336)
        PurchaseStatus.ABSENT -> Icons.Default.RemoveCircle to Color(0xFFFF9800)
        PurchaseStatus.POSTPONE -> Icons.Default.PauseCircle to Color(0xFF9C27B0)
        PurchaseStatus.LATE -> Icons.Default.Schedule to Color(0xFF2196F3)
    }

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = status.displayName,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
    }
}