package com.example.eventshoppingplanner.presentation.screens.eventlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventshoppingplanner.domain.model.Event
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EventListScreen(
    onNavigateToShoppingList: (String) -> Unit,
    onNavigateToCreateEvent: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: EventListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    // XLSXインポート用ファイルピッカー
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importEventFromXlsx(context, it) }
    }

    // Snackbar表示
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("即売会リスト") },
                actions = {
                    // XLSXインポートボタン
                    IconButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            )
                        }
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "XLSXインポート")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreateEvent
            ) {
                Icon(Icons.Default.Add, contentDescription = "新規作成")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.events.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "イベントがありません",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToCreateEvent) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("新規作成")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                importLauncher.launch(
                                    arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                )
                            }
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("XLSXからインポート")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.events, key = { it.event.id }) { eventWithStats ->
                            EventCard(
                                eventWithStats = eventWithStats,
                                dateFormatter = dateFormatter,
                                onClick = { onNavigateToShoppingList(eventWithStats.event.id) },
                                onLongClick = { viewModel.selectEvent(eventWithStats.event) }
                            )
                        }
                    }
                }
            }

            // エクスポート/インポート/更新中のオーバーレイ
            if (uiState.isExporting || uiState.isImporting || uiState.isUpdating) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                uiState.isExporting -> "エクスポート中..."
                                uiState.isImporting -> "インポート中..."
                                else -> "更新中..."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // 長押しメニュー（BottomSheet）
    if (uiState.selectedEvent != null) {
        EventBottomSheet(
            event = uiState.selectedEvent!!,
            onDismiss = { viewModel.clearSelection() },
            onUpdate = { viewModel.startUpdateSelectedEvent() },
            onRename = { viewModel.showRenameDialog() },
            onExportXlsx = { viewModel.showExportOptionsForSelectedEvent() },
            onDelete = { viewModel.showDeleteDialog() }
        )
    }

    // エクスポートオプションダイアログ
    if (uiState.showExportOptionsDialog) {
        EventExportOptionsDialog(
            options = uiState.exportOptions,
            hasMapData = uiState.exportOptionsHasMapData,
            onDismiss = { viewModel.dismissExportOptionsDialog() },
            onFormatChange = { viewModel.updateExportFormat(it) },
            onIncludeLayoutInfoChange = { viewModel.updateExportIncludeLayoutInfo(it) },
            onIncludeMapDataChange = { viewModel.updateExportIncludeMapData(it) },
            onIncludeBlockDefinitionsChange = { viewModel.updateExportIncludeBlockDefinitions(it) },
            onIncludeRouteInfoChange = { viewModel.updateExportIncludeRouteInfo(it) },
            onExport = { viewModel.exportWithSelectedOptions(context) }
        )
    }

    // 更新元URL入力ダイアログ
    if (uiState.showUpdateSourceDialog) {
        UpdateSourceDialog(
            eventName = uiState.updateTargetEvent?.name ?: "",
            spreadsheetUrl = uiState.updateSourceUrl,
            sheetName = uiState.updateSourceSheetName,
            onUrlChange = { viewModel.updateUpdateSourceUrl(it) },
            onSheetNameChange = { viewModel.updateUpdateSourceSheetName(it) },
            onDismiss = { viewModel.dismissUpdateSourceDialog() },
            onConfirm = { viewModel.confirmUpdateSourceAndBuildDiff() }
        )
    }

    // 差分確認ダイアログ
    uiState.pendingEventUpdate?.let { pendingUpdate ->
        EventUpdateConfirmDialog(
            pendingUpdate = pendingUpdate,
            onDismiss = { viewModel.dismissPendingEventUpdate() },
            onConfirm = { viewModel.applyPendingEventUpdate() }
        )
    }

    // 削除確認ダイアログ
    if (uiState.showDeleteDialog) {
        DeleteConfirmDialog(
            eventName = uiState.selectedEvent?.name ?: "",
            onDismiss = { viewModel.hideDeleteDialog() },
            onConfirm = { viewModel.deleteSelectedEvent() }
        )
    }

    // 名前変更ダイアログ
    if (uiState.showRenameDialog) {
        RenameEventDialog(
            currentName = uiState.selectedEvent?.name ?: "",
            onDismiss = { viewModel.hideRenameDialog() },
            onRename = { newName -> viewModel.renameSelectedEvent(newName) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventCard(
    eventWithStats: EventWithStats,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eventWithStats.event.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "更新: ${dateFormatter.format(eventWithStats.event.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "アイテム: ${eventWithStats.purchasedCount}/${eventWithStats.itemCount}件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventBottomSheet(
    event: Event,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onRename: () -> Unit,
    onExportXlsx: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = event.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TextButton(
                onClick = {
                    onUpdate()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("アイテム更新")
                Spacer(modifier = Modifier.weight(1f))
            }

            TextButton(
                onClick = {
                    onDismiss()
                    onRename()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("名前を変更")
                Spacer(modifier = Modifier.weight(1f))
            }

            TextButton(
                onClick = {
                    onExportXlsx()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("XLSXエクスポート")
                Spacer(modifier = Modifier.weight(1f))
            }

            TextButton(
                onClick = {
                    onDismiss()
                    onDelete()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("削除")
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EventExportOptionsDialog(
    options: EventExportOptions,
    hasMapData: Boolean,
    onDismiss: () -> Unit,
    onFormatChange: (EventExportFormat) -> Unit,
    onIncludeLayoutInfoChange: (Boolean) -> Unit,
    onIncludeMapDataChange: (Boolean) -> Unit,
    onIncludeBlockDefinitionsChange: (Boolean) -> Unit,
    onIncludeRouteInfoChange: (Boolean) -> Unit,
    onExport: () -> Unit
) {
    val isSimple = options.format == EventExportFormat.SIMPLE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("エクスポート設定") },
        text = {
            Column {
                Text(
                    text = "エクスポート内容を選択してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                ExportOptionRow(
                    label = "アイテムデータ（必須）",
                    checked = options.includeItems,
                    enabled = false,
                    onCheckedChange = {}
                )
                ExportOptionRow(
                    label = "配置情報（実行列・候補リスト順）",
                    checked = options.includeLayoutInfo,
                    enabled = !isSimple,
                    onCheckedChange = onIncludeLayoutInfoChange
                )
                ExportOptionRow(
                    label = if (hasMapData) "マップデータ" else "マップデータ（データなし）",
                    checked = options.includeMapData,
                    enabled = hasMapData && !isSimple,
                    onCheckedChange = onIncludeMapDataChange
                )
                ExportOptionRow(
                    label = if (hasMapData) "ブロック定義" else "ブロック定義（データなし）",
                    checked = options.includeBlockDefinitions,
                    enabled = hasMapData && !isSimple,
                    onCheckedChange = onIncludeBlockDefinitionsChange
                )
                ExportOptionRow(
                    label = if (hasMapData) "ルート情報" else "ルート情報（データなし）",
                    checked = options.includeRouteInfo,
                    enabled = hasMapData && !isSimple,
                    onCheckedChange = onIncludeRouteInfoChange
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ファイル形式",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = options.format == EventExportFormat.FULL,
                        onClick = { onFormatChange(EventExportFormat.FULL) }
                    )
                    Text("完全版（全データ）")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = options.format == EventExportFormat.SIMPLE,
                        onClick = { onFormatChange(EventExportFormat.SIMPLE) }
                    )
                    Text("簡易版（アイテムのみ）")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport) {
                Text("エクスポート")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun ExportOptionRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun UpdateSourceDialog(
    eventName: String,
    spreadsheetUrl: String,
    sheetName: String,
    onUrlChange: (String) -> Unit,
    onSheetNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新元URLを入力") },
        text = {
            Column {
                if (eventName.isNotBlank()) {
                    Text(
                        text = eventName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = spreadsheetUrl,
                    onValueChange = onUrlChange,
                    label = { Text("スプレッドシートURL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sheetName,
                    onValueChange = onSheetNameChange,
                    label = { Text("シート名（任意）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = spreadsheetUrl.isNotBlank()
            ) {
                Text("差分確認")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun EventUpdateConfirmDialog(
    pendingUpdate: PendingEventUpdate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val diff = pendingUpdate.diff

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("アイテム更新の確認") },
        text = {
            Column {
                Text(
                    text = pendingUpdate.eventName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (diff.itemsToDelete.isNotEmpty()) {
                    Text(
                        text = "削除: ${diff.itemsToDelete.size}件",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    diff.itemsToDelete.take(5).forEach { item ->
                        Text(
                            text = "• ${item.circle} - ${item.title}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (diff.itemsToDelete.size > 5) {
                        Text(
                            text = "...他 ${diff.itemsToDelete.size - 5}件",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (diff.itemsToUpdate.isNotEmpty()) {
                    Text(
                        text = "更新: ${diff.itemsToUpdate.size}件",
                        style = MaterialTheme.typography.titleSmall
                    )
                    diff.itemsToUpdate.take(5).forEach { item ->
                        Text(
                            text = "• ${item.circle} - ${item.title}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (diff.itemsToUpdate.size > 5) {
                        Text(
                            text = "...他 ${diff.itemsToUpdate.size - 5}件",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (diff.itemsToAdd.isNotEmpty()) {
                    Text(
                        text = "追加: ${diff.itemsToAdd.size}件",
                        style = MaterialTheme.typography.titleSmall
                    )
                    diff.itemsToAdd.take(5).forEach { item ->
                        Text(
                            text = "• ${item.circle} - ${item.title}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (diff.itemsToAdd.size > 5) {
                        Text(
                            text = "...他 ${diff.itemsToAdd.size - 5}件",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (diff.hasProtectedItems) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append("保護によりスキップ: ")
                            if (diff.protectedFromDelete > 0) {
                                append("削除${diff.protectedFromDelete}件")
                            }
                            if (diff.protectedFromDelete > 0 && diff.protectedFromUpdate > 0) {
                                append(" / ")
                            }
                            if (diff.protectedFromUpdate > 0) {
                                append("更新${diff.protectedFromUpdate}件")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = diff.hasChanges
            ) {
                Text("更新を実行")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (diff.hasChanges) "キャンセル" else "閉じる")
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    eventName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("削除の確認") },
        text = { Text("「$eventName」を削除しますか？\nこの操作は取り消せません。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun RenameEventDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("名前の変更") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("イベント名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(newName) },
                enabled = newName.isNotBlank()
            ) {
                Text("変更")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
