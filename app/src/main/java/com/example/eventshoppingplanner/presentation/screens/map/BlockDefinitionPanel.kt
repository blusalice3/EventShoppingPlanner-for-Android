package com.example.eventshoppingplanner.presentation.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.eventshoppingplanner.domain.model.BlockDefinition
import com.example.eventshoppingplanner.domain.model.DayMapData
import com.example.eventshoppingplanner.domain.model.NumberCellInfo

/**
 * ブロック定義パネル
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDefinitionPanel(
    isOpen: Boolean,
    onClose: () -> Unit,
    mapData: DayMapData,
    selectedCells: List<Pair<Int, Int>>,
    onStartCellSelection: () -> Unit,
    onCancelCellSelection: () -> Unit,
    onUpdateBlocks: (List<BlockDefinition>) -> Unit,
    isInSelectionMode: Boolean
) {
    if (!isOpen) return

    var blocks by remember(mapData.blocks) { mutableStateOf(mapData.blocks) }
    var selectedBlockIndex by remember { mutableStateOf<Int?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<BlockDefinition?>(null) }
    var sortAscending by remember { mutableStateOf(true) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // ソート済みブロック
    val sortedBlocks = remember(blocks, sortAscending) {
        if (sortAscending) {
            blocks.sortedBy { it.name }
        } else {
            blocks.sortedByDescending { it.name }
        }
    }

    // セル選択の結果を反映
    LaunchedEffect(selectedCells) {
        if (selectedCells.size == 4 && editingBlock != null) {
            val rows = selectedCells.map { it.first }
            val cols = selectedCells.map { it.second }
            val startRow = rows.minOrNull() ?: 0
            val endRow = rows.maxOrNull() ?: 0
            val startCol = cols.minOrNull() ?: 0
            val endCol = cols.maxOrNull() ?: 0

            // 範囲内の数値セルを検出
            val numberCells = detectNumberCells(mapData, startRow, startCol, endRow, endCol)

            editingBlock = editingBlock?.copy(
                startRow = startRow,
                startCol = startCol,
                endRow = endRow,
                endCol = endCol,
                numberCells = numberCells
            )
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isInSelectionMode) {
                onClose()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isInSelectionMode,
            dismissOnClickOutside = !isInSelectionMode
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ヘッダー
                TopAppBar(
                    title = { Text("ブロック定義") },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isInSelectionMode) {
                                onCancelCellSelection()
                            } else {
                                // 変更を保存して閉じる
                                onUpdateBlocks(blocks)
                                onClose()
                            }
                        }) {
                            Icon(
                                if (isInSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                                contentDescription = if (isInSelectionMode) "キャンセル" else "閉じる"
                            )
                        }
                    },
                    actions = {
                        if (!isInSelectionMode) {
                            // ソート切り替え
                            IconButton(onClick = { sortAscending = !sortAscending }) {
                                Icon(
                                    if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = "ソート"
                                )
                            }
                        }
                    }
                )

                if (isInSelectionMode) {
                    // セル選択モード中の表示
                    CellSelectionModeContent(
                        selectedCells = selectedCells,
                        onCancel = onCancelCellSelection
                    )
                } else if (editingBlock != null) {
                    // ブロック編集中
                    BlockEditContent(
                        block = editingBlock!!,
                        isNew = isAddingNew,
                        selectedCells = selectedCells,
                        onBlockChange = { editingBlock = it },
                        onStartCellSelection = onStartCellSelection,
                        onSave = { block ->
                            if (isAddingNew) {
                                blocks = blocks + block
                            } else {
                                blocks = blocks.map { if (it.id == block.id) block else it }
                            }
                            editingBlock = null
                            isAddingNew = false
                            selectedBlockIndex = null
                        },
                        onCancel = {
                            editingBlock = null
                            isAddingNew = false
                        }
                    )
                } else {
                    // ブロック一覧
                    BlockListContent(
                        blocks = sortedBlocks,
                        selectedIndex = selectedBlockIndex,
                        onSelectBlock = { index ->
                            selectedBlockIndex = index
                            editingBlock = sortedBlocks[index].copy()
                            isAddingNew = false
                        },
                        onAddNew = {
                            isAddingNew = true
                            selectedBlockIndex = null
                            editingBlock = BlockDefinition(
                                name = "",
                                startRow = 0,
                                startCol = 0,
                                endRow = 0,
                                endCol = 0,
                                isAutoDetected = false
                            )
                        },
                        onDeleteBlock = { block ->
                            blocks = blocks.filter { it.id != block.id }
                        },
                        onDeleteAll = {
                            showDeleteAllConfirm = true
                        }
                    )
                }
            }
        }
    }

    // 全削除確認ダイアログ
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("全ブロック削除") },
            text = { Text("全てのブロック定義を削除しますか？\nこの操作は取り消せません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        blocks = emptyList()
                        showDeleteAllConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

/**
 * セル選択モード中の表示
 */
@Composable
private fun CellSelectionModeContent(
    selectedCells: List<Pair<Int, Int>>,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.TouchApp,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "マップ上で4つの角をタップ",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "選択済み: ${selectedCells.size}/4",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (selectedCells.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            selectedCells.forEachIndexed { index, (row, col) ->
                Text(
                    text = "角${index + 1}: 行$row, 列$col",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(onClick = onCancel) {
            Text("キャンセル")
        }
    }
}

/**
 * ブロック編集コンテンツ
 */
@Composable
private fun BlockEditContent(
    block: BlockDefinition,
    isNew: Boolean,
    selectedCells: List<Pair<Int, Int>>,
    onBlockChange: (BlockDefinition) -> Unit,
    onStartCellSelection: () -> Unit,
    onSave: (BlockDefinition) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (isNew) "新規ブロック追加" else "ブロック編集",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ブロック名
        OutlinedTextField(
            value = block.name,
            onValueChange = { onBlockChange(block.copy(name = it)) },
            label = { Text("ブロック名") },
            placeholder = { Text("例: A, B, 東1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 範囲選択
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "範囲指定",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (block.startRow > 0 && block.endRow > 0) {
                    Text(
                        text = "行: ${block.startRow} ～ ${block.endRow}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "列: ${block.startCol} ～ ${block.endCol}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (block.numberCells.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "検出された番号: ${block.numberCells.size}件 (${block.numberCells.minOfOrNull { it.value } ?: 0}～${block.numberCells.maxOfOrNull { it.value } ?: 0})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "マップ上で4つの角をタップして範囲を指定してください",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onStartCellSelection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (block.startRow > 0) "範囲を再選択" else "範囲を選択")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 手動入力（折りたたみ）
        var showManualInput by remember { mutableStateOf(false) }

        OutlinedCard(
            onClick = { showManualInput = !showManualInput },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("手動で座標を入力")
                Icon(
                    if (showManualInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (showManualInput) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = if (block.startRow > 0) block.startRow.toString() else "",
                            onValueChange = {
                                val value = it.toIntOrNull() ?: 0
                                onBlockChange(block.copy(startRow = value))
                            },
                            label = { Text("開始行") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = if (block.startCol > 0) block.startCol.toString() else "",
                            onValueChange = {
                                val value = it.toIntOrNull() ?: 0
                                onBlockChange(block.copy(startCol = value))
                            },
                            label = { Text("開始列") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = if (block.endRow > 0) block.endRow.toString() else "",
                            onValueChange = {
                                val value = it.toIntOrNull() ?: 0
                                onBlockChange(block.copy(endRow = value))
                            },
                            label = { Text("終了行") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = if (block.endCol > 0) block.endCol.toString() else "",
                            onValueChange = {
                                val value = it.toIntOrNull() ?: 0
                                onBlockChange(block.copy(endCol = value))
                            },
                            label = { Text("終了列") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ボタン
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("キャンセル")
            }

            Button(
                onClick = { onSave(block) },
                modifier = Modifier.weight(1f),
                enabled = block.name.isNotBlank() && block.startRow > 0 && block.endRow > 0
            ) {
                Text("保存")
            }
        }
    }
}

/**
 * ブロック一覧コンテンツ
 */
@Composable
private fun BlockListContent(
    blocks: List<BlockDefinition>,
    selectedIndex: Int?,
    onSelectBlock: (Int) -> Unit,
    onAddNew: () -> Unit,
    onDeleteBlock: (BlockDefinition) -> Unit,
    onDeleteAll: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // アクションバー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "定義済み: ${blocks.size}件",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDeleteAll,
                    enabled = blocks.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("全削除")
                }

                Button(onClick = onAddNew) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新規")
                }
            }
        }

        Divider()

        if (blocks.isEmpty()) {
            // 空状態
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
                        Icons.Default.GridOn,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ブロックがありません",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "「新規」ボタンでブロックを追加してください",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(blocks) { index, block ->
                    BlockListItem(
                        block = block,
                        isSelected = selectedIndex == index,
                        onClick = { onSelectBlock(index) },
                        onDelete = { onDeleteBlock(block) }
                    )
                }
            }
        }
    }
}

/**
 * ブロックリストアイテム
 */
@Composable
private fun BlockListItem(
    block: BlockDefinition,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // ブロック色表示
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(block.color))
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = block.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (block.isAutoDetected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "自動",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "行: ${block.startRow}-${block.endRow}, 列: ${block.startCol}-${block.endCol}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (block.numberCells.isNotEmpty()) {
                        Text(
                            text = "番号: ${block.numberCells.minOfOrNull { it.value }}～${block.numberCells.maxOfOrNull { it.value }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 削除確認ダイアログ
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("ブロック削除") },
            text = { Text("「${block.name}」を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

/**
 * 指定範囲内の数値セルを検出
 */
private fun detectNumberCells(
    mapData: DayMapData,
    startRow: Int,
    startCol: Int,
    endRow: Int,
    endCol: Int
): List<NumberCellInfo> {
    val cells = mutableListOf<NumberCellInfo>()
    val minRow = minOf(startRow, endRow)
    val maxRow = maxOf(startRow, endRow)
    val minCol = minOf(startCol, endCol)
    val maxCol = maxOf(startCol, endCol)

    mapData.cells.forEach { cell ->
        if (cell.row in minRow..maxRow && cell.col in minCol..maxCol) {
            if (!cell.isMerged && cell.value != null) {
                val numValue = cell.value.toIntOrNull()
                if (numValue != null && numValue in 1..100) {
                    cells.add(NumberCellInfo(cell.row, cell.col, numValue))
                }
            }
        }
    }

    return cells.sortedBy { it.value }
}