package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DetectedTextBlock
import com.example.model.EditorTool
import com.example.model.PdfFontOption
import com.example.model.PdfOverlayElement
import com.example.model.PdfPageState
import com.example.model.ShapeKind
import com.example.model.TextAlignOption
import com.example.model.toComposeColor
import com.example.ui.theme.GsBluePrimary
import com.example.ui.theme.GsBorder
import com.example.ui.theme.GsSkyAccent
import com.example.ui.theme.GsWhite
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NotoSansBengaliFamily
import com.example.ui.theme.PlusJakartaSansFamily
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun PdfPageCanvas(
  page: PdfPageState,
  bitmap: Bitmap?,
  zoomScale: Float,
  activeTool: EditorTool,
  elements: List<PdfOverlayElement>,
  detectedTextBlocks: List<DetectedTextBlock>,
  hiddenDetectedBlockIds: Set<String>,
  selectedElementId: String?,
  activeColorArgb: Long,
  activeStrokeWidthPt: Float,
  onTapPlaceText: (Float, Float) -> Unit,
  onTapWhiteout: (Float, Float) -> Unit,
  onTapShape: (ShapeKind, Float, Float) -> Unit,
  onTapStamp: (Float, Float) -> Unit,
  onTapDetectedBlock: (DetectedTextBlock) -> Unit,
  onSelectElement: (String?) -> Unit,
  onDoubleTapTextElement: (PdfOverlayElement.TextElement) -> Unit,
  onBeginTransform: () -> Unit,
  onMoveOrResizeElement: (String, Float, Float, Float, Float) -> Unit,
  onFinishFreehand: (List<Pair<Float, Float>>) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val aspect = (page.visualHeightPt / page.visualWidthPt).coerceIn(0.4f, 2.5f)
  var activeStroke by remember(page.pageId) { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

  BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    val baseWidthDp = (maxWidth - 24.dp).coerceAtLeast(240.dp).coerceAtMost(760.dp)
    val pageWidthDp = baseWidthDp * zoomScale
    val pageHeightDp = pageWidthDp * aspect
    val pageWidthPx = with(density) { pageWidthDp.toPx() }.coerceAtLeast(1f)
    val pageHeightPx = with(density) { pageHeightDp.toPx() }.coerceAtLeast(1f)
    val ptToPxScale = pageWidthPx / page.visualWidthPt.coerceAtLeast(1f)

    Surface(
      modifier = Modifier.width(pageWidthDp).height(pageHeightDp),
      color = GsWhite,
      shadowElevation = 6.dp,
      shape = RoundedCornerShape(4.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, GsBorder),
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .pointerInput(activeTool, page.pageId, pageWidthPx, pageHeightPx) {
              if (activeTool == EditorTool.FREEHAND) {
                detectDragGestures(
                  onDragStart = { pos ->
                    activeStroke =
                      listOf(
                        (pos.x / pageWidthPx).coerceIn(0f, 1f) to
                          (pos.y / pageHeightPx).coerceIn(0f, 1f)
                      )
                  },
                  onDrag = { change, _ ->
                    change.consume()
                    val nx = (change.position.x / pageWidthPx).coerceIn(0f, 1f)
                    val ny = (change.position.y / pageHeightPx).coerceIn(0f, 1f)
                    activeStroke = activeStroke + (nx to ny)
                  },
                  onDragEnd = {
                    if (activeStroke.size >= 2) onFinishFreehand(activeStroke)
                    activeStroke = emptyList()
                  },
                )
              } else {
                detectTapGestures { offset ->
                  val xNorm = (offset.x / pageWidthPx).coerceIn(0.02f, 0.96f)
                  val yNorm = (offset.y / pageHeightPx).coerceIn(0.02f, 0.96f)
                  when (activeTool) {
                    EditorTool.ADD_TEXT -> onTapPlaceText(xNorm, yNorm)
                    EditorTool.WHITEOUT -> onTapWhiteout(xNorm, yNorm)
                    EditorTool.SHAPE_RECT -> onTapShape(ShapeKind.RECT, xNorm, yNorm)
                    EditorTool.SHAPE_CIRCLE -> onTapShape(ShapeKind.CIRCLE, xNorm, yNorm)
                    EditorTool.SHAPE_LINE -> onTapShape(ShapeKind.LINE, xNorm, yNorm)
                    EditorTool.SHAPE_ARROW -> onTapShape(ShapeKind.ARROW, xNorm, yNorm)
                    EditorTool.HIGHLIGHT -> onTapShape(ShapeKind.HIGHLIGHT, xNorm, yNorm)
                    EditorTool.STAMP -> onTapStamp(xNorm, yNorm)
                    else -> onSelectElement(null)
                  }
                }
              }
            }
      ) {
        if (bitmap != null) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "PDF Page",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GsBluePrimary)
          }
        }

        // Clickable detected existing text blocks when in SELECT_EDIT or OCR mode
        if (activeTool == EditorTool.SELECT_EDIT || activeTool == EditorTool.OCR) {
          detectedTextBlocks
            .filter { it.id !in hiddenDetectedBlockIds }
            .forEach { block ->
              val bx = (block.xNorm * pageWidthPx).roundToInt()
              val by = (block.yNorm * pageHeightPx).roundToInt()
              val bw = with(density) { (block.widthNorm * pageWidthPx).toDp() }
              val bh = with(density) { (block.heightNorm * pageHeightPx).toDp() }
              val highlightColor =
                if (block.fromOcr) Color(0xFF0D9488) else GsSkyAccent

              Box(
                modifier =
                  Modifier.offset { IntOffset(bx, by) }
                    .width(bw.coerceAtLeast(16.dp))
                    .height(bh.coerceAtLeast(14.dp))
                    .background(highlightColor.copy(alpha = 0.08f))
                    .border(1.dp, highlightColor.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                    .clickable { onTapDetectedBlock(block) }
              )
            }
        }

        // User-added & edited overlay elements
        elements.forEach { el ->
          val isSelected = el.id == selectedElementId
          val ex = (el.xNorm * pageWidthPx).roundToInt()
          val ey = (el.yNorm * pageHeightPx).roundToInt()
          val ewDp = with(density) { (el.widthNorm * pageWidthPx).toDp() }
          val ehDp = with(density) { (el.heightNorm * pageHeightPx).toDp() }

          Box(
            modifier =
              Modifier.offset { IntOffset(ex, ey) }
                .width(ewDp.coerceAtLeast(24.dp))
                .height(ehDp.coerceAtLeast(20.dp))
                .then(
                  if (isSelected) {
                    Modifier.border(2.dp, GsBluePrimary, RoundedCornerShape(4.dp))
                  } else {
                    Modifier
                  }
                )
                .pointerInput(el.id, activeTool) {
                  if (activeTool != EditorTool.FREEHAND) {
                    detectTapGestures(
                      onTap = { onSelectElement(el.id) },
                      onDoubleTap = {
                        if (el is PdfOverlayElement.TextElement) {
                          onDoubleTapTextElement(el)
                        }
                      },
                    )
                  }
                }
                .pointerInput(el.id, activeTool, pageWidthPx, pageHeightPx) {
                  if (activeTool != EditorTool.FREEHAND) {
                    detectDragGestures(
                      onDragStart = {
                        onSelectElement(el.id)
                        onBeginTransform()
                      },
                      onDrag = { change, dragAmount ->
                        change.consume()
                        onMoveOrResizeElement(
                          el.id,
                          dragAmount.x / pageWidthPx,
                          dragAmount.y / pageHeightPx,
                          0f,
                          0f,
                        )
                      },
                    )
                  }
                }
          ) {
            when (el) {
              is PdfOverlayElement.WhiteoutElement -> {
                Box(
                  modifier =
                    Modifier.fillMaxSize()
                      .background(el.colorArgb.toComposeColor())
                      .border(
                        width = if (isSelected) 0.dp else 0.5.dp,
                        color = if (isSelected) Color.Transparent else GsBorder,
                      )
                )
              }

              is PdfOverlayElement.TextElement -> {
                val fontFam =
                  when (el.font) {
                    PdfFontOption.BENGALI_UNICODE -> NotoSansBengaliFamily
                    PdfFontOption.MODERN_SANS -> PlusJakartaSansFamily
                    PdfFontOption.SERIF -> FontFamily.Serif
                    PdfFontOption.MONOSPACE -> JetBrainsMonoFamily
                  }
                val textAlign =
                  when (el.align) {
                    TextAlignOption.LEFT -> TextAlign.Start
                    TextAlignOption.CENTER -> TextAlign.Center
                    TextAlignOption.RIGHT -> TextAlign.End
                  }
                val scaledSp = with(density) { (el.fontSizePt * ptToPxScale).toSp() }
                Text(
                  text = el.text,
                  fontFamily = fontFam,
                  fontSize = scaledSp,
                  lineHeight = scaledSp * 1.25f,
                  color = el.colorArgb.toComposeColor(),
                  textAlign = textAlign,
                  modifier = Modifier.fillMaxSize().padding(1.dp),
                )
              }

              is PdfOverlayElement.ImageElement -> {
                val imgBmp =
                  remember(el.localImagePath) { BitmapFactory.decodeFile(el.localImagePath) }
                if (imgBmp != null) {
                  Image(
                    bitmap = imgBmp.asImageBitmap(),
                    contentDescription = if (el.isSignature) "Signature" else "Added Image",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                  )
                }
              }

              is PdfOverlayElement.ShapeElement -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                  val c = el.colorArgb.toComposeColor()
                  val strokePx = el.strokeWidthPt * ptToPxScale
                  when (el.kind) {
                    ShapeKind.RECT -> {
                      if (el.filled) {
                        drawRect(color = c, size = size)
                      } else {
                        drawRect(color = c, size = size, style = Stroke(width = strokePx))
                      }
                    }
                    ShapeKind.CIRCLE -> {
                      if (el.filled) {
                        drawOval(color = c, size = size)
                      } else {
                        drawOval(color = c, size = size, style = Stroke(width = strokePx))
                      }
                    }
                    ShapeKind.LINE -> {
                      drawLine(
                        color = c,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round,
                      )
                    }
                    ShapeKind.ARROW -> {
                      val start = Offset(0f, 0f)
                      val end = Offset(size.width, size.height)
                      drawLine(
                        color = c,
                        start = start,
                        end = end,
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round,
                      )
                      val angle = atan2(end.y - start.y, end.x - start.x)
                      val headLen = (strokePx * 4f).coerceIn(14f, 36f)
                      val a1 = angle + Math.toRadians(150.0).toFloat()
                      val a2 = angle - Math.toRadians(150.0).toFloat()
                      val head =
                        Path().apply {
                          moveTo(end.x, end.y)
                          lineTo(end.x + headLen * cos(a1), end.y + headLen * sin(a1))
                          lineTo(end.x + headLen * cos(a2), end.y + headLen * sin(a2))
                          close()
                        }
                      drawPath(head, color = c)
                    }
                    ShapeKind.HIGHLIGHT -> {
                      drawRect(color = c.copy(alpha = 0.35f), size = size)
                    }
                  }
                }
              }

              is PdfOverlayElement.FreehandElement -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                  if (el.pointsNorm.size >= 2) {
                    val strokePx = el.strokeWidthPt * ptToPxScale
                    val path =
                      Path().apply {
                        val first = el.pointsNorm.first()
                        val fx = ((first.first - el.xNorm) / el.widthNorm.coerceAtLeast(0.01f)) * size.width
                        val fy = ((first.second - el.yNorm) / el.heightNorm.coerceAtLeast(0.01f)) * size.height
                        moveTo(fx, fy)
                        el.pointsNorm.drop(1).forEach { (px, py) ->
                          val lx = ((px - el.xNorm) / el.widthNorm.coerceAtLeast(0.01f)) * size.width
                          val ly = ((py - el.yNorm) / el.heightNorm.coerceAtLeast(0.01f)) * size.height
                          lineTo(lx, ly)
                        }
                      }
                    drawPath(
                      path = path,
                      color = el.colorArgb.toComposeColor(),
                      style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                  }
                }
              }

              is PdfOverlayElement.StampElement -> {
                Box(
                  modifier =
                    Modifier.fillMaxSize()
                      .border(2.dp, el.colorArgb.toComposeColor(), RoundedCornerShape(4.dp))
                      .padding(horizontal = 4.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = el.label,
                    color = el.colorArgb.toComposeColor(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                  )
                }
              }
            }

            // Bottom-right resize handle when selected
            if (isSelected && el !is PdfOverlayElement.FreehandElement) {
              Box(
                modifier =
                  Modifier.align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(GsBluePrimary)
                    .border(2.dp, GsWhite, CircleShape)
                    .pointerInput(el.id, pageWidthPx, pageHeightPx) {
                      detectDragGestures(
                        onDragStart = { onBeginTransform() },
                        onDrag = { change, dragAmount ->
                          change.consume()
                          onMoveOrResizeElement(
                            el.id,
                            0f,
                            0f,
                            dragAmount.x / pageWidthPx,
                            dragAmount.y / pageHeightPx,
                          )
                        },
                      )
                    }
              )
            }
          }
        }

        // Live in-progress freehand stroke preview
        if (activeStroke.size >= 2) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val path =
              Path().apply {
                moveTo(activeStroke.first().first * size.width, activeStroke.first().second * size.height)
                activeStroke.drop(1).forEach { (nx, ny) ->
                  lineTo(nx * size.width, ny * size.height)
                }
              }
            drawPath(
              path = path,
              color = activeColorArgb.toComposeColor(),
              style =
                Stroke(
                  width = activeStrokeWidthPt * ptToPxScale,
                  cap = StrokeCap.Round,
                  join = StrokeJoin.Round,
                ),
            )
          }
        }
      }
    }
  }
}
