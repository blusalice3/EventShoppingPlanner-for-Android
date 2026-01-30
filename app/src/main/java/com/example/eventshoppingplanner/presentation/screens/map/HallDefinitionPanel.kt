package com.example.eventshoppingplanner.presentation.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.eventshoppingplanner.domain.model.BlockDefinition
import com.example.eventshoppingplanner.domain.model.DayMapData
import com.example.eventshoppingplanner.domain.model.HallDefinition
import com.example.eventshoppingplanner.domain.model.Vertex
import com.example.eventshoppingplanner.util.HallUtils
import java.util.UUID

/**
 * ホール定義パネル
 */
@Composable
fun HallDefinitionPanel(
    mapData: DayMapData,
    halls: List<HallDefinition>,
    pendingEditState: HallEditState?,
    pendingVertices: List<Vertex>,
    onDismiss: () -> Unit,
    onSaveHalls: (List<HallDefinition>) -> Unit,
    onStartVertexSelection: (String?, HallEditState) -> Unit,
    onConsumePendingEditState: () -> HallEditState?
) {
    // ローカルで編集中のホールリスト
    var localHalls by remember(halls) { mutableStateOf(halls) }

    // 編集中のホール
    var editingHall by remember { mutableStateOf<HallDefinition?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    // 編集フォームの状態
    var hallName by remember { mutableStateOf("") }
    var hallVertices by remember { mutableStateOf<List<Vertex>>(emptyList()) }
    var hallColor by remember { mutableStateOf(HallUtils.HALL_COLORS.first()) }

    // 頂点選択から戻ってきた時の処理
    LaunchedEffect(pendingEditState, pendingVertices) {
        // pendingEditStateがnullでなければ編集状態を復元
        if (pendingEditState != null) {
            val consumed = onConsumePendingEditState()
            if (consumed != null) {
                editingHall = consumed.editingHall
                isAddingNew = consumed.isAddingNew
                localHalls = consumed.currentHalls

                // 編集中のホールの情報を復元
                consumed.editingHall?.let { hall ->
                    hallName = hall.name
                    hallColor = hall.color
                }
            }
        }

        // 頂点が選択されていれば反映（pendingEditStateがnullでも）
        if (pendingVertices.isNotEmpty()) {
            hallVertices = pendingVertices
        }
    }

    // 新規作成開始
    fun startNewHall() {
        editingHall = null
        isAddingNew = true
        hallName = ""
        hallVertices = emptyList()
        hallColor = HallUtils.getNextHallColor(localHalls)
    }

    // 編集開始
    fun startEditHall(hall: HallDefinition) {
        editingHall = hall
        isAddingNew = false
        hallName = hall.name
        hallVertices = hall.vertices
        hallColor = hall.color
    }

    // 編集キャンセル
    fun cancelEdit() {
        editingHall = null
        isAddingNew = false
        hallName = ""
        hallVertices = emptyList()
    }

    // ホールを保存（ローカル）
    fun saveHallLocally() {
        if (hallName.isBlank() || hallVertices.size < 4) return

        val newHall = HallDefinition(
            id = editingHall?.id ?: UUID.randomUUID().toString(),
            name = hallName.trim(),
            vertices = hallVertices,
            color = hallColor
        )

        // isAddingNewフラグで新規追加か更新かを判断
        localHalls = if (isAddingNew) {
            // 新規追加
            localHalls + newHall
        } else {
            // 更新
            localHalls.map { if (it.id == newHall.id) newHall else it }
        }

        cancelEdit()
    }

    // ホールを削除（ローカル）
    fun deleteHallLocally(hallId: String) {
        localHalls = localHalls.filter { it.id != hallId }
        if (editingHall?.id == hallId) {
            cancelEdit()
        }
    }

    // ホール内のブロックを取得
    fun getBlocksInHall(hall: HallDefinition): List<BlockDefinition> {
        return HallUtils.getBlocksInHall(hall, mapData.blocks)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ヘッダー
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ホール定義",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる")
                    }
                }

                HorizontalDivider()

                // メインコンテンツ
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    // 左パネル: ホール一覧
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "定義済みホール",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(localHalls) { hall ->
                                HallListItem(
                                    hall = hall,
                                    isSelected = editingHall?.id == hall.id,
                                    onClick = { startEditHall(hall) },
                                    onDelete = { deleteHallLocally(hall.id) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 新規追加ボタン
                        OutlinedButton(
                            onClick = { startNewHall() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAddingNew && editingHall == null
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新規")
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    VerticalDivider()

                    Spacer(modifier = Modifier.width(16.dp))

                    // 右パネル: 編集フォーム
                    Column(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                    ) {
                        if (isAddingNew || editingHall != null) {
                            HallEditForm(
                                hallName = hallName,
                                onHallNameChange = { hallName = it },
                                hallVertices = hallVertices,
                                hallColor = hallColor,
                                onHallColorChange = { hallColor = it },
                                blocksInHall = if (hallVertices.size >= 4) {
                                    HallUtils.getBlocksInHall(
                                        HallDefinition(
                                            id = "",
                                            name = "",
                                            vertices = hallVertices,
                                            color = hallColor
                                        ),
                                        mapData.blocks
                                    )
                                } else emptyList(),
                                onStartVertexSelection = {
                                    val editState = HallEditState(
                                        editingHall = if (isAddingNew) {
                                            HallDefinition(
                                                id = UUID.randomUUID().toString(),
                                                name = hallName,
                                                vertices = hallVertices,
                                                color = hallColor
                                            )
                                        } else editingHall?.copy(
                                            name = hallName,
                                            vertices = hallVertices,
                                            color = hallColor
                                        ),
                                        isAddingNew = isAddingNew,
                                        currentHalls = localHalls
                                    )
                                    onStartVertexSelection(editingHall?.id, editState)
                                },
                                onSave = { saveHallLocally() },
                                onCancel = { cancelEdit() },
                                canSave = hallName.isNotBlank() && hallVertices.size >= 4
                            )
                        } else {
                            // 未選択時
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ホールを選択するか、新規作成してください",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // フッター
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSaveHalls(localHalls)
                            onDismiss()
                        }
                    ) {
                        Text("適用")
                    }
                }
            }
        }
    }
}

/**
 * ホール一覧アイテム
 */
@Composable
private fun HallListItem(
    hall: HallDefinition,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 色インジケータ
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(hall.color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hall.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${hall.vertices.size}頂点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * ホール編集フォーム
 */
@Composable
private fun HallEditForm(
    hallName: String,
    onHallNameChange: (String) -> Unit,
    hallVertices: List<Vertex>,
    hallColor: Long,
    onHallColorChange: (Long) -> Unit,
    blocksInHall: List<BlockDefinition>,
    onStartVertexSelection: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    canSave: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "ホール編集",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // ホール名
        OutlinedTextField(
            value = hallName,
            onValueChange = onHallNameChange,
            label = { Text("ホール名") },
            placeholder = { Text("例: 東ホール") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 頂点選択
        Column {
            Text(
                text = "エリア定義",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hallVertices.isEmpty()) {
                        "未選択"
                    } else {
                        "${hallVertices.size}頂点選択済"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hallVertices.size >= 4) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onStartVertexSelection
                ) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("頂点を選択...")
                }
            }

            if (hallVertices.size < 4 && hallVertices.isNotEmpty()) {
                Text(
                    text = "※ 4個以上の頂点が必要です",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // 色選択
        Column {
            Text(
                text = "色",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(HallUtils.HALL_COLORS) { color ->
                    ColorChip(
                        color = color,
                        isSelected = color == hallColor,
                        onClick = { onHallColorChange(color) }
                    )
                }
            }
        }

        // 含まれるブロック
        if (blocksInHall.isNotEmpty()) {
            Column {
                Text(
                    text = "含まれるブロック",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = blocksInHall.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 保存・キャンセルボタン
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("キャンセル")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = canSave
            ) {
                Text("追加")
            }
        }
    }
}

/**
 * 色選択チップ
 */
@Composable
private fun ColorChip(
    color: Long,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "選択中",
                tint = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}