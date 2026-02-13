package com.example.eventshoppingplanner.presentation.screens.eventlist

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.eventshoppingplanner.data.local.dao.DayModeDao
import com.example.eventshoppingplanner.data.local.dao.EventDao
import com.example.eventshoppingplanner.data.local.dao.ExecuteListDao
import com.example.eventshoppingplanner.data.local.dao.HallDefinitionDao
import com.example.eventshoppingplanner.data.local.dao.HallOrderDao
import com.example.eventshoppingplanner.data.local.dao.MapDataDao
import com.example.eventshoppingplanner.data.local.dao.ShoppingItemDao
import com.example.eventshoppingplanner.data.local.dao.VisitListDao
import com.example.eventshoppingplanner.data.local.database.AppDatabase
import com.example.eventshoppingplanner.data.local.entity.EventEntity
import com.example.eventshoppingplanner.data.mapper.toEntity
import com.example.eventshoppingplanner.data.mapper.toItemDomainList
import com.example.eventshoppingplanner.domain.model.Event
import com.example.eventshoppingplanner.domain.model.ItemSource
import com.example.eventshoppingplanner.domain.model.PriorityLevel
import com.example.eventshoppingplanner.domain.model.ProtectionLevel
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import com.example.eventshoppingplanner.util.CsvParser
import com.example.eventshoppingplanner.util.XlsxExportImport
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class EventWithStats(
    val event: Event,
    val itemCount: Int,
    val purchasedCount: Int
)

data class EventUpdateDiff(
    val itemsToDelete: List<ShoppingItem> = emptyList(),
    val itemsToUpdate: List<ShoppingItem> = emptyList(),
    val itemsToAdd: List<ShoppingItem> = emptyList(),
    val protectedFromDelete: Int = 0,
    val protectedFromUpdate: Int = 0
) {
    val hasChanges: Boolean
        get() = itemsToDelete.isNotEmpty() || itemsToUpdate.isNotEmpty() || itemsToAdd.isNotEmpty()

    val hasProtectedItems: Boolean
        get() = protectedFromDelete > 0 || protectedFromUpdate > 0
}

data class PendingEventUpdate(
    val eventId: String,
    val eventName: String,
    val spreadsheetUrl: String,
    val spreadsheetSheetName: String,
    val diff: EventUpdateDiff
)

enum class EventExportFormat {
    FULL,
    SIMPLE
}

data class EventExportOptions(
    val includeItems: Boolean = true,
    val includeLayoutInfo: Boolean = true,
    val includeMapData: Boolean = true,
    val includeBlockDefinitions: Boolean = true,
    val includeRouteInfo: Boolean = true,
    val format: EventExportFormat = EventExportFormat.FULL
) {
    fun toXlsxExportOptions(): XlsxExportImport.ExportOptions {
        return XlsxExportImport.ExportOptions(
            includeItems = includeItems,
            includeLayoutInfo = includeLayoutInfo,
            includeMapData = includeMapData,
            includeBlockDefinitions = includeBlockDefinitions,
            includeRouteInfo = includeRouteInfo,
            format = when (format) {
                EventExportFormat.FULL -> XlsxExportImport.ExportFormat.FULL
                EventExportFormat.SIMPLE -> XlsxExportImport.ExportFormat.SIMPLE
            }
        )
    }
}

data class EventListUiState(
    val events: List<EventWithStats> = emptyList(),
    val isLoading: Boolean = true,
    val selectedEvent: Event? = null,
    val showDeleteDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val showExportOptionsDialog: Boolean = false,
    val exportTargetEvent: Event? = null,
    val exportOptionsHasMapData: Boolean = false,
    val exportOptions: EventExportOptions = EventExportOptions(),
    val isUpdating: Boolean = false,
    val updateTargetEvent: Event? = null,
    val showUpdateSourceDialog: Boolean = false,
    val updateSourceUrl: String = "",
    val updateSourceSheetName: String = "",
    val pendingEventUpdate: PendingEventUpdate? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class EventListViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val itemRepository: ShoppingItemRepository,
    private val eventDao: EventDao,
    private val shoppingItemDao: ShoppingItemDao,
    private val executeListDao: ExecuteListDao,
    private val dayModeDao: DayModeDao,
    private val mapDataDao: MapDataDao,
    private val hallDefinitionDao: HallDefinitionDao,
    private val visitListDao: VisitListDao,
    private val hallOrderDao: HallOrderDao,
    private val appDatabase: AppDatabase
) : ViewModel() {

    companion object {
        private const val TAG = "EventListVM"
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 30_000
    }

    private val _uiState = MutableStateFlow(EventListUiState())
    val uiState: StateFlow<EventListUiState> = _uiState.asStateFlow()
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    init {
        loadEvents()
    }

    private fun loadEvents() {
        Log.d(TAG, "loadEvents: START")
        viewModelScope.launch {
            try {
                eventRepository.getAllEvents()
                    .collect { events ->
                        Log.d(TAG, "loadEvents: received ${events.size} events")
                        val eventsWithStats = events.map { event ->
                            EventWithStats(
                                event = event,
                                itemCount = itemRepository.getItemCount(event.id),
                                purchasedCount = itemRepository.getPurchasedCount(event.id)
                            )
                        }
                        _uiState.update {
                            it.copy(events = eventsWithStats, isLoading = false)
                        }
                        Log.d(TAG, "loadEvents: isLoading set to false")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "loadEvents: error", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectEvent(event: Event) {
        _uiState.update { it.copy(selectedEvent = event) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedEvent = null) }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, selectedEvent = null) }
    }

    fun deleteSelectedEvent() {
        viewModelScope.launch {
            _uiState.value.selectedEvent?.let { event ->
                eventRepository.deleteEvent(event)
            }
            hideDeleteDialog()
        }
    }

    fun showRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = true) }
    }

    fun hideRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false, selectedEvent = null) }
    }

    fun renameSelectedEvent(newName: String) {
        viewModelScope.launch {
            _uiState.value.selectedEvent?.let { event ->
                val updated = event.copy(name = newName, updatedAt = Instant.now())
                eventRepository.updateEvent(updated)
            }
            hideRenameDialog()
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // =========================================================================
    // エクスポートオプション
    // =========================================================================

    fun showExportOptionsForSelectedEvent() {
        val event = _uiState.value.selectedEvent ?: return
        _uiState.update { it.copy(selectedEvent = null) }
        viewModelScope.launch {
            val hasMapData = withContext(Dispatchers.IO) {
                mapDataDao.getMapDataMetaByEventId(event.id).isNotEmpty()
            }
            _uiState.update {
                it.copy(
                    exportTargetEvent = event,
                    exportOptionsHasMapData = hasMapData,
                    exportOptions = defaultExportOptions(hasMapData),
                    showExportOptionsDialog = true
                )
            }
        }
    }

    fun dismissExportOptionsDialog() {
        _uiState.update {
            it.copy(
                showExportOptionsDialog = false,
                exportTargetEvent = null
            )
        }
    }

    fun updateExportFormat(format: EventExportFormat) {
        _uiState.update { state ->
            val nextOptions = if (format == EventExportFormat.SIMPLE) {
                EventExportOptions(
                    includeItems = true,
                    includeLayoutInfo = false,
                    includeMapData = false,
                    includeBlockDefinitions = false,
                    includeRouteInfo = false,
                    format = EventExportFormat.SIMPLE
                )
            } else {
                defaultExportOptions(state.exportOptionsHasMapData)
            }
            state.copy(exportOptions = nextOptions)
        }
    }

    fun updateExportIncludeLayoutInfo(enabled: Boolean) {
        _uiState.update { state ->
            if (state.exportOptions.format == EventExportFormat.SIMPLE) {
                state
            } else {
                state.copy(exportOptions = state.exportOptions.copy(includeLayoutInfo = enabled))
            }
        }
    }

    fun updateExportIncludeMapData(enabled: Boolean) {
        _uiState.update { state ->
            if (state.exportOptions.format == EventExportFormat.SIMPLE || !state.exportOptionsHasMapData) {
                state
            } else {
                state.copy(exportOptions = state.exportOptions.copy(includeMapData = enabled))
            }
        }
    }

    fun updateExportIncludeBlockDefinitions(enabled: Boolean) {
        _uiState.update { state ->
            if (state.exportOptions.format == EventExportFormat.SIMPLE || !state.exportOptionsHasMapData) {
                state
            } else {
                state.copy(exportOptions = state.exportOptions.copy(includeBlockDefinitions = enabled))
            }
        }
    }

    fun updateExportIncludeRouteInfo(enabled: Boolean) {
        _uiState.update { state ->
            if (state.exportOptions.format == EventExportFormat.SIMPLE || !state.exportOptionsHasMapData) {
                state
            } else {
                state.copy(exportOptions = state.exportOptions.copy(includeRouteInfo = enabled))
            }
        }
    }

    fun exportWithSelectedOptions(context: Context) {
        val state = _uiState.value
        val event = state.exportTargetEvent ?: return
        val exportOptions = state.exportOptions.toXlsxExportOptions()

        _uiState.update { it.copy(showExportOptionsDialog = false) }
        startExport(context, event, exportOptions)
    }

    private fun defaultExportOptions(hasMapData: Boolean): EventExportOptions {
        return EventExportOptions(
            includeItems = true,
            includeLayoutInfo = true,
            includeMapData = hasMapData,
            includeBlockDefinitions = hasMapData,
            includeRouteInfo = hasMapData,
            format = EventExportFormat.FULL
        )
    }

    // =========================================================================
    // スプレッドシート差分更新
    // =========================================================================

    fun startUpdateSelectedEvent() {
        val event = _uiState.value.selectedEvent ?: return
        val spreadsheetUrl = event.spreadsheetUrl?.trim().orEmpty()
        val spreadsheetSheetName = event.spreadsheetSheetName.orEmpty()

        if (spreadsheetUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    selectedEvent = null,
                    updateTargetEvent = event,
                    showUpdateSourceDialog = true,
                    updateSourceUrl = "",
                    updateSourceSheetName = spreadsheetSheetName
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedEvent = null,
                updateTargetEvent = event,
                showUpdateSourceDialog = false,
                updateSourceUrl = spreadsheetUrl,
                updateSourceSheetName = spreadsheetSheetName
            )
        }
        buildUpdateDiffFromSpreadsheet(event, spreadsheetUrl, spreadsheetSheetName)
    }

    fun dismissUpdateSourceDialog() {
        _uiState.update {
            it.copy(
                showUpdateSourceDialog = false,
                updateTargetEvent = null,
                updateSourceUrl = "",
                updateSourceSheetName = ""
            )
        }
    }

    fun updateUpdateSourceUrl(url: String) {
        _uiState.update { it.copy(updateSourceUrl = url) }
    }

    fun updateUpdateSourceSheetName(sheetName: String) {
        _uiState.update { it.copy(updateSourceSheetName = sheetName) }
    }

    fun confirmUpdateSourceAndBuildDiff() {
        val state = _uiState.value
        val event = state.updateTargetEvent ?: return
        val spreadsheetUrl = state.updateSourceUrl.trim()
        val spreadsheetSheetName = state.updateSourceSheetName.trim()

        if (spreadsheetUrl.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "スプレッドシートURLを入力してください。") }
            return
        }

        _uiState.update { it.copy(showUpdateSourceDialog = false) }
        buildUpdateDiffFromSpreadsheet(event, spreadsheetUrl, spreadsheetSheetName)
    }

    fun dismissPendingEventUpdate() {
        _uiState.update {
            it.copy(
                pendingEventUpdate = null,
                updateTargetEvent = null
            )
        }
    }

    fun applyPendingEventUpdate() {
        val pendingUpdate = _uiState.value.pendingEventUpdate ?: return

        _uiState.update {
            it.copy(
                isUpdating = true,
                pendingEventUpdate = null
            )
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    applyPendingEventUpdateInTransaction(pendingUpdate)
                }

                val diff = pendingUpdate.diff
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        updateTargetEvent = null,
                        snackbarMessage = "更新完了（追加${diff.itemsToAdd.size}件 / 更新${diff.itemsToUpdate.size}件 / 削除${diff.itemsToDelete.size}件）"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "applyPendingEventUpdate: failed", e)
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        snackbarMessage = "更新の適用に失敗しました: ${e.message ?: "不明なエラー"}"
                    )
                }
            }
        }
    }

    private fun buildUpdateDiffFromSpreadsheet(
        event: Event,
        spreadsheetUrl: String,
        spreadsheetSheetName: String
    ) {
        _uiState.update {
            it.copy(
                isUpdating = true,
                pendingEventUpdate = null
            )
        }

        viewModelScope.launch {
            try {
                val diff = withContext(Dispatchers.IO) {
                    val sheetId = extractSheetId(spreadsheetUrl)
                        ?: throw IllegalArgumentException("無効なスプレッドシートURLです。")

                    val csvText = fetchSpreadsheetCsv(sheetId, spreadsheetSheetName)
                    val parseResult = CsvParser.parseByColumnIndex(csvText, event.id)
                    val sheetItems = parseResult.getOrElse { throw it }.items
                    val normalizedSheetItems = normalizeSheetItemsUrls(sheetItems)
                    val currentItems = shoppingItemDao.getItemsByEventIdOnce(event.id).toItemDomainList()

                    createEventUpdateDiff(
                        currentItems = currentItems,
                        sheetItems = normalizedSheetItems
                    )
                }

                val shouldShowConfirm = diff.hasChanges || diff.hasProtectedItems
                _uiState.update {
                    if (!shouldShowConfirm) {
                        it.copy(
                            isUpdating = false,
                            updateTargetEvent = null,
                            pendingEventUpdate = null,
                            snackbarMessage = "更新対象の変更はありませんでした。"
                        )
                    } else {
                        it.copy(
                            isUpdating = false,
                            pendingEventUpdate = PendingEventUpdate(
                                eventId = event.id,
                                eventName = event.name,
                                spreadsheetUrl = spreadsheetUrl,
                                spreadsheetSheetName = spreadsheetSheetName,
                                diff = diff
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "buildUpdateDiffFromSpreadsheet: failed", e)
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        updateTargetEvent = null,
                        pendingEventUpdate = null,
                        snackbarMessage = "更新データの取得に失敗しました: ${e.message ?: "不明なエラー"}"
                    )
                }
            }
        }
    }

    private suspend fun applyPendingEventUpdateInTransaction(
        pendingUpdate: PendingEventUpdate
    ) {
        appDatabase.withTransaction {
            val diff = pendingUpdate.diff
            val deleteIds = diff.itemsToDelete.map { it.id }.toSet()

            diff.itemsToDelete.forEach { item ->
                shoppingItemDao.deleteItemById(item.id)
            }

            diff.itemsToUpdate.forEach { item ->
                shoppingItemDao.updateItem(item.toEntity())
            }

            if (diff.itemsToAdd.isNotEmpty()) {
                val currentItems = shoppingItemDao.getItemsByEventIdOnce(pendingUpdate.eventId)
                val nextSortOrderStart = (currentItems.maxOfOrNull { it.sortOrder } ?: -1) + 1
                val newItems = diff.itemsToAdd.mapIndexed { index, item ->
                    item.copy(
                        id = UUID.randomUUID().toString(),
                        eventId = pendingUpdate.eventId,
                        purchaseStatus = PurchaseStatus.NONE,
                        priorityLevel = PriorityLevel.NONE,
                        protectionLevel = ProtectionLevel.NONE,
                        source = ItemSource.SPREADSHEET,
                        sortOrder = nextSortOrderStart + index,
                        isInExecuteList = false
                    ).toEntity()
                }
                shoppingItemDao.insertItems(newItems)
            }

            if (deleteIds.isNotEmpty()) {
                cleanupDeletedIdsFromLinkedLists(
                    eventId = pendingUpdate.eventId,
                    deletedIds = deleteIds
                )
            }

            val now = System.currentTimeMillis()
            eventDao.getEventById(pendingUpdate.eventId)?.let { event ->
                eventDao.updateEvent(
                    event.copy(
                        spreadsheetUrl = pendingUpdate.spreadsheetUrl,
                        spreadsheetSheetName = pendingUpdate.spreadsheetSheetName.ifBlank { null },
                        lastImportDate = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    private suspend fun cleanupDeletedIdsFromLinkedLists(
        eventId: String,
        deletedIds: Set<String>
    ) {
        val now = System.currentTimeMillis()
        val executeLists = executeListDao.getExecuteListsByEventIdOnce(eventId)
        executeLists.forEach { entity ->
            val currentIds = parseItemIdsJson(entity.itemIdsJson)
            val filteredIds = currentIds.filterNot { deletedIds.contains(it) }
            if (filteredIds.size != currentIds.size) {
                executeListDao.insertExecuteList(
                    entity.copy(
                        itemIdsJson = gson.toJson(filteredIds),
                        updatedAt = now
                    )
                )
            }
        }

        val visitLists = visitListDao.getVisitListsByEventIdOnce(eventId)
        visitLists.forEach { entity ->
            val currentIds = parseItemIdsJson(entity.itemIdsJson)
            val filteredIds = currentIds.filterNot { deletedIds.contains(it) }
            if (filteredIds.size != currentIds.size) {
                visitListDao.insertVisitList(
                    entity.copy(
                        itemIdsJson = gson.toJson(filteredIds),
                        updatedAt = now
                    )
                )
            }
        }
    }

    private fun parseItemIdsJson(itemIdsJson: String): List<String> {
        return try {
            gson.fromJson(itemIdsJson, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun createEventUpdateDiff(
        currentItems: List<ShoppingItem>,
        sheetItems: List<ShoppingItem>
    ): EventUpdateDiff {
        val currentItemsByAll = currentItems.associateBy(::getItemKey)
        val currentItemsByWithoutTitle = currentItems.associateBy(::getItemKeyWithoutTitle)
        val sheetItemsByWithoutTitle = sheetItems.associateBy(::getItemKeyWithoutTitle)

        val itemsToDelete = mutableListOf<ShoppingItem>()
        val itemsToUpdate = mutableListOf<ShoppingItem>()
        val itemsToAdd = mutableListOf<ShoppingItem>()
        var protectedFromDelete = 0
        var protectedFromUpdate = 0

        currentItems.forEach { item ->
            val keyWithoutTitle = getItemKeyWithoutTitle(item)
            if (!sheetItemsByWithoutTitle.containsKey(keyWithoutTitle)) {
                if (getEffectiveProtectionLevel(item) != ProtectionLevel.FULL) {
                    itemsToDelete.add(item)
                } else {
                    protectedFromDelete++
                }
            }
        }

        sheetItems.forEach { sheetItem ->
            val keyWithAll = getItemKey(sheetItem)
            val keyWithoutTitle = getItemKeyWithoutTitle(sheetItem)

            val existingWithAll = currentItemsByAll[keyWithAll]
            if (existingWithAll != null) {
                val protectionLevel = getEffectiveProtectionLevel(existingWithAll)
                if (protectionLevel == ProtectionLevel.FULL || protectionLevel == ProtectionLevel.DELETABLE) {
                    if (hasManagedFieldDifference(existingWithAll, sheetItem)) {
                        protectedFromUpdate++
                    }
                    return@forEach
                }

                if (hasManagedFieldDifference(existingWithAll, sheetItem)) {
                    itemsToUpdate.add(
                        existingWithAll.copy(
                            price = sheetItem.price,
                            remarks = sheetItem.remarks,
                            url = sheetItem.url
                        )
                    )
                }
                return@forEach
            }

            val existingWithoutTitle = currentItemsByWithoutTitle[keyWithoutTitle]
            if (existingWithoutTitle != null) {
                val protectionLevel = getEffectiveProtectionLevel(existingWithoutTitle)
                if (protectionLevel == ProtectionLevel.FULL || protectionLevel == ProtectionLevel.DELETABLE) {
                    protectedFromUpdate++
                    return@forEach
                }

                itemsToUpdate.add(
                    existingWithoutTitle.copy(
                        title = sheetItem.title,
                        price = sheetItem.price,
                        remarks = sheetItem.remarks,
                        url = sheetItem.url
                    )
                )
                return@forEach
            }

            itemsToAdd.add(sheetItem)
        }

        return EventUpdateDiff(
            itemsToDelete = itemsToDelete,
            itemsToUpdate = itemsToUpdate,
            itemsToAdd = itemsToAdd,
            protectedFromDelete = protectedFromDelete,
            protectedFromUpdate = protectedFromUpdate
        )
    }

    private fun normalizeSheetItemsUrls(sheetItems: List<ShoppingItem>): List<ShoppingItem> {
        val normalized = sheetItems.toMutableList()
        val groupMap = normalized.indices.groupBy { index ->
            normalized[index].eventDate to normalized[index].circle
        }

        groupMap.values.forEach { indices ->
            if (indices.size < 2) return@forEach

            val sharedUrl = indices
                .asSequence()
                .mapNotNull { idx -> normalized[idx].url?.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: return@forEach

            indices.forEach { idx ->
                if (normalized[idx].url.isNullOrBlank()) {
                    normalized[idx] = normalized[idx].copy(url = sharedUrl)
                }
            }
        }

        return normalized
    }

    private fun getItemKey(item: ShoppingItem): String {
        return "${item.circle}|${item.eventDate}|${item.block}|${item.number}|${item.title}"
    }

    private fun getItemKeyWithoutTitle(item: ShoppingItem): String {
        return "${item.circle}|${item.eventDate}|${item.block}|${item.number}"
    }

    private fun getEffectiveProtectionLevel(item: ShoppingItem): ProtectionLevel {
        return when {
            item.protectionLevel != ProtectionLevel.NONE -> item.protectionLevel
            item.source == ItemSource.APP -> ProtectionLevel.FULL
            else -> ProtectionLevel.NONE
        }
    }

    private fun hasManagedFieldDifference(
        current: ShoppingItem,
        sheet: ShoppingItem
    ): Boolean {
        return current.price != sheet.price ||
                current.remarks != sheet.remarks ||
                current.url != sheet.url
    }

    private fun extractSheetId(url: String): String? {
        val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    private suspend fun fetchSpreadsheetCsv(sheetId: String, sheetName: String): String =
        withContext(Dispatchers.IO) {
            val csvUrl = buildSpreadsheetCsvUrl(sheetId, sheetName)
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

    private fun buildSpreadsheetCsvUrl(sheetId: String, sheetName: String): String {
        val encodedSheet = if (sheetName.isNotBlank()) {
            "&sheet=${URLEncoder.encode(sheetName, "UTF-8")}"
        } else {
            ""
        }
        return "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv$encodedSheet"
    }

    // =========================================================================
    // XLSX エクスポート
    // =========================================================================

    /**
     * 選択中イベントをXLSXエクスポート
     * Downloadsフォルダに保存 + 共有インテント表示
     */
    fun exportSelectedEventToXlsx(context: Context) {
        val event = _uiState.value.selectedEvent ?: return
        startExport(context, event, defaultExportOptions(hasMapData = true).toXlsxExportOptions())
    }

    private fun startExport(
        context: Context,
        event: Event,
        exportOptions: XlsxExportImport.ExportOptions
    ) {
        _uiState.update { it.copy(isExporting = true, selectedEvent = null) }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    exportEventToXlsx(
                        context = context,
                        eventId = event.id,
                        eventName = event.name,
                        exportOptions = exportOptions
                    )
                }

                if (result != null) {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportTargetEvent = null,
                            snackbarMessage = "Downloadsフォルダに保存しました"
                        )
                    }

                    // 共有インテント
                    withContext(Dispatchers.Main) {
                        shareXlsxFile(context, result.first, result.second)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportTargetEvent = null,
                            snackbarMessage = "エクスポートに失敗しました"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportTargetEvent = null,
                        snackbarMessage = "エクスポートに失敗しました: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * @return Pair<Uri, String>(共有用URI, ファイル名) or null
     */
    private suspend fun exportEventToXlsx(
        context: Context,
        eventId: String,
        eventName: String,
        exportOptions: XlsxExportImport.ExportOptions
    ): Pair<Uri, String>? {
        // 全データ取得
        val event = eventDao.getEventById(eventId) ?: return null
        val items = shoppingItemDao.getItemsByEventIdOnce(eventId)
        val executeLists = executeListDao.getExecuteListsByEventIdOnce(eventId)
        val dayModes = dayModeDao.getDayModesByEventIdOnce(eventId)
        val visitLists = visitListDao.getVisitListsByEventIdOnce(eventId)
        val hallOrders = hallOrderDao.getHallOrdersByEventIdOnce(eventId)

        // マップデータを SUBSTR チャンク読み出し（CursorWindow 2MB上限回避）
        val mapDataMetas = mapDataDao.getMapDataMetaByEventId(eventId)
        val mapDataList = mapDataMetas.map { meta ->
            val totalLen = mapDataDao.getJsonDataLength(meta.id) ?: 0
            val jsonData = if (totalLen > 0) {
                buildString(totalLen) {
                    var offset = 1 // SQL SUBSTR は 1-indexed
                    val chunkSize = 900_000 // ~900KB per chunk
                    while (offset <= totalLen) {
                        val chunk = mapDataDao.getJsonDataChunk(meta.id, offset, chunkSize) ?: break
                        append(chunk)
                        offset += chunkSize
                    }
                }
            } else ""
            com.example.eventshoppingplanner.data.local.entity.MapDataEntity(
                id = meta.id,
                eventId = meta.eventId,
                dayName = meta.dayName,
                sheetName = meta.sheetName,
                jsonData = jsonData,
                updatedAt = meta.updatedAt
            )
        }

        // ホール定義をマップデータIDごとに取得
        val hallDefinitions = mutableMapOf<String, List<com.example.eventshoppingplanner.data.local.entity.HallDefinitionEntity>>()
        for (mapData in mapDataList) {
            val halls = hallDefinitionDao.getHallsByMapDataIdOnce(mapData.id)
            if (halls.isNotEmpty()) {
                hallDefinitions[mapData.id] = halls
            }
        }

        // ファイル名生成
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.systemDefault())
            .format(Instant.now())
        val sanitizedName = XlsxExportImport.sanitizeFileName(eventName)
        val formatSuffix = when (exportOptions.format) {
            XlsxExportImport.ExportFormat.FULL -> "full"
            XlsxExportImport.ExportFormat.SIMPLE -> "simple"
        }
        val fileName = "${sanitizedName}_${timestamp}_${formatSuffix}.xlsx"

        // キャッシュに一時ファイル作成
        val cacheFile = File(context.cacheDir, fileName)
        cacheFile.outputStream().use { outputStream ->
            XlsxExportImport.exportToXlsx(
                outputStream = outputStream,
                event = event,
                items = items,
                executeLists = executeLists,
                dayModes = dayModes,
                mapDataList = mapDataList,
                hallDefinitions = hallDefinitions,
                visitLists = visitLists,
                hallOrders = hallOrders,
                options = exportOptions
            )
        }

        // Downloadsフォルダにコピー
        saveToDownloads(context, cacheFile, fileName)

        // 共有用URI
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )

        return shareUri to fileName
    }

    /**
     * MediaStore経由でDownloadsフォルダに保存
     */
    private fun saveToDownloads(context: Context, sourceFile: File, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: MediaStore API
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            } else {
                // Android 9以下: 直接ファイルコピー
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val destFile = File(downloadsDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save to Downloads (sharing will still work)", e)
        }
    }

    /**
     * 共有インテント起動
     */
    private fun shareXlsxFile(context: Context, uri: Uri, fileName: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "XLSXをエクスポート"))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch share intent", e)
        }
    }

    // =========================================================================
    // XLSX インポート
    // =========================================================================

    /**
     * XLSXファイルをインポートして新規イベント作成
     */
    fun importEventFromXlsx(context: Context, uri: Uri) {
        _uiState.update { it.copy(isImporting = true) }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    importXlsxFromUri(context, uri)
                }

                _uiState.update {
                    it.copy(
                        isImporting = false,
                        snackbarMessage = if (result) "インポートが完了しました"
                        else "インポートに失敗しました"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        snackbarMessage = "インポートに失敗しました: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun importXlsxFromUri(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri) ?: "import.xlsx"
        val newEventId = UUID.randomUUID().toString()

        // XLSXファイル読み込み
        val importResult = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            XlsxExportImport.importFromXlsx(inputStream, newEventId, fileName)
        } ?: return false

        if (!importResult.success) {
            Log.e(TAG, "Import errors: ${importResult.errors}")
            withContext(Dispatchers.Main) {
                val errorMsg = importResult.errors.firstOrNull() ?: "不明なエラー"
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
            return false
        }

        // イベント名の重複チェック
        val existingNames = eventDao.getAllEventNames()
        val resolvedName = XlsxExportImport.resolveEventName(
            importResult.eventName, existingNames
        )

        val now = System.currentTimeMillis()

        // EventEntity作成・保存
        val eventEntity = EventEntity(
            id = newEventId,
            name = resolvedName,
            spreadsheetUrl = importResult.metadata?.spreadsheetUrl,
            spreadsheetSheetName = importResult.metadata?.spreadsheetSheetName,
            lastImportDate = importResult.metadata?.lastImportDate,
            createdAt = now,
            updatedAt = now
        )

        eventDao.insertEvent(eventEntity)
        if (importResult.items.isNotEmpty()) {
            shoppingItemDao.insertItems(importResult.items)
        }
        for (el in importResult.executeLists) {
            executeListDao.insertExecuteList(el)
        }
        for (dm in importResult.dayModes) {
            dayModeDao.insertDayMode(dm)
        }
        for (md in importResult.mapDataList) {
            mapDataDao.insert(md)
        }
        if (importResult.hallDefinitions.isNotEmpty()) {
            hallDefinitionDao.insertHalls(importResult.hallDefinitions)
        }
        for (vl in importResult.visitLists) {
            visitListDao.insertVisitList(vl)
        }
        for (ho in importResult.hallOrders) {
            hallOrderDao.insertHallOrder(ho)
        }

        // 部分的エラー警告
        if (importResult.errors.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "一部データの読み込みに問題がありました（${importResult.errors.size}件）",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        Log.d(TAG, "Import complete: $resolvedName (${importResult.items.size} items)")
        return true
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
