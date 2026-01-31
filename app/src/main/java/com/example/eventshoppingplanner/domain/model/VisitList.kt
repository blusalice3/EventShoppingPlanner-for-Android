package com.example.eventshoppingplanner.domain.model

/**
 * 訪問先リストのドメインモデル
 */
data class VisitList(
    val eventId: String,
    val dayName: String,
    val itemIds: List<String>  // 訪問順に並んだアイテムID
)

/**
 * グループ（ホール×優先度の組み合わせ）
 */
data class VisitGroup(
    val groupId: String,  // "${hallId}_${priorityLevel}" または "undefined_${priorityLevel}"
    val hallId: String?,  // null = ホール未定義
    val hallName: String,  // 表示名
    val priorityLevel: PriorityLevel,
    val items: List<ShoppingItem>  // グループ内のアイテム（訪問順）
)

/**
 * ホール順序のドメインモデル
 */
data class HallOrder(
    val eventId: String,
    val dayName: String,
    val groupOrder: List<String>  // グループIDの順序
)

/**
 * グループIDを生成
 */
fun createGroupId(hallId: String?, priorityLevel: PriorityLevel): String {
    val hallPart = hallId ?: "undefined"
    return "${hallPart}_${priorityLevel.name}"
}

/**
 * グループIDからホールIDと優先度を抽出
 */
fun parseGroupId(groupId: String): Pair<String?, PriorityLevel> {
    val lastUnderscoreIndex = groupId.lastIndexOf('_')
    if (lastUnderscoreIndex == -1) {
        return null to PriorityLevel.NONE
    }
    val hallPart = groupId.substring(0, lastUnderscoreIndex)
    val priorityPart = groupId.substring(lastUnderscoreIndex + 1)

    val hallId = if (hallPart == "undefined") null else hallPart
    val priority = try {
        PriorityLevel.valueOf(priorityPart)
    } catch (e: IllegalArgumentException) {
        PriorityLevel.NONE
    }

    return hallId to priority
}