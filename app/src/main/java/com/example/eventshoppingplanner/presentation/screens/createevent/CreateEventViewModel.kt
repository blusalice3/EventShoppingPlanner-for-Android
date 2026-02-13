package com.example.eventshoppingplanner.presentation.screens.createevent

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventshoppingplanner.domain.model.Event
import com.example.eventshoppingplanner.domain.model.ItemSource
import com.example.eventshoppingplanner.domain.model.PriorityLevel
import com.example.eventshoppingplanner.domain.model.ProtectionLevel
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import com.example.eventshoppingplanner.util.CsvParser
import com.example.eventshoppingplanner.util.LayoutInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

// =========================================================================
// UiState
// =========================================================================

enum class ImportSource {
    SPREADSHEET_URL,
    CSV_FILE
}

data class CreateEventUiState(
    // 即売会名
    val eventName: String = "",

    // スプレッドシートURL インポート
    val spreadsheetUrl: String = "",
    val isUrlImporting: Boolean = false,

    // CSV インポート関連
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val isParsed: Boolean = false,

    // インポート済みアイテム（URL / CSV 共通、排他制御により同時に1ソースのみ）
    val parsedItems: List<ShoppingItem> = emptyList(),
    val importSource: ImportSource? = null,

    // 一括テキストエリア入力
    val circles: String = "",
    val eventDates: String = "",
    val blocks: String = "",
    val numbers: String = "",
    val titles: String = "",
    val prices: String = "",
    val remarks: String = "",
    val urls: String = "",

    // UI状態
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreateComplete: Boolean = false,
    val createdEventId: String? = null,

    // インポートメタデータ
    val importedSpreadsheetUrl: String? = null,
    val importedSheetName: String? = null,
    val importedLayoutInfo: List<LayoutInfo>? = null
) {
    /** テキストエリアのいずれかに入力があるか */
    val hasTextAreaInput: Boolean
        get() = circles.isNotEmpty() || eventDates.isNotEmpty() ||
                blocks.isNotEmpty() || numbers.isNotEmpty() ||
                titles.isNotEmpty() || prices.isNotEmpty() ||
                remarks.isNotEmpty() || urls.isNotEmpty()

    /** 「リストを作成」ボタンの有効条件 */
    val canCreate: Boolean
        get() = eventName.isNotBlank() && (parsedItems.isNotEmpty() || hasTextAreaInput)
}

// =========================================================================
// ViewModel
// =========================================================================

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val itemRepository: ShoppingItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateEventUiState())
    val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "CreateEventVM"
        private const val SHEET_NAME = "品目表"
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 30_000
    }

    // =====================================================================
    // 即売会名
    // =====================================================================

    fun updateEventName(name: String) {
        _uiState.update { it.copy(eventName = name) }
    }

    // =====================================================================
    // スプレッドシートURL インポート
    // =====================================================================

    fun updateSpreadsheetUrl(url: String) {
        _uiState.update { it.copy(spreadsheetUrl = url) }
    }

    /**
     * スプレッドシートURLからCSVを取得してパースし、イベントを作成する。
     * 成功時: CSV・テキストエリアをクリア（排他制御）
     */
    fun importFromSpreadsheetUrl() {
        val state = _uiState.value
        val url = state.spreadsheetUrl.trim()

        if (url.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "スプレッドシートのURLを入力してください。") }
            return
        }
        if (state.eventName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "即売会名を入力してください。") }
            return
        }

        val sheetId = extractSheetId(url)
        if (sheetId == null) {
            _uiState.update { it.copy(errorMessage = "無効なスプレッドシートURLです。URLが正しいか確認してください。") }
            return
        }

        _uiState.update { it.copy(isUrlImporting = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val csvText = fetchSpreadsheetCsv(sheetId)
                val tempEventId = "temp_${UUID.randomUUID()}"
                val result = CsvParser.parseByColumnIndex(csvText, tempEventId)

                result.fold(
                    onSuccess = { importResult ->
                        if (importResult.items.isEmpty()) {
                            _uiState.update {
                                it.copy(
                                    isUrlImporting = false,
                                    errorMessage = "インポートできるデータが見つかりませんでした。"
                                )
                            }
                        } else {
                            // 排他制御: CSV・テキストエリアをクリア
                            _uiState.update { currentState ->
                                currentState.copy(
                                    isUrlImporting = false,
                                    parsedItems = importResult.items,
                                    importSource = ImportSource.SPREADSHEET_URL,
                                    importedSpreadsheetUrl = url,
                                    importedSheetName = SHEET_NAME,
                                    importedLayoutInfo = importResult.layoutInfo
                                )
                                    .clearCsvSelection()
                                    .clearTextAreaInputs()
                            }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "URL import parse error", e)
                        _uiState.update {
                            it.copy(
                                isUrlImporting = false,
                                errorMessage = "スプレッドシートのインポートに失敗しました。URLが正しいか確認してください。"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "URL import fetch error", e)
                _uiState.update {
                    it.copy(
                        isUrlImporting = false,
                        errorMessage = "スプレッドシートの読み込みに失敗しました。ネットワーク接続を確認してください。"
                    )
                }
            }
        }
    }

    private fun extractSheetId(url: String): String? {
        val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    private suspend fun fetchSpreadsheetCsv(sheetId: String): String =
        withContext(Dispatchers.IO) {
            val encodedSheetName = URLEncoder.encode(SHEET_NAME, "UTF-8")
            val csvUrl = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv&sheet=$encodedSheetName"

            val connection = URL(csvUrl).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.requestMethod = "GET"

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                }

                connection.inputStream.bufferedReader().readText()
            } finally {
                connection.disconnect()
            }
        }

    // =====================================================================
    // CSV ファイルインポート
    // =====================================================================

    fun selectFile(uri: Uri, fileName: String?) {
        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = fileName,
                isParsed = false,
                parsedItems = if (it.importSource == ImportSource.CSV_FILE) emptyList() else it.parsedItems,
                importSource = if (it.importSource == ImportSource.CSV_FILE) null else it.importSource
            )
        }
    }

    /**
     * CSVファイルをパースする。
     * 成功時: スプレッドシートURL・テキストエリアをクリア（排他制御）
     */
    fun parseFile(inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val text = withContext(Dispatchers.IO) {
                    inputStream.bufferedReader().readText()
                }
                val tempEventId = "temp_${UUID.randomUUID()}"
                val result = CsvParser.parseByColumnIndex(text, tempEventId)

                result.fold(
                    onSuccess = { importResult ->
                        if (importResult.items.isEmpty()) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "有効なアイテムデータが見つかりませんでした。\nサークル名・参加日・ブロック・ナンバーが全て入力されている行が必要です。"
                                )
                            }
                        } else {
                            // 排他制御: スプレッドシートURL・テキストエリアをクリア
                            _uiState.update { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    parsedItems = importResult.items,
                                    importSource = ImportSource.CSV_FILE,
                                    isParsed = true,
                                    importedSpreadsheetUrl = importResult.spreadsheetUrl,
                                    importedLayoutInfo = importResult.layoutInfo,
                                    spreadsheetUrl = ""
                                )
                                    .clearTextAreaInputs()
                            }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "CSV parse error", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "CSVファイルの読み込みに失敗しました: ${e.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "CSV read error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "CSVファイルの読み込みに失敗しました: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearFile() {
        _uiState.update {
            it.copy(
                selectedFileUri = null,
                selectedFileName = null,
                isParsed = false,
                parsedItems = if (it.importSource == ImportSource.CSV_FILE) emptyList() else it.parsedItems,
                importSource = if (it.importSource == ImportSource.CSV_FILE) null else it.importSource,
                importedLayoutInfo = if (it.importSource == ImportSource.CSV_FILE) null else it.importedLayoutInfo
            )
        }
    }

    // =====================================================================
    // 一括テキストエリア
    // =====================================================================

    /**
     * サークル名テキストエリアの値変更ハンドラ。
     * タブ文字を検出したらペーストデータとして自動振り分け。
     */
    fun onCirclesValueChange(value: String) {
        if (value.contains('\t')) {
            parseAndDistributeTabSeparatedData(value)
        } else {
            clearImportDataIfTextAreaInput(value, _uiState.value.circles)
            _uiState.update { it.copy(circles = value) }
        }
    }

    fun updateEventDates(value: String) {
        clearImportDataIfTextAreaInput(value, _uiState.value.eventDates)
        _uiState.update { it.copy(eventDates = value) }
    }

    fun updateBlocks(value: String) {
        clearImportDataIfTextAreaInput(value, _uiState.value.blocks)
        _uiState.update { it.copy(blocks = value) }
    }

    fun updateNumbers(value: String) {
        clearImportDataIfTextAreaInput(value, _uiState.value.numbers)
        _uiState.update { it.copy(numbers = value) }
    }

    fun updateTitles(value: String) {
        clearImportDataIfTextAreaInput(value, _uiState.value.titles)
        _uiState.update { it.copy(titles = value) }
    }

    fun updatePrices(value: String) {
        clearImportDataIfTextAreaInput(value, _uiState.value.prices)
        _uiState.update { it.copy(prices = value) }
    }

    fun updateRemarks(value: String) {
        clearImportDataIfTextAreaInput(value, _uiState.value.remarks)
        _uiState.update { it.copy(remarks = value) }
    }

    fun updateUrls(value: String) {
        clearImportDataIfTextAreaInput(value, _uiState.value.urls)
        _uiState.update { it.copy(urls = value) }
    }

    /**
     * テキストエリアに入力開始した際、インポートデータをクリアする（排他制御）。
     * 空→非空に変化した場合にのみクリアを発火。
     */
    private fun clearImportDataIfTextAreaInput(newValue: String, oldValue: String) {
        if (newValue.isNotEmpty() && oldValue.isEmpty() && _uiState.value.parsedItems.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    parsedItems = emptyList(),
                    importSource = null,
                    selectedFileUri = null,
                    selectedFileName = null,
                    isParsed = false,
                    spreadsheetUrl = "",
                    importedSpreadsheetUrl = null,
                    importedSheetName = null,
                    importedLayoutInfo = null
                )
            }
        }
    }

    /**
     * タブ区切りデータをパースして各テキストエリアに振り分ける。
     */
    private fun parseAndDistributeTabSeparatedData(rawText: String) {
        val lines = rawText.split('\n').filter { it.trim().isNotEmpty() }

        val circlesList = mutableListOf<String>()
        val datesList = mutableListOf<String>()
        val blocksList = mutableListOf<String>()
        val numbersList = mutableListOf<String>()
        val titlesList = mutableListOf<String>()
        val pricesList = mutableListOf<String>()
        val urlsList = mutableListOf<String>()

        for (line in lines) {
            val cells = line.split('\t').map { it.trim() }
            circlesList.add(cells.getOrElse(0) { "" })
            datesList.add(cells.getOrElse(1) { "" })
            blocksList.add(cells.getOrElse(2) { "" })
            numbersList.add(cells.getOrElse(3) { "" })
            titlesList.add(cells.getOrElse(4) { "" })
            pricesList.add(cells.getOrElse(5) { "" })
            // 7列目がある場合はURLとして取得
            if (cells.size >= 7) {
                urlsList.add(cells.getOrElse(6) { "" })
            }
        }

        // 排他制御: インポートデータをクリア
        _uiState.update {
            it.copy(
                circles = circlesList.joinToString("\n"),
                eventDates = datesList.joinToString("\n"),
                blocks = blocksList.joinToString("\n"),
                numbers = numbersList.joinToString("\n"),
                titles = titlesList.joinToString("\n"),
                prices = pricesList.joinToString("\n"),
                urls = if (urlsList.isNotEmpty()) urlsList.joinToString("\n") else it.urls,
                // インポートデータをクリア
                parsedItems = emptyList(),
                importSource = null,
                selectedFileUri = null,
                selectedFileName = null,
                isParsed = false,
                spreadsheetUrl = "",
                importedSpreadsheetUrl = null,
                importedSheetName = null,
                importedLayoutInfo = null
            )
        }
    }

    // =====================================================================
    // イベント作成
    // =====================================================================

    /**
     * データソースに応じたイベント作成。
     * parsedItems が存在すればそちらを使用、なければテキストエリアからアイテムを生成。
     */
    fun createEventWithItems() {
        val state = _uiState.value

        if (state.eventName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "即売会名を入力してください。") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val eventId = UUID.randomUUID().toString()
                val items: List<ShoppingItem>

                if (state.parsedItems.isNotEmpty()) {
                    // URLインポートまたはCSVのパース済みデータを使用
                    items = state.parsedItems.map { it.copy(eventId = eventId) }
                } else {
                    // テキストエリアからアイテムを生成
                    items = generateItemsFromTextAreas(state, eventId)
                    if (items.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "有効なアイテムデータが見つかりませんでした。\nサークル名・参加日・ブロック・ナンバーが全て入力されている行が必要です。"
                            )
                        }
                        return@launch
                    }
                }

                // イベント作成
                val event = Event(
                    id = eventId,
                    name = state.eventName.trim(),
                    spreadsheetUrl = state.importedSpreadsheetUrl,
                    spreadsheetSheetName = state.importedSheetName,
                    lastImportDate = Instant.now(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                eventRepository.insertEvent(event)
                itemRepository.insertItems(items)

                Log.d(TAG, "Created event '${event.name}' with ${items.size} items")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isCreateComplete = true,
                        createdEventId = eventId,
                        errorMessage = "${items.size}件のアイテムをインポートしました。"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Event creation error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "イベントの作成に失敗しました: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * アイテムなしで空のイベントを作成する。
     */
    fun createEmptyEvent() {
        val state = _uiState.value

        if (state.eventName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "即売会名を入力してください。") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val eventId = UUID.randomUUID().toString()
                val event = Event(
                    id = eventId,
                    name = state.eventName.trim(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                eventRepository.insertEvent(event)

                Log.d(TAG, "Created empty event '${event.name}'")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isCreateComplete = true,
                        createdEventId = eventId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Empty event creation error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "イベントの作成に失敗しました: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * テキストエリアの入力値からShoppingItemリストを生成する。
     */
    private fun generateItemsFromTextAreas(
        state: CreateEventUiState,
        eventId: String
    ): List<ShoppingItem> {
        val circlesLines = state.circles.split('\n')
        val datesLines = state.eventDates.split('\n')
        val blocksLines = state.blocks.split('\n')
        val numbersLines = state.numbers.split('\n')
        val titlesLines = state.titles.split('\n')
        val pricesLines = state.prices.split('\n')
        val remarksLines = state.remarks.split('\n')
        val urlsLines = state.urls.split('\n')

        val rowCount = maxOf(
            circlesLines.size, datesLines.size,
            blocksLines.size, numbersLines.size,
            titlesLines.size, pricesLines.size,
            remarksLines.size, urlsLines.size
        )

        val items = mutableListOf<ShoppingItem>()

        for (i in 0 until rowCount) {
            val circle = circlesLines.getOrNull(i)?.trim() ?: ""
            val eventDate = datesLines.getOrNull(i)?.trim() ?: ""
            val block = blocksLines.getOrNull(i)?.trim() ?: ""
            val number = numbersLines.getOrNull(i)?.trim() ?: ""

            // 必須4項目が全て非空でなければスキップ
            if (circle.isEmpty() || eventDate.isEmpty() || block.isEmpty() || number.isEmpty()) {
                continue
            }

            val title = titlesLines.getOrNull(i)?.trim() ?: ""
            val priceStr = pricesLines.getOrNull(i)?.trim() ?: ""
            val remarksStr = remarksLines.getOrNull(i)?.trim() ?: ""
            val urlStr = urlsLines.getOrNull(i)?.trim() ?: ""

            val item = ShoppingItem(
                id = UUID.randomUUID().toString(),
                eventId = eventId,
                circle = circle,
                eventDate = eventDate,
                block = block,
                number = number,
                title = title,
                price = CsvParser.parsePrice(priceStr),
                purchaseStatus = PurchaseStatus.NONE,
                quantity = 1,
                remarks = remarksStr,
                url = urlStr.takeIf { it.isNotEmpty() },
                priorityLevel = PriorityLevel.NONE,
                protectionLevel = ProtectionLevel.NONE,
                source = ItemSource.APP,
                sortOrder = i,
                isInExecuteList = false
            )
            items.add(item)
        }

        // URL転記処理
        CsvParser.applyUrlTransfer(items)

        return items
    }

    // =====================================================================
    // ユーティリティ
    // =====================================================================

    private fun CreateEventUiState.clearCsvSelection(): CreateEventUiState {
        return copy(
            selectedFileUri = null,
            selectedFileName = null,
            isParsed = false
        )
    }

    private fun CreateEventUiState.clearTextAreaInputs(): CreateEventUiState {
        return copy(
            circles = "",
            eventDates = "",
            blocks = "",
            numbers = "",
            titles = "",
            prices = "",
            remarks = "",
            urls = ""
        )
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
