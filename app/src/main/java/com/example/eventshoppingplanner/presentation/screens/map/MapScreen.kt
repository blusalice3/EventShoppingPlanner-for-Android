package com.example.eventshoppingplanner.presentation.screens.map

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventshoppingplanner.domain.model.*
import com.example.eventshoppingplanner.domain.model.PurchaseStatus
import com.example.eventshoppingplanner.util.HallUtils

/**
 * 新規アイテム追加時のプリセット情報
 */
private data class NewItemPreset(
    val eventDate: String,
    val block: String,
    val number: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // セルタップ時のダイアログ状態
    var selectedCellInfo by remember { mutableStateOf<CellTapInfo?>(null) }

    // 新規アイテム追加ダイアログの状態
    var showAddItemDialog by remember { mutableStateOf(false) }
    var newItemPreset by remember { mutableStateOf<NewItemPreset?>(null) }

    // ホールアイテム数を更新（ホール定義がある場合のみ）
    LaunchedEffect(uiState.halls.size, uiState.items.size, uiState.currentMapData?.id) {
        if (uiState.halls.isNotEmpty() && uiState.currentMapData != null) {
            viewModel.updateHallItemCounts()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMapFile(it) }
    }

    // 全体をBoxでラップ（パネルのオーバーレイ表示用）
    Box(modifier = Modifier.fillMaxSize()) {

        // セルアイテムダイアログ
        selectedCellInfo?.let { cellInfo ->
            CellItemsDialog(
                blockName = cellInfo.blockName,
                number = cellInfo.number,
                items = cellInfo.items,
                visitListItemIds = uiState.visitListItemIds,
                onDismiss = { selectedCellInfo = null },
                onToggleVisitList = { itemId ->
                    viewModel.toggleVisitListItem(itemId)
                },
                onOpenUrl = { url ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                },
                onAddNewItem = if (cellInfo.isInBlockDefinition) {
                    {
                        // マップ名から参加日を抽出（例: "1日目マップ" -> "1日目"）
                        val eventDate = uiState.selectedMapName?.replace("マップ", "") ?: ""
                        newItemPreset = NewItemPreset(
                            eventDate = eventDate,
                            block = cellInfo.blockName,
                            number = cellInfo.number.toString()
                        )
                        selectedCellInfo = null
                        showAddItemDialog = true
                    }
                } else null
            )
        }

        // 新規アイテム追加ダイアログ
        if (showAddItemDialog && newItemPreset != null) {
            AddItemFromMapDialog(
                preset = newItemPreset!!,
                eventId = eventId,
                onDismiss = {
                    showAddItemDialog = false
                    newItemPreset = null
                },
                onSave = { item ->
                    viewModel.addItem(item)
                    showAddItemDialog = false
                    newItemPreset = null
                }
            )
        }

        // ブロック定義パネル
        uiState.currentMapData?.let { mapData ->
            BlockDefinitionPanel(
                isOpen = uiState.isBlockDefinitionPanelOpen,
                isVisible = uiState.isBlockDefinitionPanelVisible,
                onClose = { viewModel.closeBlockDefinitionPanel() },
                mapData = mapData,
                selectedCells = uiState.selectedCells,
                onStartCellSelection = { selectionType, editState ->
                    viewModel.startCellSelection(selectionType, editState)
                },
                onCancelCellSelection = { viewModel.cancelCellSelection() },
                onUpdateBlocks = { blocks -> viewModel.updateBlocks(blocks) },
                isInSelectionMode = uiState.cellSelectionMode != CellSelectionMode.NONE,
                currentSelectionType = uiState.currentSelectionType,
                pendingEditState = uiState.pendingEditState,
                onClearPendingEditState = { viewModel.clearPendingEditState() }
            )
        }

        // ホール定義パネル
        if (uiState.isHallDefinitionPanelVisible) {
            uiState.currentMapData?.let { mapData ->
                HallDefinitionPanel(
                    mapData = mapData,
                    halls = uiState.halls,
                    pendingEditState = uiState.pendingHallEditState,
                    pendingVertices = uiState.selectedHallVertices,
                    onDismiss = { viewModel.closeHallDefinitionPanel() },
                    onSaveHalls = { halls -> viewModel.saveHalls(halls) },
                    onStartVertexSelection = { editingHallId, editState ->
                        viewModel.startHallVertexSelection(editingHallId, editState)
                    },
                    onConsumePendingEditState = { viewModel.consumePendingHallEditState() }
                )
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.event?.name ?: "マップ") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                        }
                    },
                    actions = {
                        // ホール選択アイコン（ホール定義がある場合のみ表示）
                        if (uiState.currentMapData != null && uiState.halls.isNotEmpty()) {
                            Box {
                                IconButton(
                                    onClick = { viewModel.toggleHallSelector() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = "ホール選択",
                                        tint = if (uiState.selectedHallId != null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            LocalContentColor.current
                                        }
                                    )
                                }
                                // 選択中インジケーター
                                if (uiState.selectedHallId != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                            .size(8.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                    )
                                }

                                // ドロップダウンメニュー
                                DropdownMenu(
                                    expanded = uiState.isHallSelectorOpen,
                                    onDismissRequest = { viewModel.closeHallSelector() }
                                ) {
                                    // 全ホール
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "全ホール",
                                                    fontWeight = if (uiState.selectedHallId == null) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = { viewModel.selectHall(null) },
                                        leadingIcon = {
                                            if (uiState.selectedHallId == null) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )

                                    HorizontalDivider()

                                    // 各ホール
                                    uiState.halls.forEach { hall ->
                                        val itemCount = uiState.hallItemCounts[hall.id]
                                        val executeCount = itemCount?.executeCount ?: 0
                                        val totalCount = itemCount?.totalCount ?: 0

                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // ホール色インジケーター
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .background(
                                                                    Color(hall.color),
                                                                    CircleShape
                                                                )
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            hall.name,
                                                            fontWeight = if (uiState.selectedHallId == hall.id) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                    Text(
                                                        "(${executeCount}/${totalCount}件)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = { viewModel.selectHall(hall.id) },
                                            leadingIcon = {
                                                if (uiState.selectedHallId == hall.id) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        // ルート表示トグルボタン（マップがある場合のみ表示）
                        if (uiState.currentMapData != null) {
                            IconButton(
                                onClick = { viewModel.toggleRouteVisibility() }
                            ) {
                                Icon(
                                    Icons.Default.Timeline,
                                    contentDescription = "ルート表示",
                                    tint = if (uiState.isRouteVisible)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        // 訪問先リストボタン（マップがある場合のみ表示）
                        if (uiState.currentMapData != null) {
                            IconButton(
                                onClick = { viewModel.openVisitListPanel() }
                            ) {
                                Icon(Icons.Default.PushPin, "訪問先リスト")
                            }
                        }
                        // ブロック定義ボタン（マップがある場合のみ表示）
                        if (uiState.currentMapData != null) {
                            IconButton(
                                onClick = { viewModel.openBlockDefinitionPanel() }
                            ) {
                                Icon(Icons.Default.GridOn, "ブロック定義")
                            }
                            // ホール定義ボタン
                            IconButton(
                                onClick = { viewModel.openHallDefinitionPanel() }
                            ) {
                                Icon(Icons.Default.Crop, "ホール定義")
                            }
                        }
                        IconButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel"
                                ))
                            }
                        ) {
                            Icon(Icons.Default.FolderOpen, "マップを開く")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.mapDataList.isEmpty()) {
                    NoMapPlaceholder(
                        onOpenFile = {
                            filePickerLauncher.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel"
                            ))
                        }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // タブ
                        if (uiState.mapDataList.size > 1) {
                            MapTabs(
                                mapNames = uiState.mapDataList.keys.toList(),
                                selectedMapName = uiState.selectedMapName,
                                onSelectMap = { viewModel.selectMap(it) }
                            )
                        }

                        // マップキャンバス
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clipToBounds()
                        ) {
                            uiState.currentMapData?.let { mapData ->
                                MapCanvas(
                                    mapData = mapData,
                                    scale = uiState.scale,
                                    offsetX = uiState.offsetX,
                                    offsetY = uiState.offsetY,
                                    cellItemsMap = uiState.cellItemsMap,
                                    visitListItemIds = uiState.visitListItemIds,
                                    visitPoints = uiState.visitPoints,
                                    routeSegments = uiState.routeSegments,
                                    isRouteVisible = uiState.isRouteVisible,
                                    selectedCells = uiState.selectedCells,
                                    isSelectionMode = uiState.cellSelectionMode != CellSelectionMode.NONE,
                                    currentSelectionType = uiState.currentSelectionType,
                                    halls = uiState.halls,
                                    selectedHallId = uiState.selectedHallId,
                                    hallMarkers = uiState.hallMarkers,
                                    isHallVertexSelectionMode = uiState.hallVertexSelectionMode == HallVertexSelectionMode.SELECTING,
                                    onPan = { dx, dy -> viewModel.pan(dx, dy) },
                                    onPinchZoom = { newScale, newOffsetX, newOffsetY ->
                                        viewModel.updateScaleAndOffset(newScale, newOffsetX, newOffsetY)
                                    },
                                    onCellTap = { row, col, items ->
                                        // セル選択モード中は選択に使用
                                        if (uiState.cellSelectionMode != CellSelectionMode.NONE) {
                                            viewModel.addSelectedCell(row, col)
                                        } else {
                                            // ブロック定義内かチェックし、ブロック情報を取得
                                            val blockInfo = findBlockInfoForCell(
                                                row, col,
                                                mapData.blocks,
                                                mapData.cells,
                                                mapData.mergedCells
                                            )

                                            when {
                                                blockInfo != null -> {
                                                    // ブロック定義内のセル
                                                    selectedCellInfo = CellTapInfo(
                                                        row = row,
                                                        col = col,
                                                        blockName = blockInfo.first,
                                                        number = blockInfo.second,
                                                        items = items,
                                                        isInBlockDefinition = true
                                                    )
                                                }
                                                items.isNotEmpty() -> {
                                                    // ブロック定義外だがアイテムがある場合
                                                    val firstItem = items.first()
                                                    val numValue = extractNumberFromItemNumber(firstItem.number) ?: 0
                                                    selectedCellInfo = CellTapInfo(
                                                        row = row,
                                                        col = col,
                                                        blockName = firstItem.block,
                                                        number = numValue,
                                                        items = items,
                                                        isInBlockDefinition = false
                                                    )
                                                }
                                                // ブロック定義外かつアイテムなし → 何もしない
                                            }
                                        }
                                    },
                                    onSelectedCellTap = { row, col ->
                                        // 選択済みセルをタップしたら選択解除
                                        viewModel.removeSelectedCell(row, col)
                                    }
                                )

                                // セル選択モード中のオーバーレイ
                                if (uiState.cellSelectionMode != CellSelectionMode.NONE) {
                                    val (title, requiredCount) = when (uiState.currentSelectionType) {
                                        CellSelectionType.CORNER, CellSelectionType.MULTI_CORNER -> "4つの角をタップ" to 4
                                        CellSelectionType.RANGE_START -> "開始点と終了点をタップ" to 2
                                        CellSelectionType.INDIVIDUAL -> "セルをタップして選択" to -1
                                        null -> "セルを選択" to 4
                                    }
                                    // 確定ボタンの表示条件
                                    val canConfirm = when (uiState.currentSelectionType) {
                                        CellSelectionType.CORNER, CellSelectionType.MULTI_CORNER -> uiState.selectedCells.size >= 4
                                        CellSelectionType.RANGE_START -> uiState.selectedCells.size >= 2
                                        CellSelectionType.INDIVIDUAL -> uiState.selectedCells.isNotEmpty()
                                        null -> uiState.selectedCells.size >= 4
                                    }
                                    CellSelectionOverlay(
                                        selectedCount = uiState.selectedCells.size,
                                        requiredCount = requiredCount,
                                        title = title,
                                        showConfirmButton = canConfirm,
                                        onConfirm = { viewModel.confirmSelection() },
                                        onCancel = { viewModel.cancelCellSelection() }
                                    )
                                }

                                // ホールマーカー選択モード中のオーバーレイ
                                if (uiState.hallVertexSelectionMode == HallVertexSelectionMode.SELECTING) {
                                    HallMarkerSelectionOverlay(
                                        markers = uiState.hallMarkers,
                                        isPlacingMarker = uiState.isPlacingMarker,
                                        mapData = mapData,
                                        scale = uiState.scale,
                                        offsetX = uiState.offsetX,
                                        offsetY = uiState.offsetY,
                                        onStartPlacing = {
                                            viewModel.startPlacingMarker()
                                        },
                                        onConfirmPlacement = { row, col ->
                                            viewModel.confirmMarkerPlacement(row, col)
                                        },
                                        onCancelPlacing = {
                                            viewModel.cancelPlacingMarker()
                                        },
                                        onRemoveMarker = { markerId ->
                                            viewModel.removeHallMarker(markerId)
                                        },
                                        onConfirm = {
                                            viewModel.confirmHallVertexSelection()
                                            viewModel.showHallDefinitionPanel()
                                        },
                                        onCancel = {
                                            viewModel.cancelHallVertexSelection()
                                            viewModel.showHallDefinitionPanel()
                                        }
                                    )
                                }
                            } // end of let { mapData -> }
                        }
                    }
                }

                // エラーメッセージ
                uiState.errorMessage?.let { error ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("閉じる")
                            }
                        }
                    ) {
                        Text(error)
                    }
                }
            }
        }

        // 訪問先リストパネル（Scaffoldの上に重ねて表示）
        uiState.currentMapData?.let { mapData ->
            VisitListPanel(
                isOpen = uiState.isVisitListPanelOpen,
                visitListItemIds = uiState.visitListItemIds,
                items = uiState.items,
                halls = uiState.halls,
                blocks = mapData.blocks,
                currentDayName = mapData.dayName,
                displayMode = uiState.visitListDisplayMode,
                panelWidth = uiState.visitListPanelWidth,
                selectionMode = uiState.visitListSelectionMode,
                rangeStart = uiState.visitListRangeStart,
                rangeEnd = uiState.visitListRangeEnd,
                groupOrder = uiState.groupOrder,
                onClose = { viewModel.closeVisitListPanel() },
                onRemoveFromVisitList = { itemId -> viewModel.removeFromVisitList(itemId) },
                onChangePriority = { itemId, priority -> viewModel.changeItemPriority(itemId, priority) },
                onChangeDisplayMode = { mode -> viewModel.changeVisitListDisplayMode(mode) },
                onChangePanelWidth = { width -> viewModel.changeVisitListPanelWidth(width) },
                onMoveItem = { from, to -> viewModel.moveItemInVisitList(from, to) },
                onMoveItemUp = { itemId -> viewModel.moveItemUp(itemId) },
                onMoveItemDown = { itemId -> viewModel.moveItemDown(itemId) },
                onSetSelectionMode = { mode -> viewModel.setVisitListSelectionMode(mode) },
                onSetRangeStart = { itemId -> viewModel.setVisitListRangeStart(itemId) },
                onSetRangeEnd = { itemId -> viewModel.setVisitListRangeEnd(itemId) },
                onReverseRange = { viewModel.reverseVisitListRange() },
                onOpenHallOrderPanel = { viewModel.openHallOrderPanel() }
            )

            // ホール順序パネル
            HallOrderPanel(
                isOpen = uiState.isHallOrderPanelOpen,
                groupOrder = uiState.groupOrder,
                halls = uiState.halls,
                onClose = { viewModel.closeHallOrderPanel() },
                onSave = { newOrder -> viewModel.updateGroupOrder(newOrder) },
                onReorderExecuteList = { viewModel.reorderVisitListByGroupOrder() },
                getGroupItemCount = { groupId -> viewModel.getGroupItemCount(groupId) }
            )
        }

    } // end of Box
}

@Composable
private fun MapTabs(
    mapNames: List<String>,
    selectedMapName: String?,
    onSelectMap: (String) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = mapNames.indexOf(selectedMapName).coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth()
    ) {
        mapNames.forEach { name ->
            Tab(
                selected = name == selectedMapName,
                onClick = { onSelectMap(name) },
                text = { Text(name) }
            )
        }
    }
}

@Composable
private fun MapCanvas(
    mapData: DayMapData,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    cellItemsMap: Map<String, List<ShoppingItem>>,
    visitListItemIds: List<String> = emptyList(),  // 訪問先リストに追加されたアイテムID
    visitPoints: List<VisitPoint> = emptyList(),  // 訪問ポイント（ルート描画用）
    routeSegments: List<RouteSegment> = emptyList(),  // ルートセグメント（ルート描画用）
    isRouteVisible: Boolean = true,  // ルート表示のON/OFF
    selectedCells: List<Pair<Int, Int>> = emptyList(),
    isSelectionMode: Boolean = false,
    currentSelectionType: CellSelectionType? = null,
    halls: List<HallDefinition> = emptyList(),
    selectedHallId: String? = null,
    // マーカーシステム
    hallMarkers: List<HallMarker> = emptyList(),
    isHallVertexSelectionMode: Boolean = false,
    onPan: (Float, Float) -> Unit,
    onPinchZoom: (Float, Float, Float) -> Unit,  // newScale, newOffsetX, newOffsetY
    onCellTap: (Int, Int, List<ShoppingItem>) -> Unit = { _, _, _ -> },
    onSelectedCellTap: (Int, Int) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current

    // ジェスチャー完了を示すバージョン（パン/ズーム終了時にインクリメント）
    // これをpointerInputのkeyに使用し、操作完了後に座標系を更新
    // mapData.idをキーにして、マップ切り替え時にリセット
    var gestureVersion by remember(mapData.id) { mutableStateOf(0) }

    // スケール変更時にgestureVersionを更新
    LaunchedEffect(scale) {
        gestureVersion++
    }

    // 選択済みセルのセット
    val selectedCellsSet = remember(selectedCells) {
        selectedCells.map { "${it.first}-${it.second}" }.toSet()
    }

    // マーカー位置のセット（描画用）
    val markerCellsSet = remember(hallMarkers) {
        hallMarkers.map { "${it.row}-${it.col}" }.toSet()
    }

    // 4角選択の場合の範囲を計算
    val selectionBounds = remember(selectedCells, currentSelectionType) {
        if ((currentSelectionType == CellSelectionType.CORNER ||
                    currentSelectionType == CellSelectionType.MULTI_CORNER) &&
            selectedCells.size >= 4) {
            val rows = selectedCells.map { it.first }
            val cols = selectedCells.map { it.second }
            val minRow = rows.minOrNull() ?: 0
            val maxRow = rows.maxOrNull() ?: 0
            val minCol = cols.minOrNull() ?: 0
            val maxCol = cols.maxOrNull() ?: 0
            if (minRow > 0 && maxRow > 0) {
                SelectionBounds(minRow, minCol, maxRow, maxCol)
            } else null
        } else null
    }

    // セルマップを作成
    val cellMap = remember(mapData) {
        mapData.cells.associateBy { "${it.row}-${it.col}" }
    }

    // 結合セルマップを作成
    val mergeMap = remember(mapData) {
        val map = mutableMapOf<String, MergedCellInfo>()
        mapData.mergedCells.forEach { merged ->
            for (r in merged.startRow..merged.endRow) {
                for (c in merged.startCol..merged.endCol) {
                    map["$r-$c"] = merged
                }
            }
        }
        map
    }

    // 色定数
    val colorGreen = Color(0xFFE8F5E9)
    val colorYellow = Color(0xFFFFF9C4)
    val colorRed = Color(0xFFFFCDD2)
    val colorWhite = Color.White
    val colorSelected = Color(0xFF2196F3)  // 選択セルの色
    val colorSelectionArea = Color(0xFF2196F3).copy(alpha = 0.15f)  // 選択範囲の塗りつぶし色

    // 選択されたホールの範囲を計算
    val selectedHall = remember(selectedHallId, halls) {
        if (selectedHallId != null) {
            halls.find { it.id == selectedHallId }
        } else null
    }

    // ホール内のセルかどうかを判定する関数
    val isInSelectedHall: (Int, Int) -> Boolean = remember(selectedHall) {
        if (selectedHall == null || selectedHall.vertices.size < 3) {
            { _, _ -> true }  // 全ホール表示時は常にtrue
        } else {
            { row, col -> HallUtils.isPointInPolygon(row, col, selectedHall.vertices) }
        }
    }

    // ホールの範囲（ピクセル座標）を計算 - スケールなしの基準座標
    val hallBoundsBase = remember(selectedHall, mapData) {
        if (selectedHall == null) null
        else {
            val bounds = HallUtils.getHallBounds(selectedHall)
            if (bounds != null && bounds.minCol >= 1 && bounds.minRow >= 1) {
                // 行/列からピクセル座標を計算（スケールなし）
                var minX = 0f
                for (c in 1 until bounds.minCol) {
                    minX += mapData.columnWidths[c] ?: mapData.defaultColumnWidth
                }
                var maxX = minX
                for (c in bounds.minCol..minOf(bounds.maxCol, mapData.maxCol)) {
                    maxX += mapData.columnWidths[c] ?: mapData.defaultColumnWidth
                }

                var minY = 0f
                for (r in 1 until bounds.minRow) {
                    minY += mapData.rowHeights[r] ?: mapData.defaultRowHeight
                }
                var maxY = minY
                for (r in bounds.minRow..minOf(bounds.maxRow, mapData.maxRow)) {
                    maxY += mapData.rowHeights[r] ?: mapData.defaultRowHeight
                }

                // 有効な範囲かチェック
                if (maxX > minX && maxY > minY) {
                    HallBoundsPixels(minX, maxX, minY, maxY)
                } else null
            } else null
        }
    }

    // 後方互換性のため（描画用）
    val hallBoundsPixels = hallBoundsBase

    // 現在のスケールとオフセットをローカルで保持（ピンチ操作中の計算用）
    // mapData.idをキーにして、マップ切り替え時にリセット
    var localScale by remember(mapData.id) { mutableStateOf(scale) }
    var localOffsetX by remember(mapData.id) { mutableStateOf(offsetX) }
    var localOffsetY by remember(mapData.id) { mutableStateOf(offsetY) }

    // 外部から渡されたスケール/オフセットが変わった時に同期
    LaunchedEffect(scale, offsetX, offsetY) {
        localScale = scale
        localOffsetX = offsetX
        localOffsetY = offsetY
    }

    // ドラッグ中のセル（ハイライト用）
    // mapData.idをキーにして、マップ切り替え時にリセット
    var highlightedCell by remember(mapData.id) { mutableStateOf<Pair<Int, Int>?>(null) }

    // タップ位置からセル座標を計算する関数（ローカル値を参照）
    fun findCellAtPosition(tapX: Float, tapY: Float): Pair<Int, Int>? {
        var currentX = localOffsetX
        var foundCol = -1
        for (col in 1..mapData.maxCol) {
            val colWidth = (mapData.columnWidths[col] ?: mapData.defaultColumnWidth) * localScale
            if (tapX >= currentX && tapX < currentX + colWidth) {
                foundCol = col
                break
            }
            currentX += colWidth
        }

        var currentY = localOffsetY
        var foundRow = -1
        for (row in 1..mapData.maxRow) {
            val rowHeight = (mapData.rowHeights[row] ?: mapData.defaultRowHeight) * localScale
            if (tapY >= currentY && tapY < currentY + rowHeight) {
                foundRow = row
                break
            }
            currentY += rowHeight
        }

        return if (foundRow > 0 && foundCol > 0) Pair(foundRow, foundCol) else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // ピンチズームとパン処理（マーカーモード中も有効）
                .pointerInput(selectedCellsSet, isSelectionMode, gestureVersion, hallBoundsPixels) {
                    detectTransformGestures(
                        panZoomLock = false
                    ) { centroid, pan, zoom, _ ->
                        if (zoom != 1f) {
                            // ピンチズーム処理
                            val newScale = (localScale * zoom).coerceIn(0.1f, 5.0f)

                            // ピンチの中心点を基準にズーム
                            // 中心点がスケール前後で同じ位置に留まるようにオフセットを調整
                            val scaleChange = newScale / localScale
                            var newOffsetX = centroid.x - (centroid.x - localOffsetX) * scaleChange
                            var newOffsetY = centroid.y - (centroid.y - localOffsetY) * scaleChange

                            // ホール選択時のズーム後オフセット制限
                            if (hallBoundsPixels != null) {
                                val hallWidth = (hallBoundsPixels.maxX - hallBoundsPixels.minX) * newScale
                                val hallHeight = (hallBoundsPixels.maxY - hallBoundsPixels.minY) * newScale
                                val hallStartX = -hallBoundsPixels.minX * newScale
                                val hallStartY = -hallBoundsPixels.minY * newScale

                                // ホールが画面内に収まるように制限
                                // ホールの上端/左端が画面の下20%/右20%より上/左にある
                                val maxOffsetX = hallStartX + size.width * 0.2f
                                val maxOffsetY = hallStartY + size.height * 0.2f
                                // ホールの下端/右端が画面の上20%/左20%より下/右にある
                                val minOffsetX = hallStartX - hallWidth + size.width * 0.8f
                                val minOffsetY = hallStartY - hallHeight + size.height * 0.8f

                                // ホールが画面より大きい場合（min > max）でも制限をかける
                                if (minOffsetX <= maxOffsetX) {
                                    newOffsetX = newOffsetX.coerceIn(minOffsetX, maxOffsetX)
                                } else {
                                    // ホールが画面より大きい場合：中央に寄せる方向に制限
                                    val centerOffset = (minOffsetX + maxOffsetX) / 2
                                    // ホールの端が画面に少なくとも20%残るように制限
                                    val limitedMin = hallStartX - hallWidth + size.width * 0.2f
                                    val limitedMax = hallStartX + size.width * 0.8f
                                    newOffsetX = newOffsetX.coerceIn(limitedMin, limitedMax)
                                }
                                if (minOffsetY <= maxOffsetY) {
                                    newOffsetY = newOffsetY.coerceIn(minOffsetY, maxOffsetY)
                                } else {
                                    // ホールが画面より大きい場合：端が画面に残るように制限
                                    val limitedMin = hallStartY - hallHeight + size.height * 0.2f
                                    val limitedMax = hallStartY + size.height * 0.8f
                                    newOffsetY = newOffsetY.coerceIn(limitedMin, limitedMax)
                                }
                            }

                            localScale = newScale
                            localOffsetX = newOffsetX
                            localOffsetY = newOffsetY

                            // ViewModelに即時反映
                            onPinchZoom(localScale, localOffsetX, localOffsetY)
                        } else if (pan.x != 0f || pan.y != 0f) {
                            // パン処理
                            var newOffsetX = localOffsetX + pan.x
                            var newOffsetY = localOffsetY + pan.y

                            // ホール選択時のパン制限
                            if (hallBoundsPixels != null) {
                                val hallWidth = (hallBoundsPixels.maxX - hallBoundsPixels.minX) * localScale
                                val hallHeight = (hallBoundsPixels.maxY - hallBoundsPixels.minY) * localScale
                                val hallStartX = -hallBoundsPixels.minX * localScale
                                val hallStartY = -hallBoundsPixels.minY * localScale

                                // ホールが画面内に収まるように制限
                                // ホールの上端/左端が画面の下20%/右20%より上/左にある
                                val maxOffsetX = hallStartX + size.width * 0.2f
                                val maxOffsetY = hallStartY + size.height * 0.2f
                                // ホールの下端/右端が画面の上20%/左20%より下/右にある
                                val minOffsetX = hallStartX - hallWidth + size.width * 0.8f
                                val minOffsetY = hallStartY - hallHeight + size.height * 0.8f

                                // ホールが画面より大きい場合（min > max）でも制限をかける
                                if (minOffsetX <= maxOffsetX) {
                                    newOffsetX = newOffsetX.coerceIn(minOffsetX, maxOffsetX)
                                } else {
                                    // ホールが画面より大きい場合：端が画面に少なくとも20%残るように制限
                                    val limitedMin = hallStartX - hallWidth + size.width * 0.2f
                                    val limitedMax = hallStartX + size.width * 0.8f
                                    newOffsetX = newOffsetX.coerceIn(limitedMin, limitedMax)
                                }
                                if (minOffsetY <= maxOffsetY) {
                                    newOffsetY = newOffsetY.coerceIn(minOffsetY, maxOffsetY)
                                } else {
                                    // ホールが画面より大きい場合：端が画面に残るように制限
                                    val limitedMin = hallStartY - hallHeight + size.height * 0.2f
                                    val limitedMax = hallStartY + size.height * 0.8f
                                    newOffsetY = newOffsetY.coerceIn(limitedMin, limitedMax)
                                }
                            }

                            val actualPanX = newOffsetX - localOffsetX
                            val actualPanY = newOffsetY - localOffsetY

                            localOffsetX = newOffsetX
                            localOffsetY = newOffsetY
                            onPan(actualPanX, actualPanY)
                        }
                    }
                }
                // タップ処理（別のpointerInputで処理）
                .pointerInput(selectedCellsSet, isSelectionMode, gestureVersion, isHallVertexSelectionMode) {
                    detectTapGestures { position ->
                        // マーカーモード中はタップ処理しない
                        if (isHallVertexSelectionMode) return@detectTapGestures

                        val cellPos = findCellAtPosition(position.x, position.y)
                        cellPos?.let { (row, col) ->
                            // 結合セルの場合は開始セルを使用
                            val mergedInfo = mergeMap["$row-$col"]
                            val actualRow = mergedInfo?.startRow ?: row
                            val actualCol = mergedInfo?.startCol ?: col
                            val key = "$actualRow-$actualCol"

                            // セル選択モード中で、既に選択済みのセルをタップした場合は選択解除
                            if (isSelectionMode && selectedCellsSet.contains(key)) {
                                onSelectedCellTap(actualRow, actualCol)
                            } else {
                                val items = cellItemsMap[key] ?: emptyList()
                                onCellTap(actualRow, actualCol, items)
                            }
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 描画開始位置（ローカル値を使用）
            val startX = localOffsetX
            val startY = localOffsetY
            val drawScale = localScale

            // 各セルの位置を計算するためのヘルパー関数
            fun getColumnX(col: Int): Float {
                var x = startX
                for (c in 1 until col) {
                    val width = mapData.columnWidths[c] ?: mapData.defaultColumnWidth
                    x += width * drawScale
                }
                return x
            }

            fun getRowY(row: Int): Float {
                var y = startY
                for (r in 1 until row) {
                    val height = mapData.rowHeights[r] ?: mapData.defaultRowHeight
                    y += height * drawScale
                }
                return y
            }

            fun getColumnWidth(col: Int): Float {
                return (mapData.columnWidths[col] ?: mapData.defaultColumnWidth) * drawScale
            }

            fun getRowHeight(row: Int): Float {
                return (mapData.rowHeights[row] ?: mapData.defaultRowHeight) * drawScale
            }

            // 描画範囲を計算
            var currentX = startX
            var startCol = 1
            while (currentX < 0 && startCol < mapData.maxCol) {
                currentX += getColumnWidth(startCol)
                startCol++
            }

            var currentY = startY
            var startRow = 1
            while (currentY < 0 && startRow < mapData.maxRow) {
                currentY += getRowHeight(startRow)
                startRow++
            }

            // 描画済みの結合セルを追跡
            val drawnMergedCells = mutableSetOf<String>()

            // セルを描画
            for (row in maxOf(1, startRow - 5)..minOf(mapData.maxRow, startRow + 200)) {
                val y = getRowY(row)
                if (y > canvasHeight + 100) break

                for (col in maxOf(1, startCol - 5)..minOf(mapData.maxCol, startCol + 300)) {
                    val x = getColumnX(col)
                    if (x > canvasWidth + 100) break

                    // ホール選択時はホール外のセルをスキップ
                    if (!isInSelectedHall(row, col)) continue

                    val cellKey = "$row-$col"
                    val mergedInfo = mergeMap[cellKey]

                    // 結合セルの場合
                    if (mergedInfo != null) {
                        val mergeKey = "${mergedInfo.startRow}-${mergedInfo.startCol}"

                        // ホール選択時は結合セルの開始セルがホール内かチェック
                        if (!isInSelectedHall(mergedInfo.startRow, mergedInfo.startCol)) continue

                        // 結合セルの開始セルの場合のみ描画
                        if (row == mergedInfo.startRow && col == mergedInfo.startCol &&
                            !drawnMergedCells.contains(mergeKey)) {
                            drawnMergedCells.add(mergeKey)

                            // 結合セルの幅と高さを計算
                            var mergedWidth = 0f
                            for (c in mergedInfo.startCol..mergedInfo.endCol) {
                                mergedWidth += getColumnWidth(c)
                            }
                            var mergedHeight = 0f
                            for (r in mergedInfo.startRow..mergedInfo.endRow) {
                                mergedHeight += getRowHeight(r)
                            }

                            val cell = cellMap[mergeKey]
                            val items = cellItemsMap[mergeKey] ?: emptyList()

                            // 背景色を決定（訪問先リスト状態に基づく）
                            val bgColor = if (items.isNotEmpty()) {
                                val allInVisitList = items.all { visitListItemIds.contains(it.id) }
                                val anyInVisitList = items.any { visitListItemIds.contains(it.id) }
                                when {
                                    allInVisitList -> colorRed     // 全アイテム訪問先登録 → 赤
                                    anyInVisitList -> colorYellow  // 一部アイテム訪問先登録 → 黄
                                    else -> Color(0xFFBBDEFB)      // 全アイテム未登録 → 青
                                }
                            } else if (cell?.backgroundColor != null) {
                                Color(cell.backgroundColor)
                            } else {
                                colorWhite
                            }

                            // 背景を描画
                            drawRect(
                                color = bgColor,
                                topLeft = Offset(x, y),
                                size = Size(mergedWidth, mergedHeight)
                            )

                            // 罫線を描画
                            cell?.let {
                                drawCellBorders(x, y, mergedWidth, mergedHeight, it.borders, scale)
                            }

                            // テキストを描画
                            val textValue = mergedInfo.value ?: cell?.value
                            textValue?.let { value ->
                                // Web版と同様にセルサイズベースでフォントサイズを計算
                                val isNumeric = value.toString().matches(Regex("^\\d+$"))
                                val baseSize = minOf(mergedWidth, mergedHeight)
                                val fontSize = if (isNumeric) {
                                    // 数値セル: セルサイズの40%、最大24px
                                    minOf(baseSize * 0.4f, 24f * density.density)
                                } else {
                                    // テキストセル: セルサイズの35%、最大20px
                                    minOf(baseSize * 0.35f, 20f * density.density)
                                }.coerceAtLeast(8f * density.density)  // 最小8px

                                val fontColor = cell?.fontInfo?.color ?: 0xFF000000L
                                val textColorInt = (fontColor and 0xFFFFFF).toInt()

                                drawContext.canvas.nativeCanvas.apply {
                                    val paint = android.graphics.Paint().apply {
                                        color = (0xFF000000 or textColorInt.toLong()).toInt()
                                        textSize = fontSize
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias = true
                                        if (cell?.fontInfo?.bold == true) {
                                            isFakeBoldText = true
                                        }
                                    }

                                    val textX = x + mergedWidth / 2
                                    val textY = y + mergedHeight / 2 + fontSize / 3

                                    drawText(value.toString(), textX, textY, paint)
                                }
                            }

                            // 結合セルの選択マーカー描画
                            if (selectedCellsSet.contains(mergeKey)) {
                                // 半透明の青いオーバーレイ
                                drawRect(
                                    color = colorSelected.copy(alpha = 0.3f),
                                    topLeft = Offset(x, y),
                                    size = Size(mergedWidth, mergedHeight)
                                )
                                // 枠線
                                drawRect(
                                    color = colorSelected,
                                    topLeft = Offset(x, y),
                                    size = Size(mergedWidth, mergedHeight),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                )

                                // マーカー（番号付きの円）を描画
                                val markerIndex = selectedCells.indexOfFirst {
                                    it.first == mergedInfo.startRow && it.second == mergedInfo.startCol
                                }
                                if (markerIndex >= 0) {
                                    val markerRadius = minOf(mergedWidth, mergedHeight) * 0.25f
                                    val markerCenterX = x + mergedWidth / 2
                                    val markerCenterY = y + mergedHeight / 2

                                    // 円の背景
                                    drawCircle(
                                        color = Color(0xFF1976D2),
                                        radius = markerRadius,
                                        center = Offset(markerCenterX, markerCenterY)
                                    )
                                    // 円の枠線
                                    drawCircle(
                                        color = Color.White,
                                        radius = markerRadius,
                                        center = Offset(markerCenterX, markerCenterY),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                    )

                                    // 番号を描画
                                    drawContext.canvas.nativeCanvas.apply {
                                        val markerPaint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = markerRadius * 1.2f
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                                            isAntiAlias = true
                                        }
                                        val markerTextY = markerCenterY + markerRadius * 0.35f
                                        drawText("${markerIndex + 1}", markerCenterX, markerTextY, markerPaint)
                                    }
                                }
                            }
                        }
                    } else {
                        // 通常セル
                        val cell = cellMap[cellKey]
                        val cellWidth = getColumnWidth(col)
                        val cellHeight = getRowHeight(row)
                        val items = cellItemsMap[cellKey] ?: emptyList()

                        // 背景色を決定（訪問先リスト状態に基づく）
                        val bgColor = if (items.isNotEmpty()) {
                            val allInVisitList = items.all { visitListItemIds.contains(it.id) }
                            val anyInVisitList = items.any { visitListItemIds.contains(it.id) }
                            when {
                                allInVisitList -> colorRed     // 全アイテム訪問先登録 → 赤
                                anyInVisitList -> colorYellow  // 一部アイテム訪問先登録 → 黄
                                else -> Color(0xFFBBDEFB)      // 全アイテム未登録 → 青
                            }
                        } else if (cell?.backgroundColor != null) {
                            Color(cell.backgroundColor)
                        } else {
                            colorWhite
                        }

                        // 背景を描画
                        drawRect(
                            color = bgColor,
                            topLeft = Offset(x, y),
                            size = Size(cellWidth, cellHeight)
                        )

                        // 罫線を描画
                        cell?.let {
                            drawCellBorders(x, y, cellWidth, cellHeight, it.borders, scale)
                        }

                        // テキストを描画
                        cell?.value?.let { value ->
                            // Web版と同様にセルサイズベースでフォントサイズを計算
                            val isNumeric = value.toString().matches(Regex("^\\d+$"))
                            val cellSize = minOf(cellWidth, cellHeight)
                            val fontSize = if (isNumeric) {
                                // 数値セル: セルサイズの45%、最大14px
                                minOf(cellSize * 0.45f, 14f * density.density)
                            } else {
                                // テキストセル: セルサイズの40%、最大12px
                                minOf(cellSize * 0.4f, 12f * density.density)
                            }.coerceAtLeast(8f * density.density)  // 最小8px

                            val fontColor = cell.fontInfo.color
                            val textColorInt = (fontColor and 0xFFFFFF).toInt()

                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = (0xFF000000 or textColorInt.toLong()).toInt()
                                    textSize = fontSize
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }

                                val textX = x + cellWidth / 2
                                val textY = y + cellHeight / 2 + fontSize / 3

                                drawText(value.toString(), textX, textY, paint)
                            }
                        }

                        // 選択されたセルのハイライトとマーカー描画
                        if (selectedCellsSet.contains(cellKey)) {
                            // 半透明の青いオーバーレイ
                            drawRect(
                                color = colorSelected.copy(alpha = 0.3f),
                                topLeft = Offset(x, y),
                                size = Size(cellWidth, cellHeight)
                            )
                            // 枠線
                            drawRect(
                                color = colorSelected,
                                topLeft = Offset(x, y),
                                size = Size(cellWidth, cellHeight),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )

                            // マーカー（番号付きの円）を描画
                            val markerIndex = selectedCells.indexOfFirst { it.first == row && it.second == col }
                            if (markerIndex >= 0) {
                                val markerRadius = minOf(cellWidth, cellHeight) * 0.35f
                                val markerCenterX = x + cellWidth / 2
                                val markerCenterY = y + cellHeight / 2

                                // 円の背景
                                drawCircle(
                                    color = Color(0xFF1976D2),
                                    radius = markerRadius,
                                    center = Offset(markerCenterX, markerCenterY)
                                )
                                // 円の枠線
                                drawCircle(
                                    color = Color.White,
                                    radius = markerRadius,
                                    center = Offset(markerCenterX, markerCenterY),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                )

                                // 番号を描画
                                drawContext.canvas.nativeCanvas.apply {
                                    val markerPaint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textSize = markerRadius * 1.2f
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        isAntiAlias = true
                                    }
                                    val textY = markerCenterY + markerRadius * 0.35f
                                    drawText("${markerIndex + 1}", markerCenterX, textY, markerPaint)
                                }
                            }
                        }
                    }
                }
            }

            // 4角選択完了時の範囲オーバーレイ描画
            selectionBounds?.let { bounds ->
                val areaStartX = getColumnX(bounds.minCol)
                val areaStartY = getRowY(bounds.minRow)
                var areaEndX = areaStartX
                var areaEndY = areaStartY

                // 範囲の幅と高さを計算
                for (c in bounds.minCol..bounds.maxCol) {
                    areaEndX += (mapData.columnWidths[c] ?: mapData.defaultColumnWidth) * drawScale
                }
                for (r in bounds.minRow..bounds.maxRow) {
                    areaEndY += (mapData.rowHeights[r] ?: mapData.defaultRowHeight) * drawScale
                }

                val areaWidth = areaEndX - areaStartX
                val areaHeight = areaEndY - areaStartY

                // 範囲内を薄青色で塗りつぶし
                drawRect(
                    color = colorSelectionArea,
                    topLeft = Offset(areaStartX, areaStartY),
                    size = Size(areaWidth, areaHeight)
                )

                // 範囲の枠線
                drawRect(
                    color = colorSelected,
                    topLeft = Offset(areaStartX, areaStartY),
                    size = Size(areaWidth, areaHeight),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )
            }

            // ホール範囲を描画
            halls.forEach { hall ->
                if (hall.vertices.size >= 3) {
                    val hallColor = Color(hall.color).copy(alpha = 0.3f)
                    val path = androidx.compose.ui.graphics.Path()

                    hall.vertices.forEachIndexed { index, vertex ->
                        val x = getColumnX(vertex.col) + getColumnWidth(vertex.col) / 2
                        val y = getRowY(vertex.row) + getRowHeight(vertex.row) / 2

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    path.close()

                    // 塗りつぶし
                    drawPath(path, hallColor)

                    // 枠線
                    drawPath(
                        path,
                        Color(hall.color),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
            }

            // マーカーモード中の凸包プレビュー
            if (isHallVertexSelectionMode && hallMarkers.size >= 3) {
                // マーカーから頂点リストを生成
                val markerVertices = hallMarkers.map { Vertex(it.row, it.col) }
                val sortedVertices = com.example.eventshoppingplanner.util.HallUtils.computeConvexHull(markerVertices)

                if (sortedVertices.size >= 3) {
                    val previewPath = androidx.compose.ui.graphics.Path()
                    sortedVertices.forEachIndexed { index, vertex ->
                        val x = getColumnX(vertex.col) + getColumnWidth(vertex.col) / 2
                        val y = getRowY(vertex.row) + getRowHeight(vertex.row) / 2

                        if (index == 0) {
                            previewPath.moveTo(x, y)
                        } else {
                            previewPath.lineTo(x, y)
                        }
                    }
                    previewPath.close()

                    // プレビュー塗りつぶし
                    drawPath(previewPath, Color(0xFF2196F3).copy(alpha = 0.2f))

                    // プレビュー枠線（破線風に点線で）
                    drawPath(
                        previewPath,
                        Color(0xFF2196F3),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(10f, 10f), 0f
                            )
                        )
                    )
                }
            }

            // ルート描画（訪問ポイント間の経路）
            if (isRouteVisible && routeSegments.isNotEmpty()) {
                // 基準セルサイズ（デフォルトセルサイズ × スケール）
                val baseCellSize = minOf(
                    mapData.defaultColumnWidth,
                    mapData.defaultRowHeight
                ) * drawScale

                // 優先度ごとの色を定義
                fun getPriorityColor(priority: PriorityLevel): Color {
                    return when (priority) {
                        PriorityLevel.HIGHEST -> Color(0xFFEF4444)  // 赤
                        PriorityLevel.PRIORITY -> Color(0xFFF97316)  // オレンジ
                        PriorityLevel.NONE -> Color(0xFF1976D2)  // 青
                    }
                }

                // エッジごとの通過情報を収集（重複検出用）
                val edgeUsage = mutableMapOf<String, MutableSet<PriorityLevel>>()

                fun getEdgeKey(r1: Int, c1: Int, r2: Int, c2: Int): String {
                    // 常に小さい座標を先にして正規化
                    return if (r1 < r2 || (r1 == r2 && c1 < c2)) {
                        "$r1,$c1-$r2,$c2"
                    } else {
                        "$r2,$c2-$r1,$c1"
                    }
                }

                // 全セグメントのエッジを収集
                routeSegments.forEach { segment ->
                    if (segment.path.size < 2) return@forEach
                    // グループ間接続はグレーなので重複カウントに含めない
                    if (segment.isGroupTransition) return@forEach

                    val priority = segment.fromPriority

                    for (i in 0 until segment.path.size - 1) {
                        val p1 = segment.path[i]
                        val p2 = segment.path[i + 1]
                        val key = getEdgeKey(p1.first, p1.second, p2.first, p2.second)

                        if (!edgeUsage.containsKey(key)) {
                            edgeUsage[key] = mutableSetOf()
                        }
                        edgeUsage[key]!!.add(priority)
                    }
                }

                // 線幅
                val lineWidth = maxOf(2f, baseCellSize * 0.08f)
                // 平行線のオフセット量
                val parallelOffset = maxOf(3f, baseCellSize * 0.12f)

                // 線をオフセットする関数
                fun getOffsetPoints(
                    px1: Float, py1: Float, px2: Float, py2: Float, offset: Float
                ): List<Float> {
                    val dx = px2 - px1
                    val dy = py2 - py1
                    val len = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (len == 0f) return listOf(px1, py1, px2, py2)

                    // 法線ベクトル（90度回転）
                    val nx = -dy / len
                    val ny = dx / len

                    return listOf(
                        px1 + nx * offset,
                        py1 + ny * offset,
                        px2 + nx * offset,
                        py2 + ny * offset
                    )
                }

                // セグメントを描画
                routeSegments.forEach { segment ->
                    if (segment.path.size < 2) return@forEach

                    val isTransition = segment.isGroupTransition
                    val segmentPriority = segment.fromPriority

                    // グループ間接続はグレー
                    val baseColor = if (isTransition) Color(0xFF9CA3AF) else getPriorityColor(segmentPriority)

                    // パスをエッジごとに描画
                    for (i in 0 until segment.path.size - 1) {
                        val p1 = segment.path[i]
                        val p2 = segment.path[i + 1]
                        val edgeKey = getEdgeKey(p1.first, p1.second, p2.first, p2.second)

                        // セルの中心座標を計算
                        val px1 = getColumnX(p1.second) + getColumnWidth(p1.second) / 2
                        val py1 = getRowY(p1.first) + getRowHeight(p1.first) / 2
                        val px2 = getColumnX(p2.second) + getColumnWidth(p2.second) / 2
                        val py2 = getRowY(p2.first) + getRowHeight(p2.first) / 2

                        // グループ間接続は重複チェック不要（中央に描画）
                        if (isTransition) {
                            drawLine(
                                color = baseColor,
                                start = Offset(px1, py1),
                                end = Offset(px2, py2),
                                strokeWidth = lineWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            continue
                        }

                        // 重複しているエッジかどうかを確認
                        val usedPriorities = edgeUsage[edgeKey]
                        val isOverlapping = usedPriorities != null && usedPriorities.size > 1

                        if (isOverlapping) {
                            // 重複エッジ：平行線で描画
                            val priorities = usedPriorities!!.toList().sortedBy {
                                when (it) {
                                    PriorityLevel.HIGHEST -> 0
                                    PriorityLevel.PRIORITY -> 1
                                    PriorityLevel.NONE -> 2
                                }
                            }

                            val myIndex = priorities.indexOf(segmentPriority)
                            val totalCount = priorities.size

                            // オフセットを計算（中央を基準に左右に分散）
                            val offset = (myIndex - (totalCount - 1) / 2f) * parallelOffset
                            val offsetPoints = getOffsetPoints(px1, py1, px2, py2, offset)

                            drawLine(
                                color = baseColor,
                                start = Offset(offsetPoints[0], offsetPoints[1]),
                                end = Offset(offsetPoints[2], offsetPoints[3]),
                                strokeWidth = lineWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        } else {
                            // 重複なし：中央に描画
                            drawLine(
                                color = baseColor,
                                start = Offset(px1, py1),
                                end = Offset(px2, py2),
                                strokeWidth = lineWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }

                // 訪問順番号バッジを描画（詳細表示時: 60%以上のスケール）
                val isDetailedView = scale >= 0.6f
                if (isDetailedView) {
                    visitPoints.forEach { point ->
                        val px = getColumnX(point.col) + getColumnWidth(point.col) / 2
                        val py = getRowY(point.row) + getRowHeight(point.row) / 2

                        val circleSize = maxOf(12f, baseCellSize * 0.5f)
                        val pointColor = getPriorityColor(point.priorityLevel)

                        // 背景の円
                        drawCircle(
                            color = pointColor,
                            radius = circleSize / 2,
                            center = Offset(px, py)
                        )

                        // 番号を描画（nativeCanvasを使用）
                        drawContext.canvas.nativeCanvas.apply {
                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = maxOf(8f, circleSize * 0.6f)
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            val text = (point.order + 1).toString()
                            val textY = py + textPaint.textSize / 3
                            drawText(text, px, textY, textPaint)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 選択範囲の境界
 */
private data class SelectionBounds(
    val minRow: Int,
    val minCol: Int,
    val maxRow: Int,
    val maxCol: Int
)

/**
 * ホールの境界（ピクセル座標）
 */
private data class HallBoundsPixels(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
)

/**
 * 罫線を描画
 */
private fun DrawScope.drawCellBorders(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    borders: CellBorders,
    scale: Float
) {
    // 上罫線
    if (borders.top.style != BorderWeight.NONE) {
        val strokeWidth = getBorderStrokeWidth(borders.top.style, scale)
        drawLine(
            color = Color(borders.top.color),
            start = Offset(x, y),
            end = Offset(x + width, y),
            strokeWidth = strokeWidth
        )
    }

    // 右罫線
    if (borders.right.style != BorderWeight.NONE) {
        val strokeWidth = getBorderStrokeWidth(borders.right.style, scale)
        drawLine(
            color = Color(borders.right.color),
            start = Offset(x + width, y),
            end = Offset(x + width, y + height),
            strokeWidth = strokeWidth
        )
    }

    // 下罫線
    if (borders.bottom.style != BorderWeight.NONE) {
        val strokeWidth = getBorderStrokeWidth(borders.bottom.style, scale)
        drawLine(
            color = Color(borders.bottom.color),
            start = Offset(x, y + height),
            end = Offset(x + width, y + height),
            strokeWidth = strokeWidth
        )
    }

    // 左罫線
    if (borders.left.style != BorderWeight.NONE) {
        val strokeWidth = getBorderStrokeWidth(borders.left.style, scale)
        drawLine(
            color = Color(borders.left.color),
            start = Offset(x, y),
            end = Offset(x, y + height),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * 罫線の太さを取得
 */
private fun getBorderStrokeWidth(weight: BorderWeight, scale: Float): Float {
    return when (weight) {
        BorderWeight.THIN -> 1f * scale
        BorderWeight.MEDIUM -> 2f * scale
        BorderWeight.THICK -> 3f * scale
        BorderWeight.DOUBLE -> 3f * scale
        BorderWeight.NONE -> 0f
    }.coerceAtLeast(0.5f)
}

/**
 * ホールマーカー選択オーバーレイ（Google Maps風配置方式）
 */
@Composable
private fun HallMarkerSelectionOverlay(
    markers: List<HallMarker>,
    isPlacingMarker: Boolean,
    mapData: DayMapData,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onStartPlacing: () -> Unit,
    onConfirmPlacement: (Int, Int) -> Unit,
    onCancelPlacing: () -> Unit,
    onRemoveMarker: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current

    // セル位置を計算する関数
    fun getCellPosition(row: Int, col: Int): Offset {
        var x = offsetX
        for (c in 1 until col) {
            x += (mapData.columnWidths[c] ?: mapData.defaultColumnWidth) * scale
        }
        var y = offsetY
        for (r in 1 until row) {
            y += (mapData.rowHeights[r] ?: mapData.defaultRowHeight) * scale
        }
        val cellWidth = (mapData.columnWidths[col] ?: mapData.defaultColumnWidth) * scale
        val cellHeight = (mapData.rowHeights[row] ?: mapData.defaultRowHeight) * scale
        return Offset(x + cellWidth / 2, y + cellHeight / 2)
    }

    // 画面座標からセル座標を計算
    fun findCellAt(screenX: Float, screenY: Float): Pair<Int, Int>? {
        var currentX = offsetX
        var foundCol = -1
        for (col in 1..mapData.maxCol) {
            val colWidth = (mapData.columnWidths[col] ?: mapData.defaultColumnWidth) * scale
            if (screenX >= currentX && screenX < currentX + colWidth) {
                foundCol = col
                break
            }
            currentX += colWidth
        }

        var currentY = offsetY
        var foundRow = -1
        for (row in 1..mapData.maxRow) {
            val rowHeight = (mapData.rowHeights[row] ?: mapData.defaultRowHeight) * scale
            if (screenY >= currentY && screenY < currentY + rowHeight) {
                foundRow = row
                break
            }
            currentY += rowHeight
        }

        return if (foundRow > 0 && foundCol > 0) Pair(foundRow, foundCol) else null
    }

    // セルのサイズを取得
    fun getCellSize(row: Int, col: Int): Pair<Float, Float> {
        val cellWidth = (mapData.columnWidths[col] ?: mapData.defaultColumnWidth) * scale
        val cellHeight = (mapData.rowHeights[row] ?: mapData.defaultRowHeight) * scale
        return Pair(cellWidth, cellHeight)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val screenCenterX = screenWidth / 2
        val screenCenterY = screenHeight / 2

        // 画面中央のセルを計算
        val centerCell = findCellAt(screenCenterX, screenCenterY)

        // 配置中の場合、画面中央のセルをハイライト表示
        if (isPlacingMarker && centerCell != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val (row, col) = centerCell
                val cellPos = getCellPosition(row, col)
                val (cellWidth, cellHeight) = getCellSize(row, col)

                // セルのハイライト
                drawRect(
                    color = Color(0xFF4CAF50).copy(alpha = 0.3f),
                    topLeft = Offset(cellPos.x - cellWidth / 2, cellPos.y - cellHeight / 2),
                    size = Size(cellWidth, cellHeight)
                )
                drawRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(cellPos.x - cellWidth / 2, cellPos.y - cellHeight / 2),
                    size = Size(cellWidth, cellHeight),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
            }
        }

        // 設置済みマーカーの表示
        markers.forEach { marker ->
            val cellPos = getCellPosition(marker.row, marker.col)

            // マーカーの表示（セルの中央に根元が来る）
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (cellPos.x - with(density) { 20.dp.toPx() }).toInt(),
                            (cellPos.y - with(density) { 48.dp.toPx() }).toInt()
                        )
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 削除ボタン
                    IconButton(
                        onClick = { onRemoveMarker(marker.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "削除",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // マーカーアイコン
                    Text(
                        text = "🚩",
                        fontSize = 28.sp,
                        modifier = Modifier.offset(y = (-8).dp)
                    )
                }
            }
        }

        // 配置中の🚩（画面中央に固定、根元が中央に来る）
        if (isPlacingMarker) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-24).dp)  // 🚩の根元が中央に来るように調整
            ) {
                Text(
                    text = "🚩",
                    fontSize = 48.sp
                )
            }

            // 中央の照準マーク
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 十字線
                val crossSize = 20f
                val strokeWidth = 2f
                drawLine(
                    color = Color(0xFF4CAF50),
                    start = Offset(screenCenterX - crossSize, screenCenterY),
                    end = Offset(screenCenterX + crossSize, screenCenterY),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = Color(0xFF4CAF50),
                    start = Offset(screenCenterX, screenCenterY - crossSize),
                    end = Offset(screenCenterX, screenCenterY + crossSize),
                    strokeWidth = strokeWidth
                )
            }
        }

        // 下部コントロールパネル
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📍 ホールの頂点を設定 (${markers.size}/4〜6)",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isPlacingMarker) {
                        "マップを動かして🚩を配置 → 「設置」で確定"
                    } else {
                        "「+」で新しい頂点を追加"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 配置中のセル座標を表示
                if (isPlacingMarker && centerCell != null) {
                    Text(
                        text = "位置: 行${centerCell.first}, 列${centerCell.second}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPlacingMarker) {
                        // 配置中: 設置ボタンとキャンセルボタン
                        Button(
                            onClick = {
                                centerCell?.let { (row, col) ->
                                    onConfirmPlacement(row, col)
                                }
                            },
                            enabled = centerCell != null
                        ) {
                            Text("設置")
                        }

                        OutlinedButton(onClick = onCancelPlacing) {
                            Text("戻る")
                        }
                    } else {
                        // 通常: +ボタン、確定、キャンセル
                        OutlinedButton(
                            onClick = onStartPlacing,
                            enabled = markers.size < 6
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("追加")
                        }

                        Button(
                            onClick = onConfirm,
                            enabled = markers.size >= 4
                        ) {
                            Text("確定 (${markers.size})")
                        }

                        OutlinedButton(onClick = onCancel) {
                            Text("キャンセル")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoMapPlaceholder(
    onOpenFile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.GridOn,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "マップが読み込まれていません",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Excelファイル(.xlsx)を選択してください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onOpenFile) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("マップファイルを開く")
        }
    }
}

/**
 * セル選択モード中のオーバーレイ
 */
@Composable
private fun CellSelectionOverlay(
    selectedCount: Int,
    requiredCount: Int = 4,
    title: String = "4つの角をタップ",
    showConfirmButton: Boolean = false,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.align(Alignment.TopCenter),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (requiredCount > 0) "$title ($selectedCount/$requiredCount)" else "$title ($selectedCount)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (showConfirmButton) {
                        Button(
                            onClick = onConfirm,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("確定", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = onCancel) {
                        Text("キャンセル")
                    }
                }
                if (selectedCount > 0) {
                    Text(
                        text = "※マーカーをタップで選択解除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * セルタップ時の情報を保持するデータクラス
 */
private data class CellTapInfo(
    val row: Int,
    val col: Int,
    val blockName: String,
    val number: Int,
    val items: List<ShoppingItem>,
    val isInBlockDefinition: Boolean = false
)

/**
 * セル内のアイテム一覧を表示するダイアログ
 */
@Composable
private fun CellItemsDialog(
    blockName: String,
    number: Int,
    items: List<ShoppingItem>,
    visitListItemIds: List<String>,
    onDismiss: () -> Unit,
    onToggleVisitList: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddNewItem: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // ヘッダー
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$blockName - $number",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (items.isNotEmpty()) {
                            val inListCount = items.count { visitListItemIds.contains(it.id) }
                            Text(
                                text = "${items.size}件のアイテム（訪問先: ${inListCount}件）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "閉じる",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 新規アイテム追加ボタン
                onAddNewItem?.let { addNewItem ->
                    Button(
                        onClick = addNewItem,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新規アイテム追加")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // アイテム一覧
                if (items.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items) { item ->
                            CellItemRow(
                                item = item,
                                isInVisitList = visitListItemIds.contains(item.id),
                                onToggleVisitList = { onToggleVisitList(item.id) },
                                onOpenUrl = {
                                    item.url?.let { onOpenUrl(it) }
                                }
                            )
                        }
                    }
                } else if (onAddNewItem == null) {
                    // アイテムなし＆新規追加なしの場合のみメッセージ表示
                    Text(
                        text = "このセルにはアイテムがありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * ダイアログ内のアイテム行（訪問先リスト対応）
 */
@Composable
private fun CellItemRow(
    item: ShoppingItem,
    isInVisitList: Boolean,
    onToggleVisitList: () -> Unit,
    onOpenUrl: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleVisitList() },
        colors = CardDefaults.cardColors(
            containerColor = if (isInVisitList) {
                Color(0xFFE3F2FD)  // 訪問先リストに追加済み：薄い青
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 訪問先リストアイコン
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = if (isInVisitList) "訪問先から削除" else "訪問先に追加",
                tint = if (isInVisitList) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // アイテム情報
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // サークル名
                Text(
                    text = item.circle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.title.isNotBlank()) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 価格
                Text(
                    text = item.priceDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // URLボタン
            if (!item.url.isNullOrBlank()) {
                IconButton(
                    onClick = onOpenUrl,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "URLを開く",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ===== ヘルパー関数 =====

/**
 * セルがブロックの範囲内にあるかチェック（cellGroups対応）
 */
private fun isCellInBlock(row: Int, col: Int, block: BlockDefinition): Boolean {
    // cellGroupsがある場合（複数範囲ブロックや壁ブロック）
    if (block.cellGroups.isNotEmpty()) {
        return block.cellGroups.any { group ->
            when (group.type) {
                CellGroupType.RANGE -> {
                    row >= (group.startRow ?: 0) && row <= (group.endRow ?: 0) &&
                            col >= (group.startCol ?: 0) && col <= (group.endCol ?: 0)
                }
                CellGroupType.INDIVIDUAL -> {
                    group.cells.any { it.first == row && it.second == col }
                }
            }
        }
    }
    // 通常の矩形ブロック
    return row >= block.startRow && row <= block.endRow &&
            col >= block.startCol && col <= block.endCol
}

/**
 * タップされたセルのブロック情報を特定
 * numberCellsに登録されている数値セルのみを対象とする
 * 注: タップ処理で既に結合セルは開始座標に変換済み
 * @return Pair(blockName, number) or null
 */
private fun findBlockInfoForCell(
    row: Int,
    col: Int,
    blocks: List<BlockDefinition>,
    cells: List<CellData>,
    mergedCells: List<MergedCellInfo>
): Pair<String, Int>? {
    // 各ブロックのnumberCellsから該当セルを探す
    for (block in blocks) {
        val numberCell = block.numberCells.find {
            it.row == row && it.col == col
        }
        if (numberCell != null) {
            return Pair(block.name, numberCell.value)
        }
    }
    return null
}

/**
 * アイテムナンバーから数値部分を抽出
 * "5a" → 5, "12" → 12, "abc" → null
 */
private fun extractNumberFromItemNumber(itemNumber: String): Int? {
    val match = Regex("^(\\d+)").find(itemNumber)
    return match?.groupValues?.get(1)?.toIntOrNull()
}

/**
 * マップからの新規アイテム追加ダイアログ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemFromMapDialog(
    preset: NewItemPreset,
    eventId: String,
    onDismiss: () -> Unit,
    onSave: (ShoppingItem) -> Unit
) {
    // 入力状態（プリセット値で初期化）
    var circle by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(preset.eventDate) }
    var block by remember { mutableStateOf(preset.block) }
    var number by remember { mutableStateOf(preset.number) }
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }
    var remarks by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    // ドロップダウン展開状態
    var priceExpanded by remember { mutableStateOf(false) }
    var quantityExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("新規アイテム追加")
        },
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

                // 参加日（プリセット値を表示、編集可能）
                OutlinedTextField(
                    value = eventDate,
                    onValueChange = { eventDate = it },
                    label = { Text("参加日 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ブロック・ナンバー（プリセット値を表示、編集可能）
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
                            listOf(0, 100, 200, 300, 500, 1000, 1500, 2000, 3000, 5000).forEach { p ->
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
                        id = java.util.UUID.randomUUID().toString(),
                        eventId = eventId,
                        circle = circle,
                        eventDate = eventDate,
                        block = block,
                        number = number,
                        title = title,
                        price = price.toIntOrNull(),
                        purchaseStatus = PurchaseStatus.NONE,
                        quantity = quantity,
                        remarks = remarks,
                        url = url.ifBlank { null },
                        sortOrder = 0,
                        isInExecuteList = false
                    )
                    onSave(newItem)
                },
                enabled = circle.isNotBlank() && eventDate.isNotBlank() && block.isNotBlank() && number.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}