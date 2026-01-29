package com.example.eventshoppingplanner.presentation.screens.shoppinglist

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.presentation.components.ShoppingItemCard
import com.example.eventshoppingplanner.util.CsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    viewModel: ShoppingListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveItem(from.index, to.index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.event?.name ?: "読み込み中...") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    // マップボタン
                    IconButton(onClick = onNavigateToMap) {
                        Icon(Icons.Default.Map, contentDescription = "マップ")
                    }

                    IconButton(onClick = { viewModel.toggleEditMode() }) {
                        Icon(
                            imageVector = if (uiState.isEditMode) Icons.Default.PlayArrow else Icons.Default.Edit,
                            contentDescription = if (uiState.isEditMode) "実行モード" else "編集モード"
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("CSVエクスポート") },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        exportToCsv(
                                            context = context,
                                            viewModel = viewModel,
                                            eventName = uiState.event?.name ?: "export"
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            SummaryBar(
                purchasedCount = uiState.purchasedCount,
                totalCount = uiState.totalCount,
                remainingAmount = uiState.remainingAmount
            )
        },
        floatingActionButton = {
            if (uiState.isEditMode) {
                FloatingActionButton(
                    onClick = { viewModel.showAddItemDialog() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "アイテム追加")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.eventDates.isNotEmpty()) {
                DateTabs(
                    dates = uiState.eventDates,
                    selectedDate = uiState.selectedDate,
                    onDateSelected = { viewModel.selectDate(it) }
                )
            }

            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) }
            )

            if (uiState.blocks.isNotEmpty()) {
                BlockFilter(
                    blocks = uiState.blocks,
                    selectedBlock = uiState.selectedBlock,
                    onBlockSelected = { viewModel.selectBlock(it) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.items.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "アイテムがありません",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.isEditMode) {
                                TextButton(onClick = { viewModel.showAddItemDialog() }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("アイテムを追加")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(uiState.items, key = { it.id }) { item ->
                                ReorderableItem(reorderableLazyListState, key = item.id) {
                                    ShoppingItemCard(
                                        item = item,
                                        onStatusClick = { viewModel.cycleItemStatus(item) },
                                        onItemClick = {
                                            if (uiState.isEditMode) {
                                                viewModel.showEditItemDialog(item)
                                            } else {
                                                viewModel.showStatusDialog(item)
                                            }
                                        },
                                        showDragHandle = uiState.isEditMode,
                                        reorderableScope = this,
                                        onPriceChange = { newPrice ->
                                            viewModel.updateItemPrice(item.id, newPrice)
                                        },
                                        isDuplicateCircle = uiState.duplicateSpaceItemIds.contains(item.id)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ステータス選択ダイアログ
    if (uiState.showStatusDialog && uiState.selectedItem != null) {
        StatusSelectDialog(
            currentStatus = uiState.selectedItem!!.purchaseStatus,
            onStatusSelected = { viewModel.updateItemStatus(it) },
            onDismiss = { viewModel.hideStatusDialog() }
        )
    }

    // アイテム追加ダイアログ
    if (uiState.showAddItemDialog) {
        AddEditItemDialog(
            item = null,
            eventDates = uiState.eventDates,
            onDismiss = { viewModel.hideAddItemDialog() },
            onSave = { item -> viewModel.addItem(item) }
        )
    }

    // アイテム編集ダイアログ
    if (uiState.showEditItemDialog && uiState.editingItem != null) {
        AddEditItemDialog(
            item = uiState.editingItem,
            eventDates = uiState.eventDates,
            onDismiss = { viewModel.hideEditItemDialog() },
            onSave = { item -> viewModel.updateItem(item) },
            onDelete = { viewModel.deleteItem(uiState.editingItem!!) }
        )
    }
}

private suspend fun exportToCsv(
    context: android.content.Context,
    viewModel: ShoppingListViewModel,
    eventName: String
) {
    withContext(Dispatchers.IO) {
        try {
            val items = viewModel.getAllItemsForExport()
            if (items.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "エクスポートするアイテムがありません", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            val fileName = "${eventName.replace(Regex("[^a-zA-Z0-9ぁ-んァ-ン一-龥]"), "_")}_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)

            file.outputStream().use { outputStream ->
                CsvParser.exportToOutputStream(outputStream, items)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "CSVをエクスポート"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "エクスポートに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun DateTabs(
    dates: List<String>,
    selectedDate: String?,
    onDateSelected: (String) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = dates.indexOf(selectedDate).coerceAtLeast(0),
        edgePadding = 16.dp
    ) {
        dates.forEach { date ->
            Tab(
                selected = date == selectedDate,
                onClick = { onDateSelected(date) },
                text = { Text(date) }
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("検索...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "検索") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "クリア")
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun BlockFilter(
    blocks: List<String>,
    selectedBlock: String?,
    onBlockSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedBlock == null,
            onClick = { onBlockSelected(null) },
            label = { Text("全て") }
        )
        blocks.take(5).forEach { block ->
            FilterChip(
                selected = block == selectedBlock,
                onClick = { onBlockSelected(block) },
                label = { Text(block) }
            )
        }
    }
}

@Composable
private fun SummaryBar(
    purchasedCount: Int,
    totalCount: Int,
    remainingAmount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "購入: $purchasedCount / $totalCount 件",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "残り: ¥%,d".format(remainingAmount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatusSelectDialog(
    currentStatus: PurchaseStatus,
    onStatusSelected: (PurchaseStatus) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("購入状態を選択") },
        text = {
            Column {
                PurchaseStatus.entries.forEach { status ->
                    TextButton(
                        onClick = { onStatusSelected(status) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${if (status == currentStatus) "✓ " else "   "}${status.displayName}",
                            color = Color(status.colorHex)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditItemDialog(
    item: ShoppingItem?,
    eventDates: List<String>,
    onDismiss: () -> Unit,
    onSave: (ShoppingItem) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    // 入力状態
    var circle by remember { mutableStateOf(item?.circle ?: "") }
    var eventDate by remember { mutableStateOf(item?.eventDate ?: eventDates.firstOrNull() ?: "1日目") }
    var isCustomEventDate by remember { mutableStateOf(false) }
    var block by remember { mutableStateOf(item?.block ?: "") }
    var number by remember { mutableStateOf(item?.number ?: "") }
    var title by remember { mutableStateOf(item?.title ?: "") }
    var price by remember { mutableStateOf(item?.price?.toString() ?: "") }
    var quantity by remember { mutableStateOf(item?.quantity ?: 1) }
    var remarks by remember { mutableStateOf(item?.remarks ?: "") }
    var url by remember { mutableStateOf(item?.url ?: "") }

    // ドロップダウン展開状態
    var eventDateExpanded by remember { mutableStateOf(false) }
    var priceExpanded by remember { mutableStateOf(false) }
    var quantityExpanded by remember { mutableStateOf(false) }

    // 参加日の選択肢（既存 + デフォルト）
    val defaultDates = listOf("1日目", "2日目", "3日目", "4日目")
    val allEventDates = (eventDates + defaultDates).distinct().sorted()

    // 編集時にカスタム参加日かどうかをチェック
    if (item != null && !allEventDates.contains(item.eventDate)) {
        isCustomEventDate = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "アイテム追加" else "アイテム編集") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // サークル名
                OutlinedTextField(
                    value = circle,
                    onValueChange = { circle = it },
                    label = { Text("サークル名 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // タイトル
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 参加日（ドロップダウン or テキスト入力）
                if (isCustomEventDate) {
                    OutlinedTextField(
                        value = eventDate,
                        onValueChange = { eventDate = it },
                        label = { Text("参加日 *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { isCustomEventDate = false }) {
                                Text("選択", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = eventDateExpanded,
                        onExpandedChange = { eventDateExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = eventDate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("参加日 *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eventDateExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = eventDateExpanded,
                            onDismissRequest = { eventDateExpanded = false }
                        ) {
                            allEventDates.forEach { date ->
                                DropdownMenuItem(
                                    text = { Text(date) },
                                    onClick = {
                                        eventDate = date
                                        eventDateExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("任意の値を入力...") },
                                onClick = {
                                    isCustomEventDate = true
                                    eventDateExpanded = false
                                }
                            )
                        }
                    }
                }

                // ブロック・ナンバー
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = block,
                        onValueChange = { block = it },
                        label = { Text("ブロック *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("ナンバー *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 価格（テキスト入力 + クイック選択）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = if (price.isEmpty()) "価格未定" else price,
                        onValueChange = {
                            val filtered = it.filter { c -> c.isDigit() }
                            price = filtered
                        },
                        label = { Text("価格") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        suffix = { if (price.isNotEmpty()) Text("円") },
                        textStyle = if (price.isEmpty()) {
                            MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge
                        }
                    )

                    // クイック選択ボタン
                    ExposedDropdownMenuBox(
                        expanded = priceExpanded,
                        onExpandedChange = { priceExpanded = it }
                    ) {
                        TextButton(
                            onClick = { priceExpanded = true },
                            modifier = Modifier.menuAnchor()
                        ) {
                            Text("選択")
                        }
                        ExposedDropdownMenu(
                            expanded = priceExpanded,
                            onDismissRequest = { priceExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "価格未定",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    price = ""
                                    priceExpanded = false
                                }
                            )
                            (0..100).forEach { i ->
                                val p = i * 100
                                DropdownMenuItem(
                                    text = { Text("${p}円") },
                                    onClick = {
                                        price = p.toString()
                                        priceExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 数量
                ExposedDropdownMenuBox(
                    expanded = quantityExpanded,
                    onExpandedChange = { quantityExpanded = it }
                ) {
                    OutlinedTextField(
                        value = quantity.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("数量") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = quantityExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = quantityExpanded,
                        onDismissRequest = { quantityExpanded = false }
                    ) {
                        (1..10).forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q.toString()) },
                                onClick = {
                                    quantity = q
                                    quantityExpanded = false
                                }
                            )
                        }
                    }
                }

                // 備考
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("備考") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // URL
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://...") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newItem = ShoppingItem(
                        id = item?.id ?: java.util.UUID.randomUUID().toString(),
                        eventId = item?.eventId ?: "",
                        circle = circle,
                        eventDate = eventDate,
                        block = block,
                        number = number,
                        title = title,
                        price = price.toIntOrNull(),
                        purchaseStatus = item?.purchaseStatus ?: PurchaseStatus.NONE,
                        quantity = quantity,
                        remarks = remarks,
                        url = url.ifBlank { null },
                        sortOrder = item?.sortOrder ?: 0,
                        isInExecuteList = item?.isInExecuteList ?: false
                    )
                    onSave(newItem)
                },
                enabled = circle.isNotBlank() && eventDate.isNotBlank() && block.isNotBlank() && number.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("削除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
            }
        }
    )
}