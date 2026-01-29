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

object CsvParser {

    private val csvFormat = CSVFormat.DEFAULT
        .builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(true)
        .setTrim(true)
        .build()

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
                            // Skip invalid rows
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