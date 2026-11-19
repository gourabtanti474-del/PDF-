package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.ui.theme.GsBlueContainer
import com.example.ui.theme.GsBluePrimary
import com.example.ui.theme.GsBorder
import com.example.ui.theme.GsDanger
import com.example.ui.theme.GsSuccess
import com.example.ui.theme.GsSuccessContainer
import com.example.ui.theme.GsSurfaceSubtle
import com.example.ui.theme.GsTextPrimary
import com.example.ui.theme.GsTextSecondary
import com.example.ui.theme.GsWhite

@Composable
fun GsFooter(
  strings: AppStrings,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = GsWhite,
    tonalElevation = 1.dp,
    shape = RoundedCornerShape(16.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, GsBorder),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = strings.brandTitle,
        style = MaterialTheme.typography.titleMedium,
        color = GsBluePrimary,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = strings.ownerLine,
        style = MaterialTheme.typography.bodyMedium,
        color = GsTextPrimary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag("footer_owner_name"),
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Lock,
          contentDescription = null,
          tint = GsSuccess,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = strings.footerPrivacyNote,
          style = MaterialTheme.typography.labelMedium,
          color = GsTextSecondary,
        )
      }
    }
  }
}

@Composable
fun GsTopBrandBar(
  strings: AppStrings,
  language: AppLanguage,
  onToggleLanguage: () -> Unit,
  onOpenAbout: () -> Unit,
) {
  Surface(
    color = GsWhite,
    shadowElevation = 2.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = GsBluePrimary,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(38.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Description,
            contentDescription = strings.brandTitle,
            tint = GsWhite,
            modifier = Modifier.size(22.dp),
          )
        }
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = strings.brandTitle,
          style = MaterialTheme.typography.titleMedium,
          color = GsTextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = strings.ownerLine,
          style = MaterialTheme.typography.labelMedium,
          color = GsBluePrimary,
          maxLines = 1,
        )
      }
      OutlinedButton(
        onClick = onToggleLanguage,
        modifier = Modifier.testTag("language_toggle_button"),
        shape = RoundedCornerShape(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Translate,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = language.toggleLabel, style = MaterialTheme.typography.labelLarge)
      }
      Spacer(Modifier.width(4.dp))
      IconButton(
        onClick = onOpenAbout,
        modifier = Modifier.testTag("about_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Info,
          contentDescription = strings.aboutTitle,
          tint = GsBluePrimary,
        )
      }
    }
  }
}

@Composable
fun AboutAndLimitationsDialog(
  strings: AppStrings,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(strings.brandTitle, style = MaterialTheme.typography.titleLarge, color = GsBluePrimary)
        Text(
          text = strings.ownerLine,
          style = MaterialTheme.typography.titleMedium,
          color = GsTextPrimary,
          modifier = Modifier.testTag("about_owner_name"),
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(strings.aboutDescription, style = MaterialTheme.typography.bodyMedium, color = GsTextSecondary)
        Surface(
          color = GsSuccessContainer,
          shape = RoundedCornerShape(12.dp),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings.privacyTitle, style = MaterialTheme.typography.labelLarge, color = GsSuccess)
            Text(strings.privacyBody, style = MaterialTheme.typography.bodyMedium, color = GsTextPrimary)
          }
        }
        Surface(
          color = GsBlueContainer,
          shape = RoundedCornerShape(12.dp),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings.limitationsTitle, style = MaterialTheme.typography.labelLarge, color = GsBluePrimary)
            Text(strings.limitationsBody, style = MaterialTheme.typography.bodyMedium, color = GsTextPrimary)
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss, modifier = Modifier.testTag("about_close_button")) {
        Text(strings.close)
      }
    },
  )
}

@Composable
fun TextEditDialog(
  strings: AppStrings,
  isReplacingDetectedBlock: Boolean,
  originalText: String?,
  initialText: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
  onDeleteInPlace: (() -> Unit)? = null,
) {
  var value by remember(initialText) { mutableStateOf(initialText) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (isReplacingDetectedBlock) strings.replaceExistingTextTitle else strings.addNewTextTitle,
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!originalText.isNullOrBlank()) {
          Surface(
            color = GsSurfaceSubtle,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GsBorder),
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
              Text(strings.originalTextLabel, style = MaterialTheme.typography.labelMedium, color = GsTextSecondary)
              Spacer(Modifier.height(2.dp))
              Text(originalText, style = MaterialTheme.typography.bodyMedium, color = GsTextPrimary)
            }
          }
        }
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          label = { Text(strings.newTextLabel) },
          modifier = Modifier.fillMaxWidth().testTag("text_editor_input"),
          minLines = 2,
          maxLines = 6,
        )
        if (onDeleteInPlace != null) {
          OutlinedButton(
            onClick = {
              onDeleteInPlace()
              onDismiss()
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GsDanger),
            modifier = Modifier.fillMaxWidth().testTag("delete_detected_text_button"),
          ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.deleteTextInPlace)
          }
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancel) }
    },
    confirmButton = {
      Button(
        onClick = { if (value.isNotBlank()) onConfirm(value) },
        enabled = value.isNotBlank(),
        modifier = Modifier.testTag("confirm_text_edit_button"),
      ) {
        Text(strings.apply)
      }
    },
  )
}

@Composable
fun SignaturePadDialog(
  strings: AppStrings,
  onDismiss: () -> Unit,
  onApplySignature: (Bitmap) -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var typedName by remember { mutableStateOf("") }
  var inkColor by remember { mutableStateOf(Color(0xFF0F172A)) }
  val strokes = remember { mutableStateListOf<List<Offset>>() }
  var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
  var canvasSize by remember { mutableStateOf(IntSize(600, 260)) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = GsWhite,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.signatureTitle, style = MaterialTheme.typography.titleLarge, color = GsTextPrimary)

        TabRow(selectedTabIndex = selectedTab) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text(strings.drawSignatureTab) },
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text(strings.typeSignatureTab) },
          )
        }

        // Ink color selector
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(strings.colorLabel, style = MaterialTheme.typography.labelLarge, color = GsTextSecondary)
          listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFFDC2626)).forEach { c ->
            Box(
              modifier =
                Modifier.size(28.dp)
                  .clip(CircleShape)
                  .background(c)
                  .border(
                    width = if (inkColor == c) 3.dp else 1.dp,
                    color = if (inkColor == c) GsBluePrimary else GsBorder,
                    shape = CircleShape,
                  )
                  .clickable { inkColor = c },
              contentAlignment = Alignment.Center,
            ) {
              if (inkColor == c) {
                Icon(Icons.Default.Check, contentDescription = null, tint = GsWhite, modifier = Modifier.size(16.dp))
              }
            }
          }
        }

        if (selectedTab == 0) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GsSurfaceSubtle)
                .border(1.5.dp, GsBorder, RoundedCornerShape(12.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                  detectDragGestures(
                    onDragStart = { start -> activeStroke = listOf(start) },
                    onDrag = { change, _ ->
                      change.consume()
                      activeStroke = activeStroke + change.position
                    },
                    onDragEnd = {
                      if (activeStroke.size > 1) strokes.add(activeStroke)
                      activeStroke = emptyList()
                    },
                  )
                },
          ) {
            if (strokes.isEmpty() && activeStroke.isEmpty()) {
              Text(
                text = strings.drawSignatureHint,
                color = GsTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
              )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
              val all = strokes + listOf(activeStroke)
              for (pts in all) {
                if (pts.size < 2) continue
                val p = Path().apply {
                  moveTo(pts.first().x, pts.first().y)
                  pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                  path = p,
                  color = inkColor,
                  style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
              }
            }
          }
        } else {
          OutlinedTextField(
            value = typedName,
            onValueChange = { typedName = it },
            label = { Text(strings.signatureTitle) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("typed_signature_input"),
          )
          Surface(
            color = GsSurfaceSubtle,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GsBorder),
            modifier = Modifier.fillMaxWidth().height(90.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = typedName.ifBlank { "Gopal Tanti" },
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = inkColor,
              )
            }
          }
        }

        HorizontalDivider(color = GsBorder)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          TextButton(
            onClick = {
              strokes.clear()
              activeStroke = emptyList()
              typedName = ""
            },
          ) {
            Text(strings.clear)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
            Button(
              onClick = {
                val bmp =
                  if (selectedTab == 0) {
                    renderStrokesToBitmap(strokes, canvasSize, inkColor)
                  } else {
                    renderTypedSignatureBitmap(typedName, inkColor)
                  }
                if (bmp != null) onApplySignature(bmp)
              },
              enabled = (selectedTab == 0 && strokes.isNotEmpty()) || (selectedTab == 1 && typedName.isNotBlank()),
              modifier = Modifier.testTag("apply_signature_button"),
            ) {
              Text(strings.applySignature)
            }
          }
        }
      }
    }
  }
}

private fun renderStrokesToBitmap(
  strokes: List<List<Offset>>,
  canvasSize: IntSize,
  inkColor: Color,
): Bitmap? {
  if (strokes.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return null
  val w = 800
  val h = ((canvasSize.height.toFloat() / canvasSize.width.toFloat()) * w).toInt().coerceIn(220, 500)
  val sx = w.toFloat() / canvasSize.width.toFloat()
  val sy = h.toFloat() / canvasSize.height.toFloat()

  val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
  val canvas = AndroidCanvas(bmp)
  val paint =
    AndroidPaint().apply {
      isAntiAlias = true
      style = AndroidPaint.Style.STROKE
      strokeWidth = 9f
      strokeCap = AndroidPaint.Cap.ROUND
      strokeJoin = AndroidPaint.Join.ROUND
      color = android.graphics.Color.argb(
        (inkColor.alpha * 255).toInt(),
        (inkColor.red * 255).toInt(),
        (inkColor.green * 255).toInt(),
        (inkColor.blue * 255).toInt(),
      )
    }
  for (pts in strokes) {
    if (pts.size < 2) continue
    val path = AndroidPath()
    path.moveTo(pts.first().x * sx, pts.first().y * sy)
    for (pt in pts.drop(1)) path.lineTo(pt.x * sx, pt.y * sy)
    canvas.drawPath(path, paint)
  }
  return bmp
}

private fun renderTypedSignatureBitmap(text: String, inkColor: Color): Bitmap? {
  if (text.isBlank()) return null
  val bmp = Bitmap.createBitmap(800, 240, Bitmap.Config.ARGB_8888)
  val canvas = AndroidCanvas(bmp)
  val paint =
    AndroidPaint().apply {
      isAntiAlias = true
      textSize = 92f
      typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
      textAlign = AndroidPaint.Align.CENTER
      color = android.graphics.Color.argb(
        (inkColor.alpha * 255).toInt(),
        (inkColor.red * 255).toInt(),
        (inkColor.green * 255).toInt(),
        (inkColor.blue * 255).toInt(),
      )
    }
  canvas.drawText(text.trim(), 400f, 150f, paint)
  return bmp
}
