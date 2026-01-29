package com.example.eventshoppingplanner.presentation.screens.map

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMapFile(it) }
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
                    Box(modifier = Modifier.weight(1f)) {
                        uiState.currentMapData?.let { mapData ->
                            MapCanvas(
                                mapData = mapData,
                                zoomLevel = uiState.zoomLevel,
                                offsetX = uiState.offsetX,
                                offsetY = uiState.offsetY,
                                cellItemsMap = uiState.cellItemsMap,
                                onPan = { dx, dy -> viewModel.pan(dx, dy) }
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
    onPan: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val scale = zoomLevel.scale

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
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
                                val fontSize = ((cell?.fontInfo?.size ?: 11f) * scale * 0.8f).coerceIn(4f, 48f)
                                val fontColor = cell?.fontInfo?.color ?: 0xFF000000L
                                val textColorInt = (fontColor and 0xFFFFFF).toInt()

                                drawContext.canvas.nativeCanvas.apply {
                                    val paint = android.graphics.Paint().apply {
                                        color = (0xFF000000 or textColorInt.toLong()).toInt()
                                        textSize = fontSize * density.density
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias = true
                                        if (cell?.fontInfo?.bold == true) {
                                            isFakeBoldText = true
                                        }
                                    }

                                    val textX = x + mergedWidth / 2
                                    val textY = y + mergedHeight / 2 + fontSize / 3

                                    drawText(value, textX, textY, paint)
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
                            val fontSize = (cell.fontInfo.size * scale * 0.8f).coerceIn(4f, 24f)
                            val fontColor = cell.fontInfo.color
                            val textColorInt = (fontColor and 0xFFFFFF).toInt()

                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = (0xFF000000 or textColorInt.toLong()).toInt()
                                    textSize = fontSize * density.density
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }

                                val textX = x + cellWidth / 2
                                val textY = y + cellHeight / 2 + fontSize / 3

                                drawText(value, textX, textY, paint)
                            }
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
