package com.example.eventshoppingplanner.domain.model

/**
 * A*経路探索用のノード
 */
data class PathNode(
    val row: Int,
    val col: Int,
    val g: Float,           // スタートからのコスト
    val h: Float,           // ゴールまでの推定コスト（ヒューリスティック）
    val f: Float,           // g + h
    val parent: PathNode?   // 親ノード（経路復元用）
)

/**
 * ルートセグメント（2点間の経路）
 */
data class RouteSegment(
    val fromRow: Int,
    val fromCol: Int,
    val toRow: Int,
    val toCol: Int,
    val path: List<Pair<Int, Int>>,  // 経路のセル座標リスト
    val fromPriority: PriorityLevel,
    val toPriority: PriorityLevel
) {
    /**
     * グループ間接続かどうか（優先度が異なる場合）
     */
    val isGroupTransition: Boolean
        get() = fromPriority != toPriority
}

/**
 * 訪問ポイント（描画用）
 */
data class VisitPoint(
    val row: Int,
    val col: Int,
    val order: Int,                   // 訪問順序（0から）
    val priorityLevel: PriorityLevel,
    val itemId: String
)