package com.example.eventshoppingplanner.util

import android.util.Base64
import android.util.Log
import com.example.eventshoppingplanner.data.local.entity.DayModeEntity
import com.example.eventshoppingplanner.data.local.entity.EventEntity
import com.example.eventshoppingplanner.data.local.entity.ExecuteListEntity
import com.example.eventshoppingplanner.data.local.entity.HallDefinitionEntity
import com.example.eventshoppingplanner.data.local.entity.HallOrderEntity
import com.example.eventshoppingplanner.data.local.entity.MapDataEntity
import com.example.eventshoppingplanner.data.local.entity.ShoppingItemEntity
import com.example.eventshoppingplanner.data.local.entity.VisitListEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.GZIPInputStream

/**
 * XLSX エクスポート/インポート ユーティリティ
 *
 * WEB版 (exportImport.ts) と互換性のあるXLSXフォーマットで読み書きする。
 *
 * シート構成:
 *   1. アイテムデータ (必須) - ShoppingItem一覧
 *   2. メタデータ         - バージョン・イベント名・URL等
 *   3. 配置情報           - 実行列アイテムID・日別モード
 *   4. マップデータ       - DayMapData JSON
 *   5. ルート情報         - VisitList・ホール定義・ホール順序
 */
object XlsxExportImport {

    private const val TAG = "XlsxExportImport"
    private const val EXPORT_VERSION = "2.0"
    private const val CELL_MAX_LENGTH = 32000 // Excel上限32767、余裕を持たせる

    // シート名（WEB版と完全一致）
    private const val SHEET_ITEMS = "アイテムデータ"
    private const val SHEET_METADATA = "メタデータ"
    private const val SHEET_LAYOUT = "配置情報"
    private const val SHEET_MAP_DATA = "マップデータ"
    private const val SHEET_ROUTE = "ルート情報"

    private val gson = Gson()

    enum class ExportFormat {
        FULL,
        SIMPLE
    }

    data class ExportOptions(
        val includeItems: Boolean = true, // 互換維持のため常に出力する
        val includeLayoutInfo: Boolean = true,
        val includeMapData: Boolean = true,
        val includeBlockDefinitions: Boolean = true,
        val includeRouteInfo: Boolean = true,
        val format: ExportFormat = ExportFormat.FULL
    )

    // =========================================================================
    // WEB版 ←→ Android版 ステータス変換
    // =========================================================================

    /**
     * WEB版PurchaseStatus (PascalCase) ←→ Android版 (UPPER_SNAKE_CASE) の変換マップ
     *
     * WEB:     None  | Purchased | SoldOut  | Absent | Postpone | Late
     * Android: NONE  | PURCHASED | SOLD_OUT | ABSENT | POSTPONE | LATE
     */
    private val webToAndroidStatus = mapOf(
        "None" to "NONE",
        "Purchased" to "PURCHASED",
        "SoldOut" to "SOLD_OUT",
        "Absent" to "ABSENT",
        "Postpone" to "POSTPONE",
        "Late" to "LATE"
    )
    private val androidToWebStatus = webToAndroidStatus.entries.associate { (k, v) -> v to k }

    private fun purchaseStatusToWeb(androidStatus: String): String {
        return androidToWebStatus[androidStatus] ?: "None"
    }

    private fun purchaseStatusFromWeb(webStatus: String): String {
        // WEB版PascalCase → Android版UPPER_SNAKE_CASE
        return webToAndroidStatus[webStatus]
        // Android版がそのまま入っている場合（Android→Android）
            ?: if (webStatus.uppercase() in webToAndroidStatus.values) webStatus.uppercase()
            else "NONE"
    }

    /**
     * WEB版priorityLevel (lowercase) ←→ Android版 (UPPER_CASE)
     * WEB:     none | priority | highest | (undefined→空文字)
     * Android: NONE | PRIORITY | HIGHEST
     */
    private fun priorityLevelToWeb(androidLevel: String): String {
        return androidLevel.lowercase()
    }

    private fun priorityLevelFromWeb(webLevel: String): String {
        return when (webLevel.lowercase()) {
            "priority" -> "PRIORITY"
            "highest" -> "HIGHEST"
            "", "none" -> "NONE"
            else -> webLevel.uppercase().ifEmpty { "NONE" }
        }
    }

    /**
     * WEB版protectionLevel ←→ Android版
     * WEB: full | deletable | none | (undefined→空文字)
     */
    private fun protectionLevelToWeb(androidLevel: String): String {
        return androidLevel.lowercase()
    }

    private fun protectionLevelFromWeb(webLevel: String): String {
        return when (webLevel.lowercase()) {
            "full" -> "FULL"
            "deletable" -> "DELETABLE"
            "", "none" -> "NONE"
            else -> webLevel.uppercase().ifEmpty { "NONE" }
        }
    }

    /**
     * WEB版source ←→ Android版
     * WEB: spreadsheet | app | (undefined→空文字)
     */
    private fun sourceToWeb(androidSource: String): String {
        return androidSource.lowercase()
    }

    private fun sourceFromWeb(webSource: String): String {
        return when (webSource.lowercase()) {
            "spreadsheet" -> "SPREADSHEET"
            "app" -> "APP"
            "" -> "APP"
            else -> webSource.uppercase().ifEmpty { "APP" }
        }
    }

    // =========================================================================
    // 色変換 (Long ARGB ←→ "#RRGGBB" 文字列)
    // =========================================================================

    private fun colorLongToWebHex(color: Long): String {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return String.format("#%02X%02X%02X", r, g, b)
    }

    private fun colorWebHexToLong(hex: String): Long {
        val clean = hex.removePrefix("#")
        return if (clean.length == 6) {
            val rgb = clean.toLong(16)
            0xFF000000L or rgb
        } else {
            0xFFFFE0B2L // デフォルト色
        }
    }

    // =========================================================================
    // 日付変換 (Long epoch millis ←→ ISO 8601 文字列)
    // =========================================================================

    private fun epochMillisToIso(millis: Long?): String {
        if (millis == null || millis == 0L) return ""
        return Instant.ofEpochMilli(millis)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT)
    }

    private fun isoToEpochMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ISO date: $iso", e)
            null
        }
    }

    // =========================================================================
    // エクスポート
    // =========================================================================

    /**
     * イベントの全データをXLSXとしてOutputStreamに書き出す
     */
    fun exportToXlsx(
        outputStream: OutputStream,
        event: EventEntity,
        items: List<ShoppingItemEntity>,
        executeLists: List<ExecuteListEntity>,
        dayModes: List<DayModeEntity>,
        mapDataList: List<MapDataEntity>,
        hallDefinitions: Map<String, List<HallDefinitionEntity>>, // mapDataId → halls
        visitLists: List<VisitListEntity>,
        hallOrders: List<HallOrderEntity>,
        options: ExportOptions = ExportOptions()
    ) {
        val workbook = XSSFWorkbook()

        try {
            // ヘッダー用スタイル
            val headerStyle = workbook.createCellStyle().apply {
                val font = workbook.createFont().apply { bold = true }
                setFont(font)
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            // 1. アイテムデータシート（必須）
            writeItemsSheet(workbook, items, headerStyle)

            // 2. メタデータシート（fullのみ）
            if (options.format == ExportFormat.FULL) {
                writeMetadataSheet(workbook, event, headerStyle)
            }

            // 3. 配置情報シート
            if (options.format == ExportFormat.FULL && options.includeLayoutInfo) {
                writeLayoutSheet(workbook, executeLists, dayModes, headerStyle)
            }

            // 4. マップデータシート
            if (options.format == ExportFormat.FULL && options.includeMapData && mapDataList.isNotEmpty()) {
                writeMapDataSheet(workbook, mapDataList, headerStyle)
            }

            // 5. ルート情報シート
            val hasRouteRows = visitLists.isNotEmpty() ||
                hallOrders.isNotEmpty() ||
                (options.includeBlockDefinitions && hallDefinitions.isNotEmpty())
            if (options.format == ExportFormat.FULL && options.includeRouteInfo && hasRouteRows) {
                writeRouteSheet(
                    workbook = workbook,
                    items = items,
                    mapDataList = mapDataList,
                    hallDefinitions = hallDefinitions,
                    visitLists = visitLists,
                    hallOrders = hallOrders,
                    headerStyle = headerStyle,
                    includeBlockDefinitions = options.includeBlockDefinitions
                )
            }

            workbook.write(outputStream)
        } finally {
            workbook.close()
        }
    }

    /**
     * アイテムデータシート書き込み
     * WEB版と同じ14列構成
     */
    private fun writeItemsSheet(
        workbook: XSSFWorkbook,
        items: List<ShoppingItemEntity>,
        headerStyle: XSSFCellStyle
    ) {
        val sheet = workbook.createSheet(SHEET_ITEMS)

        // ヘッダー行
        val headers = listOf(
            "ID", "サークル名", "参加日", "ブロック", "ナンバー",
            "タイトル", "価格", "数量", "ステータス", "備考",
            "URL", "優先度", "保護レベル", "追加元"
        )
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { col, title ->
            headerRow.createCell(col).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        // データ行
        items.forEachIndexed { index, item ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(item.id)
            row.createCell(1).setCellValue(item.circle)
            row.createCell(2).setCellValue(item.eventDate)
            row.createCell(3).setCellValue(item.block)
            row.createCell(4).setCellValue(item.number)
            row.createCell(5).setCellValue(item.title)
            if (item.price != null) {
                row.createCell(6).setCellValue(item.price.toDouble())
            } else {
                row.createCell(6).setCellValue("")
            }
            row.createCell(7).setCellValue(item.quantity.toDouble())
            row.createCell(8).setCellValue(purchaseStatusToWeb(item.purchaseStatus))
            row.createCell(9).setCellValue(item.remarks)
            row.createCell(10).setCellValue(item.url ?: "")
            row.createCell(11).setCellValue(priorityLevelToWeb(item.priorityLevel))
            row.createCell(12).setCellValue(protectionLevelToWeb(item.protectionLevel))
            row.createCell(13).setCellValue(sourceToWeb(item.source))
        }

        // 列幅調整
        val widths = intArrayOf(40, 20, 12, 10, 10, 30, 10, 8, 12, 30, 50, 10, 12, 12)
        widths.forEachIndexed { col, width ->
            sheet.setColumnWidth(col, width * 256)
        }
    }

    /**
     * メタデータシート書き込み
     */
    private fun writeMetadataSheet(
        workbook: XSSFWorkbook,
        event: EventEntity,
        headerStyle: XSSFCellStyle
    ) {
        val sheet = workbook.createSheet(SHEET_METADATA)

        // ヘッダー
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).apply { setCellValue("キー"); cellStyle = headerStyle }
        headerRow.createCell(1).apply { setCellValue("値"); cellStyle = headerStyle }

        // データ
        val metaEntries = listOf(
            "version" to EXPORT_VERSION,
            "exportDate" to Instant.now().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT),
            "eventName" to event.name,
            "spreadsheetUrl" to (event.spreadsheetUrl ?: ""),
            "spreadsheetSheetName" to (event.spreadsheetSheetName ?: ""),
            "lastImportDate" to epochMillisToIso(event.lastImportDate)
        )
        metaEntries.forEachIndexed { index, (key, value) ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(key)
            row.createCell(1).setCellValue(value)
        }

        sheet.setColumnWidth(0, 30 * 256)
        sheet.setColumnWidth(1, 100 * 256)
    }

    /**
     * 配置情報シート書き込み
     * executeModeItems: 日付ごとの実行列アイテムID配列
     * dayModes: 日付ごとのビューモード
     */
    private fun writeLayoutSheet(
        workbook: XSSFWorkbook,
        executeLists: List<ExecuteListEntity>,
        dayModes: List<DayModeEntity>,
        headerStyle: XSSFCellStyle
    ) {
        val sheet = workbook.createSheet(SHEET_LAYOUT)

        // ヘッダー
        val headerRow = sheet.createRow(0)
        listOf("タイプ", "参加日", "データ").forEachIndexed { col, title ->
            headerRow.createCell(col).apply { setCellValue(title); cellStyle = headerStyle }
        }

        var rowIndex = 1

        // executeModeItems
        for (executeList in executeLists) {
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue("executeModeItems")
            row.createCell(1).setCellValue(executeList.eventDate)
            setCellValueChunked(row, 2, executeList.itemIdsJson)
        }

        // dayModes
        for (dayMode in dayModes) {
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue("dayModes")
            row.createCell(1).setCellValue(dayMode.eventDate)
            row.createCell(2).setCellValue(dayMode.viewMode)
        }

        sheet.setColumnWidth(0, 20 * 256)
        sheet.setColumnWidth(1, 12 * 256)
        sheet.setColumnWidth(2, 100 * 256)
    }

    /**
     * マップデータシート書き込み
     * dayName → jsonData のペア
     */
    private fun writeMapDataSheet(
        workbook: XSSFWorkbook,
        mapDataList: List<MapDataEntity>,
        headerStyle: XSSFCellStyle
    ) {
        val sheet = workbook.createSheet(SHEET_MAP_DATA)

        // ヘッダー
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).apply { setCellValue("マップ名"); cellStyle = headerStyle }
        headerRow.createCell(1).apply { setCellValue("データ"); cellStyle = headerStyle }

        mapDataList.forEachIndexed { index, mapData ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(mapData.dayName)
            // DB内のjsonDataはGZIP+Base64圧縮されている場合があるため解凍
            val rawJson = decompressMapDataJson(mapData.jsonData)
            setCellValueChunked(row, 1, rawJson)
        }

        sheet.setColumnWidth(0, 20 * 256)
        sheet.setColumnWidth(1, 200 * 256)
    }

    /**
     * ルート情報シート書き込み
     *
     * WEB版フォーマット:
     *   タイプ=routeSettings:      { isRouteVisible, visitOrder: VisitPoint[] }
     *   タイプ=hallDefinitions:    [ { id, name, vertices, color } ]
     *   タイプ=hallRouteSettings:  { hallOrder, hallVisitLists }
     */
    private fun writeRouteSheet(
        workbook: XSSFWorkbook,
        items: List<ShoppingItemEntity>,
        mapDataList: List<MapDataEntity>,
        hallDefinitions: Map<String, List<HallDefinitionEntity>>,
        visitLists: List<VisitListEntity>,
        hallOrders: List<HallOrderEntity>,
        headerStyle: XSSFCellStyle,
        includeBlockDefinitions: Boolean
    ) {
        val sheet = workbook.createSheet(SHEET_ROUTE)

        // ヘッダー
        val headerRow = sheet.createRow(0)
        listOf("タイプ", "マップ名", "データ").forEachIndexed { col, title ->
            headerRow.createCell(col).apply { setCellValue(title); cellStyle = headerStyle }
        }

        // アイテムをIDでインデックス化（VisitPoint構築用）
        val itemMap = items.associateBy { it.id }

        var rowIndex = 1

        // routeSettings: VisitList → WEB版 RouteSettings形式に変換
        for (visitList in visitLists) {
            val visitPoints = buildVisitPointsJson(visitList, itemMap)
            val routeSettings = mapOf(
                "isRouteVisible" to true,
                "visitOrder" to visitPoints
            )
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue("routeSettings")
            row.createCell(1).setCellValue(visitList.dayName)
            setCellValueChunked(row, 2, gson.toJson(routeSettings))
        }

        // hallDefinitions: HallDefinitionEntity → WEB版 HallDefinition[]形式
        if (includeBlockDefinitions) {
            for ((mapDataId, halls) in hallDefinitions) {
                // mapDataIdからdayNameを特定
                val dayName = mapDataList.find { it.id == mapDataId }?.dayName ?: mapDataId
                val webHalls = halls.map { hall ->
                    val verticesType = object : TypeToken<List<Map<String, Int>>>() {}.type
                    val vertices: List<Map<String, Int>> = try {
                        gson.fromJson(hall.verticesJson, verticesType)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    mapOf(
                        "id" to hall.id,
                        "name" to hall.name,
                        "vertices" to vertices,
                        "color" to colorLongToWebHex(hall.color)
                    )
                }
                val row = sheet.createRow(rowIndex++)
                row.createCell(0).setCellValue("hallDefinitions")
                row.createCell(1).setCellValue(dayName)
                setCellValueChunked(row, 2, gson.toJson(webHalls))
            }
        }

        // hallRouteSettings: HallOrder → WEB版 HallRouteSettings形式
        for (hallOrder in hallOrders) {
            val webHallRouteSettings = buildHallRouteSettingsJson(hallOrder, itemMap, visitLists)
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue("hallRouteSettings")
            row.createCell(1).setCellValue(hallOrder.dayName)
            setCellValueChunked(row, 2, gson.toJson(webHallRouteSettings))
        }

        sheet.setColumnWidth(0, 20 * 256)
        sheet.setColumnWidth(1, 20 * 256)
        sheet.setColumnWidth(2, 200 * 256)
    }

    /**
     * Android VisitListEntity → WEB版 VisitPoint[] JSON構築
     *
     * WEB版 VisitPoint: { row, col, blockName, number, order, itemIds }
     * Android版はrow/colを持たないため、block/numberからの推定値を使用。
     * row/colは0にしておき、WEB側で再計算させる。
     */
    private fun buildVisitPointsJson(
        visitList: VisitListEntity,
        itemMap: Map<String, ShoppingItemEntity>
    ): List<Map<String, Any>> {
        val type = object : TypeToken<List<String>>() {}.type
        val itemIds: List<String> = try {
            gson.fromJson(visitList.itemIdsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // アイテムを同一セル（block+number）でグルーピングし、訪問順序を保持
        val visitPoints = mutableListOf<Map<String, Any>>()
        val processedLocations = mutableSetOf<String>()
        var order = 0

        for (itemId in itemIds) {
            val item = itemMap[itemId] ?: continue
            val locationKey = "${item.block}_${item.number}"

            if (locationKey !in processedLocations) {
                processedLocations.add(locationKey)

                // 同じセルにある全アイテムのIDを収集
                val cellItemIds = itemIds.filter { id ->
                    val i = itemMap[id]
                    i != null && "${i.block}_${i.number}" == locationKey
                }

                visitPoints.add(
                    mapOf(
                        "row" to 0,       // Android版はセル座標を保持しない
                        "col" to 0,       // WEB側でマップデータから再計算が必要
                        "blockName" to item.block,
                        "number" to (item.number.toIntOrNull() ?: 0),
                        "order" to order,
                        "itemIds" to cellItemIds
                    )
                )
                order++
            }
        }

        return visitPoints
    }

    /**
     * Android HallOrderEntity → WEB版 HallRouteSettings JSON構築
     *
     * WEB版: { hallOrder: string[], hallVisitLists: [{hallId, itemIds}] }
     * Android版: groupOrderJson = ["hallId_PRIORITY", "hallId_NONE", ...]
     */
    private fun buildHallRouteSettingsJson(
        hallOrder: HallOrderEntity,
        itemMap: Map<String, ShoppingItemEntity>,
        visitLists: List<VisitListEntity>
    ): Map<String, Any> {
        val type = object : TypeToken<List<String>>() {}.type
        val groupOrder: List<String> = try {
            gson.fromJson(hallOrder.groupOrderJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // groupOrderからユニークなhallIdを抽出（出現順維持）
        val hallIds = mutableListOf<String>()
        for (groupId in groupOrder) {
            val hallId = groupId.substringBeforeLast("_")
                .let { if (it == "undefined") null else it }
            if (hallId != null && hallId !in hallIds) {
                hallIds.add(hallId)
            }
        }

        // 対応するVisitListからホールごとのアイテムIDを構築
        val visitList = visitLists.find { it.dayName == hallOrder.dayName }
        val allItemIds: List<String> = if (visitList != null) {
            try {
                gson.fromJson(visitList.itemIdsJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // hallVisitLists構築（簡易版: 全itemIdsをhallIdなしで含める）
        // 完全な変換にはホール定義によるアイテム分類が必要だが、
        // 基本的なデータ保持を優先
        val hallVisitLists = hallIds.map { hallId ->
            mapOf(
                "hallId" to hallId,
                "itemIds" to emptyList<String>() // ホール別分類は別途実装
            )
        }

        return mapOf(
            "hallOrder" to hallIds,
            "hallVisitLists" to hallVisitLists
        )
    }

    // =========================================================================
    // インポート
    // =========================================================================

    /**
     * インポート結果
     */
    data class ImportResult(
        val success: Boolean,
        val eventName: String,
        val metadata: ImportedMetadata?,
        val items: List<ShoppingItemEntity>,
        val executeLists: List<ExecuteListEntity>,
        val dayModes: List<DayModeEntity>,
        val mapDataList: List<MapDataEntity>,
        val hallDefinitions: List<HallDefinitionEntity>,
        val visitLists: List<VisitListEntity>,
        val hallOrders: List<HallOrderEntity>,
        val errors: List<String>
    )

    data class ImportedMetadata(
        val spreadsheetUrl: String?,
        val spreadsheetSheetName: String?,
        val lastImportDate: Long?
    )

    /**
     * XLSXファイルを読み込み、全データを解析
     *
     * @param inputStream XLSXファイルの入力ストリーム
     * @param newEventId 新規作成するイベントのID
     * @param fileName ファイル名（イベント名フォールバック用）
     */
    fun importFromXlsx(
        inputStream: InputStream,
        newEventId: String,
        fileName: String
    ): ImportResult {
        val errors = mutableListOf<String>()

        try {
            val workbook = XSSFWorkbook(inputStream)

            try {
                // 1. アイテムデータ（必須）
                val itemsSheet = workbook.getSheet(SHEET_ITEMS)
                if (itemsSheet == null) {
                    return ImportResult(
                        success = false,
                        eventName = "",
                        metadata = null,
                        items = emptyList(),
                        executeLists = emptyList(),
                        dayModes = emptyList(),
                        mapDataList = emptyList(),
                        hallDefinitions = emptyList(),
                        visitLists = emptyList(),
                        hallOrders = emptyList(),
                        errors = listOf("アイテムデータシートが見つかりません")
                    )
                }

                val items = readItemsSheet(itemsSheet, newEventId, errors)

                // 2. メタデータ
                val metaSheet = workbook.getSheet(SHEET_METADATA)
                val (eventName, metadata) = if (metaSheet != null) {
                    readMetadataSheet(metaSheet, errors)
                } else {
                    "" to null
                }

                // イベント名フォールバック
                val resolvedEventName = eventName.ifBlank {
                    fileName.removeSuffix(".xlsx").removeSuffix(".XLSX")
                }

                // 3. 配置情報
                val layoutSheet = workbook.getSheet(SHEET_LAYOUT)
                val (executeLists, dayModes) = if (layoutSheet != null) {
                    readLayoutSheet(layoutSheet, newEventId, errors)
                } else {
                    emptyList<ExecuteListEntity>() to emptyList<DayModeEntity>()
                }

                // isInExecuteList フラグ復元
                val executeItemIds = mutableSetOf<String>()
                executeLists.forEach { el ->
                    val type = object : TypeToken<List<String>>() {}.type
                    val ids: List<String> = try {
                        gson.fromJson(el.itemIdsJson, type) ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    executeItemIds.addAll(ids)
                }
                val updatedItems = items.map { item ->
                    item.copy(isInExecuteList = item.id in executeItemIds)
                }

                // 4. マップデータ
                val mapSheet = workbook.getSheet(SHEET_MAP_DATA)
                val mapDataList = if (mapSheet != null) {
                    readMapDataSheet(mapSheet, newEventId, errors)
                } else {
                    emptyList()
                }

                // 5. ルート情報
                val routeSheet = workbook.getSheet(SHEET_ROUTE)
                val routeData = if (routeSheet != null) {
                    readRouteSheet(routeSheet, newEventId, mapDataList, errors)
                } else {
                    RouteImportData()
                }

                return ImportResult(
                    success = true,
                    eventName = resolvedEventName,
                    metadata = metadata,
                    items = updatedItems,
                    executeLists = executeLists,
                    dayModes = dayModes,
                    mapDataList = mapDataList,
                    hallDefinitions = routeData.hallDefinitions,
                    visitLists = routeData.visitLists,
                    hallOrders = routeData.hallOrders,
                    errors = errors
                )
            } finally {
                workbook.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            return ImportResult(
                success = false,
                eventName = "",
                metadata = null,
                items = emptyList(),
                executeLists = emptyList(),
                dayModes = emptyList(),
                mapDataList = emptyList(),
                hallDefinitions = emptyList(),
                visitLists = emptyList(),
                hallOrders = emptyList(),
                errors = listOf("ファイルの読み込みに失敗しました: ${e.message}")
            )
        }
    }

    /**
     * アイテムデータシート読み込み
     */
    private fun readItemsSheet(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        newEventId: String,
        errors: MutableList<String>
    ): List<ShoppingItemEntity> {
        val items = mutableListOf<ShoppingItemEntity>()

        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue

            try {
                val id = getCellString(row, 0).ifBlank { UUID.randomUUID().toString() }
                val circle = getCellString(row, 1)
                val title = getCellString(row, 5)

                // サークル名もタイトルも空ならスキップ
                if (circle.isBlank() && title.isBlank()) continue

                val item = ShoppingItemEntity(
                    id = id,
                    eventId = newEventId,
                    circle = circle,
                    eventDate = getCellString(row, 2),
                    block = getCellString(row, 3),
                    number = getCellString(row, 4),
                    title = title,
                    price = getCellNumeric(row, 6)?.toInt(),
                    quantity = getCellNumeric(row, 7)?.toInt() ?: 1,
                    purchaseStatus = purchaseStatusFromWeb(getCellString(row, 8)),
                    remarks = getCellString(row, 9),
                    url = getCellString(row, 10).ifBlank { null },
                    priorityLevel = priorityLevelFromWeb(getCellString(row, 11)),
                    protectionLevel = protectionLevelFromWeb(getCellString(row, 12)),
                    source = sourceFromWeb(getCellString(row, 13)),
                    sortOrder = rowIndex - 1,
                    isInExecuteList = false // 配置情報シートから後で復元
                )
                items.add(item)
            } catch (e: Exception) {
                errors.add("行${rowIndex + 1}の読み込みエラー: ${e.message}")
            }
        }

        return items
    }

    /**
     * メタデータシート読み込み
     */
    private fun readMetadataSheet(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        errors: MutableList<String>
    ): Pair<String, ImportedMetadata?> {
        val metaMap = mutableMapOf<String, String>()

        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val key = getCellString(row, 0)
            val value = getCellString(row, 1)
            if (key.isNotBlank()) {
                metaMap[key] = value
            }
        }

        val eventName = metaMap["eventName"] ?: ""
        val metadata = if (metaMap.containsKey("spreadsheetUrl")) {
            ImportedMetadata(
                spreadsheetUrl = metaMap["spreadsheetUrl"]?.ifBlank { null },
                spreadsheetSheetName = metaMap["spreadsheetSheetName"]?.ifBlank { null },
                lastImportDate = isoToEpochMillis(metaMap["lastImportDate"])
            )
        } else {
            null
        }

        return eventName to metadata
    }

    /**
     * 配置情報シート読み込み
     */
    private fun readLayoutSheet(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        newEventId: String,
        errors: MutableList<String>
    ): Pair<List<ExecuteListEntity>, List<DayModeEntity>> {
        val executeLists = mutableListOf<ExecuteListEntity>()
        val dayModes = mutableListOf<DayModeEntity>()

        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val type = getCellString(row, 0)
            val eventDate = getCellString(row, 1)
            val data = getJoinedCellString(row, 2)

            try {
                when (type) {
                    "executeModeItems" -> {
                        // JSON配列であることを検証
                        val listType = object : TypeToken<List<String>>() {}.type
                        val ids: List<String> = gson.fromJson(data, listType) ?: emptyList()

                        executeLists.add(
                            ExecuteListEntity(
                                id = "${newEventId}_${eventDate}",
                                eventId = newEventId,
                                eventDate = eventDate,
                                itemIdsJson = gson.toJson(ids),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    "dayModes" -> {
                        dayModes.add(
                            DayModeEntity(
                                id = "${newEventId}_${eventDate}",
                                eventId = newEventId,
                                eventDate = eventDate,
                                viewMode = data,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                errors.add("配置情報の解析エラー (${eventDate}): ${e.message}")
            }
        }

        return executeLists to dayModes
    }

    /**
     * マップデータシート読み込み
     */
    private fun readMapDataSheet(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        newEventId: String,
        errors: MutableList<String>
    ): List<MapDataEntity> {
        val mapDataList = mutableListOf<MapDataEntity>()

        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val mapName = getCellString(row, 0)
            val data = getJoinedCellString(row, 1)

            if (mapName.isBlank() || data.isBlank()) continue

            try {
                // JSONの妥当性検証 & id/eventId/dayName を新イベント用に書き換え
                val jsonElement = com.google.gson.JsonParser.parseString(data)
                if (!jsonElement.isJsonObject) {
                    errors.add("マップデータがJSONオブジェクトではありません ($mapName)")
                    continue
                }
                val jsonObj = jsonElement.asJsonObject
                val newMapDataId = "${newEventId}_${mapName}"

                // DayMapData の id/eventId/dayName を新イベントに合わせる
                jsonObj.addProperty("id", newMapDataId)
                jsonObj.addProperty("eventId", newEventId)
                jsonObj.addProperty("dayName", mapName)

                val updatedJson = gson.toJson(jsonObj)

                // sheetNameをJSONから抽出（あれば）
                val sheetName = jsonObj.get("sheetName")?.asString ?: mapName

                mapDataList.add(
                    MapDataEntity(
                        id = newMapDataId,
                        eventId = newEventId,
                        dayName = mapName,
                        sheetName = sheetName,
                        jsonData = updatedJson,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                errors.add("マップデータの解析エラー (${mapName}): ${e.message}")
            }
        }

        return mapDataList
    }

    /**
     * ルート情報の一時格納用
     */
    private data class RouteImportData(
        val visitLists: List<VisitListEntity> = emptyList(),
        val hallDefinitions: List<HallDefinitionEntity> = emptyList(),
        val hallOrders: List<HallOrderEntity> = emptyList()
    )

    /**
     * ルート情報シート読み込み
     */
    private fun readRouteSheet(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        newEventId: String,
        mapDataList: List<MapDataEntity>,
        errors: MutableList<String>
    ): RouteImportData {
        val visitLists = mutableListOf<VisitListEntity>()
        val hallDefinitions = mutableListOf<HallDefinitionEntity>()
        val hallOrders = mutableListOf<HallOrderEntity>()

        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val type = getCellString(row, 0)
            val mapName = getCellString(row, 1)
            val data = getJoinedCellString(row, 2)

            if (type.isBlank() || mapName.isBlank() || data.isBlank()) continue

            try {
                when (type) {
                    "routeSettings" -> {
                        val visitList = parseRouteSettingsToVisitList(
                            data, newEventId, mapName
                        )
                        if (visitList != null) {
                            visitLists.add(visitList)
                        }
                    }
                    "hallDefinitions" -> {
                        val mapDataId = mapDataList.find { it.dayName == mapName }?.id
                            ?: "${newEventId}_${mapName}"
                        val halls = parseHallDefinitions(data, mapDataId)
                        hallDefinitions.addAll(halls)
                    }
                    "hallRouteSettings" -> {
                        val hallOrder = parseHallRouteSettingsToHallOrder(
                            data, newEventId, mapName
                        )
                        if (hallOrder != null) {
                            hallOrders.add(hallOrder)
                        }
                    }
                }
            } catch (e: Exception) {
                errors.add("ルート情報の解析エラー (${type} - ${mapName}): ${e.message}")
            }
        }

        return RouteImportData(visitLists, hallDefinitions, hallOrders)
    }

    /**
     * WEB版 RouteSettings JSON → Android VisitListEntity
     *
     * WEB: { isRouteVisible, visitOrder: [{row,col,blockName,number,order,itemIds}] }
     * → itemIdsを順序通りに結合
     */
    private fun parseRouteSettingsToVisitList(
        json: String,
        eventId: String,
        dayName: String
    ): VisitListEntity? {
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        val routeSettings: Map<String, Any> = gson.fromJson(json, mapType)

        @Suppress("UNCHECKED_CAST")
        val visitOrder = routeSettings["visitOrder"] as? List<Map<String, Any>> ?: return null

        // visitOrderのorder順にソートし、各VisitPointのitemIdsを結合
        val sortedPoints = visitOrder.sortedBy {
            (it["order"] as? Number)?.toInt() ?: 0
        }
        val allItemIds = mutableListOf<String>()
        for (point in sortedPoints) {
            @Suppress("UNCHECKED_CAST")
            val itemIds = point["itemIds"] as? List<String> ?: continue
            allItemIds.addAll(itemIds)
        }

        if (allItemIds.isEmpty()) return null

        return VisitListEntity(
            id = "${eventId}_${dayName}",
            eventId = eventId,
            dayName = dayName,
            itemIdsJson = gson.toJson(allItemIds),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * WEB版 HallDefinition[] JSON → Android HallDefinitionEntity[]
     *
     * WEB: [{id, name, vertices: [{row,col}], color: "#RRGGBB"}]
     */
    private fun parseHallDefinitions(
        json: String,
        mapDataId: String
    ): List<HallDefinitionEntity> {
        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val webHalls: List<Map<String, Any>> = gson.fromJson(json, listType)

        return webHalls.mapNotNull { webHall ->
            try {
                val id = webHall["id"]?.toString() ?: UUID.randomUUID().toString()
                val name = webHall["name"]?.toString() ?: return@mapNotNull null

                @Suppress("UNCHECKED_CAST")
                val vertices = webHall["vertices"] as? List<Map<String, Any>>
                val verticesJson = if (vertices != null) {
                    // {row, col} の形式を保持
                    val cleanVertices = vertices.map { v ->
                        mapOf(
                            "row" to (v["row"] as? Number)?.toInt(),
                            "col" to (v["col"] as? Number)?.toInt()
                        )
                    }
                    gson.toJson(cleanVertices)
                } else {
                    "[]"
                }

                val colorStr = webHall["color"]?.toString() ?: ""
                val color = if (colorStr.startsWith("#")) {
                    colorWebHexToLong(colorStr)
                } else {
                    0xFFFFE0B2L
                }

                HallDefinitionEntity(
                    id = id,
                    mapDataId = mapDataId,
                    name = name,
                    verticesJson = verticesJson,
                    color = color,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse hall definition", e)
                null
            }
        }
    }

    /**
     * WEB版 HallRouteSettings JSON → Android HallOrderEntity
     *
     * WEB: { hallOrder: string[], hallVisitLists: [{hallId, itemIds}] }
     * → groupOrderJson: ["hallId_NONE", "hallId_PRIORITY", ...]
     *
     * WEB版のhallOrderにはhallIdのみが含まれるため、
     * 各hallIdにデフォルトの優先度グループ（NONE, PRIORITY, HIGHEST）を付与して
     * Android版のgroupOrder形式に変換する。
     */
    private fun parseHallRouteSettingsToHallOrder(
        json: String,
        eventId: String,
        dayName: String
    ): HallOrderEntity? {
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        val settings: Map<String, Any> = gson.fromJson(json, mapType)

        @Suppress("UNCHECKED_CAST")
        val hallOrder = settings["hallOrder"] as? List<String> ?: return null

        if (hallOrder.isEmpty()) return null

        // hallIdごとに優先度グループを展開
        // WEB版はhallIdのみだが、Android版はhallId_priorityLevelの組み合わせ
        val groupOrder = mutableListOf<String>()
        for (hallId in hallOrder) {
            // 各ホールに対して3つの優先度グループを生成
            groupOrder.add("${hallId}_HIGHEST")
            groupOrder.add("${hallId}_PRIORITY")
            groupOrder.add("${hallId}_NONE")
        }

        return HallOrderEntity(
            id = "${eventId}_${dayName}",
            eventId = eventId,
            dayName = dayName,
            groupOrderJson = gson.toJson(groupOrder),
            updatedAt = System.currentTimeMillis()
        )
    }

    // =========================================================================
    // セル読み取りヘルパー
    // =========================================================================

    private fun getCellString(row: org.apache.poi.ss.usermodel.Row, col: Int): String {
        val cell = row.getCell(col) ?: return ""
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue ?: ""
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                // 整数の場合は小数点なしで返す
                val num = cell.numericCellValue
                if (num == num.toLong().toDouble()) {
                    num.toLong().toString()
                } else {
                    num.toString()
                }
            }
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                try {
                    cell.stringCellValue ?: ""
                } catch (e: Exception) {
                    try {
                        cell.numericCellValue.toString()
                    } catch (e2: Exception) {
                        ""
                    }
                }
            }
            else -> ""
        }
    }

    private fun getCellNumeric(row: org.apache.poi.ss.usermodel.Row, col: Int): Double? {
        val cell = row.getCell(col) ?: return null
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue?.toDoubleOrNull()
            else -> null
        }
    }

    /**
     * 長い文字列を同一行の複数セルに分割して書き込む。
     * Excel のセル文字数上限（32767）を回避するため、
     * startCol, startCol+1, startCol+2, ... に CELL_MAX_LENGTH ずつ格納する。
     */
    private fun setCellValueChunked(
        row: org.apache.poi.ss.usermodel.Row,
        startCol: Int,
        text: String
    ) {
        if (text.length <= CELL_MAX_LENGTH) {
            row.createCell(startCol).setCellValue(text)
            return
        }
        var offset = 0
        var col = startCol
        while (offset < text.length) {
            val end = minOf(offset + CELL_MAX_LENGTH, text.length)
            row.createCell(col).setCellValue(text.substring(offset, end))
            offset = end
            col++
        }
    }

    /**
     * setCellValueChunked で分割された文字列を結合して読み取る。
     * startCol から右方向に空でないセルを連結して返す。
     */
    private fun getJoinedCellString(
        row: org.apache.poi.ss.usermodel.Row,
        startCol: Int
    ): String {
        val sb = StringBuilder()
        var col = startCol
        while (true) {
            val value = getCellString(row, col)
            if (value.isEmpty()) break
            sb.append(value)
            col++
        }
        return sb.toString()
    }

    // =========================================================================
    // ユーティリティ
    // =========================================================================

    /**
     * MapDataEntity.jsonData を解凍して生JSONに戻す。
     * DB内のjsonDataはMapDataRepositoryImplによってGZIP圧縮+Base64エンコードされている。
     * JSONの場合は { で始まるので、それ以外は圧縮データと判断。
     */
    private fun decompressMapDataJson(jsonData: String): String {
        val trimmed = jsonData.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // 既に生JSON
            return jsonData
        }
        return try {
            val bytes = Base64.decode(jsonData, Base64.NO_WRAP)
            GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
                gzip.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decompress mapData jsonData, using as-is", e)
            jsonData
        }
    }

    /**
     * イベント名の重複解決
     */
    fun resolveEventName(baseName: String, existingNames: List<String>): String {
        if (baseName !in existingNames) return baseName
        var suffix = 2
        while ("$baseName ($suffix)" in existingNames) suffix++
        return "$baseName ($suffix)"
    }

    /**
     * ファイル名に使えない文字をサニタイズ
     */
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
