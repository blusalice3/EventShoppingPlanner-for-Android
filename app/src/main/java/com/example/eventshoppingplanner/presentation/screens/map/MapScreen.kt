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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventshoppingplanner.domain.model.*
import com.example.eventshoppingplanner.domain.model.PurchaseStatus

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
            }
        )
    }

    // ブロック定義パネル
    uiState.currentMapData?.let { mapData ->
        BlockDefinitionPanel(
            isOpen = uiState.isBlockDefinitionPanelOpen,
            onClose = { viewModel.closeBlockDefinitionPanel() },
            mapData = mapData,
            selectedCells = uiState.selectedCells,
            onStartCellSelection = { viewModel.startCellSelection() },
            onCancelCellSelection = { viewModel.cancelCellSelection() },
            onUpdateBlocks = { blocks -> viewModel.updateBlocks(blocks) },
            isInSelectionMode = uiState.cellSelectionMode == CellSelectionMode.CORNER_SELECT
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
                                isSelectionMode = uiState.cellSelectionMode == CellSelectionMode.CORNER_SELECT,
                                onPan = { dx, dy -> viewModel.pan(dx, dy) },
                                onCellTap = { row, col, items ->
                                    // セル選択モード中は選択に使用
                                    if (uiState.cellSelectionMode == CellSelectionMode.CORNER_SELECT) {
                                        viewModel.addSelectedCell(row, col)
                                    } else if (items.isNotEmpty()) {
                                        val firstItem = items.first()
                                        selectedCellInfo = CellTapInfo(
                                            row = row,
                                            col = col,
                                            blockName = firstItem.block,
                                            number = firstItem.number,
                                            items = items
                                        )
                                    }
                                }
                            )
                        }

                        // セル選択モード中のオーバーレイ
                        if (uiState.cellSelectionMode == CellSelectionMode.CORNER_SELECT) {
                            CellSelectionOverlay(
                                selectedCount = uiState.selectedCells.size,
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
    onPan: (Float, Float) -> Unit,
    onCellTap: (Int, Int, List<ShoppingItem>) -> Unit = { _, _, _ -> }
) {
    val density = LocalDensity.current
    val scale = zoomLevel.scale

    // 選択済みセルのセット
    val selectedCellsSet = remember(selectedCells) {
        selectedCells.map { "${it.first}-${it.second}" }.toSet()
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
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val cellPos = findCellAtPosition(offset.x, offset.y)
                        cellPos?.let { (row, col) ->
                            // 結合セルの場合は開始セルを使用
                            val mergedInfo = mergeMap["$row-$col"]
                            val actualRow = mergedInfo?.startRow ?: row
                            val actualCol = mergedInfo?.startCol ?: col
                            val key = "$actualRow-$actualCol"
                            val items = cellItemsMap[key] ?: emptyList()
                            onCellTap(actualRow, actualCol, items)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onPan(dragAmount.x, dragAmount.y)
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

                        // 選択されたセルのハイライト描画
                        if (selectedCellsSet.contains(cellKey)) {
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
                        }
                    }
                }
            }
        }
    }
}

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
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "4つの角をタップ ($selectedCount/4)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                TextButton(onClick = onCancel) {
                    Text("キャンセル")
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
    val number: String,
    val items: List<ShoppingItem>
)

/**
 * セル内のアイテム一覧を表示するダイアログ
 */
@Composable
private fun CellItemsDialog(
    blockName: String,
    number: String,
    items: List<ShoppingItem>,
    onDismiss: () -> Unit,
    onUpdateStatus: (String, PurchaseStatus) -> Unit,
    onOpenUrl: (String) -> Unit
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
                Text(
                    text = "$blockName - $number",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "${items.size}件のアイテム",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // アイテム一覧
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
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

                Spacer(modifier = Modifier.height(16.dp))

                // 閉じるボタン
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("閉じる")
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