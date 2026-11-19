package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.engine.PdfRendererAndOcr
import com.example.i18n.stringsFor
import com.example.model.EditorTool
import com.example.ui.components.AboutAndLimitationsDialog
import com.example.ui.screens.CompressPdfDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DashboardToolAction
import com.example.ui.screens.EditorWorkspaceScreen
import com.example.ui.screens.ImagesToPdfDialog
import com.example.ui.screens.MergePdfsDialog
import com.example.ui.screens.PdfToImagesDialog
import com.example.ui.screens.ResizePdfDialog
import com.example.ui.screens.SplitPdfDialog
import com.example.ui.screens.ToolResultDialog
import com.example.ui.theme.GsBluePrimary
import com.example.ui.theme.GsPdfEditorTheme
import com.example.ui.theme.GsTextPrimary
import com.example.ui.theme.GsWhite
import com.example.viewmodel.PdfEditorViewModel
import java.io.File
import kotlinx.coroutines.launch

private object Routes {
  const val DASHBOARD = "dashboard"
  const val EDITOR = "editor"
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      GsPdfEditorTheme {
        GsPdfEditorApp()
      }
    }
  }
}

@Composable
fun GsPdfEditorApp(viewModel: PdfEditorViewModel = viewModel()) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val strings = remember(state.language) { stringsFor(state.language) }
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var showAboutDialog by remember { mutableStateOf(false) }
  var activeToolDialog by remember { mutableStateOf<DashboardToolAction?>(null) }
  var editorInitialTab by remember { mutableIntStateOf(0) }
  var pendingSaveFile by remember { mutableStateOf<File?>(null) }

  LaunchedEffect(state.statusBanner) {
    val msg = state.statusBanner
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearBanner()
    }
  }

  val openPdfForEditorLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        viewModel.openPdfFromUri(uri) {
          navController.navigate(Routes.EDITOR) { launchSingleTop = true }
        }
      }
    }

  val openPdfToPrintLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        scope.launch {
          runCatching {
            val file =
              PdfRendererAndOcr.copyUriToCacheFile(
                context,
                uri,
                "print_direct_${System.currentTimeMillis()}.pdf",
              )
            val name = PdfRendererAndOcr.getDisplayName(context, uri)
            PdfRendererAndOcr.printPdfFile(context, file, name)
          }.onFailure { err ->
            viewModel.showBanner("Print error: ${err.localizedMessage}")
          }
        }
      }
    }

  val saveEditedPdfLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destUri ->
      if (destUri != null) {
        viewModel.exportEditedPdfToUri(destUri, strings.savePdfSuccess)
      }
    }

  val saveGenericFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
      val file = pendingSaveFile
      if (destUri != null && file != null) {
        viewModel.saveFileToUri(file, destUri, strings.savePdfSuccess)
      }
      pendingSaveFile = null
    }

  fun shareFiles(files: List<File>) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { PdfRendererAndOcr.getShareableUri(context, it) })
    val intent =
      if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
          type = PdfRendererAndOcr.getMimeType(files.first())
          putExtra(Intent.EXTRA_STREAM, uris.first())
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
          type = "*/*"
          putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      }
    context.startActivity(Intent.createChooser(intent, strings.sharePdf))
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
      NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        composable(Routes.DASHBOARD) {
          DashboardScreen(
            state = state,
            strings = strings,
            onToggleLanguage = viewModel::toggleLanguage,
            onOpenAbout = { showAboutDialog = true },
            onUploadPdfForEditor = {
              editorInitialTab = 0
              viewModel.setActiveTool(EditorTool.SELECT_EDIT)
              openPdfForEditorLauncher.launch(arrayOf("application/pdf"))
            },
            onResumeEditor = {
              editorInitialTab = 0
              navController.navigate(Routes.EDITOR) { launchSingleTop = true }
            },
            onLaunchTool = { action ->
              when (action) {
                DashboardToolAction.EDIT_PDF -> {
                  editorInitialTab = 0
                  viewModel.setActiveTool(EditorTool.SELECT_EDIT)
                  if (state.sourcePdfFile != null) {
                    navController.navigate(Routes.EDITOR) { launchSingleTop = true }
                  } else {
                    openPdfForEditorLauncher.launch(arrayOf("application/pdf"))
                  }
                }
                DashboardToolAction.ORGANIZE_PAGES -> {
                  editorInitialTab = 1
                  viewModel.setActiveTool(EditorTool.PAGES)
                  if (state.sourcePdfFile != null) {
                    navController.navigate(Routes.EDITOR) { launchSingleTop = true }
                  } else {
                    openPdfForEditorLauncher.launch(arrayOf("application/pdf"))
                  }
                }
                DashboardToolAction.OCR_PDF -> {
                  editorInitialTab = 0
                  viewModel.setActiveTool(EditorTool.OCR)
                  if (state.sourcePdfFile != null) {
                    navController.navigate(Routes.EDITOR) { launchSingleTop = true }
                  } else {
                    openPdfForEditorLauncher.launch(arrayOf("application/pdf"))
                  }
                }
                DashboardToolAction.PRINT_PDF -> {
                  if (state.sourcePdfFile != null) {
                    viewModel.generateEditedPdfTempFile { file ->
                      PdfRendererAndOcr.printPdfFile(context, file, state.documentName.ifBlank { "GS_Document.pdf" })
                    }
                  } else {
                    openPdfToPrintLauncher.launch(arrayOf("application/pdf"))
                  }
                }
                else -> activeToolDialog = action
              }
            },
          )
        }

        composable(Routes.EDITOR) {
          EditorWorkspaceScreen(
            viewModel = viewModel,
            state = state,
            strings = strings,
            initialSidebarTab = editorInitialTab,
            onNavigateBack = { navController.popBackStack() },
            onOpenPdfPicker = { openPdfForEditorLauncher.launch(arrayOf("application/pdf")) },
            onDownloadEditedPdf = {
              val suggested =
                state.documentName.removeSuffix(".pdf").ifBlank { "edited_document" } + "_edited.pdf"
              saveEditedPdfLauncher.launch(suggested)
            },
            onShareEditedPdf = {
              viewModel.generateEditedPdfTempFile { file -> shareFiles(listOf(file)) }
            },
            onPrintEditedPdf = {
              viewModel.generateEditedPdfTempFile { file ->
                PdfRendererAndOcr.printPdfFile(context, file, state.documentName.ifBlank { "GS_Edited.pdf" })
              }
            },
          )
        }
      }
    }

    if (showAboutDialog) {
      AboutAndLimitationsDialog(strings = strings, onDismiss = { showAboutDialog = false })
    }

    when (activeToolDialog) {
      DashboardToolAction.MERGE_PDF ->
        MergePdfsDialog(
          strings = strings,
          onDismiss = { activeToolDialog = null },
          onMerge = { uris ->
            activeToolDialog = null
            viewModel.runMergePdfs(uris)
          },
        )
      DashboardToolAction.SPLIT_PDF ->
        SplitPdfDialog(
          strings = strings,
          onDismiss = { activeToolDialog = null },
          onSplit = { uri, extract, range, everyN ->
            activeToolDialog = null
            viewModel.runSplitPdf(uri, extract, range, everyN)
          },
        )
      DashboardToolAction.COMPRESS_PDF ->
        CompressPdfDialog(
          strings = strings,
          onDismiss = { activeToolDialog = null },
          onCompress = { uri, level ->
            activeToolDialog = null
            viewModel.runCompressPdf(uri, level)
          },
        )
      DashboardToolAction.RESIZE_PDF ->
        ResizePdfDialog(
          strings = strings,
          onDismiss = { activeToolDialog = null },
          onResize = { uri, w, h, label ->
            activeToolDialog = null
            viewModel.runResizePdf(uri, w, h, label)
          },
        )
      DashboardToolAction.PDF_TO_IMAGES ->
        PdfToImagesDialog(
          strings = strings,
          onDismiss = { activeToolDialog = null },
          onConvert = { uri, fmt, dpi, range ->
            activeToolDialog = null
            viewModel.runPdfToImages(uri, fmt, dpi, range)
          },
        )
      DashboardToolAction.IMAGES_TO_PDF ->
        ImagesToPdfDialog(
          strings = strings,
          onDismiss = { activeToolDialog = null },
          onConvert = { uris, preset, margin ->
            activeToolDialog = null
            viewModel.runImagesToPdf(uris, preset, margin)
          },
        )
      else -> Unit
    }

    state.toolResult?.let { result ->
      ToolResultDialog(
        strings = strings,
        result = result,
        onDismiss = viewModel::clearToolResult,
        onSaveFileAs = { file ->
          pendingSaveFile = file
          saveGenericFileLauncher.launch(file.name)
        },
        onOpenInEditor = { file ->
          viewModel.openGeneratedPdfInEditor(file, file.name) {
            navController.navigate(Routes.EDITOR) { launchSingleTop = true }
          }
        },
        onShareFiles = { files -> shareFiles(files) },
        onPrintFile = { file -> PdfRendererAndOcr.printPdfFile(context, file, file.name) },
      )
    }

    if (state.isBusy) {
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
      ) {
        Surface(color = GsWhite, shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator(color = GsBluePrimary)
            Text(
              text = state.busyMessage.ifBlank { strings.processingMessage },
              style = MaterialTheme.typography.titleMedium,
              color = GsTextPrimary,
            )
          }
        }
      }
    }
  }
}
