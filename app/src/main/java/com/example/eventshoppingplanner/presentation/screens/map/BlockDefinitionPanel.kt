package com.example.eventshoppingplanner.presentation.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.eventshoppingplanner.domain.model.BlockDefinition
import com.example.eventshoppingplanner.domain.model.CellGroup
import com.example.eventshoppingplanner.domain.model.CellGroupType
import com.example.eventshoppingplanner.domain.model.DayMapData
import com.example.eventshoppingplanner.domain.model.NumberCellInfo

/**
 * ブロック色の定義
 */
private val BLOCK_COLORS = listOf(
    0xFFE3F2FD, 0xFFE8F5E9, 0xFFFFF3E0, 0xFFF3E5F5, 0xFFE0F7FA,
    0xFFFBE9E7, 0xFFF1F8E9, 0xFFFCE4EC, 0xFFE8EAF6, 0xFFFFFDE7,
    0xFFEFEBE9, 0xFFECEFF1
)

/**
 * 編集モード
 */
enum class EditMode {
    NORMAL,  // 通常（4角選択）
    MULTI,   // 複数範囲
    WALL     // 壁ブロック
}

/**
 * セル選択タイプ
 */
enum class CellSelectionType {
    CORNER,        // 通常モードの4角選択
    MULTI_CORNER,  // 複数範囲モードの4角選択
    RANGE_START,   // 壁ブロックの範囲選択（2点）
    INDIVIDUAL     // 壁ブロックの個別セル選択
}

/**
 * 範囲情報
 */
data class MultiRange(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
)

/**
 * ブロック定義パネル
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDefinitionPanel(
    isOpen: Boolean,
    isVisible: Boolean,
    onClose: () -> Unit,
    mapData: DayMapData,
    selectedCells: List<Pair<Int, Int>>,
    onStartCellSelection: (CellSelectionType, BlockEditState) -> Unit,
    onCancelCellSelection: () -> Unit,
    onUpdateBlocks: (List<BlockDefinition>) -> Unit,
    isInSelectionMode: Boolean,
    currentSelectionType: CellSelectionType?,
    pendingEditState: BlockEditState?,
    onClearPendingEditState: () -> Unit
) {
    // 状態（isOpenがtrueの間は保持される）
    var blocks by remember(mapData.blocks) { mutableStateOf(mapData.blocks) }
    var selectedBlockIndex by remember { mutableStateOf<Int?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<BlockDefinition?>(null) }
    var sortAscending by remember { mutableStateOf(true) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // 編集モード関連
    var editMode by remember { mutableStateOf(EditMode.NORMAL) }
    var wallCellGroups by remember { mutableStateOf<List<CellGroup>>(emptyList()) }
    var multiRanges by remember { mutableStateOf<List<MultiRange>>(emptyList()) }

    // pendingEditStateから状態を復元
    LaunchedEffect(pendingEditState, isVisible) {
        if (isVisible && pendingEditState != null) {
            editingBlock = pendingEditState.editingBlock
            isAddingNew = pendingEditState.isAddingNew
            editMode = pendingEditState.editMode
            wallCellGroups = pendingEditState.wallCellGroups
            multiRanges = pendingEditState.multiRanges
            blocks = pendingEditState.currentBlocks
            onClearPendingEditState()
        }
    }

    // isOpenがfalseなら何もしない
    if (!isOpen) return

    // isVisibleがfalseなら表示しない（でも状態は保持）
    if (!isVisible) return

    // ソート済みブロック
    val sortedBlocks = remember(blocks, sortAscending) {
        if (sortAscending) blocks.sortedBy { it.name }
        else blocks.sortedByDescending { it.name }
    }

    // セル選択の結果を反映
    LaunchedEffect(selectedCells, currentSelectionType) {
        if (selectedCells.isEmpty() || editingBlock == null) return@LaunchedEffect

        when (currentSelectionType) {
            CellSelectionType.CORNER -> {
                if (selectedCells.size >= 4) {
                    val rows = selectedCells.map { it.first }
                    val cols = selectedCells.map { it.second }
                    val startRow = rows.minOrNull() ?: 0
                    val endRow = rows.maxOrNull() ?: 0
                    val startCol = cols.minOrNull() ?: 0
                    val endCol = cols.maxOrNull() ?: 0
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
            CellSelectionType.MULTI_CORNER -> {
                if (selectedCells.size >= 4) {
                    val rows = selectedCells.map { it.first }
                    val cols = selectedCells.map { it.second }
                    val newRange = MultiRange(
                        startRow = rows.minOrNull() ?: 0,
                        startCol = cols.minOrNull() ?: 0,
                        endRow = rows.maxOrNull() ?: 0,
                        endCol = cols.maxOrNull() ?: 0
                    )
                    multiRanges = multiRanges + newRange
                }
            }
            CellSelectionType.RANGE_START -> {
                if (selectedCells.size >= 2 && wallCellGroups.size < 6) {
                    val (start, end) = selectedCells.take(2)
                    val newGroup = CellGroup(
                        type = CellGroupType.RANGE,
                        startRow = minOf(start.first, end.first),
                        startCol = minOf(start.second, end.second),
                        endRow = maxOf(start.first, end.first),
                        endCol = maxOf(start.second, end.second)
                    )
                    wallCellGroups = wallCellGroups + newGroup
                }
            }
            CellSelectionType.INDIVIDUAL -> {
                if (selectedCells.isNotEmpty() && wallCellGroups.size < 6) {
                    val newGroup = CellGroup(
                        type = CellGroupType.INDIVIDUAL,
                        cells = selectedCells.toList()
                    )
                    wallCellGroups = wallCellGroups + newGroup
                }
            }
            null -> {}
        }
    }

    // プレビュー用の数値セル
    val previewNumberCells = remember(editingBlock, editMode, wallCellGroups, multiRanges, mapData) {
        when (editMode) {
            EditMode.WALL -> {
                val all = mutableListOf<NumberCellInfo>()
                wallCellGroups.forEach { g ->
                    if (g.type == CellGroupType.RANGE && g.startRow > 0) {
                        all.addAll(detectNumberCells(mapData, g.startRow, g.startCol, g.endRow, g.endCol))
                    } else if (g.type == CellGroupType.INDIVIDUAL) {
                        g.cells.forEach { (r, c) ->
                            mapData.cells.find { it.row == r && it.col == c }?.let { cell ->
                                if (!cell.isMerged && cell.value != null) {
                                    cell.value.toIntOrNull()?.let { num ->
                                        if (num in 1..100) all.add(NumberCellInfo(r, c, num))
                                    }
                                }
                            }
                        }
                    }
                }
                all.distinctBy { "${it.row}-${it.col}" }.sortedBy { it.value }
            }
            EditMode.MULTI -> {
                val all = mutableListOf<NumberCellInfo>()
                multiRanges.forEach { range ->
                    all.addAll(detectNumberCells(mapData, range.startRow, range.startCol, range.endRow, range.endCol))
                }
                all.distinctBy { "${it.row}-${it.col}" }.sortedBy { it.value }
            }
            EditMode.NORMAL -> {
                editingBlock?.let { block ->
                    if (block.startRow > 0 && block.endRow > 0) {
                        detectNumberCells(mapData, block.startRow, block.startCol, block.endRow, block.endCol)
                    } else emptyList()
                } ?: emptyList()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isInSelectionMode) {
                if (editingBlock != null) {
                    // 編集中なら編集をキャンセルして一覧に戻る
                    editingBlock = null
                    isAddingNew = false
                    wallCellGroups = emptyList()
                    multiRanges = emptyList()
                    editMode = EditMode.NORMAL
                } else {
                    onUpdateBlocks(blocks)
                    onClose()
                }
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
            Column(modifier = Modifier.fillMaxSize()) {
                // ヘッダー
                TopAppBar(
                    title = {
                        Text(
                            when {
                                editingBlock != null && isAddingNew -> "新規ブロック"
                                editingBlock != null -> "ブロック編集"
                                else -> "ブロック定義"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            when {
                                isInSelectionMode -> {
                                    onCancelCellSelection()
                                }
                                editingBlock != null -> {
                                    // 編集中なら編集をキャンセルして一覧に戻る
                                    editingBlock = null
                                    isAddingNew = false
                                    wallCellGroups = emptyList()
                                    multiRanges = emptyList()
                                    editMode = EditMode.NORMAL
                                }
                                else -> {
                                    onUpdateBlocks(blocks)
                                    onClose()
                                }
                            }
                        }) {
                            Icon(
                                when {
                                    isInSelectionMode -> Icons.Default.Close
                                    editingBlock != null -> Icons.Default.ArrowBack
                                    else -> Icons.Default.Close
                                },
                                contentDescription = when {
                                    isInSelectionMode -> "キャンセル"
                                    editingBlock != null -> "一覧に戻る"
                                    else -> "閉じる"
                                }
                            )
                        }
                    }
                )

                when {
                    isInSelectionMode -> {
                        // セル選択モード中
                        CellSelectionModeContent(
                            selectedCells = selectedCells,
                            selectionType = currentSelectionType,
                            onCancel = onCancelCellSelection
                        )
                    }
                    editingBlock != null -> {
                        // ブロック編集中
                        BlockEditContent(
                            block = editingBlock!!,
                            isNew = isAddingNew,
                            editMode = editMode,
                            wallCellGroups = wallCellGroups,
                            multiRanges = multiRanges,
                            previewNumberCells = previewNumberCells,
                            onBlockChange = { editingBlock = it },
                            onEditModeChange = { newMode ->
                                editMode = newMode
                                wallCellGroups = emptyList()
                                multiRanges = emptyList()
                                if (newMode != EditMode.NORMAL) {
                                    editingBlock = editingBlock?.copy(
                                        startRow = 0, startCol = 0, endRow = 0, endCol = 0
                                    )
                                }
                            },
                            onWallCellGroupsChange = { wallCellGroups = it },
                            onMultiRangesChange = { multiRanges = it },
                            onStartCellSelection = { selectionType ->
                                // 現在の編集状態を保存してセル選択を開始
                                onStartCellSelection(
                                    selectionType,
                                    BlockEditState(
                                        editingBlock = editingBlock,
                                        isAddingNew = isAddingNew,
                                        editMode = editMode,
                                        wallCellGroups = wallCellGroups,
                                        multiRanges = multiRanges,
                                        currentBlocks = blocks
                                    )
                                )
                            },
                            onSave = { block ->
                                val savedBlock = when (editMode) {
                                    EditMode.WALL -> {
                                        if (wallCellGroups.isEmpty()) {
                                            null
                                        } else {
                                            var minR = Int.MAX_VALUE; var minC = Int.MAX_VALUE
                                            var maxR = 0; var maxC = 0
                                            wallCellGroups.forEach { g ->
                                                if (g.type == CellGroupType.RANGE) {
                                                    minR = minOf(minR, g.startRow)
                                                    minC = minOf(minC, g.startCol)
                                                    maxR = maxOf(maxR, g.endRow)
                                                    maxC = maxOf(maxC, g.endCol)
                                                } else {
                                                    g.cells.forEach { (r, c) ->
                                                        minR = minOf(minR, r); minC = minOf(minC, c)
                                                        maxR = maxOf(maxR, r); maxC = maxOf(maxC, c)
                                                    }
                                                }
                                            }
                                            block.copy(
                                                startRow = minR, startCol = minC,
                                                endRow = maxR, endCol = maxC,
                                                numberCells = previewNumberCells,
                                                isAutoDetected = false,
                                                isWallBlock = true,
                                                cellGroups = wallCellGroups
                                            )
                                        }
                                    }
                                    EditMode.MULTI -> {
                                        if (multiRanges.isEmpty()) {
                                            null
                                        } else {
                                            var minR = Int.MAX_VALUE; var minC = Int.MAX_VALUE
                                            var maxR = 0; var maxC = 0
                                            multiRanges.forEach { r ->
                                                minR = minOf(minR, r.startRow)
                                                minC = minOf(minC, r.startCol)
                                                maxR = maxOf(maxR, r.endRow)
                                                maxC = maxOf(maxC, r.endCol)
                                            }
                                            val cellGroups = multiRanges.map { r ->
                                                CellGroup(
                                                    type = CellGroupType.RANGE,
                                                    startRow = r.startRow,
                                                    startCol = r.startCol,
                                                    endRow = r.endRow,
                                                    endCol = r.endCol
                                                )
                                            }
                                            block.copy(
                                                startRow = minR, startCol = minC,
                                                endRow = maxR, endCol = maxC,
                                                numberCells = previewNumberCells,
                                                isAutoDetected = false,
                                                isWallBlock = false,
                                                cellGroups = cellGroups
                                            )
                                        }
                                    }
                                    EditMode.NORMAL -> {
                                        if (block.startRow <= 0 || block.endRow <= 0) {
                                            null
                                        } else {
                                            block.copy(
                                                numberCells = previewNumberCells,
                                                isAutoDetected = false,
                                                isWallBlock = false,
                                                cellGroups = emptyList()
                                            )
                                        }
                                    }
                                }

                                if (savedBlock != null) {
                                    if (isAddingNew) {
                                        val existing = blocks.find { it.name == savedBlock.name }
                                        blocks = if (existing != null) {
                                            blocks.map { if (it.name == savedBlock.name) savedBlock else it }
                                        } else {
                                            blocks + savedBlock
                                        }
                                    } else {
                                        blocks = blocks.map { if (it.id == savedBlock.id) savedBlock else it }
                                    }
                                    editingBlock = null
                                    isAddingNew = false
                                    selectedBlockIndex = null
                                    wallCellGroups = emptyList()
                                    multiRanges = emptyList()
                                    editMode = EditMode.NORMAL
                                }
                            },
                            onCancel = {
                                editingBlock = null
                                isAddingNew = false
                                wallCellGroups = emptyList()
                                multiRanges = emptyList()
                                editMode = EditMode.NORMAL
                            }
                        )
                    }
                    else -> {
                        // ブロック一覧
                        BlockListContent(
                            blocks = sortedBlocks,
                            selectedIndex = selectedBlockIndex,
                            sortAscending = sortAscending,
                            onSortToggle = { sortAscending = !sortAscending },
                            onSelectBlock = { index ->
                                selectedBlockIndex = index
                                val b = sortedBlocks[index]
                                editingBlock = b.copy()
                                isAddingNew = false
                                when {
                                    b.isWallBlock -> {
                                        editMode = EditMode.WALL
                                        wallCellGroups = b.cellGroups
                                        multiRanges = emptyList()
                                    }
                                    b.cellGroups.isNotEmpty() -> {
                                        editMode = EditMode.MULTI
                                        wallCellGroups = emptyList()
                                        multiRanges = b.cellGroups.filter { it.type == CellGroupType.RANGE }.map {
                                            MultiRange(it.startRow, it.startCol, it.endRow, it.endCol)
                                        }
                                    }
                                    else -> {
                                        editMode = EditMode.NORMAL
                                        wallCellGroups = emptyList()
                                        multiRanges = emptyList()
                                    }
                                }
                            },
                            onAddNew = {
                                isAddingNew = true
                                selectedBlockIndex = null
                                editMode = EditMode.NORMAL
                                editingBlock = BlockDefinition(
                                    name = "",
                                    startRow = 0,
                                    startCol = 0,
                                    endRow = 0,
                                    endCol = 0,
                                    color = BLOCK_COLORS[blocks.size % BLOCK_COLORS.size],
                                    isAutoDetected = false
                                )
                                wallCellGroups = emptyList()
                                multiRanges = emptyList()
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
    }

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

@Composable
private fun CellSelectionModeContent(
    selectedCells: List<Pair<Int, Int>>,
    selectionType: CellSelectionType?,
    onCancel: () -> Unit
) {
    val (title, requiredCount) = when (selectionType) {
        CellSelectionType.CORNER, CellSelectionType.MULTI_CORNER -> "4つの角をタップ" to 4
        CellSelectionType.RANGE_START -> "範囲の開始点と終了点をタップ" to 2
        CellSelectionType.INDIVIDUAL -> "セルをタップして選択（完了したら確定）" to 1
        null -> "セルを選択" to 4
    }

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
        Text(text = "マップ上で$title", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "選択済み: ${selectedCells.size}/${if (selectionType == CellSelectionType.INDIVIDUAL) "∞" else requiredCount}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (selectedCells.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            selectedCells.take(8).forEachIndexed { index, (row, col) ->
                Text(text = "${index + 1}: 行$row, 列$col", style = MaterialTheme.typography.bodySmall)
            }
            if (selectedCells.size > 8) {
                Text(
                    text = "... 他${selectedCells.size - 8}件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) { Text("キャンセル") }
    }
}

@Composable
private fun BlockEditContent(
    block: BlockDefinition,
    isNew: Boolean,
    editMode: EditMode,
    wallCellGroups: List<CellGroup>,
    multiRanges: List<MultiRange>,
    previewNumberCells: List<NumberCellInfo>,
    onBlockChange: (BlockDefinition) -> Unit,
    onEditModeChange: (EditMode) -> Unit,
    onWallCellGroupsChange: (List<CellGroup>) -> Unit,
    onMultiRangesChange: (List<MultiRange>) -> Unit,
    onStartCellSelection: (CellSelectionType) -> Unit,
    onSave: (BlockDefinition) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isNew) "新規追加" else "編集",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = editMode == EditMode.NORMAL,
                    onClick = { onEditModeChange(EditMode.NORMAL) },
                    label = { Text("通常", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = editMode == EditMode.MULTI,
                    onClick = { onEditModeChange(EditMode.MULTI) },
                    label = { Text("複数範囲", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = editMode == EditMode.WALL,
                    onClick = { onEditModeChange(EditMode.WALL) },
                    label = { Text("壁", fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = block.name,
            onValueChange = { onBlockChange(block.copy(name = it)) },
            label = { Text("ブロック名") },
            placeholder = { Text("例: ア, め, N") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (editMode) {
                    EditMode.NORMAL -> {
                        Text("範囲指定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (block.startRow > 0 && block.endRow > 0) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "範囲: 行${block.startRow}-${block.endRow}, 列${block.startCol}-${block.endCol}",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { onStartCellSelection(CellSelectionType.CORNER) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.TouchApp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("4つの角をクリックして選択")
                        }
                    }
                    EditMode.MULTI -> {
                        Text("複数範囲指定（Nブロックなど）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (multiRanges.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                multiRanges.forEachIndexed { index, range ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("範囲${index + 1}: 行${range.startRow}-${range.endRow}, 列${range.startCol}-${range.endCol}", style = MaterialTheme.typography.bodySmall)
                                            IconButton(onClick = { onMultiRangesChange(multiRanges.filterIndexed { i, _ -> i != index }) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "削除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Button(
                            onClick = { onStartCellSelection(CellSelectionType.MULTI_CORNER) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("4つの角をクリックして範囲を追加")
                        }
                    }
                    EditMode.WALL -> {
                        Text("セル群（最大6）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (wallCellGroups.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                wallCellGroups.forEachIndexed { index, group ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (group.type == CellGroupType.RANGE) "範囲(${group.startRow},${group.startCol})-(${group.endRow},${group.endCol})" else "個別${group.cells.size}セル",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            IconButton(onClick = { onWallCellGroupsChange(wallCellGroups.filterIndexed { i, _ -> i != index }) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "削除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onStartCellSelection(CellSelectionType.RANGE_START) }, enabled = wallCellGroups.size < 6, modifier = Modifier.weight(1f)) {
                                Text("+ 範囲追加", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { onStartCellSelection(CellSelectionType.INDIVIDUAL) },
                                enabled = wallCellGroups.size < 6,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text("+ 個別追加", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BLOCK_COLORS) { color ->
                        val isSelected = block.color == color
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(color))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onBlockChange(block.copy(color = color)) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("検出セル: ${previewNumberCells.size}個", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (previewNumberCells.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(previewNumberCells) { cell ->
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                                Text(text = cell.value.toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    Text("範囲を指定してください", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("キャンセル") }
            Button(
                onClick = { onSave(block) },
                modifier = Modifier.weight(1f),
                enabled = block.name.isNotBlank() && (
                        (editMode == EditMode.NORMAL && block.startRow > 0) ||
                                (editMode == EditMode.MULTI && multiRanges.isNotEmpty()) ||
                                (editMode == EditMode.WALL && wallCellGroups.isNotEmpty())
                        )
            ) {
                Text(if (isNew) "追加" else "保存")
            }
        }
    }
}

@Composable
private fun BlockListContent(
    blocks: List<BlockDefinition>,
    selectedIndex: Int?,
    sortAscending: Boolean,
    onSortToggle: () -> Unit,
    onSelectBlock: (Int) -> Unit,
    onAddNew: () -> Unit,
    onDeleteBlock: (BlockDefinition) -> Unit,
    onDeleteAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("定義済み (${blocks.size}件)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSortToggle, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (sortAscending) "昇順" else "降順", fontSize = 12.sp)
                }
                Button(onClick = onAddNew, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新規", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDeleteAll,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = blocks.isNotEmpty()
                ) {
                    Text("全削除", fontSize = 12.sp)
                }
            }
        }
        Divider()
        if (blocks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("ブロックがありません", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("「新規」ボタンでブロックを追加してください", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(blocks) { index, block ->
                    BlockListItem(block = block, isSelected = selectedIndex == index, onClick = { onSelectBlock(index) }, onDelete = { onDeleteBlock(block) })
                }
            }
        }
    }
}

@Composable
private fun BlockListItem(block: BlockDefinition, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(block.color)).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = block.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = block.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (block.isWallBlock) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                                Text("壁", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        } else if (block.cellGroups.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                Text("複数範囲", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text("${block.numberCells.size}セル", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (block.isAutoDetected) {
                        Text("⚡自動検出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("ブロック削除") },
            text = { Text("「${block.name}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") } }
        )
    }
}

private fun detectNumberCells(mapData: DayMapData, startRow: Int, startCol: Int, endRow: Int, endCol: Int): List<NumberCellInfo> {
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