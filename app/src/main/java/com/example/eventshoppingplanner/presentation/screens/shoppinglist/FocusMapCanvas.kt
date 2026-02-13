package com.example.eventshoppingplanner.presentation.screens.shoppinglist

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.example.eventshoppingplanner.domain.model.*
import com.example.eventshoppingplanner.domain.model.RouteSegment
import kotlin.math.*

// ========== 定数 ==========
private const val BASE_CELL_SIZE = 28f

// ========== セル状態色 ==========
private val COLOR_CURRENT_BG = Color(0x9922C55E)      // 緑60%
private val COLOR_NEXT_BG = Color(0x99FF9800)          // オレンジ60%
private val COLOR_VISITED_BG = Color(0x809E9E9E)       // グレー50%
private val COLOR_POSTPONED_BG = Color(0x669C27B0)     // 紫40%
private val COLOR_LATE_BG = Color(0x662196F3)          // 青40%
private val COLOR_UNVISITED_BG = Color(0x4D42A5F5)     // 薄青30%

private val COLOR_TEXT_CURRENT = 0xFFE65100.toInt()     // オレンジ
private val COLOR_TEXT_VISITED = 0xFF616161.toInt()     // グレー
private val COLOR_TEXT_HAS_ITEMS = 0xFF1565C0.toInt()   // 青
private val COLOR_TEXT_DEFAULT = 0xFF333333.toInt()

private val COLOR_CURRENT_BORDER = Color(0xFF22C55E)    // 現在位置枠
private val COLOR_NEXT_BORDER = Color(0xFFFF6D00)       // 次の目的地枠

private val COLOR_POSTPONE_OVERLAY_TEXT = 0xFF7B1FA2.toInt()
private val COLOR_LATE_OVERLAY_TEXT = 0xFF1976D2.toInt()

/**
 * 集中モード専用マップCanvas
 *
 * 既存MapCanvasの描画パターンをベースに、集中モードの状態オーバーレイ・ルート描画・マーカーを追加。
 */
@Composable
fun FocusMapCanvas(
    mapData: DayMapData,
    cellStates: Map<String, FocusCellState>,
    currentCellCoords: Pair<Int, Int>?,
    nextCellCoords: Pair<Int, Int>?,
    routeSegments: List<RouteSegment>,
    routeBounds: FocusRouteBounds?,
    currentPhase: FocusPhase,
    zoomLevel: Int,
    rotationDegrees: Float,
    selectedHall: HallDefinition?,
    onZoomChange: (Int) -> Unit,
    onCellTap: (String, Int, List<ShoppingItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scale = zoomLevel / 100f
    val cellSize = BASE_CELL_SIZE * scale * density.density

    // オフセット管理
    var offsetX by remember(mapData.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(mapData.id) { mutableFloatStateOf(0f) }
    var localScale by remember(mapData.id) { mutableFloatStateOf(scale) }

    // コンテナサイズ（Canvas描画時に記録）
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }

    // 外部スケール同期（画面中心を基準にズーム）
    LaunchedEffect(scale) {
        val oldScale = localScale
        val newScale = scale
        val cw = containerWidth
        val ch = containerHeight
        if (oldScale != newScale && oldScale > 0f && cw > 0f) {
            val scaleChange = newScale / oldScale
            val centerX = cw / 2f
            val centerY = ch / 2f
            offsetX = centerX - (centerX - offsetX) * scaleChange
            offsetY = centerY - (centerY - offsetY) * scaleChange
            localScale = newScale
        } else {
            localScale = newScale
        }
    }

    // セルマップ
    val cellMap = remember(mapData) {
        mapData.cells.associateBy { "${it.row}-${it.col}" }
    }

    // 結合セルマップ（全セル→結合情報）
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

    // 結合セルマップ（開始セルのみ）
    val mergedStartMap = remember(mapData) {
        mapData.mergedCells.associateBy { "${it.startRow}-${it.startCol}" }
    }

    // ホール範囲フィルタ
    val isInSelectedHall: (Int, Int) -> Boolean = remember(selectedHall) {
        if (selectedHall == null || selectedHall.vertices.size < 3) {
            { _, _ -> true }
        } else {
            { row, col ->
                com.example.eventshoppingplanner.util.HallUtils.isPointInPolygon(row, col, selectedHall.vertices)
            }
        }
    }

    // ホール範囲（ピクセル座標、スケールなし）
    val hallBoundsBase = remember(selectedHall, mapData) {
        if (selectedHall == null) null
        else {
            val bounds = com.example.eventshoppingplanner.util.HallUtils.getHallBounds(selectedHall)
            if (bounds != null && bounds.minCol >= 1 && bounds.minRow >= 1) {
                var minX = 0f
                for (c in 1 until bounds.minCol) {
                    minX += mapData.columnWidths[c] ?: mapData.defaultColumnWidth
                }
                var maxX = minX
                for (c in bounds.minCol..minOf(bounds.maxCol, mapData.maxCol)) {
                    maxX += mapData.columnWidths[c] ?: mapData.defaultColumnWidth
                }
                var minY = 0f
                for (r in 1 until bounds.minRow) {
                    minY += mapData.rowHeights[r] ?: mapData.defaultRowHeight
                }
                var maxY = minY
                for (r in bounds.minRow..minOf(bounds.maxRow, mapData.maxRow)) {
                    maxY += mapData.rowHeights[r] ?: mapData.defaultRowHeight
                }
                if (maxX > minX && maxY > minY) {
                    FocusHallBoundsPixels(minX, maxX, minY, maxY)
                } else null
            } else null
        }
    }

    // 列・行の位置キャッシュ
    val colPositions = remember(mapData, localScale) {
        val positions = FloatArray(mapData.maxCol + 2)
        var x = 0f
        for (c in 1..mapData.maxCol) {
            positions[c] = x
            x += (mapData.columnWidths[c] ?: mapData.defaultColumnWidth) * localScale * density.density
        }
        positions[mapData.maxCol + 1] = x
        positions
    }

    val rowPositions = remember(mapData, localScale) {
        val positions = FloatArray(mapData.maxRow + 2)
        var y = 0f
        for (r in 1..mapData.maxRow) {
            positions[r] = y
            y += (mapData.rowHeights[r] ?: mapData.defaultRowHeight) * localScale * density.density
        }
        positions[mapData.maxRow + 1] = y
        positions
    }

    fun getColWidth(col: Int): Float {
        return if (col in 1..mapData.maxCol) colPositions[col + 1] - colPositions[col]
        else (mapData.defaultColumnWidth) * localScale * density.density
    }
    fun getRowHeight(row: Int): Float {
        return if (row in 1..mapData.maxRow) rowPositions[row + 1] - rowPositions[row]
        else (mapData.defaultRowHeight) * localScale * density.density
    }

    // 自動ズーム: 訪問先変更時にルート全体を画面に収める
    var prevVisitKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(currentCellCoords, routeBounds) {
        if (currentCellCoords == prevVisitKey) return@LaunchedEffect
        prevVisitKey = currentCellCoords

        if (routeBounds == null || currentCellCoords == null) return@LaunchedEffect
        if (containerWidth <= 0f || containerHeight <= 0f) return@LaunchedEffect

        // routeBounds範囲の実際のピクセルサイズ（スケール1.0ベース）を計算
        var boundsWidthBase = 0f
        for (c in routeBounds.minCol..minOf(routeBounds.maxCol, mapData.maxCol)) {
            boundsWidthBase += (mapData.columnWidths[c] ?: mapData.defaultColumnWidth)
        }
        var boundsHeightBase = 0f
        for (r in routeBounds.minRow..minOf(routeBounds.maxRow, mapData.maxRow)) {
            boundsHeightBase += (mapData.rowHeights[r] ?: mapData.defaultRowHeight)
        }

        if (boundsWidthBase <= 0f || boundsHeightBase <= 0f) return@LaunchedEffect

        // 最適スケール計算（ルート範囲がコンテナに収まるスケール）
        val requiredWidthScale = containerWidth / (boundsWidthBase * density.density)
        val requiredHeightScale = containerHeight / (boundsHeightBase * density.density)
        val optimalScale = minOf(requiredWidthScale, requiredHeightScale, 1.0f)
        val newZoom = maxOf(30, minOf(100, (optimalScale * 100).toInt() / 10 * 10))

        onZoomChange(newZoom)

        // ルート範囲の中心をピクセル座標で計算（新スケールベース）
        val newScale = newZoom / 100f
        var boundsStartX = 0f
        for (c in 1 until routeBounds.minCol) {
            boundsStartX += (mapData.columnWidths[c] ?: mapData.defaultColumnWidth)
        }
        var boundsStartY = 0f
        for (r in 1 until routeBounds.minRow) {
            boundsStartY += (mapData.rowHeights[r] ?: mapData.defaultRowHeight)
        }

        val boundsCenterX = (boundsStartX + boundsWidthBase / 2f) * newScale * density.density
        val boundsCenterY = (boundsStartY + boundsHeightBase / 2f) * newScale * density.density

        offsetX = containerWidth / 2f - boundsCenterX
        offsetY = containerHeight / 2f - boundsCenterY
    }

    // コールバック・パラメータの最新参照（pointerInputコルーチン内から安全に読むため）
    val currentCellStates by rememberUpdatedState(cellStates)
    val currentOnCellTap by rememberUpdatedState(onCellTap)

    // タップ位置からセル座標を計算（MapScreen方式: state変数から毎回オンデマンド計算）
    fun findCellAtPosition(tapX: Float, tapY: Float): Pair<Int, Int>? {
        val curScale = localScale
        val curOffsetX = offsetX
        val curOffsetY = offsetY
        val d = density.density

        var currentX = curOffsetX
        var foundCol = -1
        for (col in 1..mapData.maxCol) {
            val colWidth = (mapData.columnWidths[col] ?: mapData.defaultColumnWidth) * curScale * d
            if (tapX >= currentX && tapX < currentX + colWidth) {
                foundCol = col
                break
            }
            currentX += colWidth
        }

        var currentY = curOffsetY
        var foundRow = -1
        for (row in 1..mapData.maxRow) {
            val rowHeight = (mapData.rowHeights[row] ?: mapData.defaultRowHeight) * curScale * d
            if (tapY >= currentY && tapY < currentY + rowHeight) {
                foundRow = row
                break
            }
            currentY += rowHeight
        }

        return if (foundRow > 0 && foundCol > 0) foundRow to foundCol else null
    }

    fun rotatePointAroundPivot(point: Offset, pivot: Offset, degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val cosValue = cos(radians).toFloat()
        val sinValue = sin(radians).toFloat()
        val translatedX = point.x - pivot.x
        val translatedY = point.y - pivot.y
        val rotatedX = translatedX * cosValue - translatedY * sinValue
        val rotatedY = translatedX * sinValue + translatedY * cosValue
        return Offset(rotatedX + pivot.x, rotatedY + pivot.y)
    }

    fun rotateVector(vector: Offset, degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val cosValue = cos(radians).toFloat()
        val sinValue = sin(radians).toFloat()
        return Offset(
            x = vector.x * cosValue - vector.y * sinValue,
            y = vector.x * sinValue + vector.y * cosValue
        )
    }

    // セルタップ処理（rememberUpdatedState経由で最新のcellStates/onCellTapを使用）
    fun handleCellTap(row: Int, col: Int) {
        for (block in mapData.blocks) {
            val inBlock = if (block.cellGroups.isNotEmpty()) {
                block.cellGroups.any { group ->
                    when (group.type) {
                        CellGroupType.RANGE -> row >= group.startRow && row <= group.endRow &&
                                col >= group.startCol && col <= group.endCol
                        CellGroupType.INDIVIDUAL -> group.cells.any { it.first == row && it.second == col }
                    }
                }
            } else {
                row in block.startRow..block.endRow && col in block.startCol..block.endCol
            }

            if (inBlock) {
                // numberCellsから番号を探す
                var foundNumber: Int? = block.numberCells.find { it.row == row && it.col == col }?.value

                // 見つからない場合、セル値をチェック
                if (foundNumber == null) {
                    var cell = cellMap["$row-$col"]
                    // 結合セル内かチェック
                    if (cell == null) {
                        val merge = mergeMap["$row-$col"]
                        if (merge != null) {
                            cell = cellMap["${merge.startRow}-${merge.startCol}"]
                        }
                    }
                    cell?.value?.let { value ->
                        val numMatch = Regex("^(\\d+)").find(value.toString())
                        numMatch?.let { foundNumber = it.groupValues[1].toIntOrNull() }
                    }
                }

                if (foundNumber != null) {
                    val num = foundNumber!!
                    val states = currentCellStates
                    val matchingItems = states.values
                        .flatMap { it.items }
                        .filter { it.block == block.name }
                        .filter {
                            val numStr = Regex("^(\\d+)").find(it.number)?.groupValues?.get(1)
                            numStr?.toIntOrNull() == num
                        }
                        .distinctBy { it.id }
                    currentOnCellTap(block.name, num, matchingItems)
                    break
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                // ピンチズーム + パン
                .pointerInput(rotationDegrees) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val pivot = Offset(size.width / 2f, size.height / 2f)
                        val unrotatedCentroid = rotatePointAroundPivot(
                            point = centroid,
                            pivot = pivot,
                            degrees = -rotationDegrees
                        )
                        val unrotatedPan = rotateVector(pan, -rotationDegrees)

                        if (zoom != 1f) {
                            val newScale = (localScale * zoom).coerceIn(0.3f, 2.0f)
                            val scaleChange = newScale / localScale
                            offsetX = unrotatedCentroid.x - (unrotatedCentroid.x - offsetX) * scaleChange
                            offsetY = unrotatedCentroid.y - (unrotatedCentroid.y - offsetY) * scaleChange
                            localScale = newScale
                            // ズームレベルに変換して通知
                            val zl = (newScale * 100).toInt().coerceIn(30, 100)
                            onZoomChange((zl / 10) * 10)
                        } else if (pan.x != 0f || pan.y != 0f) {
                            offsetX += unrotatedPan.x
                            offsetY += unrotatedPan.y
                        }
                    }
                }
                // タップ（state変数から毎回計算するのでキー不要）
                .pointerInput(rotationDegrees) {
                    detectTapGestures { offset ->
                        val pivot = Offset(size.width / 2f, size.height / 2f)
                        val unrotatedPosition = rotatePointAroundPivot(
                            point = offset,
                            pivot = pivot,
                            degrees = -rotationDegrees
                        )
                        val cell = findCellAtPosition(unrotatedPosition.x, unrotatedPosition.y)
                        if (cell != null) {
                            handleCellTap(cell.first, cell.second)
                        }
                    }
                }
        ) {
            // コンテナサイズを記録
            containerWidth = size.width
            containerHeight = size.height

            val actualScale = localScale
            val actualCellSize = BASE_CELL_SIZE * actualScale * density.density
            val rotationPivot = Offset(size.width / 2f, size.height / 2f)

            withTransform({
                rotate(degrees = rotationDegrees, pivot = rotationPivot)
            }) {
                // 描画済み結合セルの追跡
                val drawnMergedCells = mutableSetOf<String>()

                // ========== 1. セル背景 + 状態オーバーレイ ==========
                for (row in 1..mapData.maxRow) {
                    for (col in 1..mapData.maxCol) {
                        if (!isInSelectedHall(row, col)) continue
                        val cellKey = "$row-$col"
                        val mergedInfo = mergeMap[cellKey]

                        if (mergedInfo != null) {
                            val mergeKey = "${mergedInfo.startRow}-${mergedInfo.startCol}"
                            if (row == mergedInfo.startRow && col == mergedInfo.startCol &&
                                !drawnMergedCells.contains(mergeKey)) {
                                drawnMergedCells.add(mergeKey)
                                val x = offsetX + colPositions[mergedInfo.startCol]
                                val y = offsetY + rowPositions[mergedInfo.startRow]
                                var mw = 0f
                                for (c in mergedInfo.startCol..mergedInfo.endCol) mw += getColWidth(c)
                                var mh = 0f
                                for (r in mergedInfo.startRow..mergedInfo.endRow) mh += getRowHeight(r)
                                drawCellBackground(x, y, mw, mh, mergeKey, cellMap, cellStates, currentPhase)
                            }
                        } else {
                            val x = offsetX + colPositions[col]
                            val y = offsetY + rowPositions[row]
                            val cw = getColWidth(col)
                            val ch = getRowHeight(row)
                            drawCellBackground(x, y, cw, ch, cellKey, cellMap, cellStates, currentPhase)
                        }
                    }
                }

                // ========== 2. 罫線 ==========
                drawnMergedCells.clear()
                for (row in 1..mapData.maxRow) {
                    for (col in 1..mapData.maxCol) {
                        if (!isInSelectedHall(row, col)) continue
                        val cellKey = "$row-$col"
                        val mergedInfo = mergeMap[cellKey]

                        if (mergedInfo != null) {
                            val mergeKey = "${mergedInfo.startRow}-${mergedInfo.startCol}"
                            if (row == mergedInfo.startRow && col == mergedInfo.startCol &&
                                !drawnMergedCells.contains(mergeKey)) {
                                drawnMergedCells.add(mergeKey)
                                val cell = cellMap[mergeKey] ?: continue
                                val x = offsetX + colPositions[mergedInfo.startCol]
                                val y = offsetY + rowPositions[mergedInfo.startRow]
                                var mw = 0f
                                for (c in mergedInfo.startCol..mergedInfo.endCol) mw += getColWidth(c)
                                var mh = 0f
                                for (r in mergedInfo.startRow..mergedInfo.endRow) mh += getRowHeight(r)
                                drawFocusCellBorders(x, y, mw, mh, cell.borders, actualScale)
                            }
                        } else {
                            val cell = cellMap[cellKey] ?: continue
                            val x = offsetX + colPositions[col]
                            val y = offsetY + rowPositions[row]
                            drawFocusCellBorders(x, y, getColWidth(col), getRowHeight(row), cell.borders, actualScale)
                        }
                    }
                }
            

            // ========== 3. テキスト ==========
            drawnMergedCells.clear()
            for (row in 1..mapData.maxRow) {
                for (col in 1..mapData.maxCol) {
                    if (!isInSelectedHall(row, col)) continue
                    val cellKey = "$row-$col"
                    val mergedInfo = mergeMap[cellKey]

                    if (mergedInfo != null) {
                        val mergeKey = "${mergedInfo.startRow}-${mergedInfo.startCol}"
                        if (row == mergedInfo.startRow && col == mergedInfo.startCol &&
                            !drawnMergedCells.contains(mergeKey)) {
                            drawnMergedCells.add(mergeKey)
                            val cell = cellMap[mergeKey]
                            val textValue = mergedInfo.value ?: cell?.value ?: continue
                            val x = offsetX + colPositions[mergedInfo.startCol]
                            val y = offsetY + rowPositions[mergedInfo.startRow]
                            var mw = 0f
                            for (c in mergedInfo.startCol..mergedInfo.endCol) mw += getColWidth(c)
                            var mh = 0f
                            for (r in mergedInfo.startRow..mergedInfo.endRow) mh += getRowHeight(r)
                            val state = cellStates[mergeKey]
                            drawCellText(
                                x = x,
                                y = y,
                                width = mw,
                                height = mh,
                                text = textValue.toString(),
                                state = state,
                                scale = actualScale,
                                densityScale = density.density,
                                rotationDegrees = rotationDegrees
                            )
                        }
                    } else {
                        val cell = cellMap[cellKey]
                        val textValue = cell?.value ?: continue
                        val x = offsetX + colPositions[col]
                        val y = offsetY + rowPositions[row]
                        val state = cellStates[cellKey]
                        drawCellText(
                            x = x,
                            y = y,
                            width = getColWidth(col),
                            height = getRowHeight(row),
                            text = textValue.toString(),
                            state = state,
                            scale = actualScale,
                            densityScale = density.density,
                            rotationDegrees = rotationDegrees
                        )
                    }
                }
            }

            // ========== 4. 後回し/遅参オーバーレイ ==========
            for ((key, state) in cellStates) {
                if (!state.hasItems) continue
                val (r, c) = key.split("-").map(String::toInt)
                if (!isInSelectedHall(r, c)) continue
                if (r > mapData.maxRow || c > mapData.maxCol) continue
                val cx = offsetX + colPositions[c]
                val cy = offsetY + rowPositions[r]
                val cw = getColWidth(c)
                val ch = getRowHeight(r)

                if (state.hasPostponed && !state.allNone && currentPhase != FocusPhase.POSTPONED) {
                    drawRect(Color.White.copy(alpha = 0.5f), Offset(cx, cy), Size(cw, ch))
                    val iconSize = maxOf(12f * density.density, actualCellSize * 0.4f)
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = Paint().apply {
                            color = COLOR_POSTPONE_OVERLAY_TEXT
                            textSize = iconSize
                            textAlign = Paint.Align.CENTER
                            typeface = Typeface.DEFAULT_BOLD
                            isAntiAlias = true
                        }
                        drawText("後", cx + cw / 2, cy + ch / 2 + iconSize / 3, paint)
                    }
                }
                if (state.hasLate && !state.allNone && currentPhase != FocusPhase.LATE) {
                    drawRect(Color.White.copy(alpha = 0.5f), Offset(cx, cy), Size(cw, ch))
                    val iconSize = maxOf(12f * density.density, actualCellSize * 0.4f)
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = Paint().apply {
                            color = COLOR_LATE_OVERLAY_TEXT
                            textSize = iconSize
                            textAlign = Paint.Align.CENTER
                            typeface = Typeface.DEFAULT_BOLD
                            isAntiAlias = true
                        }
                        drawText("遅", cx + cw / 2, cy + ch / 2 + iconSize / 3, paint)
                    }
                }
            }

            // ========== 5. ルート描画（MapScreen方式） ==========
            if (routeSegments.isNotEmpty()) {
                val baseCellSize = minOf(
                    mapData.defaultColumnWidth,
                    mapData.defaultRowHeight
                ) * localScale * density.density

                // 優先度ごとの色
                fun getPriorityColor(priority: PriorityLevel): Color {
                    return when (priority) {
                        PriorityLevel.HIGHEST -> Color(0xFFEF4444)  // 赤
                        PriorityLevel.PRIORITY -> Color(0xFFF97316)  // オレンジ
                        PriorityLevel.NONE -> Color(0xFF1976D2)      // 青
                    }
                }

                // エッジごとの通過情報（重複検出用）
                val edgeUsage = mutableMapOf<String, MutableSet<PriorityLevel>>()

                fun getEdgeKey(r1: Int, c1: Int, r2: Int, c2: Int): String {
                    return if (r1 < r2 || (r1 == r2 && c1 < c2)) "$r1,$c1-$r2,$c2"
                    else "$r2,$c2-$r1,$c1"
                }

                // 全セグメントのエッジを収集
                routeSegments.forEach { segment ->
                    if (segment.path.size < 2) return@forEach
                    if (segment.isGroupTransition) return@forEach
                    val priority = segment.fromPriority
                    for (i in 0 until segment.path.size - 1) {
                        val p1 = segment.path[i]
                        val p2 = segment.path[i + 1]
                        val key = getEdgeKey(p1.first, p1.second, p2.first, p2.second)
                        edgeUsage.getOrPut(key) { mutableSetOf() }.add(priority)
                    }
                }

                val lineWidth = maxOf(2f, baseCellSize * 0.08f)
                val parallelOffset = maxOf(3f, baseCellSize * 0.12f)

                // 平行線オフセット計算
                fun getOffsetPoints(
                    px1: Float, py1: Float, px2: Float, py2: Float, offset: Float
                ): List<Float> {
                    val dx = px2 - px1
                    val dy = py2 - py1
                    val len = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (len == 0f) return listOf(px1, py1, px2, py2)
                    val nx = -dy / len
                    val ny = dx / len
                    return listOf(
                        px1 + nx * offset, py1 + ny * offset,
                        px2 + nx * offset, py2 + ny * offset
                    )
                }

                // セグメントを描画
                routeSegments.forEach { segment ->
                    if (segment.path.size < 2) return@forEach

                    val isTransition = segment.isGroupTransition
                    val segmentPriority = segment.fromPriority
                    val baseColor = if (isTransition) Color(0xFF9CA3AF) else getPriorityColor(segmentPriority)

                    for (i in 0 until segment.path.size - 1) {
                        val p1 = segment.path[i]
                        val p2 = segment.path[i + 1]
                        val edgeKey = getEdgeKey(p1.first, p1.second, p2.first, p2.second)

                        val px1 = offsetX + colPositions.getOrElse(p1.second) { 0f } + getColWidth(p1.second) / 2
                        val py1 = offsetY + rowPositions.getOrElse(p1.first) { 0f } + getRowHeight(p1.first) / 2
                        val px2 = offsetX + colPositions.getOrElse(p2.second) { 0f } + getColWidth(p2.second) / 2
                        val py2 = offsetY + rowPositions.getOrElse(p2.first) { 0f } + getRowHeight(p2.first) / 2

                        if (isTransition) {
                            drawLine(
                                color = baseColor,
                                start = Offset(px1, py1),
                                end = Offset(px2, py2),
                                strokeWidth = lineWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            continue
                        }

                        val usedPriorities = edgeUsage[edgeKey]
                        val isOverlapping = usedPriorities != null && usedPriorities.size > 1

                        if (isOverlapping) {
                            val priorities = usedPriorities!!.toList().sortedBy {
                                when (it) {
                                    PriorityLevel.HIGHEST -> 0
                                    PriorityLevel.PRIORITY -> 1
                                    PriorityLevel.NONE -> 2
                                }
                            }
                            val myIndex = priorities.indexOf(segmentPriority)
                            val totalCount = priorities.size
                            val offset = (myIndex - (totalCount - 1) / 2f) * parallelOffset
                            val offsetPoints = getOffsetPoints(px1, py1, px2, py2, offset)

                            drawLine(
                                color = baseColor,
                                start = Offset(offsetPoints[0], offsetPoints[1]),
                                end = Offset(offsetPoints[2], offsetPoints[3]),
                                strokeWidth = lineWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        } else {
                            drawLine(
                                color = baseColor,
                                start = Offset(px1, py1),
                                end = Offset(px2, py2),
                                strokeWidth = lineWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // ========== 6. 次の目的地マーカー ==========
            if (nextCellCoords != null) {
                val (nr, nc) = nextCellCoords
                val nx = offsetX + colPositions.getOrElse(nc) { 0f }
                val ny = offsetY + rowPositions.getOrElse(nr) { 0f }
                val ncw = getColWidth(nc)
                val nch = getRowHeight(nr)

                // オレンジ枠
                val borderWidth = maxOf(3f, actualCellSize * 0.12f)
                drawRect(
                    color = COLOR_NEXT_BORDER,
                    topLeft = Offset(nx - 1, ny - 1),
                    size = Size(ncw + 2, nch + 2),
                    style = Stroke(width = borderWidth)
                )

                // 🚩マーカー
                val markerSize = maxOf(14f * density.density, actualCellSize * 0.45f)
                drawContext.canvas.nativeCanvas.apply {
                    val paint = Paint().apply {
                        textSize = markerSize
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText("🚩", nx + ncw / 2, ny - 2 * density.density, paint)
                }
            }

            // ========== 7. 現在位置マーカー ==========
            if (currentCellCoords != null) {
                val (cr, cc) = currentCellCoords
                val cx = offsetX + colPositions.getOrElse(cc) { 0f }
                val cy = offsetY + rowPositions.getOrElse(cr) { 0f }
                val ccw = getColWidth(cc)
                val cch = getRowHeight(cr)

                // 緑枠
                val borderWidth = maxOf(4f, actualCellSize * 0.15f)
                drawRect(
                    color = COLOR_CURRENT_BORDER,
                    topLeft = Offset(cx - 2, cy - 2),
                    size = Size(ccw + 4, cch + 4),
                    style = Stroke(width = borderWidth)
                )

                // 📍マーカー
                val markerSize = maxOf(16f * density.density, actualCellSize * 0.5f)
                drawContext.canvas.nativeCanvas.apply {
                    val paint = Paint().apply {
                        textSize = markerSize
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText("📍", cx + ccw / 2, cy - 2 * density.density, paint)
                }
            }
        }
    }
}

}

// ========== セル背景描画 ==========

private fun DrawScope.drawCellBackground(
    x: Float, y: Float, width: Float, height: Float,
    cellKey: String,
    cellMap: Map<String, CellData>,
    cellStates: Map<String, FocusCellState>,
    currentPhase: FocusPhase
) {
    val cell = cellMap[cellKey]
    val state = cellStates[cellKey]

    // 元の背景色
    if (cell?.backgroundColor != null) {
        drawRect(Color(cell.backgroundColor), Offset(x, y), Size(width, height))
    }

    // 集中モード状態オーバーレイ
    if (state != null && state.hasItems) {
        val overlayColor = when {
            state.isCurrentPosition -> COLOR_CURRENT_BG
            state.isNextDestination -> COLOR_NEXT_BG
            state.isVisited -> COLOR_VISITED_BG
            state.hasPostponed && currentPhase != FocusPhase.POSTPONED -> COLOR_POSTPONED_BG
            state.hasLate && currentPhase != FocusPhase.LATE -> COLOR_LATE_BG
            state.allNone -> COLOR_UNVISITED_BG
            else -> null
        }
        if (overlayColor != null) {
            drawRect(overlayColor, Offset(x, y), Size(width, height))
        }
    }
}

// ========== 罫線描画 ==========

private fun DrawScope.drawFocusCellBorders(
    x: Float, y: Float, width: Float, height: Float,
    borders: CellBorders, scale: Float
) {
    fun drawBorder(fromX: Float, fromY: Float, toX: Float, toY: Float, border: CellBorderStyle) {
        if (border.style == BorderWeight.NONE) return
        val strokeWidth = when (border.style) {
            BorderWeight.THIN -> 1f * scale
            BorderWeight.MEDIUM -> 2f * scale
            BorderWeight.THICK -> 3f * scale
            BorderWeight.DOUBLE -> 3f * scale
            BorderWeight.NONE -> 0f
        }.coerceAtLeast(0.5f)
        drawLine(Color(border.color), Offset(fromX, fromY), Offset(toX, toY), strokeWidth)
    }

    drawBorder(x, y, x + width, y, borders.top)
    drawBorder(x + width, y, x + width, y + height, borders.right)
    drawBorder(x, y + height, x + width, y + height, borders.bottom)
    drawBorder(x, y, x, y + height, borders.left)
}

// ========== テキスト描画 ==========

private fun DrawScope.drawCellText(
    x: Float, y: Float, width: Float, height: Float,
    text: String,
    state: FocusCellState?,
    scale: Float,
    densityScale: Float,
    rotationDegrees: Float
) {
    val isNumeric = text.matches(Regex("^\\d+$"))
    val cellSize = minOf(width, height)
    val fontSize = if (isNumeric) {
        minOf(cellSize * 0.45f, 14f * densityScale)
    } else {
        minOf(cellSize * 0.4f, 12f * densityScale)
    }.coerceAtLeast(8f * densityScale)

    // 状態に応じたテキスト色
    val textColor = when {
        state?.isCurrentPosition == true -> COLOR_TEXT_CURRENT
        state?.isVisited == true -> COLOR_TEXT_VISITED
        state?.hasItems == true -> COLOR_TEXT_HAS_ITEMS
        else -> COLOR_TEXT_DEFAULT
    }

    val centerX = x + width / 2f
    val centerY = y + height / 2f
    val baselineY = centerY + fontSize / 3f

    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            color = textColor
            this.textSize = fontSize
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        save()
        if (rotationDegrees != 0f) {
            rotate(-rotationDegrees, centerX, centerY)
        }
        drawText(text, centerX, baselineY, paint)
        restore()
    }
}

// ========== データクラス ==========

private data class FocusHallBoundsPixels(
    val minX: Float, val maxX: Float,
    val minY: Float, val maxY: Float
)
