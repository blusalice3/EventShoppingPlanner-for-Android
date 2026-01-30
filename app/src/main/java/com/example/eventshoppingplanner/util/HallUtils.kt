package com.example.eventshoppingplanner.util

import com.example.eventshoppingplanner.domain.model.BlockDefinition
import com.example.eventshoppingplanner.domain.model.HallDefinition
import com.example.eventshoppingplanner.domain.model.Vertex
import kotlin.math.atan2

/**
 * ホール定義関連のユーティリティ関数
 */
object HallUtils {

    /**
     * ホール色の定義
     */
    val HALL_COLORS = listOf(
        0xFFFFE0B2, // Orange 100
        0xFFFFCCBC, // Deep Orange 100
        0xFFD7CCC8, // Brown 100
        0xFFCFD8DC, // Blue Grey 100
        0xFFB2DFDB, // Teal 100
        0xFFC8E6C9, // Green 100
        0xFFDCEDC8, // Light Green 100
        0xFFF0F4C3, // Lime 100
        0xFFFFF9C4, // Yellow 100
        0xFFFFECB3, // Amber 100
        0xFFE1BEE7, // Purple 100
        0xFFD1C4E9, // Deep Purple 100
    )

    /**
     * ホールの範囲（bounding box）
     */
    data class HallBounds(
        val minRow: Int,
        val maxRow: Int,
        val minCol: Int,
        val maxCol: Int
    ) {
        val width: Int get() = maxCol - minCol + 1
        val height: Int get() = maxRow - minRow + 1
    }

    /**
     * 頂点リストから多角形を形成（重心からの角度でソート）
     * 凸包ではなく、選択した全ての頂点を含む多角形を形成
     * @param points 頂点リスト
     * @return 時計回りに並んだ頂点リスト
     */
    fun computeConvexHull(points: List<Vertex>): List<Vertex> {
        if (points.size < 3) return points

        // 重複を除去
        val uniquePoints = points.distinctBy { "${it.row}-${it.col}" }
        if (uniquePoints.size < 3) return uniquePoints

        // 重心を計算
        val centerRow = uniquePoints.map { it.row }.average()
        val centerCol = uniquePoints.map { it.col }.average()

        // 重心からの角度でソート（時計回り）
        val sorted = uniquePoints.sortedBy { vertex ->
            atan2(
                (vertex.row - centerRow),
                (vertex.col - centerCol)
            )
        }

        return sorted
    }

    /**
     * 外積（反時計回りなら正、時計回りなら負）
     */
    private fun crossProduct(o: Vertex, a: Vertex, b: Vertex): Int {
        return (a.col - o.col) * (b.row - o.row) - (a.row - o.row) * (b.col - o.col)
    }

    /**
     * 2点間の距離の2乗
     */
    private fun distanceSquared(a: Vertex, b: Vertex): Int {
        val dx = b.col - a.col
        val dy = b.row - a.row
        return dx * dx + dy * dy
    }

    /**
     * 点が多角形内にあるか判定（Ray Casting Algorithm）
     * @param row 判定する点の行
     * @param col 判定する点の列
     * @param vertices 多角形の頂点リスト
     * @return 多角形内にある場合true
     */
    fun isPointInPolygon(
        row: Float,
        col: Float,
        vertices: List<Vertex>
    ): Boolean {
        if (vertices.size < 3) return false

        var inside = false
        val n = vertices.size

        var j = n - 1
        for (i in 0 until n) {
            val vi = vertices[i]
            val vj = vertices[j]

            if (((vi.col > col) != (vj.col > col)) &&
                (row < (vj.row - vi.row) * (col - vi.col) / (vj.col - vi.col).toFloat() + vi.row)) {
                inside = !inside
            }
            j = i
        }

        return inside
    }

    /**
     * 点が多角形内にあるか判定（Int版）
     */
    fun isPointInPolygon(row: Int, col: Int, vertices: List<Vertex>): Boolean {
        return isPointInPolygon(row.toFloat(), col.toFloat(), vertices)
    }

    /**
     * ブロックが属するホールのIDを取得
     * @param block ブロック定義
     * @param halls ホール定義リスト
     * @return ホールID（属さない場合はnull）
     */
    fun getBlockHallId(block: BlockDefinition, halls: List<HallDefinition>): String? {
        // ブロックの中心点を計算
        val centerRow = (block.startRow + block.endRow) / 2f
        val centerCol = (block.startCol + block.endCol) / 2f

        for (hall in halls) {
            if (hall.vertices.size >= 3 &&
                isPointInPolygon(centerRow, centerCol, hall.vertices)) {
                return hall.id
            }
        }
        return null
    }

    /**
     * ホールに含まれるブロックを取得
     * @param hall ホール定義
     * @param blocks ブロック定義リスト
     * @return ホール内のブロックリスト
     */
    fun getBlocksInHall(hall: HallDefinition, blocks: List<BlockDefinition>): List<BlockDefinition> {
        if (hall.vertices.size < 3) return emptyList()

        return blocks.filter { block ->
            val centerRow = (block.startRow + block.endRow) / 2f
            val centerCol = (block.startCol + block.endCol) / 2f
            isPointInPolygon(centerRow, centerCol, hall.vertices)
        }
    }

    /**
     * ホールの範囲（bounding box）を計算
     * @param hall ホール定義
     * @return ホールの範囲
     */
    fun getHallBounds(hall: HallDefinition): HallBounds? {
        if (hall.vertices.size < 3) return null

        val minRow = hall.vertices.minOf { it.row }
        val maxRow = hall.vertices.maxOf { it.row }
        val minCol = hall.vertices.minOf { it.col }
        val maxCol = hall.vertices.maxOf { it.col }

        return HallBounds(minRow, maxRow, minCol, maxCol)
    }

    /**
     * 次に使用すべきホール色を取得
     * @param existingHalls 既存のホールリスト
     * @return 使用されていない色、または最も使用回数が少ない色
     */
    fun getNextHallColor(existingHalls: List<HallDefinition>): Long {
        val usedColors = existingHalls.map { it.color }.toSet()

        // 未使用の色があればそれを返す
        HALL_COLORS.forEach { color ->
            if (color !in usedColors) {
                return color
            }
        }

        // 全て使用済みの場合は最初の色を返す
        return HALL_COLORS.first()
    }
}