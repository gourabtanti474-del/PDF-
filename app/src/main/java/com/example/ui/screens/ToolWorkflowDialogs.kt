package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.PdfRendererAndOcr
import com.example.i18n.AppStrings
import com.example.model.CompressionLevel
import com.example.model.ImageExportFormat
import com.example.model.PageSizePreset
import com.example.ui.theme.GsBlueContainer
import com.example.ui.theme.GsBluePrimary
import com.example.ui.theme.GsBorder
import com.example.ui.theme.GsDanger
import com.example.ui.theme.GsSuccess
import com.example.ui.theme.GsSuccessContainer
import com.example.ui.theme.GsSurfaceSubtle
import com.example.ui.theme.GsTextPrimary
import com.example.ui.theme.GsTextSecondary
import com.example.viewmodel.ToolOperationResult
import com.example.viewmodel.formatBytes
import java.io.File

@Composable
fun MergePdfsDialog(
  strings: AppStrings,
  onDismiss: () -> Unit,
  onMerge: (List<Uri>) -> Unit,
) {
  val context = LocalContext.current
  val items = remember { mutableStateListOf<Pair<Uri, String>>() }
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      uris.forEach { uri ->
        val name = PdfRendererAndOcr.getDisplayName(context, uri)
        items.add(uri to name)
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.toolMergePdf, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = { picker.launch(arrayOf("application/pdf")) },
          modifier = Modifier.fillMaxWidth().testTag("merge_pick_files_button"),
        ) {
          Icon(Icons.Default.Add, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(strings.mergeSelectFiles)
        }

        items.forEachIndexed { index, (_, name) ->
          Surface(
            color = GsSurfaceSubtle,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, GsBorder),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${index + 1}. $name",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              IconButton(
                onClick = {
                  if (index > 0) {
                    val item = items.removeAt(index)
                    items.add(index - 1, item)
                  }
                },
                enabled = index > 0,
              ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = strings.moveUp, modifier = Modifier.size(18.dp))
              }
              IconButton(
                onClick = {
                  if (index < items.lastIndex) {
                    val item = items.removeAt(index)
                    items.add(index + 1, item)
                  }
                },
                enabled = index < items.lastIndex,
              ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = strings.moveDown, modifier = Modifier.size(18.dp))
              }
              IconButton(onClick = { items.removeAt(index) }) {
                Icon(Icons.Default.Delete, contentDescription = strings.deleteAction, tint = GsDanger, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = { onMerge(items.map { it.first }) },
        enabled = items.size >= 2,
        modifier = Modifier.testTag("merge_confirm_button"),
      ) {
        Text(strings.mergeAction)
      }
    },
  )
}

@Composable
fun SplitPdfDialog(
  strings: AppStrings,
  onDismiss: () -> Unit,
  onSplit: (Uri, Boolean, String, Int) -> Unit,
) {
  val context = LocalContext.current
  var selectedUri by remember { mutableStateOf<Uri?>(null) }
  var selectedName by remember { mutableStateOf("") }
  var extractMode by remember { mutableStateOf(true) }
  var rangeText by remember { mutableStateOf("1-2") }
  var everyNText by remember { mutableStateOf("1") }

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        selectedUri = uri
        selectedName = PdfRendererAndOcr.getDisplayName(context, uri)
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.toolSplitPdf, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = { picker.launch(arrayOf("application/pdf")) },
          modifier = Modifier.fillMaxWidth().testTag("split_pick_pdf_button"),
        ) {
          Text(if (selectedName.isBlank()) strings.pickPdfLabel else selectedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = extractMode,
            onClick = { extractMode = true },
            label = { Text(strings.splitModeExtract) },
          )
          FilterChip(
            selected = !extractMode,
            onClick = { extractMode = false },
            label = { Text(strings.splitModeEveryN) },
          )
        }
        if (extractMode) {
          OutlinedTextField(
            value = rangeText,
            onValueChange = { rangeText = it },
            label = { Text(strings.splitRangesHint) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          OutlinedTextField(
            value = everyNText,
            onValueChange = { everyNText = it.filter { ch -> ch.isDigit() } },
            label = { Text("N") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = {
          selectedUri?.let {
            onSplit(it, extractMode, rangeText, everyNText.toIntOrNull()?.coerceAtLeast(1) ?: 1)
          }
        },
        enabled = selectedUri != null,
        modifier = Modifier.testTag("split_confirm_button"),
      ) {
        Text(strings.splitAction)
      }
    },
  )
}

@Composable
fun CompressPdfDialog(
  strings: AppStrings,
  onDismiss: () -> Unit,
  onCompress: (Uri, CompressionLevel) -> Unit,
) {
  val context = LocalContext.current
  var selectedUri by remember { mutableStateOf<Uri?>(null) }
  var selectedName by remember { mutableStateOf("") }
  var selectedSize by remember { mutableStateOf(0L) }
  var level by remember { mutableStateOf(CompressionLevel.MEDIUM) }

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        selectedUri = uri
        selectedName = PdfRendererAndOcr.getDisplayName(context, uri)
        selectedSize = PdfRendererAndOcr.getFileSize(context, uri)
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.toolCompressPdf, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = { picker.launch(arrayOf("application/pdf")) },
          modifier = Modifier.fillMaxWidth().testTag("compress_pick_pdf_button"),
        ) {
          Text(
            if (selectedName.isBlank()) strings.pickPdfLabel
            else "$selectedName (${formatBytes(selectedSize)})",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        CompressionLevel.entries.forEach { opt ->
          val label =
            when (opt) {
              CompressionLevel.LOW -> strings.compressLevelLow
              CompressionLevel.MEDIUM -> strings.compressLevelMedium
              CompressionLevel.HIGH -> strings.compressLevelHigh
            }
          FilterChip(
            selected = level == opt,
            onClick = { level = opt },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = { selectedUri?.let { onCompress(it, level) } },
        enabled = selectedUri != null,
        modifier = Modifier.testTag("compress_confirm_button"),
      ) {
        Text(strings.compressAction)
      }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResizePdfDialog(
  strings: AppStrings,
  onDismiss: () -> Unit,
  onResize: (Uri, Float, Float, String) -> Unit,
) {
  val context = LocalContext.current
  var selectedUri by remember { mutableStateOf<Uri?>(null) }
  var selectedName by remember { mutableStateOf("") }
  var selectedPreset by remember { mutableStateOf(PageSizePreset.A4) }

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        selectedUri = uri
        selectedName = PdfRendererAndOcr.getDisplayName(context, uri)
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.toolResizePdf, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = { picker.launch(arrayOf("application/pdf")) },
          modifier = Modifier.fillMaxWidth().testTag("resize_pick_pdf_button"),
        ) {
          Text(if (selectedName.isBlank()) strings.pickPdfLabel else selectedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(strings.resizeTargetLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          PageSizePreset.entries.forEach { preset ->
            FilterChip(
              selected = selectedPreset == preset,
              onClick = { selectedPreset = preset },
              label = { Text(preset.label) },
            )
          }
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = {
          selectedUri?.let {
            onResize(it, selectedPreset.widthPt, selectedPreset.heightPt, selectedPreset.name)
          }
        },
        enabled = selectedUri != null,
        modifier = Modifier.testTag("resize_confirm_button"),
      ) {
        Text(strings.resizeAction)
      }
    },
  )
}

@Composable
fun PdfToImagesDialog(
  strings: AppStrings,
  onDismiss: () -> Unit,
  onConvert: (Uri, ImageExportFormat, Int, String) -> Unit,
) {
  val context = LocalContext.current
  var selectedUri by remember { mutableStateOf<Uri?>(null) }
  var selectedName by remember { mutableStateOf("") }
  var format by remember { mutableStateOf(ImageExportFormat.JPG) }
  var dpi by remember { mutableIntStateOf(150) }
  var rangeText by remember { mutableStateOf("") }

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        selectedUri = uri
        selectedName = PdfRendererAndOcr.getDisplayName(context, uri)
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.toolPdfToImages, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = { picker.launch(arrayOf("application/pdf")) },
          modifier = Modifier.fillMaxWidth().testTag("pdf_to_img_pick_button"),
        ) {
          Text(if (selectedName.isBlank()) strings.pickPdfLabel else selectedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(strings.exportFormatLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ImageExportFormat.entries.forEach { fmt ->
            FilterChip(
              selected = format == fmt,
              onClick = { format = fmt },
              label = { Text(fmt.name) },
            )
          }
        }
        Text(strings.exportDpiLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(150, 300).forEach { opt ->
            FilterChip(
              selected = dpi == opt,
              onClick = { dpi = opt },
              label = { Text("$opt DPI") },
            )
          }
        }
        OutlinedTextField(
          value = rangeText,
          onValueChange = { rangeText = it },
          label = { Text("${strings.splitRangesHint} (blank = all)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = { selectedUri?.let { onConvert(it, format, dpi, rangeText) } },
        enabled = selectedUri != null,
        modifier = Modifier.testTag("pdf_to_img_confirm_button"),
      ) {
        Text(strings.convertToImagesAction)
      }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImagesToPdfDialog(
  strings: AppStrings,
  onDismiss: () -> Unit,
  onConvert: (List<Uri>, PageSizePreset?, Float) -> Unit,
) {
  val context = LocalContext.current
  val selectedImages = remember { mutableStateListOf<Pair<Uri, String>>() }
  var selectedPreset by remember { mutableStateOf<PageSizePreset?>(PageSizePreset.A4) }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
      uris.forEach { uri ->
        selectedImages.add(uri to PdfRendererAndOcr.getDisplayName(context, uri))
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.toolImagesToPdf, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          modifier = Modifier.fillMaxWidth().testTag("img_to_pdf_pick_button"),
        ) {
          Icon(Icons.Default.Add, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(strings.pickImagesLabel)
        }
        selectedImages.forEachIndexed { idx, (_, name) ->
          Surface(
            color = GsSurfaceSubtle,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, GsBorder),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("${idx + 1}. $name", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
              IconButton(onClick = { selectedImages.removeAt(idx) }) {
                Icon(Icons.Default.Delete, contentDescription = strings.deleteAction, tint = GsDanger, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
        Text(strings.resizeTargetLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = selectedPreset == null,
            onClick = { selectedPreset = null },
            label = { Text("Fit Image") },
          )
          listOf(PageSizePreset.A4, PageSizePreset.LETTER).forEach { preset ->
            FilterChip(
              selected = selectedPreset == preset,
              onClick = { selectedPreset = preset },
              label = { Text(preset.name) },
            )
          }
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = { onConvert(selectedImages.map { it.first }, selectedPreset, 18f) },
        enabled = selectedImages.isNotEmpty(),
        modifier = Modifier.testTag("img_to_pdf_confirm_button"),
      ) {
        Text(strings.convertImagesToPdfAction)
      }
    },
  )
}

@Composable
fun ToolResultDialog(
  strings: AppStrings,
  result: ToolOperationResult,
  onDismiss: () -> Unit,
  onSaveFileAs: (File) -> Unit,
  onOpenInEditor: (File) -> Unit,
  onShareFiles: (List<File>) -> Unit,
  onPrintFile: (File) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GsSuccess)
        Text(result.title, style = MaterialTheme.typography.titleLarge)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = GsSuccessContainer, shape = RoundedCornerShape(10.dp)) {
          Text(
            text = result.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = GsTextPrimary,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
          )
        }
        if (result.isPdf && result.outputFiles.size == 1) {
          val mainFile = result.outputFiles.first()
          Button(
            onClick = { onSaveFileAs(mainFile) },
            modifier = Modifier.fillMaxWidth().testTag("result_download_button"),
          ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(strings.downloadPdf)
          }
          OutlinedButton(
            onClick = { onOpenInEditor(mainFile) },
            modifier = Modifier.fillMaxWidth().testTag("result_open_editor_button"),
          ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(strings.resumeAction)
          }
          OutlinedButton(
            onClick = { onPrintFile(mainFile) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(strings.printPdf)
          }
        } else {
          result.outputFiles.forEachIndexed { idx, file ->
            Surface(color = GsBlueContainer, shape = RoundedCornerShape(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "${idx + 1}. ${file.name} (${formatBytes(file.length())})",
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onSaveFileAs(file) }) {
                  Icon(Icons.Default.Download, contentDescription = strings.save, tint = GsBluePrimary)
                }
              }
            }
          }
        }
        OutlinedButton(
          onClick = { onShareFiles(result.outputFiles) },
          modifier = Modifier.fillMaxWidth().testTag("result_share_button"),
        ) {
          Icon(Icons.Default.Share, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(strings.sharePdf)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(strings.close) }
    },
  )
}
