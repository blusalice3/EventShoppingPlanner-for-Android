package com.example.eventshoppingplanner.domain.model

/**
 * 罫線のスタイル
 */
data class CellBorderStyle(
    val style: BorderWeight = BorderWeight.NONE,
    val color: Long = 0xFF000000
)

/**
 * 罫線の太さ
 */
enum class BorderWeight {
    NONE,
    THIN,      // 細い線
    MEDIUM,    // 中くらい
    THICK,     // 太い線
    DOUBLE     // 二重線
}

/**
 * セルの4辺の罫線
 */
data class CellBorders(
    val top: CellBorderStyle = CellBorderStyle(),
    val right: CellBorderStyle = CellBorderStyle(),
    val bottom: CellBorderStyle = CellBorderStyle(),
    val left: CellBorderStyle = CellBorderStyle()
)

/**
 * フォント情報
 */
data class FontInfo(
    val size: Float = 11f,
    val color: Long = 0xFF000000,
    val bold: Boolean = false,
    val name: String = "Arial"
)

/**
 * セルのスタイル情報
 */
data class CellStyle(
    val fontInfo: FontInfo = FontInfo(),
    val backgroundColor: Long? = null,
    val borders: CellBorders = CellBorders(),
    val horizontalAlignment: String = "center",
    val verticalAlignment: String = "center"
)

/**
 * セルデータ
 */
data class CellData(
    val row: Int,
    val col: Int,
    val value: String? = null,
    val backgroundColor: Long? = null,
    val borders: CellBorders = CellBorders(),
    val isMerged: Boolean = false,
    val mergeParentRow: Int? = null,
    val mergeParentCol: Int? = null,
    val isVerticalText: Boolean = false,
    val styleIndex: Int = 0,
    val fontInfo: FontInfo = FontInfo()
)

/**
 * 結合セル情報
 */
data class MergedCellInfo(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
    val value: String? = null
)

/**
 * 数値セル（スペース番号）情報
 */
data class NumberCellInfo(
    val row: Int,
    val col: Int,
    val value: Int
)

/**
 * セルグループタイプ
 */
enum class CellGroupType {
    RANGE,      // 範囲指定（2点間の矩形）
    INDIVIDUAL  // 個別セル指定
}

/**
 * セルグループ（壁ブロック/複数範囲ブロック用）
 */
data class CellGroup(
    val type: CellGroupType = CellGroupType.RANGE,
    // RANGEタイプ用
    val startRow: Int = 0,
    val startCol: Int = 0,
    val endRow: Int = 0,
    val endCol: Int = 0,
    // INDIVIDUALタイプ用
    val cells: List<Pair<Int, Int>> = emptyList()
)

/**
 * ブロック定義
 */
data class BlockDefinition(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
    val numberCells: List<NumberCellInfo> = emptyList(),
    val color: Long = 0xFFE3F2FD,
    val isAutoDetected: Boolean = true,
    val isWallBlock: Boolean = false,
    val cellGroups: List<CellGroup> = emptyList()
)

/**
 * 列情報
 */
data class ColumnInfo(
    val index: Int,
    val width: Float  // ピクセル単位
)

/**
 * 行情報
 */
data class RowInfo(
    val index: Int,
    val height: Float  // ピクセル単位
)

/**
 * 日別マップデータ
 */
data class DayMapData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val eventId: String,
    val dayName: String,
    val sheetName: String,
    val maxRow: Int,
    val maxCol: Int,
    val cells: List<CellData>,
    val mergedCells: List<MergedCellInfo>,
    val blocks: List<BlockDefinition>,
    val columnWidths: Map<Int, Float> = emptyMap(),  // 列インデックス -> 幅（ピクセル）
    val rowHeights: Map<Int, Float> = emptyMap(),    // 行インデックス -> 高さ（ピクセル）
    val defaultColumnWidth: Float = 21f,  // デフォルト列幅（ピクセル）
    val defaultRowHeight: Float = 15f,    // デフォルト行高さ（ピクセル）
    val styles: List<CellStyle> = emptyList()  // スタイル配列
)

/**
 * マップ上のセルの状態
 */
enum class MapCellState {
    DEFAULT,      // 通常
    HAS_ITEMS,    // アイテムあり（未購入）
    PARTIAL_VISIT,// 一部訪問済み
    ALL_VISIT,    // 全て購入済み
    PRIORITY      // 優先アイテムあり
}

/**
 * マップ表示用のセル情報
 */
data class MapDisplayCell(
    val row: Int,
    val col: Int,
    val cellData: CellData?,
    val state: MapCellState = MapCellState.DEFAULT,
    val matchingItemIds: List<String> = emptyList()
)

/**
 * ズームレベル
 */
enum class ZoomLevel(val scale: Float, val displayName: String) {
    ZOOM_25(0.25f, "25%"),
    ZOOM_30(0.30f, "30%"),
    ZOOM_50(0.50f, "50%"),
    ZOOM_75(0.75f, "75%"),
    ZOOM_100(1.0f, "100%"),
    ZOOM_125(1.25f, "125%"),
    ZOOM_150(1.5f, "150%")
}

// ===== ホール定義関連 =====

/**
 * 頂点座標
 */
data class Vertex(
    val row: Int,
    val col: Int
)

/**
 * ホール定義（多角形エリア）
 */
data class HallDefinition(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val vertices: List<Vertex>,  // 4〜6個の頂点（凸包計算後の順序）
    val color: Long = 0xFFFFE0B2  // 表示色（ARGB）
)