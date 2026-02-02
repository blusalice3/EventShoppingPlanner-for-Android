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
                                isInExecuteList = record.get("列の種類") == "実行列",
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
            if (lines.isNotEmpty() && lines[0].contains("サークル名")) {
                startIndex = 1
            }

            // メタデータ行をチェック（ファイル末尾にある可能性）
            for (i in lines.indices.reversed()) {
                if (lines[i].startsWith("#METADATA")) {
                    val metadataCells = parseCSVLine(lines[i])
                    if (metadataCells.size >= 3 && metadataCells[1] == "spreadsheetUrl") {
                        spreadsheetUrl = metadataCells[2].trim()
                    }
                    break
                }
            }

            var sortOrderCounter = 0

            for (i in startIndex until lines.size) {
                val line = lines[i]
                if (line.isBlank()) continue

                // メタデータ行をスキップ
                if (line.startsWith("#METADATA")) continue

                // ヘッダー行をスキップ（念のため再チェック）
                if (line.contains("サークル名") && line.contains("参加日") && line.contains("ブロック")) {
                    continue
                }

                val cells = parseCSVLine(line)

                // --- フォーマット判定 ---
                // フォーマット1: A列(0)〜D列(3)がすべて非空
                var circle = cells.getOrNull(0)?.trim() ?: ""
                var eventDate = cells.getOrNull(1)?.trim() ?: ""
                var block = cells.getOrNull(2)?.trim() ?: ""
                var number = cells.getOrNull(3)?.trim() ?: ""
                var title = cells.getOrNull(4)?.trim() ?: ""
                var priceStr = cells.getOrNull(5)?.trim() ?: ""
                var quantityStr = cells.getOrNull(6)?.trim() ?: ""
                var remarks = cells.getOrNull(8)?.trim() ?: ""
                var url = cells.getOrNull(11)?.trim() ?: ""
                var columnType: String? = null
                var order = 0

                // 配置情報（エクスポートCSV形式）
                if (cells.size >= 11) {
                    val columnTypeStr = cells.getOrNull(9)?.trim() ?: ""
                    if (columnTypeStr == "実行列") {
                        columnType = "execute"
                    } else if (columnTypeStr == "候補リスト") {
                        columnType = "candidate"
                    }
                    order = cells.getOrNull(10)?.trim()?.toIntOrNull() ?: 0
                }

                // A〜D列が揃っていなければフォーマット2（M列〜）を試す
                if (circle.isEmpty() || eventDate.isEmpty() || block.isEmpty() || number.isEmpty()) {
                    circle = cells.getOrNull(12)?.trim() ?: ""
                    eventDate = cells.getOrNull(13)?.trim() ?: ""
                    block = cells.getOrNull(14)?.trim() ?: ""
                    number = cells.getOrNull(15)?.trim() ?: ""
                    title = cells.getOrNull(16)?.trim() ?: ""
                    priceStr = cells.getOrNull(17)?.trim() ?: ""
                    remarks = cells.getOrNull(22)?.trim() ?: ""
                    url = cells.getOrNull(24)?.trim() ?: ""
                    quantityStr = cells.getOrNull(26)?.trim() ?: ""

                    // それでも必須項目が揃わなければスキップ
                    if (circle.isEmpty() || eventDate.isEmpty() || block.isEmpty() || number.isEmpty()) {
                        continue
                    }
                }

                val price = parsePrice(priceStr)
                val quantity = parseQuantity(quantityStr)

                val item = ShoppingItem(
                    id = UUID.randomUUID().toString(),
                    eventId = eventId,
                    circle = circle,
                    eventDate = eventDate,
                    block = block,
                    number = number,
                    title = title,
                    price = price,
                    purchaseStatus = PurchaseStatus.NONE,
                    quantity = quantity,
                    remarks = remarks,
                    url = url.takeIf { it.isNotEmpty() },
                    priorityLevel = PriorityLevel.NONE,
                    protectionLevel = ProtectionLevel.NONE,
                    source = ItemSource.SPREADSHEET,
                    sortOrder = if (columnType != null && order > 0) order else sortOrderCounter,
                    isInExecuteList = columnType == "execute"
                )
                newItems.add(item)
                sortOrderCounter++

                // 配置情報を記録
                if (columnType != null && order > 0) {
                    layoutInfo.add(
                        LayoutInfo(
                            itemKey = getItemKey(item),
                            eventDate = eventDate,
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
        val dateGroups = items.groupBy { it.eventDate }

        dateGroups.forEach { (_, dateItems) ->
            val circleGroups = dateItems.groupBy { it.circle }

            circleGroups.forEach { (_, circleItems) ->
                if (circleItems.size >= 2) {
                    val itemWithUrl = circleItems.find { !it.url.isNullOrBlank() }

                    if (itemWithUrl?.url != null) {
                        circleItems.forEach { item ->
                            if (item.url.isNullOrBlank()) {
                                val index = items.indexOf(item)
                                if (index >= 0) {
                                    items[index] = item.copy(url = itemWithUrl.url)
                                }
                            }
                        }
                    }
                }
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
        return trimmed.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
    }

    /**
     * 数量文字列をパースする。1〜10の範囲に制限。
     * "" → 1, "0" → 1, "5" → 5, "15" → 10
     */
    fun parseQuantity(quantityStr: String): Int {
        val trimmed = quantityStr.trim()
        if (trimmed.isEmpty()) return 1
        val parsed = trimmed.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
        return parsed.coerceIn(1, 10)
    }

    /**
     * アイテムの一意キーを生成する（WEB版 getItemKey 移植）。
     * 配置情報の紐付けに使用。
     */
    private fun getItemKey(item: ShoppingItem): String {
        return "${item.circle}|${item.eventDate}|${item.block}|${item.number}|${item.title}"
    }

    // =========================================================================
    // エクスポート（既存のまま）
    // =========================================================================

    fun exportToOutputStream(
        outputStream: OutputStream,
        items: List<ShoppingItem>
    ): Result<Unit> {
        return try {
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
                    items.forEach { item ->
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
                            if (item.isInExecuteList) "実行列" else "候補リスト",
                            item.sortOrder,
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
}