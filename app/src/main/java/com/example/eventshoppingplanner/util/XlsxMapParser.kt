package com.example.eventshoppingplanner.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import com.example.eventshoppingplanner.domain.model.*
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Excelマップファイル解析ユーティリティ（完全版）
 * .xlsxファイルをZIPとして解凍し、XMLを直接解析
 * スタイル情報（罫線、背景色、フォント）、列幅、行高さ、結合セルを完全にサポート
 */
object XlsxMapParser {

    private const val TAG = "XlsxMapParser"

    // Excel幅単位からピクセルへの変換係数（おおよその値）
    private const val WIDTH_UNIT_TO_PIXELS = 8f
    // Excel高さ単位からピクセルへの変換係数
    private const val HEIGHT_UNIT_TO_PIXELS = 1.33f

    // ブロック用の色リスト
    private val blockColors = listOf(
        0xFFE3F2FD, 0xFFE8F5E9, 0xFFFFF3E0, 0xFFF3E5F5, 0xFFE0F7FA,
        0xFFFBE9E7, 0xFFF1F8E9, 0xFFFCE4EC, 0xFFE8EAF6, 0xFFFFFDE7,
        0xFFEFEBE9, 0xFFECEFF1
    )

    // スタイル情報を保持するクラス
    data class StylesData(
        val fonts: List<FontInfo>,
        val fills: List<Long?>,       // 背景色のリスト（nullは透明）
        val borders: List<CellBorders>,
        val cellXfs: List<CellXf>     // セルスタイルの参照情報
    )

    data class CellXf(
        val fontId: Int,
        val fillId: Int,
        val borderId: Int,
        val horizontalAlignment: String = "center",
        val verticalAlignment: String = "center"
    )

    /**
     * Excelファイルからマップデータを解析
     */
    fun parseMapFile(
        context: Context,
        uri: Uri,
        eventId: String
    ): Map<String, DayMapData>? {
        return try {
            Log.d(TAG, "Opening file: $uri")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseXlsx(inputStream, eventId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing map file", e)
            null
        }
    }

    /**
     * .xlsxファイルを解析
     */
    private fun parseXlsx(
        inputStream: InputStream,
        eventId: String
    ): Map<String, DayMapData>? {
        val result = mutableMapOf<String, DayMapData>()
        val sharedStrings = mutableListOf<String>()
        val sheetNames = mutableMapOf<String, String>()

        // 一時保存用
        val sheets = mutableMapOf<String, ByteArray>()
        var workbookXml: ByteArray? = null
        var sharedStringsXml: ByteArray? = null
        var stylesXml: ByteArray? = null

        try {
            // ZIPとして解凍
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry

                while (entry != null) {
                    val name = entry.name
                    Log.d(TAG, "ZIP entry: $name")

                    when {
                        name == "xl/sharedStrings.xml" -> {
                            sharedStringsXml = zip.readBytes()
                        }
                        name == "xl/workbook.xml" -> {
                            workbookXml = zip.readBytes()
                        }
                        name == "xl/styles.xml" -> {
                            stylesXml = zip.readBytes()
                        }
                        name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                            sheets[name] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // 共有文字列を解析
            sharedStringsXml?.let {
                parseSharedStrings(it.inputStream(), sharedStrings)
            }
            Log.d(TAG, "Shared strings: ${sharedStrings.size}")

            // ワークブックからシート名を取得
            workbookXml?.let {
                parseWorkbook(it.inputStream(), sheetNames)
            }
            Log.d(TAG, "Sheet names: $sheetNames")

            // スタイル情報を解析
            val stylesData = stylesXml?.let {
                parseStyles(it.inputStream())
            } ?: StylesData(emptyList(), emptyList(), emptyList(), emptyList())
            Log.d(TAG, "Styles: fonts=${stylesData.fonts.size}, fills=${stylesData.fills.size}, borders=${stylesData.borders.size}, cellXfs=${stylesData.cellXfs.size}")

            // 各シートを解析
            sheets.forEach { (path, data) ->
                val sheetNum = Regex("sheet(\\d+)\\.xml").find(path)?.groupValues?.getOrNull(1)
                val sheetNamesList = sheetNames.values.toList()
                val sheetName = sheetNames["rId$sheetNum"]
                    ?: sheetNamesList.getOrNull(sheetNum?.toIntOrNull()?.minus(1) ?: 0)

                if (sheetName != null) {
                    Log.d(TAG, "Parsing sheet: $sheetName")
                    val mapData = parseSheet(data.inputStream(), sharedStrings, stylesData, eventId, sheetName)
                    if (mapData != null) {
                        // "○日目" パターンのシートのみ追加
                        val dayPattern = Regex("^(\\d+日目)$")
                        val match = dayPattern.find(sheetName)
                        if (match != null) {
                            val mapName = "${match.groupValues[1]}マップ"
                            result[mapName] = mapData
                            Log.d(TAG, "Added map: $mapName")
                        }
                    }
                }
            }

            Log.d(TAG, "Total maps: ${result.size}")
            return if (result.isNotEmpty()) result else null

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing xlsx", e)
            return null
        }
    }

    /**
     * 共有文字列を解析
     */
    private fun parseSharedStrings(inputStream: InputStream, strings: MutableList<String>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var currentText = StringBuilder()
            var inT = false

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "t") {
                            inT = true
                            currentText = StringBuilder()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") {
                            inT = false
                        } else if (parser.name == "si") {
                            strings.add(currentText.toString())
                            currentText = StringBuilder()
                        }
                    }
                }
                parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing shared strings", e)
        }
    }

    /**
     * ワークブックからシート名を取得
     */
    private fun parseWorkbook(inputStream: InputStream, sheetNames: MutableMap<String, String>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name")
                    val rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                        ?: parser.getAttributeValue(null, "r:id")
                        ?: "rId${sheetNames.size + 1}"

                    if (name != null) {
                        sheetNames[rId] = name
                    }
                }
                parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing workbook", e)
        }
    }

    /**
     * スタイル情報を解析
     */
    private fun parseStyles(inputStream: InputStream): StylesData {
        val fonts = mutableListOf<FontInfo>()
        val fills = mutableListOf<Long?>()
        val borders = mutableListOf<CellBorders>()
        val cellXfs = mutableListOf<CellXf>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var inFonts = false
            var inFont = false
            var inFills = false
            var inFill = false
            var inBorders = false
            var inBorder = false
            var inCellXfs = false

            var currentFontSize = 11f
            var currentFontColor = 0xFF000000L
            var currentFontBold = false
            var currentFontName = "Arial"

            var currentFillColor: Long? = null
            var currentPatternType = ""

            var currentBorderTop = CellBorderStyle()
            var currentBorderRight = CellBorderStyle()
            var currentBorderBottom = CellBorderStyle()
            var currentBorderLeft = CellBorderStyle()
            var currentBorderSide = ""

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "fonts" -> inFonts = true
                            "font" -> {
                                if (inFonts) {
                                    inFont = true
                                    currentFontSize = 11f
                                    currentFontColor = 0xFF000000L
                                    currentFontBold = false
                                    currentFontName = "Arial"
                                }
                            }
                            "sz" -> {
                                if (inFont) {
                                    currentFontSize = parser.getAttributeValue(null, "val")?.toFloatOrNull() ?: 11f
                                }
                            }
                            "color" -> {
                                if (inFont) {
                                    val rgb = parser.getAttributeValue(null, "rgb")
                                    if (rgb != null && rgb.length >= 6) {
                                        currentFontColor = parseColorString(rgb)
                                    }
                                } else if (currentBorderSide.isNotEmpty()) {
                                    val rgb = parser.getAttributeValue(null, "rgb")
                                    if (rgb != null) {
                                        val color = parseColorString(rgb)
                                        when (currentBorderSide) {
                                            "left" -> currentBorderLeft = currentBorderLeft.copy(color = color)
                                            "right" -> currentBorderRight = currentBorderRight.copy(color = color)
                                            "top" -> currentBorderTop = currentBorderTop.copy(color = color)
                                            "bottom" -> currentBorderBottom = currentBorderBottom.copy(color = color)
                                        }
                                    }
                                }
                            }
                            "b" -> {
                                if (inFont) currentFontBold = true
                            }
                            "name" -> {
                                if (inFont) {
                                    currentFontName = parser.getAttributeValue(null, "val") ?: "Arial"
                                }
                            }
                            "fills" -> inFills = true
                            "fill" -> {
                                if (inFills) {
                                    inFill = true
                                    currentFillColor = null
                                    currentPatternType = ""
                                }
                            }
                            "patternFill" -> {
                                if (inFill) {
                                    currentPatternType = parser.getAttributeValue(null, "patternType") ?: ""
                                }
                            }
                            "fgColor" -> {
                                if (inFill && (currentPatternType == "solid" || currentPatternType.isEmpty())) {
                                    val rgb = parser.getAttributeValue(null, "rgb")
                                    val theme = parser.getAttributeValue(null, "theme")
                                    if (rgb != null && rgb.length >= 6) {
                                        val color = parseColorString(rgb)
                                        // 白色（FFFFFF）は背景色として扱わない
                                        if (color != 0xFFFFFFFFL) {
                                            currentFillColor = color
                                        }
                                    } else if (theme != null) {
                                        // テーマカラーの処理（簡易版）
                                        currentFillColor = when (theme) {
                                            "0" -> null  // 白
                                            "1" -> 0xFF000000L  // 黒
                                            else -> null
                                        }
                                    }
                                }
                            }
                            "borders" -> inBorders = true
                            "border" -> {
                                if (inBorders) {
                                    inBorder = true
                                    currentBorderTop = CellBorderStyle()
                                    currentBorderRight = CellBorderStyle()
                                    currentBorderBottom = CellBorderStyle()
                                    currentBorderLeft = CellBorderStyle()
                                }
                            }
                            "left", "right", "top", "bottom" -> {
                                if (inBorder) {
                                    currentBorderSide = parser.name
                                    val style = parser.getAttributeValue(null, "style")
                                    val weight = parseBorderWeight(style)
                                    when (parser.name) {
                                        "left" -> currentBorderLeft = CellBorderStyle(weight, 0xFF000000L)
                                        "right" -> currentBorderRight = CellBorderStyle(weight, 0xFF000000L)
                                        "top" -> currentBorderTop = CellBorderStyle(weight, 0xFF000000L)
                                        "bottom" -> currentBorderBottom = CellBorderStyle(weight, 0xFF000000L)
                                    }
                                }
                            }
                            "cellXfs" -> inCellXfs = true
                            "xf" -> {
                                if (inCellXfs) {
                                    val fontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
                                    val fillId = parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                                    val borderId = parser.getAttributeValue(null, "borderId")?.toIntOrNull() ?: 0
                                    cellXfs.add(CellXf(fontId, fillId, borderId))
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "fonts" -> inFonts = false
                            "font" -> {
                                if (inFont) {
                                    fonts.add(FontInfo(currentFontSize, currentFontColor, currentFontBold, currentFontName))
                                    inFont = false
                                }
                            }
                            "fills" -> inFills = false
                            "fill" -> {
                                if (inFill) {
                                    fills.add(currentFillColor)
                                    inFill = false
                                }
                            }
                            "borders" -> inBorders = false
                            "border" -> {
                                if (inBorder) {
                                    borders.add(CellBorders(currentBorderTop, currentBorderRight, currentBorderBottom, currentBorderLeft))
                                    inBorder = false
                                }
                            }
                            "left", "right", "top", "bottom" -> {
                                currentBorderSide = ""
                            }
                            "cellXfs" -> inCellXfs = false
                        }
                    }
                }
                parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing styles", e)
        }

        return StylesData(fonts, fills, borders, cellXfs)
    }

    /**
     * 色文字列を解析
     */
    private fun parseColorString(colorStr: String): Long {
        return try {
            val hex = if (colorStr.length == 8) colorStr else "FF$colorStr"
            java.lang.Long.parseLong(hex, 16)
        } catch (e: Exception) {
            0xFF000000L
        }
    }

    /**
     * 罫線の太さを解析
     */
    private fun parseBorderWeight(style: String?): BorderWeight {
        return when (style) {
            "thin" -> BorderWeight.THIN
            "medium" -> BorderWeight.MEDIUM
            "thick" -> BorderWeight.THICK
            "double" -> BorderWeight.DOUBLE
            else -> BorderWeight.NONE
        }
    }

    /**
     * シートを解析
     */
    private fun parseSheet(
        inputStream: InputStream,
        sharedStrings: List<String>,
        stylesData: StylesData,
        eventId: String,
        sheetName: String
    ): DayMapData? {
        val cells = mutableListOf<CellData>()
        val mergedCells = mutableListOf<MergedCellInfo>()
        val columnWidths = mutableMapOf<Int, Float>()
        val rowHeights = mutableMapOf<Int, Float>()
        var defaultColWidth = 21f
        var defaultRowHeight = 15f
        var maxRow = 0
        var maxCol = 0

        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var inSheetData = false
            var inRow = false
            var currentRowNum = 0
            var currentRowHeight: Float? = null
            var currentCellRef = ""
            var currentCellStyle = 0
            var currentCellType = ""
            var currentCellValue = ""
            var inValue = false
            var inCols = false
            var inMergeCells = false

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "sheetFormatPr" -> {
                                parser.getAttributeValue(null, "defaultColWidth")?.toFloatOrNull()?.let {
                                    defaultColWidth = it * WIDTH_UNIT_TO_PIXELS
                                }
                                parser.getAttributeValue(null, "defaultRowHeight")?.toFloatOrNull()?.let {
                                    defaultRowHeight = it * HEIGHT_UNIT_TO_PIXELS
                                }
                            }
                            "cols" -> inCols = true
                            "col" -> {
                                if (inCols) {
                                    val min = parser.getAttributeValue(null, "min")?.toIntOrNull() ?: 1
                                    val max = parser.getAttributeValue(null, "max")?.toIntOrNull() ?: min
                                    val width = parser.getAttributeValue(null, "width")?.toFloatOrNull()
                                    if (width != null) {
                                        val pixelWidth = width * WIDTH_UNIT_TO_PIXELS
                                        for (col in min..max) {
                                            columnWidths[col] = pixelWidth
                                        }
                                    }
                                }
                            }
                            "sheetData" -> inSheetData = true
                            "row" -> {
                                if (inSheetData) {
                                    inRow = true
                                    currentRowNum = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: 0
                                    currentRowHeight = parser.getAttributeValue(null, "ht")?.toFloatOrNull()?.let {
                                        it * HEIGHT_UNIT_TO_PIXELS
                                    }
                                    if (currentRowHeight != null) {
                                        rowHeights[currentRowNum] = currentRowHeight!!
                                    }
                                    maxRow = maxOf(maxRow, currentRowNum)
                                }
                            }
                            "c" -> {
                                if (inRow) {
                                    currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                                    currentCellStyle = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                                    currentCellType = parser.getAttributeValue(null, "t") ?: ""
                                    currentCellValue = ""

                                    val colIndex = cellRefToColIndex(currentCellRef) + 1
                                    maxCol = maxOf(maxCol, colIndex)
                                }
                            }
                            "v", "t" -> {
                                inValue = true
                            }
                            "mergeCells" -> inMergeCells = true
                            "mergeCell" -> {
                                if (inMergeCells) {
                                    val ref = parser.getAttributeValue(null, "ref")
                                    if (ref != null) {
                                        val parts = ref.split(":")
                                        if (parts.size == 2) {
                                            val start = parseCellRef(parts[0])
                                            val end = parseCellRef(parts[1])
                                            mergedCells.add(MergedCellInfo(
                                                startRow = start.first,
                                                startCol = start.second,
                                                endRow = end.first,
                                                endCol = end.second
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inValue) {
                            currentCellValue += parser.text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "cols" -> inCols = false
                            "sheetData" -> inSheetData = false
                            "row" -> inRow = false
                            "v", "t" -> inValue = false
                            "c" -> {
                                if (currentCellRef.isNotEmpty()) {
                                    val (rowNum, colNum) = parseCellRef(currentCellRef)

                                    // 値を取得
                                    val value = if (currentCellType == "s" && currentCellValue.isNotEmpty()) {
                                        val index = currentCellValue.toIntOrNull()
                                        if (index != null && index < sharedStrings.size) {
                                            sharedStrings[index]
                                        } else {
                                            currentCellValue.takeIf { it.isNotEmpty() }
                                        }
                                    } else {
                                        currentCellValue.takeIf { it.isNotEmpty() }
                                    }

                                    // スタイル情報を取得
                                    val cellXf = stylesData.cellXfs.getOrNull(currentCellStyle)
                                    val fontInfo = cellXf?.let { stylesData.fonts.getOrNull(it.fontId) } ?: FontInfo()
                                    val backgroundColor = cellXf?.let { stylesData.fills.getOrNull(it.fillId) }
                                    val borders = cellXf?.let { stylesData.borders.getOrNull(it.borderId) } ?: CellBorders()

                                    // 値があるか、スタイルがある場合にセルを追加
                                    if (value != null || backgroundColor != null ||
                                        borders.top.style != BorderWeight.NONE ||
                                        borders.right.style != BorderWeight.NONE ||
                                        borders.bottom.style != BorderWeight.NONE ||
                                        borders.left.style != BorderWeight.NONE) {

                                        cells.add(CellData(
                                            row = rowNum,
                                            col = colNum,
                                            value = value,
                                            backgroundColor = backgroundColor,
                                            borders = borders,
                                            styleIndex = currentCellStyle,
                                            fontInfo = fontInfo
                                        ))
                                    }
                                }
                            }
                            "mergeCells" -> inMergeCells = false
                        }
                    }
                }
                parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sheet", e)
        }

        if (cells.isEmpty() && mergedCells.isEmpty()) return null

        Log.d(TAG, "Sheet $sheetName: ${cells.size} cells, ${mergedCells.size} merged, $maxRow rows, $maxCol cols")

        // 結合セルの値を設定
        val cellMap = cells.associateBy { "${it.row}-${it.col}" }
        val updatedMergedCells = mergedCells.map { merged ->
            val cell = cellMap["${merged.startRow}-${merged.startCol}"]
            merged.copy(value = cell?.value)
        }

        // ブロックを検出
        val blocks = detectBlocksFromCells(cells, updatedMergedCells, maxRow, maxCol)

        return DayMapData(
            eventId = eventId,
            dayName = sheetName,
            sheetName = sheetName,
            maxRow = maxRow,
            maxCol = maxCol,
            cells = cells,
            mergedCells = updatedMergedCells,
            blocks = blocks,
            columnWidths = columnWidths,
            rowHeights = rowHeights,
            defaultColumnWidth = defaultColWidth,
            defaultRowHeight = defaultRowHeight
        )
    }

    /**
     * セル参照から列インデックスを取得（0始まり）
     */
    private fun cellRefToColIndex(ref: String): Int {
        val colPart = ref.takeWhile { it.isLetter() }
        var index = 0
        for (char in colPart.uppercase()) {
            index = index * 26 + (char - 'A' + 1)
        }
        return index - 1
    }

    /**
     * セル参照を(行, 列)に変換（1始まり）
     */
    private fun parseCellRef(ref: String): Pair<Int, Int> {
        val colPart = ref.takeWhile { it.isLetter() }
        val rowPart = ref.dropWhile { it.isLetter() }

        var colIndex = 0
        for (char in colPart.uppercase()) {
            colIndex = colIndex * 26 + (char - 'A' + 1)
        }

        val rowIndex = rowPart.toIntOrNull() ?: 1

        return Pair(rowIndex, colIndex)
    }

    /**
     * ブロック名かどうかを判定
     */
    private fun isBlockName(value: String): Boolean {
        val str = value.trim()
        if (str.isEmpty() || str.length > 4) return false
        if (str.all { it.isDigit() || it in '０'..'９' }) return false
        val allowedPattern = Regex("^[ア-ンァ-ヴーあ-んぁ-ゔーA-Za-z\\u4E00-\\u9FFF\\u3400-\\u4DBF0-9０-９]+$")
        return allowedPattern.matches(str)
    }

    /**
     * セルデータからブロックを検出
     */
    private fun detectBlocksFromCells(
        cells: List<CellData>,
        mergedCells: List<MergedCellInfo>,
        maxRow: Int,
        maxCol: Int
    ): List<BlockDefinition> {
        val blocks = mutableListOf<BlockDefinition>()
        var colorIndex = 0
        val blockNameCells = mutableListOf<Triple<Int, Int, String>>()

        // セルからブロック名を検出
        cells.forEach { cell ->
            val value = cell.value
            if (value != null && isBlockName(value)) {
                blockNameCells.add(Triple(cell.row, cell.col, value))
            }
        }

        // 結合セルからもブロック名を検出
        mergedCells.forEach { merged ->
            val value = merged.value
            if (value != null && isBlockName(value)) {
                val exists = blockNameCells.any { it.first == merged.startRow && it.second == merged.startCol }
                if (!exists) {
                    blockNameCells.add(Triple(merged.startRow, merged.startCol, value))
                }
            }
        }

        Log.d(TAG, "Block name cells found: ${blockNameCells.size}")

        // セルをマップに変換
        val cellMap = cells.associateBy { "${it.row}-${it.col}" }

        blockNameCells.forEach { (blockRow, blockCol, name) ->
            val numberCells = mutableListOf<NumberCellInfo>()
            val searchRadius = 15

            // 周辺の数値セルを検索
            for (r in maxOf(1, blockRow - searchRadius)..minOf(maxRow, blockRow + searchRadius)) {
                for (c in maxOf(1, blockCol - searchRadius)..minOf(maxCol, blockCol + searchRadius)) {
                    val cell = cellMap["$r-$c"]
                    val num = cell?.value?.toIntOrNull()
                    if (num != null && num in 1..100) {
                        numberCells.add(NumberCellInfo(r, c, num))
                    }
                }
            }

            if (numberCells.isNotEmpty()) {
                val allRows = numberCells.map { it.row } + blockRow
                val allCols = numberCells.map { it.col } + blockCol

                blocks.add(
                    BlockDefinition(
                        name = name,
                        startRow = allRows.minOrNull() ?: blockRow,
                        startCol = allCols.minOrNull() ?: blockCol,
                        endRow = allRows.maxOrNull() ?: blockRow,
                        endCol = allCols.maxOrNull() ?: blockCol,
                        numberCells = numberCells.sortedBy { it.value },
                        color = blockColors[colorIndex % blockColors.size],
                        isAutoDetected = true
                    )
                )
                colorIndex++
            }
        }

        return blocks
    }

    /**
     * アイテムをマップのセルにマッチング
     */
    fun matchItemToCell(
        item: ShoppingItem,
        mapData: DayMapData,
        dayName: String
    ): Pair<Int, Int>? {
        val itemDayName = item.eventDate
        if (!dayName.contains(itemDayName.replace("日目", "")) &&
            !itemDayName.contains(dayName.replace("マップ", "")) &&
            itemDayName != dayName.replace("マップ", "")) {
            return null
        }

        val itemBlockName = item.block.trim()

        var block = mapData.blocks.find { it.name == itemBlockName }

        if (block == null) {
            val candidates = mapData.blocks.filter {
                it.name.lowercase() == itemBlockName.lowercase()
            }
            if (candidates.size == 1) {
                block = candidates[0]
            }
        }

        if (block == null) return null

        val numStr = extractNumberFromItemNumber(item.number) ?: return null
        val numValue = numStr.toIntOrNull() ?: return null

        val numberCell = block.numberCells.find { it.value == numValue } ?: return null

        return Pair(numberCell.row, numberCell.col)
    }

    private fun extractNumberFromItemNumber(itemNumber: String): String? {
        val match = Regex("^(\\d+)").find(itemNumber)
        return match?.groupValues?.getOrNull(1)
    }
}