package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.AppStrings
import com.example.model.EditorColorPalette
import com.example.model.EditorTool
import com.example.model.PdfFontOption
import com.example.model.PdfPageState
import com.example.model.TextAlignOption
import com.example.model.toComposeColor
import com.example.ui.theme.GsBlueContainer
import com.example.ui.theme.GsBluePrimary
import com.example.ui.theme.GsBorder
import com.example.ui.theme.GsDanger
import com.example.ui.theme.GsSurfaceSubtle
import com.example.ui.theme.GsTealAccent
import com.example.ui.theme.GsTealContainer
import com.example.ui.theme.GsTextPrimary
import com.example.ui.theme.GsTextSecondary
import com.example.ui.theme.GsWhite
import com.example.viewmodel.EditorUiState
import kotlin.math.roundToInt

data class ToolDescriptor(
  val tool: EditorTool,
  val label: String,
  val icon: ImageVector,
  val testTag: String,
)

fun buildEditorToolsList(strings: AppStrings): List<ToolDescriptor> =
  listOf(
    ToolDescriptor(EditorTool.SELECT_EDIT, strings.modeSelectEdit, Icons.Default.Edit, "editor_tool_select_edit"),
    ToolDescriptor(EditorTool.ADD_TEXT, strings.modeAddText, Icons.Default.TextFields, "editor_tool_add_text"),
    ToolDescriptor(EditorTool.WHITEOUT, strings.modeWhiteout, Icons.Default.Rectangle, "editor_tool_whiteout"),
    ToolDescriptor(EditorTool.SIGNATURE, strings.modeSignature, Icons.Default.Draw, "editor_tool_signature"),
    ToolDescriptor(EditorTool.IMAGE, strings.modeImage, Icons.Default.AddPhotoAlternate, "editor_tool_image"),
    ToolDescriptor(EditorTool.SHAPE_RECT, "Rect", Icons.Default.CropSquare, "editor_tool_rect"),
    ToolDescriptor(EditorTool.SHAPE_CIRCLE, "Circle", Icons.Default.Circle, "editor_tool_circle"),
    ToolDescriptor(EditorTool.SHAPE_LINE, "Line", Icons.Default.HorizontalRule, "editor_tool_line"),
    ToolDescriptor(EditorTool.SHAPE_ARROW, "Arrow", Icons.Default.NorthEast, "editor_tool_arrow"),
    ToolDescriptor(EditorTool.HIGHLIGHT, strings.modeHighlight, Icons.Default.BorderColor, "editor_tool_highlight"),
    ToolDescriptor(EditorTool.FREEHAND, strings.modeFreehand, Icons.Default.Brush, "editor_tool_freehand"),
    ToolDescriptor(EditorTool.STAMP, strings.modeStamp, Icons.Default.Approval, "editor_tool_stamp"),
    ToolDescriptor(EditorTool.OCR, strings.modeOcr, Icons.Default.DocumentScanner, "editor_tool_ocr"),
    ToolDescriptor(EditorTool.PAGES, strings.modePages, Icons.Default.ViewAgenda, "editor_tool_pages"),
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorToolsPalettePanel(
  strings: AppStrings,
  activeTool: EditorTool,
  onToolSelected: (EditorTool) -> Unit,
  onRunOcr: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tools = buildEditorToolsList(strings)
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(strings.tools, style = MaterialTheme.typography.titleMedium, color = GsTextPrimary)
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      tools.forEach { item ->
        val selected = item.tool == activeTool
        Surface(
          color = if (selected) GsBlueContainer else GsSurfaceSubtle,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, if (selected) GsBluePrimary else GsBorder),
          modifier =
            Modifier.width(108.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable { onToolSelected(item.tool) }
              .testTag(item.testTag),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
              color = if (selected) GsBluePrimary else GsTextPrimary,
              maxLines = 1,
            )
          }
        }
      }
    }

    HorizontalDivider(color = GsBorder)

    Surface(
      color = GsTealContainer,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.toolOcrPdf, style = MaterialTheme.typography.labelLarge, color = GsTealAccent)
        Button(
          onClick = onRunOcr,
          modifier = Modifier.fillMaxWidth().testTag("sidebar_run_ocr_button"),
        ) {
          Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(strings.runOcrPage)
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorStyleInspector(
  strings: AppStrings,
  state: EditorUiState,
  onSelectFont: (PdfFontOption) -> Unit,
  onChangeFontSize: (Float) -> Unit,
  onSelectColor: (Long) -> Unit,
  onSelectAlign: (TextAlignOption) -> Unit,
  onChangeStrokeWidth: (Float) -> Unit,
  onToggleShapeFilled: () -> Unit,
  onSelectStampLabel: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(strings.fontLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      PdfFontOption.entries.forEach { font ->
        FilterChip(
          selected = state.activeFont == font,
          onClick = { onSelectFont(font) },
          label = { Text(font.displayName) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(strings.fontSizeLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
      Text("${state.activeFontSizePt.roundToInt()} pt", style = MaterialTheme.typography.labelLarge, color = GsBluePrimary)
    }
    Slider(
      value = state.activeFontSizePt,
      onValueChange = onChangeFontSize,
      valueRange = 8f..56f,
    )

    Text(strings.alignmentLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextAlignOption.entries.forEach { align ->
        val icon =
          when (align) {
            TextAlignOption.LEFT -> Icons.AutoMirrored.Filled.FormatAlignLeft
            TextAlignOption.CENTER -> Icons.Default.FormatAlignCenter
            TextAlignOption.RIGHT -> Icons.AutoMirrored.Filled.FormatAlignRight
          }
        FilterChip(
          selected = state.activeAlign == align,
          onClick = { onSelectAlign(align) },
          label = { Icon(icon, contentDescription = align.displayName, modifier = Modifier.size(18.dp)) },
        )
      }
    }

    Text(strings.colorLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      EditorColorPalette.forEach { (name, argb) ->
        val selected = state.activeColorArgb == argb
        Box(
          modifier =
            Modifier.size(32.dp)
              .clip(CircleShape)
              .background(argb.toComposeColor())
              .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) GsBluePrimary else GsBorder,
                shape = CircleShape,
              )
              .clickable { onSelectColor(argb) },
          contentAlignment = Alignment.Center,
        ) {
          if (selected) {
            val checkTint = if (argb == 0xFFFFFFFF) GsBluePrimary else GsWhite
            Icon(Icons.Default.Check, contentDescription = name, tint = checkTint, modifier = Modifier.size(16.dp))
          }
        }
      }
    }

    HorizontalDivider(color = GsBorder)

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(strings.strokeWidthLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
      Text("${String.format("%.1f", state.activeStrokeWidthPt)} pt", color = GsBluePrimary)
    }
    Slider(
      value = state.activeStrokeWidthPt,
      onValueChange = onChangeStrokeWidth,
      valueRange = 1f..10f,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Fill Shape", style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
      Switch(checked = state.activeShapeFilled, onCheckedChange = { onToggleShapeFilled() })
    }

    Text(strings.modeStamp, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("APPROVED", "CONFIDENTIAL", "DRAFT", "PAID").forEach { stamp ->
        FilterChip(
          selected = state.activeStampLabel == stamp,
          onClick = { onSelectStampLabel(stamp) },
          label = { Text(stamp) },
        )
      }
    }
  }
}

@Composable
fun PagesOrganizerPanel(
  strings: AppStrings,
  pages: List<PdfPageState>,
  currentPageIndex: Int,
  bitmaps: Map<String, Bitmap>,
  bitmapKeyProvider: (PdfPageState) -> String,
  onSelectPage: (Int) -> Unit,
  onRotateCw: (Int) -> Unit,
  onRotateCcw: (Int) -> Unit,
  onMovePage: (Int, Int) -> Unit,
  onDeletePage: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text("${strings.pages} (${pages.size})", style = MaterialTheme.typography.titleMedium, color = GsTextPrimary)
    pages.forEachIndexed { idx, page ->
      val selected = idx == currentPageIndex
      val bmp = bitmaps[bitmapKeyProvider(page)]
      Surface(
        color = if (selected) GsBlueContainer else GsWhite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) GsBluePrimary else GsBorder),
        modifier = Modifier.fillMaxWidth().clickable { onSelectPage(idx) },
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, GsBorder),
              modifier = Modifier.size(width = 54.dp, height = 72.dp),
            ) {
              if (bmp != null) {
                Image(
                  bitmap = bmp.asImageBitmap(),
                  contentDescription = "Page ${idx + 1}",
                  contentScale = ContentScale.Fit,
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = String.format(strings.pageOf, idx + 1, pages.size),
                style = MaterialTheme.typography.titleMedium,
                color = GsTextPrimary,
              )
              Text(
                text = "${page.visualWidthPt.roundToInt()} × ${page.visualHeightPt.roundToInt()} pt • ${page.totalRotation}°",
                style = MaterialTheme.typography.labelMedium,
                color = GsTextSecondary,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            IconButton(onClick = { onRotateCcw(idx) }) {
              Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = strings.rotateCcw, tint = GsBluePrimary)
            }
            IconButton(onClick = { onRotateCw(idx) }) {
              Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = strings.rotateCw, tint = GsBluePrimary)
            }
            IconButton(onClick = { onMovePage(idx, idx - 1) }, enabled = idx > 0) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.moveEarlier)
            }
            IconButton(onClick = { onMovePage(idx, idx + 1) }, enabled = idx < pages.lastIndex) {
              Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = strings.moveLater)
            }
            IconButton(
              onClick = { onDeletePage(idx) },
              enabled = pages.size > 1,
            ) {
              Icon(
                Icons.Default.Delete,
                contentDescription = strings.deletePage,
                tint = if (pages.size > 1) GsDanger else GsTextSecondary.copy(alpha = 0.4f),
              )
            }
          }
        }
      }
    }
  }
}
