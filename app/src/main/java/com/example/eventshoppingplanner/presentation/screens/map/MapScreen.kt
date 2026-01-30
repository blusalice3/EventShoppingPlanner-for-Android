package com.example.eventshoppingplanner.presentation.screens.map

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventshoppingplanner.domain.model.*
import com.example.eventshoppingplanner.domain.model.PurchaseStatus

/**
 * 新規アイテム追加時のプリセット情報
 */
private data class NewItemPreset(
    val eventDate: String,
    val block: String,
    val number: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // セルタップ時のダイアログ状態
    var selectedCellInfo by remember { mutableStateOf<CellTapInfo?>(null) }

    // 新規アイテム追加ダイアログの状態
    var showAddItemDialog by remember { mutableStateOf(false) }
    var newItemPreset by remember { mutableStateOf<NewItemPreset?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMapFile(it) }
    }

    // セルアイテムダイアログ
    selectedCellInfo?.let { cellInfo ->
        CellItemsDialog(
            blockName = cellInfo.blockName,
            number = cellInfo.number,
            items = cellInfo.items,
            onDismiss = { selectedCellInfo = null },
            onUpdateStatus = { itemId, status ->
                viewModel.updateItemStatus(itemId, status)
            },
            onOpenUrl = { url ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            },
            onAddNewItem = if (cellInfo.isInBlockDefinition) {
                {
                    // マップ名から参加日を抽出（例: "1日目マップ" -> "1日目"）
                    val eventDate = uiState.selectedMapName?.replace("マップ", "") ?: ""
                    newItemPreset = NewItemPreset(
                        eventDate = eventDate,
                        block = cellInfo.blockName,
                        number = cellInfo.number.toString()
                    )
                    selectedCellInfo = null
                    showAddItemDialog = true
                }
            } else null
        )
    }

    // 新規アイテム追加ダイアログ
    if (showAddItemDialog && newItemPreset != null) {
        AddItemFromMapDialog(
            preset = newItemPreset!!,
            eventId = eventId,
            onDismiss = {
                showAddItemDialog = false
                newItemPreset = null
            },
            onSave = { item ->
                viewModel.addItem(item)
                showAddItemDialog = false
                newItemPreset = null
            }
        )
    }

    // ブロック定義パネル
    uiState.currentMapData?.let { mapData ->
        BlockDefinitionPanel(
            isOpen = uiState.isBlockDefinitionPanelOpen,
            isVisible = uiState.isBlockDefinitionPanelVisible,
            onClose = { viewModel.closeBlockDefinitionPanel() },
            mapData = mapData,
            selectedCells = uiState.selectedCells,
            onStartCellSelection = { selectionType, editState ->
                viewModel.startCellSelection(selectionType, editState)
            },
            onCancelCellSelection = { viewModel.cancelCellSelection() },
            onUpdateBlocks = { blocks -> viewModel.updateBlocks(blocks) },
            isInSelectionMode = uiState.cellSelectionMode != CellSelectionMode.NONE,
            currentSelectionType = uiState.currentSelectionType,
            pendingEditState = uiState.pendingEditState,
            onClearPendingEditState = { viewModel.clearPendingEditState() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.event?.name ?: "マップ") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                    }
                },
                actions = {
                    // ブロック定義ボタン（マップがある場合のみ表示）
                    if (uiState.currentMapData != null) {
                        IconButton(
                            onClick = { viewModel.openBlockDefinitionPanel() }
                        ) {
                            Icon(Icons.Default.GridOn, "ブロック定義")
                        }
                    }
                    IconButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel"
                            ))
                        }
                    ) {
                        Icon(Icons.Default.FolderOpen, "マップを開く")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.mapDataList.isEmpty()) {
                NoMapPlaceholder(
                    onOpenFile = {
                        filePickerLauncher.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel"
                        ))
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // タブ
                    if (uiState.mapDataList.size > 1) {
                        MapTabs(
                            mapNames = uiState.mapDataList.keys.toList(),
                            selectedMapName = uiState.selectedMapName,
                            onSelectMap = { viewModel.selectMap(it) }
                        )
                    }

                    // マップキャンバス
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clipToBounds()
                    ) {
                        uiState.currentMapData?.let { mapData ->
                            MapCanvas(
                                mapData = mapData,
                                zoomLevel = uiState.zoomLevel,
                                offsetX = uiState.offsetX,
                                offsetY = uiState.offsetY,
                                cellItemsMap = uiState.cellItemsMap,
                                selectedCells = uiState.selectedCells,
                                isSelectionMode = uiState.cellSelectionMode != CellSelectionMode.NONE,
                                currentSelectionType = uiState.currentSelectionType,
                                onPan = { dx, dy -> viewModel.pan(dx, dy) },
                                onCellTap = { row, col, items ->
                                    // セル選択モード中は選択に使用
                                    if (uiState.cellSelectionMode != CellSelectionMode.NONE) {
                                        viewModel.addSelectedCell(row, col)
                                    } else {
                                        // ブロック定義内かチェックし、ブロック情報を取得
                                        val blockInfo = findBlockInfoForCell(
                                            row, col,
                                            mapData.blocks,
                                            mapData.cells,
                                            mapData.mergedCells
                                        )

                                        when {
                                            blockInfo != null -> {
                                                // ブロック定義内のセル
                                                selectedCellInfo = CellTapInfo(
                                                    row = row,
                                                    col = col,
                                                    blockName = blockInfo.first,
                                                    number = blockInfo.second,
                                                    items = items,
                                                    isInBlockDefinition = true
                                                )
                                            }
                                            items.isNotEmpty() -> {
                                                // ブロック定義外だがアイテムがある場合
                                                val firstItem = items.first()
                                                val numValue = extractNumberFromItemNumber(firstItem.number) ?: 0
                                                selectedCellInfo = CellTapInfo(
                                                    row = row,
                                                    col = col,
                                                    blockName = firstItem.block,
                                                    number = numValue,
                                                    items = items,
                                                    isInBlockDefinition = false
                                                )
                                            }
                                            // ブロック定義外かつアイテムなし → 何もしない
                                        }
                                    }
                                },
                                onSelectedCellTap = { row, col ->
                                    // 選択済みセルをタップしたら選択解除
                                    viewModel.removeSelectedCell(row, col)
                                }
                            )
                        }

                        // セル選択モード中のオーバーレイ
                        if (uiState.cellSelectionMode != CellSelectionMode.NONE) {
                            val (title, requiredCount) = when (uiState.currentSelectionType) {
                                CellSelectionType.CORNER, CellSelectionType.MULTI_CORNER -> "4つの角をタップ" to 4
                                CellSelectionType.RANGE_START -> "開始点と終了点をタップ" to 2
                                CellSelectionType.INDIVIDUAL -> "セルをタップして選択" to -1
                                null -> "セルを選択" to 4
                            }
                            // 確定ボタンの表示条件
                            val canConfirm = when (uiState.currentSelectionType) {
                                CellSelectionType.CORNER, CellSelectionType.MULTI_CORNER -> uiState.selectedCells.size >= 4
                                CellSelectionType.RANGE_START -> uiState.selectedCells.size >= 2
                                CellSelectionType.INDIVIDUAL -> uiState.selectedCells.isNotEmpty()
                                null -> uiState.selectedCells.size >= 4
                            }
                            CellSelectionOverlay(
                                selectedCount = uiState.selectedCells.size,
                                requiredCount = requiredCount,
                                title = title,
                                showConfirmButton = canConfirm,
                                onConfirm = { viewModel.confirmSelection() },
                                onCancel = { viewModel.cancelCellSelection() }
                            )
                        }

                        // ズームコントロール
                        ZoomControls(
                            zoomLevel = uiState.zoomLevel,
                            onZoomIn = { viewModel.zoomIn() },
                            onZoomOut = { viewModel.zoomOut() },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        )
                    }
                }
            }

            // エラーメッセージ
            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("閉じる")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun MapTabs(
    mapNames: List<String>,
    selectedMapName: String?,
    onSelectMap: (String) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = mapNames.indexOf(selectedMapName).coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth()
    ) {
        mapNames.forEach { name ->
            Tab(
                selected = name == selectedMapName,
                onClick = { onSelectMap(name) },
                text = { Text(name) }
            )
        }
    }
}

@Composable
private fun MapCanvas(
    mapData: DayMapData,
    zoomLevel: ZoomLevel,
    offsetX: Float,
    offsetY: Float,
    cellItemsMap: Map<String, List<ShoppingItem>>,
    selectedCells: List<Pair<Int, Int>> = emptyList(),
    isSelectionMode: Boolean = false,
    currentSelectionType: CellSelectionType? = null,
    onPan: (Float, Float) -> Unit,
    onCellTap: (Int, Int, List<ShoppingItem>) -> Unit = { _, _, _ -> },
    onSelectedCellTap: (Int, Int) -> Unit = { _, _ -> }  // 選択済みセルタップ時のコールバック
) {
    val density = LocalDensity.current
    val scale = zoomLevel.scale

    // ジェスチャー完了を示すバージョン（パン/ズーム終了時にインクリメント）
    // これをpointerInputのkeyに使用し、操作完了後に座標系を更新
    var gestureVersion by remember { mutableStateOf(0) }

    // ズームレベル変更時にgestureVersionを更新
    LaunchedEffect(zoomLevel) {
        gestureVersion++
    }

    // 選択済みセルのセット
    val selectedCellsSet = remember(selectedCells) {
        selectedCells.map { "${it.first}-${it.second}" }.toSet()
    }

    // 4角選択の場合の範囲を計算
    val selectionBounds = remember(selectedCells, currentSelectionType) {
        if ((currentSelectionType == CellSelectionType.CORNER ||
                    currentSelectionType == CellSelectionType.MULTI_CORNER) &&
            selectedCells.size >= 4) {
            val rows = selectedCells.map { it.first }
            val cols = selectedCells.map { it.second }
            val minRow = rows.minOrNull() ?: 0
            val maxRow = rows.maxOrNull() ?: 0
            val minCol = cols.minOrNull() ?: 0
            val maxCol = cols.maxOrNull() ?: 0
            if (minRow > 0 && maxRow > 0) {
                SelectionBounds(minRow, minCol, maxRow, maxCol)
            } else null
        } else null
    }

    // セルマップを作成
    val cellMap = remember(mapData) {
        mapData.cells.associateBy { "${it.row}-${it.col}" }
    }

    // 結合セルマップを作成
    val mergeMap = remember(mapData) {
        val map = mutableMapOf<String, MergedCellInfo>()
        mapData.mergedCells.forEach { merged ->
            for (r in merged.startRow..merged.endRow) {
                for (c in merged.startCol..merged.endCol) {
                    map["$r-$c"] = merged
                }
            }
        }
        map
    }

    // 色定数
    val colorGreen = Color(0xFFE8F5E9)
    val colorYellow = Color(0xFFFFF9C4)
    val colorRed = Color(0xFFFFCDD2)
    val colorWhite = Color.White
    val colorSelected = Color(0xFF2196F3)  // 選択セルの色
    val colorSelectionArea = Color(0xFF2196F3).copy(alpha = 0.15f)  // 選択範囲の塗りつぶし色

    // タップ位置からセル座標を計算する関数
    fun findCellAtPosition(tapX: Float, tapY: Float): Pair<Int, Int>? {
        var currentX = offsetX
        var foundCol = -1
        for (col in 1..mapData.maxCol) {
            val colWidth = (mapData.columnWidths[col] ?: mapData.defaultColumnWidth) * scale
            if (tapX >= currentX && tapX < currentX + colWidth) {
                foundCol = col
                break
            }
            currentX += colWidth
        }

        var currentY = offsetY
        var foundRow = -1
        for (row in 1..mapData.maxRow) {
            val rowHeight = (mapData.rowHeights[row] ?: mapData.defaultRowHeight) * scale
            if (tapY >= currentY && tapY < currentY + rowHeight) {
                foundRow = row
                break
            }
            currentY += rowHeight
        }

        return if (foundRow > 0 && foundCol > 0) Pair(foundRow, foundCol) else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // gestureVersionをkeyに追加：パン/ズーム完了後にpointerInputが再生成される
                .pointerInput(selectedCellsSet, isSelectionMode, gestureVersion) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue

                            if (change.pressed) {
                                val startPosition = change.position
                                val startTime = System.currentTimeMillis()
                                var totalDrag = Offset.Zero
                                var wasDragging = false

                                // ポインタが離されるまで追跡
                                while (change.pressed) {
                                    val nextEvent = awaitPointerEvent()
                                    val nextChange = nextEvent.changes.firstOrNull() ?: break

                                    if (nextChange.pressed) {
                                        val dragAmount = nextChange.position - nextChange.previousPosition
                                        totalDrag += dragAmount

                                        // ドラッグ閾値を超えたらパン処理
                                        if (totalDrag.getDistance() > 10f) {
                                            wasDragging = true
                                            onPan(dragAmount.x, dragAmount.y)
                                            nextChange.consume()
                                        }
                                    } else {
                                        // ポインタが離された
                                        val endTime = System.currentTimeMillis()
                                        val duration = endTime - startTime

                                        // 短いタップで移動量が少ない場合はタップとして処理
                                        if (duration < 300 && totalDrag.getDistance() < 20f) {
                                            val cellPos = findCellAtPosition(startPosition.x, startPosition.y)
                                            cellPos?.let { (row, col) ->
                                                // 結合セルの場合は開始セルを使用
                                                val mergedInfo = mergeMap["$row-$col"]
                                                val actualRow = mergedInfo?.startRow ?: row
                                                val actualCol = mergedInfo?.startCol ?: col
                                                val key = "$actualRow-$actualCol"

                                                // セル選択モード中で、既に選択済みのセルをタップした場合は選択解除
                                                if (isSelectionMode && selectedCellsSet.contains(key)) {
                                                    onSelectedCellTap(actualRow, actualCol)
                                                } else {
                                                    val items = cellItemsMap[key] ?: emptyList()
                                                    onCellTap(actualRow, actualCol, items)
                                                }
                                            }
                                        }
                                        nextChange.consume()
                                        break
                                    }
                                }

                                // パン操作が終了した場合、gestureVersionをインクリメント
                                // これにより次のタップで最新の座標系が使われる
                                if (wasDragging) {
                                    gestureVersion++
                                }
                            }
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 描画開始位置
            val startX = offsetX
            val startY = offsetY

            // 各セルの位置を計算するためのヘルパー関数
            fun getColumnX(col: Int): Float {
                var x = startX
                for (c in 1 until col) {
                    val width = mapData.columnWidths[c] ?: mapData.defaultColumnWidth
                    x += width * scale
                }
                return x
            }

            fun getRowY(row: Int): Float {
                var y = startY
                for (r in 1 until row) {
                    val height = mapData.rowHeights[r] ?: mapData.defaultRowHeight
                    y += height * scale
                }
                return y
            }

            fun getColumnWidth(col: Int): Float {
                return (mapData.columnWidths[col] ?: mapData.defaultColumnWidth) * scale
            }

            fun getRowHeight(row: Int): Float {
                return (mapData.rowHeights[row] ?: mapData.defaultRowHeight) * scale
            }

            // 描画範囲を計算
            var currentX = startX
            var startCol = 1
            while (currentX < 0 && startCol < mapData.maxCol) {
                currentX += getColumnWidth(startCol)
                startCol++
            }

            var currentY = startY
            var startRow = 1
            while (currentY < 0 && startRow < mapData.maxRow) {
                currentY += getRowHeight(startRow)
                startRow++
            }

            // 描画済みの結合セルを追跡
            val drawnMergedCells = mutableSetOf<String>()

            // セルを描画
            for (row in maxOf(1, startRow - 5)..minOf(mapData.maxRow, startRow + 200)) {
                val y = getRowY(row)
                if (y > canvasHeight + 100) break

                for (col in maxOf(1, startCol - 5)..minOf(mapData.maxCol, startCol + 300)) {
                    val x = getColumnX(col)
                    if (x > canvasWidth + 100) break

                    val cellKey = "$row-$col"
                    val mergedInfo = mergeMap[cellKey]

                    // 結合セルの場合
                    if (mergedInfo != null) {
                        val mergeKey = "${mergedInfo.startRow}-${mergedInfo.startCol}"

                        // 結合セルの開始セルの場合のみ描画
                        if (row == mergedInfo.startRow && col == mergedInfo.startCol &&
                            !drawnMergedCells.contains(mergeKey)) {
                            drawnMergedCells.add(mergeKey)

                            // 結合セルの幅と高さを計算
                            var mergedWidth = 0f
                            for (c in mergedInfo.startCol..mergedInfo.endCol) {
                                mergedWidth += getColumnWidth(c)
                            }
                            var mergedHeight = 0f
                            for (r in mergedInfo.startRow..mergedInfo.endRow) {
                                mergedHeight += getRowHeight(r)
                            }

                            val cell = cellMap[mergeKey]
                            val items = cellItemsMap[mergeKey] ?: emptyList()

                            // 背景色を決定
                            val bgColor = if (items.isNotEmpty()) {
                                val allPurchased = items.all { it.purchaseStatus == PurchaseStatus.PURCHASED }
                                val anyPurchased = items.any { it.purchaseStatus == PurchaseStatus.PURCHASED }
                                when {
                                    allPurchased -> colorGreen
                                    anyPurchased -> colorYellow
                                    else -> colorRed
                                }
                            } else if (cell?.backgroundColor != null) {
                                Color(cell.backgroundColor)
                            } else {
                                colorWhite
                            }

                            // 背景を描画
                            drawRect(
                                color = bgColor,
                                topLeft = Offset(x, y),
                                size = Size(mergedWidth, mergedHeight)
                            )

                            // 罫線を描画
                            cell?.let {
                                drawCellBorders(x, y, mergedWidth, mergedHeight, it.borders, scale)
                            }

                            // テキストを描画
                            val textValue = mergedInfo.value ?: cell?.value
                            textValue?.let { value ->
                                // Web版と同様にセルサイズベースでフォントサイズを計算
                                val isNumeric = value.toString().matches(Regex("^\\d+$"))
                                val baseSize = minOf(mergedWidth, mergedHeight)
                                val fontSize = if (isNumeric) {
                                    // 数値セル: セルサイズの40%、最大24px
                                    minOf(baseSize * 0.4f, 24f * density.density)
                                } else {
                                    // テキストセル: セルサイズの35%、最大20px
                                    minOf(baseSize * 0.35f, 20f * density.density)
                                }.coerceAtLeast(8f * density.density)  // 最小8px

                                val fontColor = cell?.fontInfo?.color ?: 0xFF000000L
                                val textColorInt = (fontColor and 0xFFFFFF).toInt()

                                drawContext.canvas.nativeCanvas.apply {
                                    val paint = android.graphics.Paint().apply {
                                        color = (0xFF000000 or textColorInt.toLong()).toInt()
                                        textSize = fontSize
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias = true
                                        if (cell?.fontInfo?.bold == true) {
                                            isFakeBoldText = true
                                        }
                                    }

                                    val textX = x + mergedWidth / 2
                                    val textY = y + mergedHeight / 2 + fontSize / 3

                                    drawText(value.toString(), textX, textY, paint)
                                }
                            }

                            // 結合セルの選択マーカー描画
                            if (selectedCellsSet.contains(mergeKey)) {
                                // 半透明の青いオーバーレイ
                                drawRect(
                                    color = colorSelected.copy(alpha = 0.3f),
                                    topLeft = Offset(x, y),
                                    size = Size(mergedWidth, mergedHeight)
                                )
                                // 枠線
                                drawRect(
                                    color = colorSelected,
                                    topLeft = Offset(x, y),
                                    size = Size(mergedWidth, mergedHeight),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                )

                                // マーカー（番号付きの円）を描画
                                val markerIndex = selectedCells.indexOfFirst {
                                    it.first == mergedInfo.startRow && it.second == mergedInfo.startCol
                                }
                                if (markerIndex >= 0) {
                                    val markerRadius = minOf(mergedWidth, mergedHeight) * 0.25f
                                    val markerCenterX = x + mergedWidth / 2
                                    val markerCenterY = y + mergedHeight / 2

                                    // 円の背景
                                    drawCircle(
                                        color = Color(0xFF1976D2),
                                        radius = markerRadius,
                                        center = Offset(markerCenterX, markerCenterY)
                                    )
                                    // 円の枠線
                                    drawCircle(
                                        color = Color.White,
                                        radius = markerRadius,
                                        center = Offset(markerCenterX, markerCenterY),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                    )

                                    // 番号を描画
                                    drawContext.canvas.nativeCanvas.apply {
                                        val markerPaint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = markerRadius * 1.2f
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                                            isAntiAlias = true
                                        }
                                        val markerTextY = markerCenterY + markerRadius * 0.35f
                                        drawText("${markerIndex + 1}", markerCenterX, markerTextY, markerPaint)
                                    }
                                }
                            }
                        }
                    } else {
                        // 通常セル
                        val cell = cellMap[cellKey]
                        val cellWidth = getColumnWidth(col)
                        val cellHeight = getRowHeight(row)
                        val items = cellItemsMap[cellKey] ?: emptyList()

                        // 背景色を決定
                        val bgColor = if (items.isNotEmpty()) {
                            val allPurchased = items.all { it.purchaseStatus == PurchaseStatus.PURCHASED }
                            val anyPurchased = items.any { it.purchaseStatus == PurchaseStatus.PURCHASED }
                            when {
                                allPurchased -> colorGreen
                                anyPurchased -> colorYellow
                                else -> colorRed
                            }
                        } else if (cell?.backgroundColor != null) {
                            Color(cell.backgroundColor)
                        } else {
                            colorWhite
                        }

                        // 背景を描画
                        drawRect(
                            color = bgColor,
                            topLeft = Offset(x, y),
                            size = Size(cellWidth, cellHeight)
                        )

                        // 罫線を描画
                        cell?.let {
                            drawCellBorders(x, y, cellWidth, cellHeight, it.borders, scale)
                        }

                        // テキストを描画
                        cell?.value?.let { value ->
                            // Web版と同様にセルサイズベースでフォントサイズを計算
                            val isNumeric = value.toString().matches(Regex("^\\d+$"))
                            val cellSize = minOf(cellWidth, cellHeight)
                            val fontSize = if (isNumeric) {
                                // 数値セル: セルサイズの45%、最大14px
                                minOf(cellSize * 0.45f, 14f * density.density)
                            } else {
                                // テキストセル: セルサイズの40%、最大12px
                                minOf(cellSize * 0.4f, 12f * density.density)
                            }.coerceAtLeast(8f * density.density)  // 最小8px

                            val fontColor = cell.fontInfo.color
                            val textColorInt = (fontColor and 0xFFFFFF).toInt()

                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = (0xFF000000 or textColorInt.toLong()).toInt()
                                    textSize = fontSize
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }

                                val textX = x + cellWidth / 2
                                val textY = y + cellHeight / 2 + fontSize / 3

                                drawText(value.toString(), textX, textY, paint)
                            }
                        }

                        // 選択されたセルのハイライトとマーカー描画
                        if (selectedCellsSet.contains(cellKey)) {
                            // 半透明の青いオーバーレイ
                            drawRect(
                                color = colorSelected.copy(alpha = 0.3f),
                                topLeft = Offset(x, y),
                                size = Size(cellWidth, cellHeight)
                            )
                            // 枠線
                            drawRect(
                                color = colorSelected,
                                topLeft = Offset(x, y),
                                size = Size(cellWidth, cellHeight),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )

                            // マーカー（番号付きの円）を描画
                            val markerIndex = selectedCells.indexOfFirst { it.first == row && it.second == col }
                            if (markerIndex >= 0) {
                                val markerRadius = minOf(cellWidth, cellHeight) * 0.35f
                                val markerCenterX = x + cellWidth / 2
                                val markerCenterY = y + cellHeight / 2

                                // 円の背景
                                drawCircle(
                                    color = Color(0xFF1976D2),
                                    radius = markerRadius,
                                    center = Offset(markerCenterX, markerCenterY)
                                )
                                // 円の枠線
                                drawCircle(
                                    color = Color.White,
                                    radius = markerRadius,
                                    center = Offset(markerCenterX, markerCenterY),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                )

                                // 番号を描画
                                drawContext.canvas.nativeCanvas.apply {
                                    val markerPaint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textSize = markerRadius * 1.2f
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        isAntiAlias = true
                                    }
                                    val textY = markerCenterY + markerRadius * 0.35f
                                    drawText("${markerIndex + 1}", markerCenterX, textY, markerPaint)
                                }
                            }
                        }
                    }
                }
            }

            // 4角選択完了時の範囲オーバーレイ描画
            selectionBounds?.let { bounds ->
                val areaStartX = getColumnX(bounds.minCol)
                val areaStartY = getRowY(bounds.minRow)
                var areaEndX = areaStartX
                var areaEndY = areaStartY

                // 範囲の幅と高さを計算
                for (c in bounds.minCol..bounds.maxCol) {
                    areaEndX += (mapData.columnWidths[c] ?: mapData.defaultColumnWidth) * scale
                }
                for (r in bounds.minRow..bounds.maxRow) {
                    areaEndY += (mapData.rowHeights[r] ?: mapData.defaultRowHeight) * scale
                }

                val areaWidth = areaEndX - areaStartX
                val areaHeight = areaEndY - areaStartY

                // 範囲内を薄青色で塗りつぶし
                drawRect(
                    color = colorSelectionArea,
                    topLeft = Offset(areaStartX, areaStartY),
                    size = Size(areaWidth, areaHeight)
                )

                // 範囲の枠線
                drawRect(
                    color = colorSelected,
                    topLeft = Offset(areaStartX, areaStartY),
                    size = Size(areaWidth, areaHeight),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )
            }
        }
    }
}

/**
 * 選択範囲の境界
 */
private data class SelectionBounds(
    val minRow: Int,
    val minCol: Int,
    val maxRow: Int,
    val maxCol: Int
)

/**
 * 罫線を描画
 */
private fun DrawScope.drawCellBorders(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    borders: CellBorders,
    scale: Float
) {
    // 上罫線
    if (borders.top.style != BorderWeight.NONE) {
        val strokeWidth = getBorderStrokeWidth(borders.top.style, scale)
        drawLine(
            color = Color(borders.top.color),
            start = Offset(x, y),
            end = Offset(x + width, y),
            strokeWidth = strokeWidth
        )
    }

    // 右罫線
    if (borders.right.style != BorderWeight.NONE) {
        val strokeWidth = getBorderStrokeWidth(borders.right.style, scale)
        drawLine(
            color = Color(borders.right.color),
            start = Offset(x + width, y),
            end = Offset(x + width, y + height),
            strokeWidth = strokeWidth
        )
    }

    // 下罫線
    if (borders.bottom.style != BorderWeight.NONE) {
        val strokeWidth = getBorderStrokeWidth(borders.bottom.style, scale)
        drawLine(
            color = Color(borders.bottom.color),
            start = Offset(x, y + height),
            end = Offset(x + width, y + height),
            strokeWidth = strokeWidth
        )
    }

    // 左罫線
    if (borders.left.style != BorderWeight.NONE) {
        val strokeWidth = getBorderStrokeWidth(borders.left.style, scale)
        drawLine(
            color = Color(borders.left.color),
            start = Offset(x, y),
            end = Offset(x, y + height),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * 罫線の太さを取得
 */
private fun getBorderStrokeWidth(weight: BorderWeight, scale: Float): Float {
    return when (weight) {
        BorderWeight.THIN -> 1f * scale
        BorderWeight.MEDIUM -> 2f * scale
        BorderWeight.THICK -> 3f * scale
        BorderWeight.DOUBLE -> 3f * scale
        BorderWeight.NONE -> 0f
    }.coerceAtLeast(0.5f)
}

@Composable
private fun ZoomControls(
    zoomLevel: ZoomLevel,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Default.Add, "拡大")
            }
            Text(
                text = zoomLevel.displayName,
                style = MaterialTheme.typography.bodySmall
            )
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Default.Remove, "縮小")
            }
        }
    }
}

@Composable
private fun NoMapPlaceholder(
    onOpenFile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ZoomIn,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "マップが読み込まれていません",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Excelファイル(.xlsx)を選択してください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onOpenFile) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("マップファイルを開く")
        }
    }
}

/**
 * セル選択モード中のオーバーレイ
 */
@Composable
private fun CellSelectionOverlay(
    selectedCount: Int,
    requiredCount: Int = 4,
    title: String = "4つの角をタップ",
    showConfirmButton: Boolean = false,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.align(Alignment.TopCenter),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (requiredCount > 0) "$title ($selectedCount/$requiredCount)" else "$title ($selectedCount)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (showConfirmButton) {
                        Button(
                            onClick = onConfirm,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("確定", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = onCancel) {
                        Text("キャンセル")
                    }
                }
                if (selectedCount > 0) {
                    Text(
                        text = "※マーカーをタップで選択解除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * セルタップ時の情報を保持するデータクラス
 */
private data class CellTapInfo(
    val row: Int,
    val col: Int,
    val blockName: String,
    val number: Int,
    val items: List<ShoppingItem>,
    val isInBlockDefinition: Boolean = false
)

/**
 * セル内のアイテム一覧を表示するダイアログ
 */
@Composable
private fun CellItemsDialog(
    blockName: String,
    number: Int,
    items: List<ShoppingItem>,
    onDismiss: () -> Unit,
    onUpdateStatus: (String, PurchaseStatus) -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddNewItem: (() -> Unit)? = null
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
                    Column {
                        Text(
                            text = "$blockName - $number",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (items.isNotEmpty()) {
                            Text(
                                text = "${items.size}件のアイテム",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                // 新規アイテム追加ボタン
                onAddNewItem?.let { addNewItem ->
                    Button(
                        onClick = addNewItem,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
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
                }

                // アイテム一覧
                if (items.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items) { item ->
                            CellItemRow(
                                item = item,
                                onStatusChange = { status ->
                                    onUpdateStatus(item.id, status)
                                },
                                onOpenUrl = {
                                    item.url?.let { onOpenUrl(it) }
                                }
                            )
                        }
                    }
                } else if (onAddNewItem == null) {
                    // アイテムなし＆新規追加なしの場合のみメッセージ表示
                    Text(
                        text = "このセルにはアイテムがありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * ダイアログ内のアイテム行
 */
@Composable
private fun CellItemRow(
    item: ShoppingItem,
    onStatusChange: (PurchaseStatus) -> Unit,
    onOpenUrl: () -> Unit
) {
    var showStatusMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (item.purchaseStatus) {
                PurchaseStatus.PURCHASED -> Color(0xFFE8F5E9)
                PurchaseStatus.SOLD_OUT -> Color(0xFFFFCDD2)
                PurchaseStatus.ABSENT -> Color(0xFFFFF9C4)
                PurchaseStatus.POSTPONE -> Color(0xFFE1BEE7)
                PurchaseStatus.LATE -> Color(0xFFBBDEFB)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // サークル名とタイトル
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

            Spacer(modifier = Modifier.height(8.dp))

            // 下部：価格、ステータス、URLボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 価格
                Text(
                    text = item.priceDisplay,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // URLボタン
                    if (!item.url.isNullOrBlank()) {
                        IconButton(
                            onClick = onOpenUrl,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "URLを開く",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // ステータス変更ボタン
                    Box {
                        TextButton(
                            onClick = { showStatusMenu = true }
                        ) {
                            Text(
                                text = item.purchaseStatus.displayName,
                                color = Color(item.purchaseStatus.colorHex)
                            )
                        }

                        DropdownMenu(
                            expanded = showStatusMenu,
                            onDismissRequest = { showStatusMenu = false }
                        ) {
                            PurchaseStatus.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = status.displayName,
                                            color = Color(status.colorHex)
                                        )
                                    },
                                    onClick = {
                                        onStatusChange(status)
                                        showStatusMenu = false
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

// ===== ヘルパー関数 =====

/**
 * セルがブロックの範囲内にあるかチェック（cellGroups対応）
 */
private fun isCellInBlock(row: Int, col: Int, block: BlockDefinition): Boolean {
    // cellGroupsがある場合（複数範囲ブロックや壁ブロック）
    if (block.cellGroups.isNotEmpty()) {
        return block.cellGroups.any { group ->
            when (group.type) {
                CellGroupType.RANGE -> {
                    row >= (group.startRow ?: 0) && row <= (group.endRow ?: 0) &&
                            col >= (group.startCol ?: 0) && col <= (group.endCol ?: 0)
                }
                CellGroupType.INDIVIDUAL -> {
                    group.cells.any { it.first == row && it.second == col }
                }
            }
        }
    }
    // 通常の矩形ブロック
    return row >= block.startRow && row <= block.endRow &&
            col >= block.startCol && col <= block.endCol
}

/**
 * タップされたセルのブロック情報を特定
 * numberCellsに登録されている数値セルのみを対象とする
 * 注: タップ処理で既に結合セルは開始座標に変換済み
 * @return Pair(blockName, number) or null
 */
private fun findBlockInfoForCell(
    row: Int,
    col: Int,
    blocks: List<BlockDefinition>,
    cells: List<CellData>,
    mergedCells: List<MergedCellInfo>
): Pair<String, Int>? {
    // 各ブロックのnumberCellsから該当セルを探す
    for (block in blocks) {
        val numberCell = block.numberCells.find {
            it.row == row && it.col == col
        }
        if (numberCell != null) {
            return Pair(block.name, numberCell.value)
        }
    }
    return null
}

/**
 * アイテムナンバーから数値部分を抽出
 * "5a" → 5, "12" → 12, "abc" → null
 */
private fun extractNumberFromItemNumber(itemNumber: String): Int? {
    val match = Regex("^(\\d+)").find(itemNumber)
    return match?.groupValues?.get(1)?.toIntOrNull()
}

/**
 * マップからの新規アイテム追加ダイアログ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemFromMapDialog(
    preset: NewItemPreset,
    eventId: String,
    onDismiss: () -> Unit,
    onSave: (ShoppingItem) -> Unit
) {
    // 入力状態（プリセット値で初期化）
    var circle by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(preset.eventDate) }
    var block by remember { mutableStateOf(preset.block) }
    var number by remember { mutableStateOf(preset.number) }
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }
    var remarks by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    // ドロップダウン展開状態
    var priceExpanded by remember { mutableStateOf(false) }
    var quantityExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("新規アイテム追加")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // サークル名
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

                // 参加日（プリセット値を表示、編集可能）
                OutlinedTextField(
                    value = eventDate,
                    onValueChange = { eventDate = it },
                    label = { Text("参加日 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ブロック・ナンバー（プリセット値を表示、編集可能）
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

                // 価格（テキスト入力 + クイック選択）
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

                    // クイック選択ボタン
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

                // 数量
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newItem = ShoppingItem(
                        id = java.util.UUID.randomUUID().toString(),
                        eventId = eventId,
                        circle = circle,
                        eventDate = eventDate,
                        block = block,
                        number = number,
                        title = title,
                        price = price.toIntOrNull(),
                        purchaseStatus = PurchaseStatus.NONE,
                        quantity = quantity,
                        remarks = remarks,
                        url = url.ifBlank { null },
                        sortOrder = 0,
                        isInExecuteList = false
                    )
                    onSave(newItem)
                },
                enabled = circle.isNotBlank() && eventDate.isNotBlank() && block.isNotBlank() && number.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}