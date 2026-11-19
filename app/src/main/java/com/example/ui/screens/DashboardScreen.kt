package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.ui.components.GsFooter
import com.example.ui.components.GsTopBrandBar
import com.example.ui.theme.GsBlueBright
import com.example.ui.theme.GsBlueContainer
import com.example.ui.theme.GsBlueHover
import com.example.ui.theme.GsBluePrimary
import com.example.ui.theme.GsBorder
import com.example.ui.theme.GsCanvasBg
import com.example.ui.theme.GsPurpleAccent
import com.example.ui.theme.GsPurpleContainer
import com.example.ui.theme.GsSkyAccent
import com.example.ui.theme.GsSkyContainer
import com.example.ui.theme.GsSuccess
import com.example.ui.theme.GsSuccessContainer
import com.example.ui.theme.GsTealAccent
import com.example.ui.theme.GsTealContainer
import com.example.ui.theme.GsTextPrimary
import com.example.ui.theme.GsTextSecondary
import com.example.ui.theme.GsWarning
import com.example.ui.theme.GsWarningContainer
import com.example.ui.theme.GsWhite
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.formatBytes

enum class DashboardToolAction {
  EDIT_PDF,
  MERGE_PDF,
  SPLIT_PDF,
  COMPRESS_PDF,
  RESIZE_PDF,
  PDF_TO_IMAGES,
  IMAGES_TO_PDF,
  ORGANIZE_PAGES,
  OCR_PDF,
  PRINT_PDF,
}

private data class ToolCardModel(
  val action: DashboardToolAction,
  val title: String,
  val description: String,
  val icon: ImageVector,
  val accent: Color,
  val iconBg: Color,
  val testTag: String,
)

@Composable
fun DashboardScreen(
  state: EditorUiState,
  strings: AppStrings,
  onToggleLanguage: () -> Unit,
  onOpenAbout: () -> Unit,
  onUploadPdfForEditor: () -> Unit,
  onResumeEditor: () -> Unit,
  onLaunchTool: (DashboardToolAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().background(GsCanvasBg),
  ) {
    GsTopBrandBar(
      strings = strings,
      language = state.language,
      onToggleLanguage = onToggleLanguage,
      onOpenAbout = onOpenAbout,
    )

    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 1080.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        // Sejda-inspired Hero Banner
        Surface(
          shape = RoundedCornerShape(22.dp),
          shadowElevation = 4.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .background(
                  Brush.linearGradient(
                    colors = listOf(GsBluePrimary, GsBlueBright, Color(0xFF0284C7))
                  )
                )
                .padding(22.dp)
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Surface(
                color = GsWhite.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = GsWhite,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = strings.privacyBadge,
                    style = MaterialTheme.typography.labelMedium,
                    color = GsWhite,
                  )
                }
              }

              Text(
                text = strings.brandTitle,
                style = MaterialTheme.typography.headlineLarge,
                color = GsWhite,
              )
              Text(
                text = strings.brandSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = GsWhite.copy(alpha = 0.90f),
              )

              Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Button(
                  onClick = onUploadPdfForEditor,
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = GsWhite,
                      contentColor = GsBlueHover,
                    ),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                  modifier = Modifier.testTag("hero_upload_pdf_button"),
                ) {
                  Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text(strings.openPdfCta, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                  onClick = { onLaunchTool(DashboardToolAction.IMAGES_TO_PDF) },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = GsWhite),
                  border = BorderStroke(1.5.dp, GsWhite.copy(alpha = 0.7f)),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                  modifier = Modifier.testTag("hero_images_to_pdf_button"),
                ) {
                  Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text(strings.createFromImagesCta, fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }

        // Active Document Resume Card
        if (state.sourcePdfFile != null && state.pages.isNotEmpty()) {
          Surface(
            color = GsWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, GsBluePrimary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().clickable { onResumeEditor() },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
              ) {
                Surface(
                  color = GsBlueContainer,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.size(44.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = GsBluePrimary)
                  }
                }
                Column {
                  Text(strings.resumeSession, style = MaterialTheme.typography.labelMedium, color = GsBluePrimary)
                  Text(
                    text = "${state.documentName} • ${state.pages.size} p • ${formatBytes(state.documentSizeBytes)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = GsTextPrimary,
                  )
                }
              }
              Button(
                onClick = onResumeEditor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("resume_editor_button"),
              ) {
                Text(strings.resumeAction)
              }
            }
          }
        }

        // Categorized Tools Grid
        val editTools =
          listOf(
            ToolCardModel(
              DashboardToolAction.EDIT_PDF,
              strings.toolEditPdf,
              strings.toolEditPdfDesc,
              Icons.Default.EditNote,
              GsBluePrimary,
              GsBlueContainer,
              "tool_edit_pdf",
            ),
            ToolCardModel(
              DashboardToolAction.OCR_PDF,
              strings.toolOcrPdf,
              strings.toolOcrPdfDesc,
              Icons.Default.DocumentScanner,
              GsTealAccent,
              GsTealContainer,
              "tool_ocr_pdf",
            ),
            ToolCardModel(
              DashboardToolAction.ORGANIZE_PAGES,
              strings.toolOrganizePages,
              strings.toolOrganizePagesDesc,
              Icons.Default.ViewAgenda,
              GsPurpleAccent,
              GsPurpleContainer,
              "tool_organize_pages",
            ),
          )

        val organizeTools =
          listOf(
            ToolCardModel(
              DashboardToolAction.MERGE_PDF,
              strings.toolMergePdf,
              strings.toolMergePdfDesc,
              Icons.AutoMirrored.Filled.MergeType,
              GsBlueBright,
              GsBlueContainer,
              "tool_merge_pdf",
            ),
            ToolCardModel(
              DashboardToolAction.SPLIT_PDF,
              strings.toolSplitPdf,
              strings.toolSplitPdfDesc,
              Icons.AutoMirrored.Filled.CallSplit,
              GsSkyAccent,
              GsSkyContainer,
              "tool_split_pdf",
            ),
            ToolCardModel(
              DashboardToolAction.COMPRESS_PDF,
              strings.toolCompressPdf,
              strings.toolCompressPdfDesc,
              Icons.Default.Compress,
              GsSuccess,
              GsSuccessContainer,
              "tool_compress_pdf",
            ),
            ToolCardModel(
              DashboardToolAction.RESIZE_PDF,
              strings.toolResizePdf,
              strings.toolResizePdfDesc,
              Icons.Default.AspectRatio,
              GsWarning,
              GsWarningContainer,
              "tool_resize_pdf",
            ),
          )

        val convertTools =
          listOf(
            ToolCardModel(
              DashboardToolAction.PDF_TO_IMAGES,
              strings.toolPdfToImages,
              strings.toolPdfToImagesDesc,
              Icons.Default.Image,
              GsPurpleAccent,
              GsPurpleContainer,
              "tool_pdf_to_images",
            ),
            ToolCardModel(
              DashboardToolAction.IMAGES_TO_PDF,
              strings.toolImagesToPdf,
              strings.toolImagesToPdfDesc,
              Icons.Default.PhotoLibrary,
              GsTealAccent,
              GsTealContainer,
              "tool_images_to_pdf",
            ),
            ToolCardModel(
              DashboardToolAction.PRINT_PDF,
              strings.toolPrintPdf,
              strings.toolPrintPdfDesc,
              Icons.Default.Print,
              GsBluePrimary,
              GsBlueContainer,
              "tool_print_pdf",
            ),
          )

        ToolSection(strings.toolsSectionEdit, editTools, onLaunchTool)
        ToolSection(strings.toolsSectionOrganize, organizeTools, onLaunchTool)
        ToolSection(strings.toolsSectionConvert, convertTools, onLaunchTool)

        // About & Limitations Summary Card
        Surface(
          color = GsWhite,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, GsBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = strings.aboutTitle,
                style = MaterialTheme.typography.titleLarge,
                color = GsBluePrimary,
              )
              Surface(color = GsBlueContainer, shape = RoundedCornerShape(8.dp)) {
                Text(
                  text = strings.ownerLine,
                  style = MaterialTheme.typography.labelLarge,
                  color = GsBluePrimary,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp).testTag("about_section_owner_name"),
                )
              }
            }
            Text(
              text = strings.aboutDescription,
              style = MaterialTheme.typography.bodyMedium,
              color = GsTextSecondary,
            )
            Text(
              text = strings.limitationsTitle,
              style = MaterialTheme.typography.titleMedium,
              color = GsTextPrimary,
              modifier = Modifier.padding(top = 4.dp),
            )
            Text(
              text = strings.limitationsBody,
              style = MaterialTheme.typography.bodyMedium,
              color = GsTextSecondary,
            )
          }
        }

        GsFooter(strings = strings)
        Spacer(Modifier.height(12.dp))
      }
    }
  }
}

@Composable
private fun ToolSection(
  sectionTitle: String,
  tools: List<ToolCardModel>,
  onLaunchTool: (DashboardToolAction) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = sectionTitle,
      style = MaterialTheme.typography.titleLarge,
      color = GsTextPrimary,
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val columns =
        when {
          maxWidth >= 820.dp -> 3
          maxWidth >= 520.dp -> 2
          else -> 1
        }
      val rows = tools.chunked(columns)
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in rows) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            for (item in row) {
              ToolCard(
                item = item,
                onClick = { onLaunchTool(item.action) },
                modifier = Modifier.weight(1f),
              )
            }
            repeat(columns - row.size) {
              Spacer(Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ToolCard(
  item: ToolCardModel,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).testTag(item.testTag),
    color = GsWhite,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, GsBorder),
    shadowElevation = 1.dp,
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        color = item.iconBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(46.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = item.accent,
            modifier = Modifier.size(24.dp),
          )
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleMedium,
          color = GsTextPrimary,
        )
        Text(
          text = item.description,
          style = MaterialTheme.typography.bodyMedium,
          color = GsTextSecondary,
        )
      }
    }
  }
}
