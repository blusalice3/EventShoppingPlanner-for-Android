package com.example.eventshoppingplanner.util

import com.example.eventshoppingplanner.domain.model.ItemSource
import com.example.eventshoppingplanner.domain.model.PriorityLevel
import com.example.eventshoppingplanner.domain.model.ProtectionLevel
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * インポート結果データクラス。
 * parseByColumnIndex() の戻り値として使用。
 */
data class ImportResult(
    val items: List<ShoppingItem>,
    val spreadsheetUrl: String?,
    val layoutInfo: List<LayoutInfo>?
)

/**
 * CSVエクスポートの配置情報（実行列/候補リストの復元に使用）。
 */
data class LayoutInfo(
    val itemKey: String,
    val eventDate: String,
    val columnType: String, // "execute" or "candidate"
    val order: Int
)

object CsvParser {

    private val csvFormat = CSVFormat.DEFAULT
        .builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(true)
        .setTrim(true)
        .build()

    private const val METADATA_PREFIX = "#METADATA"
    private const val METADATA_SPREADSHEET_URL = "spreadsheetUrl"
    private const val COLUMN_TYPE_EXECUTE = "実行列"
    private const val COLUMN_TYPE_CANDIDATE = "候補リスト"
    private const val COLUMN_TYPE_EXECUTE_KEY = "execute"
    private const val COLUMN_TYPE_CANDIDATE_KEY = "candidate"

    private val nonDigitRegex = Regex("[^0-9]")

    private data class ItemFields(
        val circle: String,
        val eventDate: String,
        val block: String,
        val number: String,
        val title: String,
        val priceStr: String,
        val quantityStr: String,
        val remarks: String,
        val url: String
    )

    private data class UrlTransferGroupKey(
        val eventDate: String,
        val circle: String
    )

    // =========================================================================
    // 既存: ヘッダー名ベースのパーサ（アプリエクスポートCSV後方互換）
    // =========================================================================

    fun parseFromInputStream(
        inputStream: InputStream,
        eventId: String
    ): Result<List<ShoppingItem>> {
        return try {
            val items = mutableListOf<ShoppingItem>()

            inputStream.bufferedReader().use { reader ->
                CSVParser(reader, csvFormat).use { parser ->
                    var sortOrder = 0
                    for (record in parser) {
                        try {
                            val circle = record.get("サークル名")?.takeIf { it.isNotBlank() } ?: continue
                            val eventDate = record.get("参加日")?.takeIf { it.isNotBlank() } ?: continue
                            val block = record.get("ブロック")?.takeIf { it.isNotBlank() } ?: continue
                            val number = record.get("ナンバー")?.takeIf { it.isNotBlank() } ?: continue

                            val item = ShoppingItem(
                                id = UUID.randomUUID().toString(),
                                eventId = eventId,
                                circle = circle,
                                eventDate = eventDate,
                                block = block,
                                number = number,
                                title = record.get("タイトル") ?: "",
                                price = record.get("頒布価格")?.toIntOrNull(),
                                purchaseStatus = try {
                                    PurchaseStatus.fromString(record.get("購入状態") ?: "NONE")
                                } catch (e: Exception) {
                                    PurchaseStatus.NONE
                                },
                                quantity = record.get("数量")?.toIntOrNull() ?: 1,
                                remarks = record.get("備考") ?: "",
                                url = record.get("URL")?.takeIf { it.isNotBlank() },
                                priorityLevel = PriorityLevel.NONE,
                                protectionLevel = ProtectionLevel.NONE,
                                source = ItemSource.SPREADSHEET,
                                isInExecuteList = record.get("列の種類") == COLUMN_TYPE_EXECUTE,
                                sortOrder = record.get("列内順番")?.toIntOrNull() ?: sortOrder++
                            )
                            items.add(item)
                        } catch (e: Exception) {
                            continue
                        }
                    }
                }
            }

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 新規: インデックスベースのパーサ（WEB版 processImportData 移植）
    // CSVファイル・スプレッドシートURL両方で使用
    // =========================================================================

    /**
     * WEB版 processImportData() を移植したインデックスベースのパーサ。
     * フォーマット1（A列開始）とフォーマット2（M列開始）を自動判別する。
     *
     * @param text CSV全文（改行区切りのテキスト）
     * @param eventId アイテムに紐づけるイベントID
     * @return パース結果（アイテムリスト、スプレッドシートURL、配置情報）
     */
    fun parseByColumnIndex(
        text: String,
        eventId: String
    ): Result<ImportResult> {
        return try {
            val lines = text.split('\n').filter { it.trim().isNotEmpty() }
            val newItems = mutableListOf<ShoppingItem>()
            var spreadsheetUrl: String? = null
            val layoutInfo = mutableListOf<LayoutInfo>()

            // ヘッダー行判定: 1行目に「サークル名」が含まれればスキップ
            var startIndex = 0
            if (lines.isNotEmpty() && lines.first().contains("サークル名")) {
                startIndex = 1
            }

            // メタデータ行をチェック（ファイル末尾にある可能性）
            for (i in lines.indices.reversed()) {
                val line = lines[i]
                if (line.startsWith(METADATA_PREFIX)) {
                    val metadataCells = parseCSVLine(line)
                    if (metadataCells.size >= 3 && metadataCells[1] == METADATA_SPREADSHEET_URL) {
                        spreadsheetUrl = metadataCells[2].trim()
                    }
                    break
                }
            }

            var sortOrderCounter = 0

            for (i in startIndex until lines.size) {
                val line = lines[i]
                if (shouldSkipImportLine(line)) continue

                val cells = parseCSVLine(line)
                val fields = parseItemFields(cells) ?: continue
                val (columnType, order) = parseColumnLayout(cells)

                val item = ShoppingItem(
                    id = UUID.randomUUID().toString(),
                    eventId = eventId,
                    circle = fields.circle,
                    eventDate = fields.eventDate,
                    block = fields.block,
                    number = fields.number,
                    title = fields.title,
                    price = parsePrice(fields.priceStr),
                    purchaseStatus = PurchaseStatus.NONE,
                    quantity = parseQuantity(fields.quantityStr),
                    remarks = fields.remarks,
                    url = fields.url.takeIf { it.isNotEmpty() },
                    priorityLevel = PriorityLevel.NONE,
                    protectionLevel = ProtectionLevel.NONE,
                    source = ItemSource.SPREADSHEET,
                    sortOrder = if (columnType != null && order > 0) order else sortOrderCounter,
                    isInExecuteList = columnType == COLUMN_TYPE_EXECUTE_KEY
                )
                newItems.add(item)
                sortOrderCounter++

                // 配置情報を記録
                if (columnType != null && order > 0) {
                    layoutInfo.add(
                        LayoutInfo(
                            itemKey = getItemKey(item),
                            eventDate = fields.eventDate,
                            columnType = columnType,
                            order = order
                        )
                    )
                }
            }

            // URL転記処理
            applyUrlTransfer(newItems)

            Result.success(
                ImportResult(
                    items = newItems,
                    spreadsheetUrl = spreadsheetUrl,
                    layoutInfo = layoutInfo.takeIf { it.isNotEmpty() }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun shouldSkipImportLine(line: String): Boolean {
        if (line.isBlank()) return true
        if (line.startsWith(METADATA_PREFIX)) return true
        return line.contains("サークル名") && line.contains("参加日") && line.contains("ブロック")
    }

    private fun parseColumnLayout(cells: List<String>): Pair<String?, Int> {
        val columnType = parseColumnType(cells.cell(9))
        val order = cells.cell(10).toIntOrNull() ?: 0
        return columnType to order
    }

    private fun parseColumnType(columnType: String): String? {
        return when (columnType) {
            COLUMN_TYPE_EXECUTE -> COLUMN_TYPE_EXECUTE_KEY
            COLUMN_TYPE_CANDIDATE -> COLUMN_TYPE_CANDIDATE_KEY
            else -> null
        }
    }

    private fun parseItemFields(cells: List<String>): ItemFields? {
        val primaryFields = extractPrimaryFormatFields(cells)
        if (hasRequiredFields(primaryFields)) return primaryFields

        val secondaryFields = extractSecondaryFormatFields(cells)
        return secondaryFields.takeIf(::hasRequiredFields)
    }

    private fun extractPrimaryFormatFields(cells: List<String>): ItemFields {
        return ItemFields(
            circle = cells.cell(0),
            eventDate = cells.cell(1),
            block = cells.cell(2),
            number = cells.cell(3),
            title = cells.cell(4),
            priceStr = cells.cell(5),
            quantityStr = cells.cell(6),
            remarks = cells.cell(8),
            url = cells.cell(11)
        )
    }

    private fun extractSecondaryFormatFields(cells: List<String>): ItemFields {
        return ItemFields(
            circle = cells.cell(12),
            eventDate = cells.cell(13),
            block = cells.cell(14),
            number = cells.cell(15),
            title = cells.cell(16),
            priceStr = cells.cell(17),
            quantityStr = cells.cell(26),
            remarks = cells.cell(22),
            url = cells.cell(24)
        )
    }

    private fun hasRequiredFields(fields: ItemFields): Boolean {
        return fields.circle.isNotEmpty() &&
            fields.eventDate.isNotEmpty() &&
            fields.block.isNotEmpty() &&
            fields.number.isNotEmpty()
    }

    private fun List<String>.cell(index: Int): String {
        return getOrNull(index)?.trim().orEmpty()
    }

    // =========================================================================
    // ダブルクォート対応CSVラインパーサ（WEB版 parseCSVLine 移植）
    // =========================================================================

    /**
     * ダブルクォート対応のCSV行パーサ。
     * - ダブルクォート内のカンマは区切りとして扱わない
     * - ダブルクォートのエスケープ("" → ")に対応
     */
    fun parseCSVLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val currentCell = StringBuilder()
        var insideQuotes = false
        var j = 0
        while (j < line.length) {
            val char = line[j]
            when {
                char == '"' -> {
                    if (insideQuotes && j + 1 < line.length && line[j + 1] == '"') {
                        currentCell.append('"')
                        j++
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }
                char == ',' && !insideQuotes -> {
                    cells.add(currentCell.toString())
                    currentCell.clear()
                }
                else -> currentCell.append(char)
            }
            j++
        }
        cells.add(currentCell.toString())
        return cells
    }

    // =========================================================================
    // URL転記処理
    // =========================================================================

    /**
     * 同一参加日・同一サークル名のアイテム間でURLを転記する。
     * URLを持つアイテムから持たないアイテムへ自動コピー。
     *
     * @param items 処理対象のアイテムリスト（mutable、直接変更される）
     */
    fun applyUrlTransfer(items: MutableList<ShoppingItem>) {
        if (items.size < 2) return

        val urlByGroup = items
            .groupBy { UrlTransferGroupKey(it.eventDate, it.circle) }
            .mapValues { (_, groupItems) ->
                groupItems.firstOrNull { !it.url.isNullOrBlank() }?.url
            }

        items.indices.forEach { index ->
            val item = items[index]
            if (!item.url.isNullOrBlank()) return@forEach

            val transferUrl = urlByGroup[UrlTransferGroupKey(item.eventDate, item.circle)]
            if (!transferUrl.isNullOrBlank()) {
                items[index] = item.copy(url = transferUrl)
            }
        }
    }

    // =========================================================================
    // ユーティリティ
    // =========================================================================

    /**
     * 価格文字列をパースする。
     * "¥2,000" → 2000, "" → null, "0" → 0, "abc" → 0
     */
    fun parsePrice(priceStr: String): Int? {
        val trimmed = priceStr.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.replace(nonDigitRegex, "").toIntOrNull() ?: 0
    }

    /**
     * 数量文字列をパースする。1〜10の範囲に制限。
     * "" → 1, "0" → 1, "5" → 5, "15" → 10
     */
    fun parseQuantity(quantityStr: String): Int {
        val trimmed = quantityStr.trim()
        if (trimmed.isEmpty()) return 1
        val parsed = trimmed.replace(nonDigitRegex, "").toIntOrNull() ?: 1
        return parsed.coerceIn(1, 10)
    }

    /**
     * アイテムの一意キーを生成する（WEB版 getItemKey 移植）。
     * 配置情報の紐付けに使用。
     */
    fun getItemKey(item: ShoppingItem): String {
        return "${item.circle}|${item.eventDate}|${item.block}|${item.number}|${item.title}"
    }

    // =========================================================================
    // エクスポート（実行列・候補リストの順序を正しく出力）
    // =========================================================================

    /**
     * CSVエクスポート
     * @param outputStream 出力先ストリーム
     * @param items 全アイテム
     * @param executeListItemIds 参加日ごとの実行列アイテムID順序
     */
    fun exportToOutputStream(
        outputStream: OutputStream,
        items: List<ShoppingItem>,
        executeListItemIds: Map<String, List<String>> = emptyMap()
    ): Result<Unit> {
        return try {
            // 実行列と候補リストに分け、それぞれ順序付けする
            val itemsWithLayout = buildItemsWithLayout(items, executeListItemIds)

            outputStream.bufferedWriter().use { writer ->
                CSVPrinter(
                    writer,
                    CSVFormat.DEFAULT.builder()
                        .setHeader(
                            "サークル名", "参加日", "ブロック", "ナンバー",
                            "タイトル", "頒布価格", "購入状態", "数量", "備考",
                            "列の種類", "列内順番", "URL"
                        )
                        .build()
                ).use { printer ->
                    itemsWithLayout.forEach { (item, columnType, order) ->
                        printer.printRecord(
                            item.circle,
                            item.eventDate,
                            item.block,
                            item.number,
                            item.title,
                            item.price?.toString() ?: "",
                            item.purchaseStatus.name,
                            item.quantity,
                            item.remarks,
                            columnType,
                            order,
                            item.url ?: ""
                        )
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * アイテムに列タイプと列内順番を付与する
     */
    private fun buildItemsWithLayout(
        items: List<ShoppingItem>,
        executeListItemIds: Map<String, List<String>>
    ): List<Triple<ShoppingItem, String, Int>> {
        val result = mutableListOf<Triple<ShoppingItem, String, Int>>()

        // 参加日ごとにグループ化
        val itemsByDate = items.groupBy { it.eventDate }

        // 参加日をソート
        val sortedDates = itemsByDate.keys.sortedWith(compareBy<String> {
            val match = Regex("\\d+").find(it)
            match?.value?.toIntOrNull() ?: 0
        }.thenBy { it })

        for (eventDate in sortedDates) {
            val dateItems = itemsByDate[eventDate] ?: continue
            val executeIds = executeListItemIds[eventDate] ?: emptyList()
            val executeIdSet = executeIds.toSet()
            val itemsById = dateItems.associateBy { it.id }

            // 実行列アイテム（順序付き）
            val executeItems = executeIds.mapNotNull(itemsById::get)
            executeItems.forEachIndexed { index, item ->
                result.add(Triple(item, COLUMN_TYPE_EXECUTE, index + 1))
            }

            // 候補リストアイテム（sortOrder順）
            val candidateItems = dateItems
                .filter { !executeIdSet.contains(it.id) }
                .sortedBy { it.sortOrder }
            candidateItems.forEachIndexed { index, item ->
                result.add(Triple(item, COLUMN_TYPE_CANDIDATE, index + 1))
            }
        }

        return result
    }
}
