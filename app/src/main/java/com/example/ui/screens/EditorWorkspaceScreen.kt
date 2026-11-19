package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.i18n.AppStrings
import com.example.model.DetectedTextBlock
import com.example.model.EditorTool
import com.example.model.PdfOverlayElement
import com.example.ui.components.EditorStyleInspector
import com.example.ui.components.EditorToolsPalettePanel
import com.example.ui.components.PagesOrganizerPanel
import com.example.ui.components.PdfPageCanvas
import com.example.ui.components.SignaturePadDialog
import com.example.ui.components.TextEditDialog
import com.example.ui.components.buildEditorToolsList
import com.example.ui.theme.GsBlueContainer
import com.example.ui.theme.GsBluePrimary
import com.example.ui.theme.GsBorder
import com.example.ui.theme.GsCanvasBg
import com.example.ui.theme.GsDanger
import com.example.ui.theme.GsSurfaceSubtle
import com.example.ui.theme.GsTextPrimary
import com.example.ui.theme.GsTextSecondary
import com.example.ui.theme.GsWhite
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.PdfEditorViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorWorkspaceScreen(
  viewModel: PdfEditorViewModel,
  state: EditorUiState,
  strings: AppStrings,
  initialSidebarTab: Int = 0,
  onNavigateBack: () -> Unit,
  onOpenPdfPicker: () -> Unit,
  onDownloadEditedPdf: () -> Unit,
  onShareEditedPdf: () -> Unit,
  onPrintEditedPdf: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler { onNavigateBack() }

  var sidebarTab by remember(initialSidebarTab) { mutableIntStateOf(initialSidebarTab) }
  var showMobileStyleSheet by remember { mutableStateOf(false) }
  var showMobilePagesSheet by remember { mutableStateOf(false) }
  var showSignatureDialog by remember { mutableStateOf(false) }

  var pendingNewTextNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }
  var editingTextElement by remember { mutableStateOf<PdfOverlayElement.TextElement?>(null) }
  var editingDetectedBlock by remember { mutableStateOf<DetectedTextBlock?>(null) }
  var replacingImageElementId by remember { mutableStateOf<String?>(null) }

  val currentPage = state.pages.getOrNull(state.currentPageIndex)

  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
      val page = currentPage ?: return@rememberLauncherForActivityResult
      if (uri != null) {
        viewModel.addImageFromUri(uri, page.pageId, replacingImageElementId)
      }
      replacingImageElementId = null
    }

  fun handleToolClick(tool: EditorTool) {
    when (tool) {
      EditorTool.SIGNATURE -> showSignatureDialog = true
      EditorTool.IMAGE -> {
        replacingImageElementId = null
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      }
      EditorTool.PAGES -> {
        sidebarTab = 1
        showMobilePagesSheet = true
      }
      EditorTool.OCR -> {
        viewModel.setActiveTool(EditorTool.OCR)
        viewModel.runOcrOnCurrentPage { count ->
          val msg =
            if (count > 0) String.format(strings.ocrSuccessBanner, count)
            else strings.ocrNoText
          viewModel.showBanner(msg)
        }
      }
      else -> viewModel.setActiveTool(tool)
    }
  }

  Column(modifier = modifier.fillMaxSize().background(GsCanvasBg)) {
    // Top Editor Toolbar
    Surface(color = GsWhite, shadowElevation = 3.dp) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.testTag("editor_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.navHome, tint = GsTextPrimary)
        }

        Column(modifier = Modifier.padding(end = 8.dp)) {
          Text(
            text = state.documentName.ifBlank { strings.brandTitle },
            style = MaterialTheme.typography.titleMedium,
            color = GsTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (state.pages.isNotEmpty()) {
            Text(
              text = String.format(strings.pageOf, state.currentPageIndex + 1, state.pages.size),
              style = MaterialTheme.typography.labelMedium,
              color = GsBluePrimary,
            )
          }
        }

        IconButton(
          onClick = { viewModel.undo() },
          enabled = state.canUndo,
          modifier = Modifier.testTag("editor_undo_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.undo)
        }
        IconButton(
          onClick = { viewModel.redo() },
          enabled = state.canRedo,
          modifier = Modifier.testTag("editor_redo_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = strings.redo)
        }

        Surface(color = GsSurfaceSubtle, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, GsBorder)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = { viewModel.zoomOut() },
              modifier = Modifier.size(36.dp).testTag("editor_zoom_out_button"),
            ) {
              Icon(Icons.Default.ZoomOut, contentDescription = strings.zoomOut, modifier = Modifier.size(18.dp))
            }
            Text(
              text = "${(state.zoomScale * 100).roundToInt()}%",
              style = MaterialTheme.typography.labelLarge,
              color = GsTextPrimary,
              modifier = Modifier.clickable { viewModel.resetZoom() }.padding(horizontal = 4.dp),
            )
            IconButton(
              onClick = { viewModel.zoomIn() },
              modifier = Modifier.size(36.dp).testTag("editor_zoom_in_button"),
            ) {
              Icon(Icons.Default.ZoomIn, contentDescription = strings.zoomIn, modifier = Modifier.size(18.dp))
            }
          }
        }

        IconButton(onClick = { viewModel.toggleLanguage() }) {
          Icon(Icons.Default.Translate, contentDescription = state.language.toggleLabel, tint = GsBluePrimary)
        }

        if (currentPage != null) {
          IconButton(
            onClick = onPrintEditedPdf,
            modifier = Modifier.testTag("editor_print_button"),
          ) {
            Icon(Icons.Default.Print, contentDescription = strings.printPdf, tint = GsTextPrimary)
          }
          IconButton(
            onClick = onShareEditedPdf,
            modifier = Modifier.testTag("editor_share_button"),
          ) {
            Icon(Icons.Default.Share, contentDescription = strings.sharePdf, tint = GsTextPrimary)
          }
          Button(
            onClick = onDownloadEditedPdf,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.testTag("editor_download_button"),
          ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.downloadPdf)
          }
        }
      }
    }

    if (currentPage == null) {
      // Empty Workspace State
      Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
          color = GsWhite,
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, GsBorder),
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
          Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = GsBluePrimary, modifier = Modifier.size(54.dp))
            Text(strings.noDocumentTitle, style = MaterialTheme.typography.headlineMedium, color = GsTextPrimary)
            Text(strings.noDocumentDesc, style = MaterialTheme.typography.bodyMedium, color = GsTextSecondary)
            Button(onClick = onOpenPdfPicker, shape = RoundedCornerShape(12.dp)) {
              Text(strings.openPdfCta)
            }
          }
        }
      }
    } else {
      val pageElements = state.elements.filter { it.pageId == currentPage.pageId }
      val pageDetectedBlocks = state.detectedTextBlocks[currentPage.pageId].orEmpty()
      val selectedElement = pageElements.find { it.id == state.selectedElementId }

      BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        val isExpandedDesktop = maxWidth >= 840.dp

        Column(modifier = Modifier.fillMaxSize()) {
          Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
          // Left Sidebar on Desktop/Tablet
          if (isExpandedDesktop) {
            Surface(
              color = GsWhite,
              shadowElevation = 2.dp,
              modifier = Modifier.width(276.dp).fillMaxHeight(),
            ) {
              Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = sidebarTab) {
                  Tab(selected = sidebarTab == 0, onClick = { sidebarTab = 0 }, text = { Text(strings.tools) })
                  Tab(selected = sidebarTab == 1, onClick = { sidebarTab = 1 }, text = { Text(strings.pages) })
                  Tab(selected = sidebarTab == 2, onClick = { sidebarTab = 2 }, text = { Text(strings.properties) })
                }
                when (sidebarTab) {
                  0 ->
                    EditorToolsPalettePanel(
                      strings = strings,
                      activeTool = state.activeTool,
                      onToolSelected = ::handleToolClick,
                      onRunOcr = {
                        viewModel.runOcrOnCurrentPage { }
                      },
                      modifier = Modifier.fillMaxSize(),
                    )
                  1 ->
                    PagesOrganizerPanel(
                      strings = strings,
                      pages = state.pages,
                      currentPageIndex = state.currentPageIndex,
                      bitmaps = state.pageBitmaps,
                      bitmapKeyProvider = viewModel::bitmapCacheKey,
                      onSelectPage = viewModel::selectPage,
                      onRotateCw = { viewModel.rotatePage(it, 90) },
                      onRotateCcw = { viewModel.rotatePage(it, -90) },
                      onMovePage = viewModel::movePage,
                      onDeletePage = viewModel::deletePage,
                      modifier = Modifier.fillMaxSize(),
                    )
                  else ->
                    EditorStyleInspector(
                      strings = strings,
                      state = state,
                      onSelectFont = viewModel::updateFont,
                      onChangeFontSize = viewModel::updateFontSize,
                      onSelectColor = viewModel::updateColor,
                      onSelectAlign = viewModel::updateAlign,
                      onChangeStrokeWidth = viewModel::updateStrokeWidth,
                      onToggleShapeFilled = viewModel::toggleShapeFilled,
                      onSelectStampLabel = viewModel::updateStampLabel,
                      modifier = Modifier.fillMaxSize(),
                    )
                }
              }
            }
          }

          // Central PDF Preview Workspace
          Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxSize()) {
              // Mode Hint Banner
              val hintText =
                when (state.activeTool) {
                  EditorTool.SELECT_EDIT ->
                    if (selectedElement != null) strings.selectElementHint else strings.editDetectedTextHint
                  EditorTool.ADD_TEXT -> strings.addTextHint
                  EditorTool.WHITEOUT -> strings.whiteoutHint
                  EditorTool.FREEHAND -> strings.freehandHint
                  EditorTool.OCR -> strings.runOcrPage
                  else -> strings.shapeHint
                }
              Surface(color = GsBlueContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GsBluePrimary,
                    modifier = Modifier.weight(1f),
                  )
                  if (!isExpandedDesktop) {
                    TextButton(
                      onClick = { showMobileStyleSheet = true },
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                      modifier = Modifier.testTag("mobile_style_button"),
                    ) {
                      Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text(strings.properties)
                    }
                  }
                }
              }

              // Scrollable Zoomable PDF Canvas
              Box(
                modifier =
                  Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 18.dp, horizontal = 12.dp),
                contentAlignment = Alignment.TopCenter,
              ) {
                PdfPageCanvas(
                  page = currentPage,
                  bitmap = state.pageBitmaps[viewModel.bitmapCacheKey(currentPage)],
                  zoomScale = state.zoomScale,
                  activeTool = state.activeTool,
                  elements = pageElements,
                  detectedTextBlocks = pageDetectedBlocks,
                  hiddenDetectedBlockIds = state.hiddenDetectedBlockIds,
                  selectedElementId = state.selectedElementId,
                  activeColorArgb = state.activeColorArgb,
                  activeStrokeWidthPt = state.activeStrokeWidthPt,
                  onTapPlaceText = { x, y -> pendingNewTextNorm = x to y },
                  onTapWhiteout = { x, y -> viewModel.addWhiteout(currentPage.pageId, x, y) },
                  onTapShape = { kind, x, y -> viewModel.addShape(currentPage.pageId, kind, x, y) },
                  onTapStamp = { x, y -> viewModel.addStamp(currentPage.pageId, x, y) },
                  onTapDetectedBlock = { block -> editingDetectedBlock = block },
                  onSelectElement = viewModel::selectElement,
                  onDoubleTapTextElement = { txtEl -> editingTextElement = txtEl },
                  onBeginTransform = viewModel::beginElementTransform,
                  onMoveOrResizeElement = viewModel::moveAndResizeElement,
                  onFinishFreehand = { pts -> viewModel.addFreehandPath(currentPage.pageId, pts) },
                )
              }
            }

            // Selected Element Quick Actions Bar
            if (selectedElement != null) {
              Surface(
                color = GsWhite,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, GsBorder),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  if (selectedElement is PdfOverlayElement.TextElement) {
                    OutlinedButton(
                      onClick = { editingTextElement = selectedElement },
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                      Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text(strings.editTextAction)
                    }
                  }
                  if (selectedElement is PdfOverlayElement.ImageElement && !selectedElement.isSignature) {
                    OutlinedButton(
                      onClick = {
                        replacingImageElementId = selectedElement.id
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                      },
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                      Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text(strings.replaceImageAction)
                    }
                  }
                  IconButton(onClick = { showMobileStyleSheet = true }) {
                    Icon(Icons.Default.Palette, contentDescription = strings.properties, tint = GsBluePrimary)
                  }
                  IconButton(onClick = { viewModel.duplicateElement(selectedElement.id) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = strings.duplicateAction, tint = GsTextPrimary)
                  }
                  IconButton(onClick = { viewModel.deleteElement(selectedElement.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = strings.deleteAction, tint = GsDanger)
                  }
                }
              }
            } else if (state.pages.size > 1) {
              // Floating Page Stepper Pill
              Surface(
                color = GsWhite.copy(alpha = 0.96f),
                shape = RoundedCornerShape(50),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, GsBorder),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                  IconButton(
                    onClick = { viewModel.selectPage(state.currentPageIndex - 1) },
                    enabled = state.currentPageIndex > 0,
                  ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                  }
                  Text(
                    text = String.format(strings.pageOf, state.currentPageIndex + 1, state.pages.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { showMobilePagesSheet = true }.padding(horizontal = 8.dp),
                  )
                  IconButton(
                    onClick = { viewModel.selectPage(state.currentPageIndex + 1) },
                    enabled = state.currentPageIndex < state.pages.lastIndex,
                  ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                  }
                }
              }
            }
          }
          }

          if (!isExpandedDesktop) {
            val tools = buildEditorToolsList(strings)
            Surface(color = GsWhite, shadowElevation = 6.dp) {
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                tools.forEach { item ->
                  val selected = item.tool == state.activeTool
                  Surface(
                    color = if (selected) GsBlueContainer else GsSurfaceSubtle,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (selected) GsBluePrimary else GsBorder),
                    modifier =
                      Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { handleToolClick(item.tool) }
                        .testTag(item.testTag),
                  ) {
                    Column(
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                      Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) GsBluePrimary else GsTextSecondary,
                        modifier = Modifier.size(20.dp),
                      )
                      Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) GsBluePrimary else GsTextPrimary,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Mobile Style Bottom Sheet
  if (showMobileStyleSheet) {
    ModalBottomSheet(onDismissRequest = { showMobileStyleSheet = false }) {
      EditorStyleInspector(
        strings = strings,
        state = state,
        onSelectFont = viewModel::updateFont,
        onChangeFontSize = viewModel::updateFontSize,
        onSelectColor = viewModel::updateColor,
        onSelectAlign = viewModel::updateAlign,
        onChangeStrokeWidth = viewModel::updateStrokeWidth,
        onToggleShapeFilled = viewModel::toggleShapeFilled,
        onSelectStampLabel = viewModel::updateStampLabel,
        modifier = Modifier.fillMaxWidth().height(420.dp),
      )
    }
  }

  // Mobile Organize Pages Bottom Sheet
  if (showMobilePagesSheet) {
    ModalBottomSheet(onDismissRequest = { showMobilePagesSheet = false }) {
      PagesOrganizerPanel(
        strings = strings,
        pages = state.pages,
        currentPageIndex = state.currentPageIndex,
        bitmaps = state.pageBitmaps,
        bitmapKeyProvider = viewModel::bitmapCacheKey,
        onSelectPage = {
          viewModel.selectPage(it)
          showMobilePagesSheet = false
        },
        onRotateCw = { viewModel.rotatePage(it, 90) },
        onRotateCcw = { viewModel.rotatePage(it, -90) },
        onMovePage = viewModel::movePage,
        onDeletePage = viewModel::deletePage,
        modifier = Modifier.fillMaxWidth().height(460.dp),
      )
    }
  }

  // Signature Dialog
  if (showSignatureDialog && currentPage != null) {
    SignaturePadDialog(
      strings = strings,
      onDismiss = { showSignatureDialog = false },
      onApplySignature = { bmp ->
        viewModel.saveSignatureAndPlace(bmp, currentPage.pageId)
        showSignatureDialog = false
      },
    )
  }

  // Add New Text Dialog
  pendingNewTextNorm?.let { (x, y) ->
    if (currentPage != null) {
      TextEditDialog(
        strings = strings,
        isReplacingDetectedBlock = false,
        originalText = null,
        initialText = "",
        onDismiss = { pendingNewTextNorm = null },
        onConfirm = { newText ->
          viewModel.addTextElement(currentPage.pageId, x, y, newText)
          pendingNewTextNorm = null
        },
      )
    }
  }

  // Edit Existing Overlay Text Dialog
  editingTextElement?.let { el ->
    TextEditDialog(
      strings = strings,
      isReplacingDetectedBlock = false,
      originalText = el.text,
      initialText = el.text,
      onDismiss = { editingTextElement = null },
      onConfirm = { updated ->
        viewModel.editExistingTextElement(el.id, updated)
        editingTextElement = null
      },
    )
  }

  // Edit / Replace / Delete Detected Original PDF Text Dialog
  editingDetectedBlock?.let { block ->
    TextEditDialog(
      strings = strings,
      isReplacingDetectedBlock = true,
      originalText = block.text,
      initialText = block.text,
      onDismiss = { editingDetectedBlock = null },
      onConfirm = { updated ->
        viewModel.replaceDetectedTextBlock(block, updated)
        editingDetectedBlock = null
      },
      onDeleteInPlace = {
        viewModel.replaceDetectedTextBlock(block, null)
      },
    )
  }
}
