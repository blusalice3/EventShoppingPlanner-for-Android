package com.example.eventshoppingplanner.presentation.screens.createevent

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShoppingList: (String) -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // エラーメッセージ → Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // 作成完了 → ShoppingListScreen に遷移
    LaunchedEffect(uiState.isCreateComplete) {
        if (uiState.isCreateComplete) {
            uiState.createdEventId?.let { eventId ->
                onNavigateToShoppingList(eventId)
            }
        }
    }

    // CSVファイル選択ランチャー
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(context, it)
            viewModel.selectFile(it, fileName)
            // 選択後すぐにパース
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                viewModel.parseFile(inputStream)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新規リスト作成") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 説明テキスト
                Text(
                    text = "スプレッドシートのM列からR列の値をコピーし、下の「サークル名」の欄に貼り付けてください。データが自動で振り分けられます。備考・URLは各欄に直接貼り付けてください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // 即売会名
                OutlinedTextField(
                    value = uiState.eventName,
                    onValueChange = { viewModel.updateEventName(it) },
                    label = { Text("即売会名") },
                    placeholder = { Text("例: C105") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ━━ インポート方法 ━━━
                Text(
                    text = "インポート方法",
                    style = MaterialTheme.typography.titleMedium
                )

                // --- スプレッドシートURLインポート ---
                SpreadsheetUrlImportSection(
                    url = uiState.spreadsheetUrl,
                    onUrlChange = { viewModel.updateSpreadsheetUrl(it) },
                    onImport = { viewModel.importFromSpreadsheetUrl() },
                    isImporting = uiState.isUrlImporting,
                    isEnabled = uiState.eventName.isNotBlank() && !uiState.isUrlImporting,
                    importedItemCount = if (uiState.importSource == ImportSource.SPREADSHEET_URL) uiState.parsedItems.size else null
                )

                // --- CSVファイルインポート ---
                CsvFileImportSection(
                    selectedFileName = uiState.selectedFileName,
                    isParsed = uiState.isParsed,
                    parsedItemCount = if (uiState.importSource == ImportSource.CSV_FILE) uiState.parsedItems.size else null,
                    onSelectFile = {
                        filePickerLauncher.launch(arrayOf("text/*", "*/*"))
                    },
                    onClearFile = { viewModel.clearFile() }
                )

                // ─── または ───
                OrDivider()

                // --- 一括テキストエリア ---
                Text(
                    text = "手動入力（一括テキストエリア）",
                    style = MaterialTheme.typography.titleMedium
                )

                TextAreaGrid(
                    circles = uiState.circles,
                    eventDates = uiState.eventDates,
                    blocks = uiState.blocks,
                    numbers = uiState.numbers,
                    titles = uiState.titles,
                    prices = uiState.prices,
                    remarks = uiState.remarks,
                    urls = uiState.urls,
                    onCirclesChange = { viewModel.onCirclesValueChange(it) },
                    onEventDatesChange = { viewModel.updateEventDates(it) },
                    onBlocksChange = { viewModel.updateBlocks(it) },
                    onNumbersChange = { viewModel.updateNumbers(it) },
                    onTitlesChange = { viewModel.updateTitles(it) },
                    onPricesChange = { viewModel.updatePrices(it) },
                    onRemarksChange = { viewModel.updateRemarks(it) },
                    onUrlsChange = { viewModel.updateUrls(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- アクションボタン ---
                Button(
                    onClick = { viewModel.createEventWithItems() },
                    enabled = uiState.canCreate && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("リストを作成")
                }

                OutlinedButton(
                    onClick = { viewModel.createEmptyEvent() },
                    enabled = uiState.eventName.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CSVなしで作成")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// =============================================================================
// スプレッドシートURLインポートセクション
// =============================================================================

@Composable
private fun SpreadsheetUrlImportSection(
    url: String,
    onUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    isImporting: Boolean,
    isEnabled: Boolean,
    importedItemCount: Int?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "スプレッドシートURLからインポート",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = { Text("https://docs.google.com/spreadsheets/d/.../edit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onImport,
                enabled = isEnabled && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("URLからインポート")
            }

            Text(
                text = "※ シート名「品目表」のデータをインポートします",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // インポート成功バッジ
            if (importedItemCount != null && importedItemCount > 0) {
                ImportSuccessBadge(
                    text = "スプレッドシートから${importedItemCount}件のアイテムをインポート済み"
                )
            }
        }
    }
}

// =============================================================================
// CSVファイルインポートセクション
// =============================================================================

@Composable
private fun CsvFileImportSection(
    selectedFileName: String?,
    isParsed: Boolean,
    parsedItemCount: Int?,
    onSelectFile: () -> Unit,
    onClearFile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CSVファイルからインポート",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onSelectFile) {
                    Text(if (selectedFileName != null) "ファイルを変更" else "ファイルを選択")
                }

                if (selectedFileName != null) {
                    OutlinedButton(onClick = onClearFile) {
                        Text("クリア")
                    }
                }
            }

            // ファイル選択済み表示
            if (selectedFileName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = selectedFileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // パース成功バッジ
            if (isParsed && parsedItemCount != null && parsedItemCount > 0) {
                ImportSuccessBadge(
                    text = "CSVファイルから${parsedItemCount}件のアイテムを検出"
                )
            }

            Text(
                text = "※ A列からD列の値が全て入力されている行のみインポートします",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// =============================================================================
// インポート成功バッジ
// =============================================================================

@Composable
private fun ImportSuccessBadge(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// =============================================================================
// 「または」セパレーター
// =============================================================================

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = "  または  ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

// =============================================================================
// 一括テキストエリア（2列×4行）
// =============================================================================

@Composable
private fun TextAreaGrid(
    circles: String,
    eventDates: String,
    blocks: String,
    numbers: String,
    titles: String,
    prices: String,
    remarks: String,
    urls: String,
    onCirclesChange: (String) -> Unit,
    onEventDatesChange: (String) -> Unit,
    onBlocksChange: (String) -> Unit,
    onNumbersChange: (String) -> Unit,
    onTitlesChange: (String) -> Unit,
    onPricesChange: (String) -> Unit,
    onRemarksChange: (String) -> Unit,
    onUrlsChange: (String) -> Unit
) {
    val mainMinHeight = 120.dp
    val subMinHeight = 80.dp

    // 1行目: サークル名 | 参加日
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BulkTextField(
            value = circles,
            onValueChange = onCirclesChange,
            label = "サークル名",
            placeholder = "サークルA\nサークルB",
            modifier = Modifier.weight(1f),
            minHeight = mainMinHeight
        )
        BulkTextField(
            value = eventDates,
            onValueChange = onEventDatesChange,
            label = "参加日",
            placeholder = "1日目\n2日目",
            modifier = Modifier.weight(1f),
            minHeight = mainMinHeight
        )
    }

    // 2行目: ブロック | ナンバー
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BulkTextField(
            value = blocks,
            onValueChange = onBlocksChange,
            label = "ブロック",
            placeholder = "ア\nイ",
            modifier = Modifier.weight(1f),
            minHeight = mainMinHeight
        )
        BulkTextField(
            value = numbers,
            onValueChange = onNumbersChange,
            label = "ナンバー",
            placeholder = "01a\n02b",
            modifier = Modifier.weight(1f),
            minHeight = mainMinHeight
        )
    }

    // 3行目: タイトル | 頒布価格
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BulkTextField(
            value = titles,
            onValueChange = onTitlesChange,
            label = "タイトル",
            placeholder = "作品A\n作品B",
            modifier = Modifier.weight(1f),
            minHeight = mainMinHeight
        )
        BulkTextField(
            value = prices,
            onValueChange = onPricesChange,
            label = "頒布価格",
            placeholder = "500\n1000",
            modifier = Modifier.weight(1f),
            minHeight = mainMinHeight
        )
    }

    // 4行目: 備考 | URL
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BulkTextField(
            value = remarks,
            onValueChange = onRemarksChange,
            label = "備考",
            placeholder = "",
            modifier = Modifier.weight(1f),
            minHeight = subMinHeight
        )
        BulkTextField(
            value = urls,
            onValueChange = onUrlsChange,
            label = "URL",
            placeholder = "https://...\nhttps://...",
            modifier = Modifier.weight(1f),
            minHeight = subMinHeight
        )
    }
}

@Composable
private fun BulkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 120.dp
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        singleLine = false,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace
        ),
        modifier = modifier.height(minHeight)
    )
}

// =============================================================================
// ユーティリティ
// =============================================================================

private fun getFileName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}