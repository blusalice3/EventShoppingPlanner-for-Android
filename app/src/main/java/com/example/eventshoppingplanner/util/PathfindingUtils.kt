package com.example.eventshoppingplanner.util

import com.example.eventshoppingplanner.domain.model.CellData
import com.example.eventshoppingplanner.domain.model.DayMapData
import com.example.eventshoppingplanner.domain.model.PathNode
import com.example.eventshoppingplanner.domain.model.PriorityLevel
import com.example.eventshoppingplanner.domain.model.RouteSegment
import com.example.eventshoppingplanner.domain.model.VisitPoint
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 経路探索ユーティリティ
 */
object PathfindingUtils {

    // 8方向の移動（上下左右 + 斜め4方向）
    private val DIRECTIONS = listOf(
        Triple(-1, 0, 1.0f),    // 上
        Triple(1, 0, 1.0f),     // 下
        Triple(0, -1, 1.0f),    // 左
        Triple(0, 1, 1.0f),     // 右
        Triple(-1, -1, 1.4f),   // 左上
        Triple(-1, 1, 1.4f),    // 右上
        Triple(1, -1, 1.4f),    // 左下
        Triple(1, 1, 1.4f)      // 右下
    )

    /**
     * A*アルゴリズムで2点間の最短経路を探索
     *
     * @param mapData マップデータ
     * @param startRow 開始行
     * @param startCol 開始列
     * @param endRow 終了行
     * @param endCol 終了列
     * @param blockNameCells ブロック名セルのキーセット（"row-col"形式）
     * @return 経路のセル座標リスト
     */
    fun findPath(
        mapData: DayMapData,
        startRow: Int,
        startCol: Int,
        endRow: Int,
        endCol: Int,
        blockNameCells: Set<String>
    ): List<Pair<Int, Int>> {
        val maxRow = mapData.maxRow
        val maxCol = mapData.maxCol

        // セルマップを作成（高速アクセス用）
        val cellsMap = mapData.cells.associateBy { "${it.row}-${it.col}" }

        // クローズドセット
        val closedSet = mutableSetOf<String>()

        // オープンリスト（優先度付きキュー、f値が小さい順）
        val openList = PriorityQueue<PathNode>(compareBy { it.f })

        // スタートノード
        val startNode = PathNode(
            row = startRow,
            col = startCol,
            g = 0f,
            h = heuristic(startRow, startCol, endRow, endCol),
            f = heuristic(startRow, startCol, endRow, endCol),
            parent = null
        )
        openList.add(startNode)

        // オープンリスト内のノードを高速検索するためのマップ
        val openMap = mutableMapOf<String, PathNode>()
        openMap["$startRow-$startCol"] = startNode

        // 無限ループ防止
        val maxIterations = maxRow * maxCol * 2
        var iterations = 0

        while (openList.isNotEmpty() && iterations < maxIterations) {
            iterations++

            // f値が最小のノードを取得
            val currentNode = openList.poll() ?: break
            val currentKey = "${currentNode.row}-${currentNode.col}"

            // 既に処理済みならスキップ
            if (closedSet.contains(currentKey)) continue

            // ゴールに到達
            if (currentNode.row == endRow && currentNode.col == endCol) {
                return reconstructPath(currentNode)
            }

            closedSet.add(currentKey)
            openMap.remove(currentKey)

            // 隣接ノードを探索
            for ((dr, dc, cost) in DIRECTIONS) {
                val newRow = currentNode.row + dr
                val newCol = currentNode.col + dc
                val newKey = "$newRow-$newCol"

                // 既に処理済みならスキップ
                if (closedSet.contains(newKey)) continue

                // 通過可能かチェック（ゴールセルは例外的に通過可能）
                val isGoal = newRow == endRow && newCol == endCol
                if (!isGoal && !isPassableCell(cellsMap, newRow, newCol, maxRow, maxCol, blockNameCells)) {
                    continue
                }

                // 斜め移動の場合、両隣のセルが通過可能かチェック
                if (abs(dr) == 1 && abs(dc) == 1) {
                    val side1Passable = isPassableCell(
                        cellsMap, currentNode.row + dr, currentNode.col, maxRow, maxCol, blockNameCells
                    )
                    val side2Passable = isPassableCell(
                        cellsMap, currentNode.row, currentNode.col + dc, maxRow, maxCol, blockNameCells
                    )
                    if (!side1Passable || !side2Passable) continue
                }

                val g = currentNode.g + cost
                val h = heuristic(newRow, newCol, endRow, endCol)
                val f = g + h

                // 既にオープンリストにあるか確認
                val existingNode = openMap[newKey]
                if (existingNode != null) {
                    // より良い経路が見つかった場合は更新
                    if (g < existingNode.g) {
                        openList.remove(existingNode)
                        val updatedNode = PathNode(newRow, newCol, g, h, f, currentNode)
                        openList.add(updatedNode)
                        openMap[newKey] = updatedNode
                    }
                } else {
                    // 新しいノードを追加
                    val newNode = PathNode(newRow, newCol, g, h, f, currentNode)
                    openList.add(newNode)
                    openMap[newKey] = newNode
                }
            }
        }

        // 経路が見つからない場合は直線で結ぶ
        return listOf(startRow to startCol, endRow to endCol)
    }

    /**
     * 経路を復元
     */
    private fun reconstructPath(endNode: PathNode): List<Pair<Int, Int>> {
        val path = mutableListOf<Pair<Int, Int>>()
        var node: PathNode? = endNode
        while (node != null) {
            path.add(0, node.row to node.col)
            node = node.parent
        }
        return path
    }

    /**
     * マンハッタン距離（ヒューリスティック）
     */
    private fun heuristic(row1: Int, col1: Int, row2: Int, col2: Int): Float {
        return (abs(row1 - row2) + abs(col1 - col2)).toFloat()
    }

    /**
     * セルが通過可能かどうかを判定
     */
    private fun isPassableCell(
        cellsMap: Map<String, CellData>,
        row: Int,
        col: Int,
        maxRow: Int,
        maxCol: Int,
        blockNameCells: Set<String>
    ): Boolean {
        // 範囲外は通過不可
        if (row < 1 || col < 1 || row > maxRow || col > maxCol) return false

        val key = "$row-$col"
        val cell = cellsMap[key]

        // セルが存在しない場合は通過可能
        if (cell == null) return true

        // ブロック名セルは通過可能
        if (blockNameCells.contains(key)) return true

        // 数値セル（スペース番号）は通過不可
        val value = cell.value
        if (value != null) {
            if (value.toIntOrNull() != null) return false
        }

        // 塗りつぶしセル（背景色あり、白以外）は通過不可
        val bgColor = cell.backgroundColor
        if (bgColor != null && bgColor != 0xFFFFFFFF) return false

        return true
    }

    /**
     * 訪問先間のルートセグメントを生成
     *
     * @param mapData マップデータ
     * @param visitPoints 訪問ポイントリスト（順序通り）
     * @param blockNameCells ブロック名セルのキーセット
     * @return ルートセグメントリスト
     */
    fun generateRouteSegments(
        mapData: DayMapData,
        visitPoints: List<VisitPoint>,
        blockNameCells: Set<String>
    ): List<RouteSegment> {
        if (visitPoints.size < 2) return emptyList()

        val segments = mutableListOf<RouteSegment>()

        for (i in 0 until visitPoints.size - 1) {
            val from = visitPoints[i]
            val to = visitPoints[i + 1]

            val path = findPath(
                mapData,
                from.row,
                from.col,
                to.row,
                to.col,
                blockNameCells
            )

            // 経路を簡略化
            val simplifiedPath = simplifyPath(path)

            segments.add(
                RouteSegment(
                    fromRow = from.row,
                    fromCol = from.col,
                    toRow = to.row,
                    toCol = to.col,
                    path = simplifiedPath,
                    fromPriority = from.priorityLevel,
                    toPriority = to.priorityLevel
                )
            )
        }

        return segments
    }

    /**
     * ブロック名セルのキーセットを生成
     *
     * @param mapData マップデータ
     * @return ブロック名セルのキーセット（"row-col"形式）
     */
    fun generateBlockNameCells(mapData: DayMapData): Set<String> {
        val blockNameCells = mutableSetOf<String>()
        val cellsMap = mapData.cells.associateBy { "${it.row}-${it.col}" }

        mapData.blocks.forEach { block ->
            for (r in block.startRow..block.endRow) {
                for (c in block.startCol..block.endCol) {
                    val cell = cellsMap["$r-$c"]
                    // 数値でない文字列を含むセルはブロック名セル
                    if (cell?.value != null && cell.value.toIntOrNull() == null) {
                        blockNameCells.add("$r-$c")
                    }
                }
            }
        }

        return blockNameCells
    }

    /**
     * Douglas-Peuckerアルゴリズムで経路を簡略化
     *
     * @param path 経路のセル座標リスト
     * @param tolerance 許容誤差（デフォルト: 0.5）
     * @return 簡略化された経路
     */
    fun simplifyPath(
        path: List<Pair<Int, Int>>,
        tolerance: Float = 0.5f
    ): List<Pair<Int, Int>> {
        if (path.size <= 2) return path

        // 最も遠い点を見つける
        var maxDistance = 0f
        var maxIndex = 0

        val start = path.first()
        val end = path.last()

        for (i in 1 until path.size - 1) {
            val distance = pointToLineDistance(
                path[i].first.toFloat(),
                path[i].second.toFloat(),
                start.first.toFloat(),
                start.second.toFloat(),
                end.first.toFloat(),
                end.second.toFloat()
            )

            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = i
            }
        }

        // 許容範囲を超える場合は分割して再帰
        if (maxDistance > tolerance) {
            val left = simplifyPath(path.subList(0, maxIndex + 1), tolerance)
            val right = simplifyPath(path.subList(maxIndex, path.size), tolerance)

            return left.dropLast(1) + right
        }

        // 許容範囲内の場合は始点と終点のみ
        return listOf(start, end)
    }

    /**
     * 点から線分への距離
     */
    private fun pointToLineDistance(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1

        if (dx == 0f && dy == 0f) {
            return sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        }

        val t = maxOf(0f, minOf(1f, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))

        val nearestX = x1 + t * dx
        val nearestY = y1 + t * dy

        return sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY))
    }
}