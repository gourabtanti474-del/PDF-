package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.PdfBoxProcessor
import com.example.engine.PdfRendererAndOcr
import com.example.i18n.AppLanguage
import com.example.model.CompressionLevel
import com.example.model.DetectedTextBlock
import com.example.model.EditorSnapshot
import com.example.model.EditorTool
import com.example.model.ImageExportFormat
import com.example.model.PageSizePreset
import com.example.model.PdfFontOption
import com.example.model.PdfOverlayElement
import com.example.model.PdfPageState
import com.example.model.ShapeKind
import com.example.model.TextAlignOption
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

private val Application.dataStore by preferencesDataStore(name = "gs_pdf_prefs")
private val KEY_LANGUAGE = stringPreferencesKey("app_language")

data class PickedPdfItem(
  val uri: Uri,
  val name: String,
  val sizeBytes: Long,
  val pageCount: Int = 0,
)

data class ToolOperationResult(
  val title: String,
  val summary: String,
  val outputFiles: List<File>,
  val isPdf: Boolean,
  val originalSizeBytes: Long? = null,
  val resultSizeBytes: Long? = null,
)

data class EditorUiState(
  val language: AppLanguage = AppLanguage.ENGLISH,
  val sourcePdfFile: File? = null,
  val documentName: String = "",
  val documentSizeBytes: Long = 0L,
  val pages: List<PdfPageState> = emptyList(),
  val currentPageIndex: Int = 0,
  val pageBitmaps: Map<String, Bitmap> = emptyMap(),
  val detectedTextBlocks: Map<String, List<DetectedTextBlock>> = emptyMap(),
  val hiddenDetectedBlockIds: Set<String> = emptySet(),
  val elements: List<PdfOverlayElement> = emptyList(),
  val selectedElementId: String? = null,
  val activeTool: EditorTool = EditorTool.SELECT_EDIT,
  val activeFont: PdfFontOption = PdfFontOption.BENGALI_UNICODE,
  val activeFontSizePt: Float = 14f,
  val activeColorArgb: Long = 0xFF0F172A,
  val activeAlign: TextAlignOption = TextAlignOption.LEFT,
  val activeStrokeWidthPt: Float = 2.5f,
  val activeShapeFilled: Boolean = false,
  val activeStampLabel: String = "APPROVED",
  val zoomScale: Float = 1.0f,
  val canUndo: Boolean = false,
  val canRedo: Boolean = false,
  val isBusy: Boolean = false,
  val busyMessage: String = "",
  val statusBanner: String? = null,
  val savedSignaturePath: String? = null,
  val toolResult: ToolOperationResult? = null,
)

class PdfEditorViewModel(application: Application) : AndroidViewModel(application) {
  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

  private val undoStack = ArrayDeque<EditorSnapshot>()
  private val redoStack = ArrayDeque<EditorSnapshot>()

  init {
    viewModelScope.launch {
      val prefs = getApplication<Application>().dataStore.data.first()
      val langCode = prefs[KEY_LANGUAGE]
      if (langCode == AppLanguage.BENGALI.code) {
        _uiState.update { it.copy(language = AppLanguage.BENGALI) }
      }
    }
  }

  fun toggleLanguage() {
    val next =
      if (_uiState.value.language == AppLanguage.ENGLISH) AppLanguage.BENGALI
      else AppLanguage.ENGLISH
    _uiState.update { it.copy(language = next) }
    viewModelScope.launch {
      getApplication<Application>().dataStore.edit { it[KEY_LANGUAGE] = next.code }
    }
  }

  fun clearBanner() {
    _uiState.update { it.copy(statusBanner = null) }
  }

  fun showBanner(message: String) {
    _uiState.update { it.copy(statusBanner = message) }
  }

  fun clearToolResult() {
    _uiState.update { it.copy(toolResult = null) }
  }

  fun openPdfFromUri(uri: Uri, onOpened: () -> Unit = {}) {
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Loading PDF...") }
      runCatching {
        val name = PdfRendererAndOcr.getDisplayName(ctx, uri)
        val size = PdfRendererAndOcr.getFileSize(ctx, uri)
        val workingFile =
          PdfRendererAndOcr.copyUriToCacheFile(ctx, uri, "working_${System.currentTimeMillis()}.pdf")
        openLocalPdfFile(workingFile, name, size)
      }.onSuccess {
        _uiState.update { it.copy(isBusy = false, busyMessage = "") }
        onOpened()
      }.onFailure { err ->
        _uiState.update {
          it.copy(
            isBusy = false,
            busyMessage = "",
            statusBanner = "Failed to open PDF: ${err.localizedMessage ?: "Unsupported file"}",
          )
        }
      }
    }
  }

  suspend fun openLocalPdfFile(file: File, name: String, sizeBytes: Long = file.length()) {
    undoStack.clear()
    redoStack.clear()
    val pages = PdfBoxProcessor.inspectPdfPages(file)
    val detected = PdfRendererAndOcr.extractTextBlocks(file, pages)
    _uiState.update {
      it.copy(
        sourcePdfFile = file,
        documentName = name,
        documentSizeBytes = sizeBytes,
        pages = pages,
        currentPageIndex = 0,
        pageBitmaps = emptyMap(),
        detectedTextBlocks = detected,
        hiddenDetectedBlockIds = emptySet(),
        elements = emptyList(),
        selectedElementId = null,
        zoomScale = 1.0f,
        canUndo = false,
        canRedo = false,
      )
    }
    pages.forEach { renderAndCachePage(it) }
  }

  private fun renderAndCachePage(pageState: PdfPageState) {
    val srcFile = _uiState.value.sourcePdfFile ?: return
    viewModelScope.launch {
      val bmp =
        PdfRendererAndOcr.renderPageBitmap(
          file = srcFile,
          sourcePageIndex = pageState.sourcePageIndex,
          extraRotationDegrees = pageState.extraRotation,
          maxDimensionPx = 1400,
        )
      if (bmp != null) {
        val key = bitmapCacheKey(pageState)
        _uiState.update { state -> state.copy(pageBitmaps = state.pageBitmaps + (key to bmp)) }
      }
    }
  }

  fun bitmapCacheKey(page: PdfPageState): String = "${page.pageId}_${page.extraRotation}"

  fun selectPage(index: Int) {
    val maxIdx = (_uiState.value.pages.size - 1).coerceAtLeast(0)
    _uiState.update { it.copy(currentPageIndex = index.coerceIn(0, maxIdx), selectedElementId = null) }
  }

  fun setActiveTool(tool: EditorTool) {
    _uiState.update { it.copy(activeTool = tool, selectedElementId = null) }
  }

  fun selectElement(id: String?) {
    _uiState.update { state ->
      val el = state.elements.find { it.id == id }
      when (el) {
        is PdfOverlayElement.TextElement ->
          state.copy(
            selectedElementId = id,
            activeFont = el.font,
            activeFontSizePt = el.fontSizePt,
            activeColorArgb = el.colorArgb,
            activeAlign = el.align,
          )
        is PdfOverlayElement.ShapeElement ->
          state.copy(
            selectedElementId = id,
            activeColorArgb = el.colorArgb,
            activeStrokeWidthPt = el.strokeWidthPt,
            activeShapeFilled = el.filled,
          )
        is PdfOverlayElement.FreehandElement ->
          state.copy(
            selectedElementId = id,
            activeColorArgb = el.colorArgb,
            activeStrokeWidthPt = el.strokeWidthPt,
          )
        else -> state.copy(selectedElementId = id)
      }
    }
  }

  fun updateFont(font: PdfFontOption) {
    _uiState.update { it.copy(activeFont = font) }
    updateSelectedTextElement { it.copy(font = font) }
  }

  fun updateFontSize(sizePt: Float) {
    val clamped = sizePt.coerceIn(8f, 72f)
    _uiState.update { it.copy(activeFontSizePt = clamped) }
    updateSelectedTextElement { it.copy(fontSizePt = clamped) }
  }

  fun updateColor(colorArgb: Long) {
    _uiState.update { it.copy(activeColorArgb = colorArgb) }
    val selId = _uiState.value.selectedElementId ?: return
    pushUndoSnapshot()
    _uiState.update { state ->
      state.copy(
        elements =
          state.elements.map { el ->
            if (el.id != selId) el
            else
              when (el) {
                is PdfOverlayElement.TextElement -> el.copy(colorArgb = colorArgb)
                is PdfOverlayElement.ShapeElement -> el.copy(colorArgb = colorArgb)
                is PdfOverlayElement.FreehandElement -> el.copy(colorArgb = colorArgb)
                is PdfOverlayElement.StampElement -> el.copy(colorArgb = colorArgb)
                else -> el
              }
          }
      )
    }
  }

  fun updateAlign(align: TextAlignOption) {
    _uiState.update { it.copy(activeAlign = align) }
    updateSelectedTextElement { it.copy(align = align) }
  }

  fun updateStrokeWidth(widthPt: Float) {
    val clamped = widthPt.coerceIn(1f, 14f)
    _uiState.update { it.copy(activeStrokeWidthPt = clamped) }
    val selId = _uiState.value.selectedElementId ?: return
    pushUndoSnapshot()
    _uiState.update { state ->
      state.copy(
        elements =
          state.elements.map { el ->
            if (el.id != selId) el
            else
              when (el) {
                is PdfOverlayElement.ShapeElement -> el.copy(strokeWidthPt = clamped)
                is PdfOverlayElement.FreehandElement -> el.copy(strokeWidthPt = clamped)
                else -> el
              }
          }
      )
    }
  }

  fun toggleShapeFilled() {
    val next = !_uiState.value.activeShapeFilled
    _uiState.update { it.copy(activeShapeFilled = next) }
    val selId = _uiState.value.selectedElementId ?: return
    pushUndoSnapshot()
    _uiState.update { state ->
      state.copy(
        elements =
          state.elements.map { el ->
            if (el.id == selId && el is PdfOverlayElement.ShapeElement) el.copy(filled = next) else el
          }
      )
    }
  }

  fun updateStampLabel(label: String) {
    _uiState.update { it.copy(activeStampLabel = label) }
  }

  private fun updateSelectedTextElement(
    transform: (PdfOverlayElement.TextElement) -> PdfOverlayElement.TextElement
  ) {
    val selId = _uiState.value.selectedElementId ?: return
    pushUndoSnapshot()
    _uiState.update { state ->
      state.copy(
        elements =
          state.elements.map { el ->
            if (el.id == selId && el is PdfOverlayElement.TextElement) transform(el) else el
          }
      )
    }
  }

  fun addTextElement(pageId: String, xNorm: Float, yNorm: Float, text: String) {
    if (text.isBlank()) return
    pushUndoSnapshot()
    val state = _uiState.value
    val el =
      PdfOverlayElement.TextElement(
        pageId = pageId,
        xNorm = xNorm.coerceIn(0.02f, 0.78f),
        yNorm = yNorm.coerceIn(0.02f, 0.90f),
        widthNorm = 0.36f,
        heightNorm = 0.08f,
        text = text,
        font = state.activeFont,
        fontSizePt = state.activeFontSizePt,
        colorArgb = state.activeColorArgb,
        align = state.activeAlign,
      )
    _uiState.update {
      it.copy(
        elements = it.elements + el,
        selectedElementId = el.id,
        activeTool = EditorTool.SELECT_EDIT,
      )
    }
  }

  fun editExistingTextElement(elementId: String, newText: String) {
    if (newText.isBlank()) {
      deleteElement(elementId)
      return
    }
    pushUndoSnapshot()
    _uiState.update { state ->
      state.copy(
        elements =
          state.elements.map { el ->
            if (el.id == elementId && el is PdfOverlayElement.TextElement) el.copy(text = newText)
            else el
          }
      )
    }
  }

  fun replaceDetectedTextBlock(block: DetectedTextBlock, replacementText: String?) {
    pushUndoSnapshot()
    val padX = 0.004f
    val padY = 0.004f
    val whiteout =
      PdfOverlayElement.WhiteoutElement(
        pageId = block.pageId,
        xNorm = (block.xNorm - padX).coerceAtLeast(0f),
        yNorm = (block.yNorm - padY).coerceAtLeast(0f),
        widthNorm = (block.widthNorm + padX * 2f).coerceAtMost(1f),
        heightNorm = (block.heightNorm + padY * 2f).coerceAtMost(1f),
        colorArgb = 0xFFFFFFFF,
      )
    val newElements = mutableListOf<PdfOverlayElement>(whiteout)
    var selectId: String? = whiteout.id
    if (!replacementText.isNullOrBlank()) {
      val textEl =
        PdfOverlayElement.TextElement(
          pageId = block.pageId,
          xNorm = block.xNorm,
          yNorm = block.yNorm,
          widthNorm = maxOf(block.widthNorm * 1.15f, 0.28f).coerceAtMost(1f - block.xNorm),
          heightNorm = maxOf(block.heightNorm * 1.4f, 0.06f),
          text = replacementText,
          font = _uiState.value.activeFont,
          fontSizePt = block.approxFontSizePt.coerceIn(8f, 48f),
          colorArgb = _uiState.value.activeColorArgb,
          align = _uiState.value.activeAlign,
        )
      newElements.add(textEl)
      selectId = textEl.id
    }
    _uiState.update { state ->
      state.copy(
        elements = state.elements + newElements,
        hiddenDetectedBlockIds = state.hiddenDetectedBlockIds + block.id,
        selectedElementId = selectId,
      )
    }
  }

  fun addWhiteout(pageId: String, xNorm: Float, yNorm: Float) {
    pushUndoSnapshot()
    val el =
      PdfOverlayElement.WhiteoutElement(
        pageId = pageId,
        xNorm = xNorm.coerceIn(0.01f, 0.72f),
        yNorm = yNorm.coerceIn(0.01f, 0.90f),
        widthNorm = 0.25f,
        heightNorm = 0.045f,
        colorArgb = 0xFFFFFFFF,
      )
    _uiState.update { it.copy(elements = it.elements + el, selectedElementId = el.id) }
  }

  fun addShape(pageId: String, kind: ShapeKind, xNorm: Float, yNorm: Float) {
    pushUndoSnapshot()
    val state = _uiState.value
    val defaultColor =
      if (kind == ShapeKind.HIGHLIGHT) 0xFFFACC15 else state.activeColorArgb
    val defaultH = if (kind == ShapeKind.HIGHLIGHT) 0.035f else 0.12f
    val el =
      PdfOverlayElement.ShapeElement(
        pageId = pageId,
        xNorm = xNorm.coerceIn(0.02f, 0.72f),
        yNorm = yNorm.coerceIn(0.02f, 0.85f),
        widthNorm = 0.24f,
        heightNorm = defaultH,
        kind = kind,
        colorArgb = defaultColor,
        strokeWidthPt = state.activeStrokeWidthPt,
        filled = state.activeShapeFilled,
      )
    _uiState.update {
      it.copy(
        elements = it.elements + el,
        selectedElementId = el.id,
        activeTool = EditorTool.SELECT_EDIT,
      )
    }
  }

  fun addFreehandPath(pageId: String, pointsNorm: List<Pair<Float, Float>>) {
    if (pointsNorm.size < 2) return
    pushUndoSnapshot()
    val minX = pointsNorm.minOf { it.first }
    val maxX = pointsNorm.maxOf { it.first }
    val minY = pointsNorm.minOf { it.second }
    val maxY = pointsNorm.maxOf { it.second }
    val el =
      PdfOverlayElement.FreehandElement(
        pageId = pageId,
        xNorm = minX,
        yNorm = minY,
        widthNorm = (maxX - minX).coerceAtLeast(0.02f),
        heightNorm = (maxY - minY).coerceAtLeast(0.02f),
        pointsNorm = pointsNorm,
        colorArgb = _uiState.value.activeColorArgb,
        strokeWidthPt = _uiState.value.activeStrokeWidthPt,
      )
    _uiState.update { it.copy(elements = it.elements + el, selectedElementId = el.id) }
  }

  fun addStamp(pageId: String, xNorm: Float, yNorm: Float) {
    pushUndoSnapshot()
    val el =
      PdfOverlayElement.StampElement(
        pageId = pageId,
        xNorm = xNorm.coerceIn(0.02f, 0.68f),
        yNorm = yNorm.coerceIn(0.02f, 0.90f),
        widthNorm = 0.28f,
        heightNorm = 0.065f,
        label = _uiState.value.activeStampLabel,
        colorArgb = _uiState.value.activeColorArgb,
      )
    _uiState.update {
      it.copy(
        elements = it.elements + el,
        selectedElementId = el.id,
        activeTool = EditorTool.SELECT_EDIT,
      )
    }
  }

  fun saveSignatureAndPlace(bitmap: Bitmap, pageId: String) {
    val ctx = getApplication<Application>()
    viewModelScope.launch(Dispatchers.IO) {
      val file = File(ctx.cacheDir, "sig_${System.currentTimeMillis()}.png")
      FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
      val aspect = (bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)).coerceIn(0.2f, 0.8f)
      val wNorm = 0.28f
      val hNorm = wNorm * aspect
      pushUndoSnapshot()
      val el =
        PdfOverlayElement.ImageElement(
          pageId = pageId,
          xNorm = 0.36f,
          yNorm = 0.72f,
          widthNorm = wNorm,
          heightNorm = hNorm,
          localImagePath = file.absolutePath,
          isSignature = true,
        )
      _uiState.update {
        it.copy(
          savedSignaturePath = file.absolutePath,
          elements = it.elements + el,
          selectedElementId = el.id,
          activeTool = EditorTool.SELECT_EDIT,
        )
      }
    }
  }

  fun addImageFromUri(uri: Uri, pageId: String, replaceElementId: String? = null) {
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      runCatching {
        val bmp = PdfRendererAndOcr.decodeBitmapWithExif(ctx, uri, 1600)
        val file = File(ctx.cacheDir, "img_${UUID.randomUUID()}.png")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val aspect = (bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)).coerceIn(0.2f, 1.5f)
        bmp.recycle()
        file to aspect
      }.onSuccess { (file, aspect) ->
        pushUndoSnapshot()
        if (replaceElementId != null) {
          _uiState.update { state ->
            state.copy(
              elements =
                state.elements.map { el ->
                  if (el.id == replaceElementId && el is PdfOverlayElement.ImageElement) {
                    el.copy(localImagePath = file.absolutePath)
                  } else el
                }
            )
          }
        } else {
          val wNorm = 0.32f
          val el =
            PdfOverlayElement.ImageElement(
              pageId = pageId,
              xNorm = 0.34f,
              yNorm = 0.34f,
              widthNorm = wNorm,
              heightNorm = (wNorm * aspect).coerceAtMost(0.5f),
              localImagePath = file.absolutePath,
              isSignature = false,
            )
          _uiState.update {
            it.copy(
              elements = it.elements + el,
              selectedElementId = el.id,
              activeTool = EditorTool.SELECT_EDIT,
            )
          }
        }
      }.onFailure { err ->
        _uiState.update { it.copy(statusBanner = "Image load failed: ${err.localizedMessage}") }
      }
    }
  }

  fun beginElementTransform() {
    pushUndoSnapshot()
  }

  fun moveAndResizeElement(
    elementId: String,
    dxNorm: Float,
    dyNorm: Float,
    dwNorm: Float = 0f,
    dhNorm: Float = 0f,
  ) {
    _uiState.update { state ->
      state.copy(
        elements =
          state.elements.map { el ->
            if (el.id != elementId) el
            else {
              val newW = (el.widthNorm + dwNorm).coerceIn(0.04f, 0.96f)
              val newH = (el.heightNorm + dhNorm).coerceIn(0.02f, 0.96f)
              val newX = (el.xNorm + dxNorm).coerceIn(0f, 1f - newW)
              val newY = (el.yNorm + dyNorm).coerceIn(0f, 1f - newH)
              when (el) {
                is PdfOverlayElement.TextElement -> el.copy(xNorm = newX, yNorm = newY, widthNorm = newW, heightNorm = newH)
                is PdfOverlayElement.WhiteoutElement -> el.copy(xNorm = newX, yNorm = newY, widthNorm = newW, heightNorm = newH)
                is PdfOverlayElement.ImageElement -> el.copy(xNorm = newX, yNorm = newY, widthNorm = newW, heightNorm = newH)
                is PdfOverlayElement.ShapeElement -> el.copy(xNorm = newX, yNorm = newY, widthNorm = newW, heightNorm = newH)
                is PdfOverlayElement.StampElement -> el.copy(xNorm = newX, yNorm = newY, widthNorm = newW, heightNorm = newH)
                is PdfOverlayElement.FreehandElement -> {
                  val shifted = el.pointsNorm.map { (px, py) ->
                    (px + (newX - el.xNorm)).coerceIn(0f, 1f) to (py + (newY - el.yNorm)).coerceIn(0f, 1f)
                  }
                  el.copy(xNorm = newX, yNorm = newY, pointsNorm = shifted)
                }
              }
            }
          }
      )
    }
  }

  fun deleteElement(elementId: String) {
    pushUndoSnapshot()
    _uiState.update { state ->
      state.copy(
        elements = state.elements.filterNot { it.id == elementId },
        selectedElementId = if (state.selectedElementId == elementId) null else state.selectedElementId,
      )
    }
  }

  fun duplicateElement(elementId: String) {
    val el = _uiState.value.elements.find { it.id == elementId } ?: return
    pushUndoSnapshot()
    val shift = 0.03f
    val copyEl: PdfOverlayElement =
      when (el) {
        is PdfOverlayElement.TextElement -> el.copy(id = UUID.randomUUID().toString(), xNorm = (el.xNorm + shift).coerceAtMost(0.9f), yNorm = (el.yNorm + shift).coerceAtMost(0.9f))
        is PdfOverlayElement.WhiteoutElement -> el.copy(id = UUID.randomUUID().toString(), xNorm = (el.xNorm + shift).coerceAtMost(0.9f), yNorm = (el.yNorm + shift).coerceAtMost(0.9f))
        is PdfOverlayElement.ImageElement -> el.copy(id = UUID.randomUUID().toString(), xNorm = (el.xNorm + shift).coerceAtMost(0.9f), yNorm = (el.yNorm + shift).coerceAtMost(0.9f))
        is PdfOverlayElement.ShapeElement -> el.copy(id = UUID.randomUUID().toString(), xNorm = (el.xNorm + shift).coerceAtMost(0.9f), yNorm = (el.yNorm + shift).coerceAtMost(0.9f))
        is PdfOverlayElement.StampElement -> el.copy(id = UUID.randomUUID().toString(), xNorm = (el.xNorm + shift).coerceAtMost(0.9f), yNorm = (el.yNorm + shift).coerceAtMost(0.9f))
        is PdfOverlayElement.FreehandElement -> el.copy(id = UUID.randomUUID().toString(), xNorm = (el.xNorm + shift).coerceAtMost(0.9f), yNorm = (el.yNorm + shift).coerceAtMost(0.9f), pointsNorm = el.pointsNorm.map { (px, py) -> (px + shift).coerceAtMost(1f) to (py + shift).coerceAtMost(1f) })
      }
    _uiState.update { it.copy(elements = it.elements + copyEl, selectedElementId = copyEl.id) }
  }

  fun rotatePage(pageIndex: Int, deltaDegrees: Int) {
    val page = _uiState.value.pages.getOrNull(pageIndex) ?: return
    pushUndoSnapshot()
    val updatedPage = page.copy(extraRotation = ((page.extraRotation + deltaDegrees) % 360 + 360) % 360)
    _uiState.update { state ->
      state.copy(
        pages = state.pages.toMutableList().also { it[pageIndex] = updatedPage }
      )
    }
    renderAndCachePage(updatedPage)
  }

  fun deletePage(pageIndex: Int) {
    val currentPages = _uiState.value.pages
    if (currentPages.size <= 1 || pageIndex !in currentPages.indices) return
    val removed = currentPages[pageIndex]
    pushUndoSnapshot()
    val updated = currentPages.toMutableList().apply { removeAt(pageIndex) }
    _uiState.update { state ->
      state.copy(
        pages = updated,
        currentPageIndex = state.currentPageIndex.coerceIn(0, updated.lastIndex),
        elements = state.elements.filterNot { it.pageId == removed.pageId },
        selectedElementId = null,
      )
    }
  }

  fun movePage(fromIndex: Int, toIndex: Int) {
    val list = _uiState.value.pages
    if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
    pushUndoSnapshot()
    val mutable = list.toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    _uiState.update { it.copy(pages = mutable, currentPageIndex = toIndex) }
  }

  fun zoomIn() {
    _uiState.update { it.copy(zoomScale = (it.zoomScale + 0.25f).coerceAtMost(3.0f)) }
  }

  fun zoomOut() {
    _uiState.update { it.copy(zoomScale = (it.zoomScale - 0.25f).coerceAtLeast(0.5f)) }
  }

  fun resetZoom() {
    _uiState.update { it.copy(zoomScale = 1.0f) }
  }

  fun setZoom(scale: Float) {
    _uiState.update { it.copy(zoomScale = scale.coerceIn(0.5f, 3.0f)) }
  }

  private fun pushUndoSnapshot() {
    val st = _uiState.value
    undoStack.addLast(EditorSnapshot(st.pages, st.elements, st.hiddenDetectedBlockIds))
    if (undoStack.size > 40) undoStack.removeFirst()
    redoStack.clear()
    _uiState.update { it.copy(canUndo = true, canRedo = false) }
  }

  fun undo() {
    if (undoStack.isEmpty()) return
    val current = _uiState.value
    redoStack.addLast(EditorSnapshot(current.pages, current.elements, current.hiddenDetectedBlockIds))
    val prev = undoStack.removeLast()
    _uiState.update {
      it.copy(
        pages = prev.pages,
        elements = prev.elements,
        hiddenDetectedBlockIds = prev.hiddenDetectedBlockIds,
        selectedElementId = null,
        currentPageIndex = it.currentPageIndex.coerceIn(0, (prev.pages.size - 1).coerceAtLeast(0)),
        canUndo = undoStack.isNotEmpty(),
        canRedo = redoStack.isNotEmpty(),
      )
    }
    prev.pages.forEach { page ->
      if (!_uiState.value.pageBitmaps.containsKey(bitmapCacheKey(page))) renderAndCachePage(page)
    }
  }

  fun redo() {
    if (redoStack.isEmpty()) return
    val current = _uiState.value
    undoStack.addLast(EditorSnapshot(current.pages, current.elements, current.hiddenDetectedBlockIds))
    val next = redoStack.removeLast()
    _uiState.update {
      it.copy(
        pages = next.pages,
        elements = next.elements,
        hiddenDetectedBlockIds = next.hiddenDetectedBlockIds,
        selectedElementId = null,
        currentPageIndex = it.currentPageIndex.coerceIn(0, (next.pages.size - 1).coerceAtLeast(0)),
        canUndo = undoStack.isNotEmpty(),
        canRedo = redoStack.isNotEmpty(),
      )
    }
    next.pages.forEach { page ->
      if (!_uiState.value.pageBitmaps.containsKey(bitmapCacheKey(page))) renderAndCachePage(page)
    }
  }

  fun runOcrOnCurrentPage(onResultMessage: (Int) -> Unit) {
    val st = _uiState.value
    val page = st.pages.getOrNull(st.currentPageIndex) ?: return
    val bmp = st.pageBitmaps[bitmapCacheKey(page)] ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Running on-device OCR...") }
      runCatching {
        PdfRendererAndOcr.runOcrOnBitmap(bmp, page.pageId)
      }.onSuccess { ocrBlocks ->
        _uiState.update { state ->
          val existing = state.detectedTextBlocks[page.pageId].orEmpty().filter { !it.fromOcr }
          val combined = existing + ocrBlocks
          state.copy(
            isBusy = false,
            busyMessage = "",
            detectedTextBlocks = state.detectedTextBlocks + (page.pageId to combined),
          )
        }
        onResultMessage(ocrBlocks.size)
      }.onFailure { err ->
        _uiState.update {
          it.copy(isBusy = false, busyMessage = "", statusBanner = "OCR error: ${err.localizedMessage}")
        }
      }
    }
  }

  fun exportEditedPdfToUri(destUri: Uri, successMessage: String) {
    val st = _uiState.value
    val src = st.sourcePdfFile ?: return
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Exporting edited PDF...") }
      runCatching {
        val tempOut = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.pdf")
        PdfBoxProcessor.exportEditedPdf(ctx, src, st.pages, st.elements, tempOut)
        PdfBoxProcessor.copyFileToOutputUri(ctx, tempOut, destUri)
        tempOut.delete()
      }.onSuccess {
        _uiState.update { it.copy(isBusy = false, busyMessage = "", statusBanner = successMessage) }
      }.onFailure { err ->
        _uiState.update {
          it.copy(isBusy = false, busyMessage = "", statusBanner = "Export failed: ${err.localizedMessage}")
        }
      }
    }
  }

  fun generateEditedPdfTempFile(onReady: (File) -> Unit) {
    val st = _uiState.value
    val src = st.sourcePdfFile ?: return
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Preparing PDF...") }
      runCatching {
        val tempOut = File(ctx.cacheDir, "gs_edited_${System.currentTimeMillis()}.pdf")
        PdfBoxProcessor.exportEditedPdf(ctx, src, st.pages, st.elements, tempOut)
      }.onSuccess { outFile ->
        _uiState.update { it.copy(isBusy = false, busyMessage = "") }
        onReady(outFile)
      }.onFailure { err ->
        _uiState.update {
          it.copy(isBusy = false, busyMessage = "", statusBanner = "Failed: ${err.localizedMessage}")
        }
      }
    }
  }

  // --- Standalone Tool Pipelines ---

  fun runMergePdfs(uris: List<Uri>) {
    if (uris.size < 2) return
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Merging ${uris.size} PDFs...") }
      runCatching {
        val outFile = File(ctx.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
        PdfBoxProcessor.mergePdfs(ctx, uris, outFile)
      }.onSuccess { outFile ->
        _uiState.update {
          it.copy(
            isBusy = false,
            busyMessage = "",
            toolResult =
              ToolOperationResult(
                title = "Merged PDF Ready",
                summary = "Combined ${uris.size} documents (${formatBytes(outFile.length())})",
                outputFiles = listOf(outFile),
                isPdf = true,
                resultSizeBytes = outFile.length(),
              ),
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(isBusy = false, busyMessage = "", statusBanner = "Merge failed: ${err.localizedMessage}")
        }
      }
    }
  }

  fun runSplitPdf(uri: Uri, extractMode: Boolean, rangeText: String, everyN: Int) {
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Splitting PDF...") }
      runCatching {
        val src = PdfRendererAndOcr.copyUriToCacheFile(ctx, uri, "split_src_${System.currentTimeMillis()}.pdf")
        val pages = PdfBoxProcessor.inspectPdfPages(src)
        val total = pages.size
        if (extractMode) {
          val selected = PdfBoxProcessor.parsePageRangeInput(rangeText, total)
          if (selected.isEmpty()) error("No valid pages in range (1-$total)")
          val out = File(ctx.cacheDir, "split_extract_${System.currentTimeMillis()}.pdf")
          PdfBoxProcessor.splitPdfExtract(src, selected, out)
          listOf(out)
        } else {
          val dir = File(ctx.cacheDir, "split_parts_${System.currentTimeMillis()}").apply { mkdirs() }
          PdfBoxProcessor.splitPdfEveryN(src, everyN, dir, "split")
        }
      }.onSuccess { files ->
        _uiState.update {
          it.copy(
            isBusy = false,
            busyMessage = "",
            toolResult =
              ToolOperationResult(
                title = "PDF Split Complete",
                summary = "Created ${files.size} PDF file(s) locally.",
                outputFiles = files,
                isPdf = true,
                resultSizeBytes = files.sumOf { f -> f.length() },
              ),
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(isBusy = false, busyMessage = "", statusBanner = "Split failed: ${err.localizedMessage}")
        }
      }
    }
  }

  fun runCompressPdf(uri: Uri, level: CompressionLevel) {
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Compressing PDF...") }
      runCatching {
        val src = PdfRendererAndOcr.copyUriToCacheFile(ctx, uri, "cmp_src_${System.currentTimeMillis()}.pdf")
        val origSize = src.length()
        val out = File(ctx.cacheDir, "compressed_${System.currentTimeMillis()}.pdf")
        PdfBoxProcessor.compressPdf(src, level, out)
        origSize to out
      }.onSuccess { (origSize, outFile) ->
        val newSize = outFile.length()
        val pct = if (origSize > 0) ((origSize - newSize) * 100f / origSize).toInt().coerceAtLeast(0) else 0
        _uiState.update {
          it.copy(
            isBusy = false,
            busyMessage = "",
            toolResult =
              ToolOperationResult(
                title = "Compression Complete ($pct% smaller)",
                summary = "${formatBytes(origSize)} → ${formatBytes(newSize)}",
                outputFiles = listOf(outFile),
                isPdf = true,
                originalSizeBytes = origSize,
                resultSizeBytes = newSize,
              ),
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(isBusy = false, busyMessage = "", statusBanner = "Compression failed: ${err.localizedMessage}")
        }
      }
    }
  }

  fun runResizePdf(uri: Uri, widthPt: Float, heightPt: Float, label: String) {
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Resizing pages to $label...") }
      runCatching {
        val src = PdfRendererAndOcr.copyUriToCacheFile(ctx, uri, "rsz_src_${System.currentTimeMillis()}.pdf")
        val out = File(ctx.cacheDir, "resized_${System.currentTimeMillis()}.pdf")
        PdfBoxProcessor.resizePdf(src, widthPt, heightPt, out)
      }.onSuccess { outFile ->
        _uiState.update {
          it.copy(
            isBusy = false,
            busyMessage = "",
            toolResult =
              ToolOperationResult(
                title = "Resized to $label",
                summary = "All pages scaled cleanly (${formatBytes(outFile.length())})",
                outputFiles = listOf(outFile),
                isPdf = true,
                resultSizeBytes = outFile.length(),
              ),
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(isBusy = false, busyMessage = "", statusBanner = "Resize failed: ${err.localizedMessage}")
        }
      }
    }
  }

  fun runPdfToImages(uri: Uri, format: ImageExportFormat, dpi: Int, rangeText: String) {
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Converting PDF to ${format.extension.uppercase()}...") }
      runCatching {
        val src = PdfRendererAndOcr.copyUriToCacheFile(ctx, uri, "p2i_src_${System.currentTimeMillis()}.pdf")
        val pages = PdfBoxProcessor.inspectPdfPages(src)
        val indices =
          if (rangeText.isBlank()) (1..pages.size).toList()
          else PdfBoxProcessor.parsePageRangeInput(rangeText, pages.size)
        if (indices.isEmpty()) error("No valid pages selected")
        val maxDim = if (dpi >= 250) 2400 else 1500
        val outFiles = mutableListOf<File>()
        val stamp = System.currentTimeMillis()
        for (p in indices) {
          val bmp = PdfRendererAndOcr.renderPageBitmap(src, p - 1, 0, maxDim) ?: continue
          val (file, _) =
            PdfRendererAndOcr.saveImageToCacheAndMediaStore(
              ctx,
              bmp,
              "gs_pdf_page_${p}_$stamp",
              format,
            )
          bmp.recycle()
          outFiles.add(file)
        }
        outFiles
      }.onSuccess { files ->
        _uiState.update {
          it.copy(
            isBusy = false,
            busyMessage = "",
            toolResult =
              ToolOperationResult(
                title = "Exported ${files.size} ${format.extension.uppercase()} Image(s)",
                summary = "Saved to Pictures/GS PDF Editor and ready to share.",
                outputFiles = files,
                isPdf = false,
              ),
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(isBusy = false, busyMessage = "", statusBanner = "Conversion failed: ${err.localizedMessage}")
        }
      }
    }
  }

  fun runImagesToPdf(uris: List<Uri>, pageSizePreset: PageSizePreset?, marginPt: Float) {
    if (uris.isEmpty()) return
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Building PDF from ${uris.size} image(s)...") }
      runCatching {
        val out = File(ctx.cacheDir, "images_to_pdf_${System.currentTimeMillis()}.pdf")
        PdfBoxProcessor.imagesToPdf(ctx, uris, pageSizePreset, marginPt, out)
      }.onSuccess { outFile ->
        _uiState.update {
          it.copy(
            isBusy = false,
            busyMessage = "",
            toolResult =
              ToolOperationResult(
                title = "PDF Created from ${uris.size} Image(s)",
                summary = "Ready to download, edit, or print (${formatBytes(outFile.length())})",
                outputFiles = listOf(outFile),
                isPdf = true,
                resultSizeBytes = outFile.length(),
              ),
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(isBusy = false, busyMessage = "", statusBanner = "Creation failed: ${err.localizedMessage}")
        }
      }
    }
  }

  fun saveFileToUri(sourceFile: File, destUri: Uri, successMsg: String) {
    val ctx = getApplication<Application>()
    viewModelScope.launch {
      runCatching { PdfBoxProcessor.copyFileToOutputUri(ctx, sourceFile, destUri) }
        .onSuccess { _uiState.update { it.copy(statusBanner = successMsg) } }
        .onFailure { err ->
          _uiState.update { it.copy(statusBanner = "Save failed: ${err.localizedMessage}") }
        }
    }
  }

  fun openGeneratedPdfInEditor(file: File, name: String, onDone: () -> Unit) {
    viewModelScope.launch {
      _uiState.update { it.copy(isBusy = true, busyMessage = "Opening in Editor...") }
      runCatching { openLocalPdfFile(file, name, file.length()) }
        .onSuccess {
          _uiState.update { it.copy(isBusy = false, busyMessage = "", toolResult = null) }
          onDone()
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(isBusy = false, busyMessage = "", statusBanner = "Error: ${err.localizedMessage}")
          }
        }
    }
  }
}

fun formatBytes(bytes: Long): String {
  if (bytes < 1024) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024) return String.format("%.1f KB", kb)
  return String.format("%.2f MB", kb / 1024.0)
}
