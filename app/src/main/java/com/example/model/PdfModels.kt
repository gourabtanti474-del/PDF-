package com.example.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class EditorTool {
  SELECT_EDIT,
  ADD_TEXT,
  WHITEOUT,
  SIGNATURE,
  IMAGE,
  SHAPE_RECT,
  SHAPE_CIRCLE,
  SHAPE_LINE,
  SHAPE_ARROW,
  HIGHLIGHT,
  FREEHAND,
  STAMP,
  OCR,
  PAGES,
}

enum class PdfFontOption(val displayName: String) {
  BENGALI_UNICODE("Noto Sans Bengali (বাংলা + EN)"),
  MODERN_SANS("Plus Jakarta Sans"),
  SERIF("Serif (Times)"),
  MONOSPACE("JetBrains Mono"),
}

enum class TextAlignOption(val displayName: String) {
  LEFT("Left"),
  CENTER("Center"),
  RIGHT("Right"),
}

enum class ShapeKind {
  RECT,
  CIRCLE,
  LINE,
  ARROW,
  HIGHLIGHT,
}

enum class PageSizePreset(val label: String, val widthPt: Float, val heightPt: Float) {
  A4("A4 (210 × 297 mm)", 595.28f, 841.89f),
  LETTER("US Letter (8.5 × 11 in)", 612f, 792f),
  LEGAL("Legal (8.5 × 14 in)", 612f, 1008f),
  A5("A5 (148 × 210 mm)", 419.53f, 595.28f),
  A3("A3 (297 × 420 mm)", 841.89f, 1190.55f),
}

enum class CompressionLevel(val jpegQuality: Int, val maxImageDim: Int) {
  LOW(85, 2200),
  MEDIUM(62, 1600),
  HIGH(38, 1100),
}

enum class ImageExportFormat(val extension: String, val mimeType: String) {
  JPG("jpg", "image/jpeg"),
  PNG("png", "image/png"),
}

data class PdfPageState(
  val pageId: String = UUID.randomUUID().toString(),
  val sourcePageIndex: Int,
  val baseWidthPt: Float,
  val baseHeightPt: Float,
  val baseRotation: Int,
  val extraRotation: Int = 0,
) {
  val totalRotation: Int
    get() = ((baseRotation + extraRotation) % 360 + 360) % 360

  val visualWidthPt: Float
    get() = if (totalRotation == 90 || totalRotation == 270) baseHeightPt else baseWidthPt

  val visualHeightPt: Float
    get() = if (totalRotation == 90 || totalRotation == 270) baseWidthPt else baseHeightPt
}

data class DetectedTextBlock(
  val id: String = UUID.randomUUID().toString(),
  val pageId: String,
  val text: String,
  val xNorm: Float,
  val yNorm: Float,
  val widthNorm: Float,
  val heightNorm: Float,
  val approxFontSizePt: Float,
  val fromOcr: Boolean = false,
)

sealed interface PdfOverlayElement {
  val id: String
  val pageId: String
  val xNorm: Float
  val yNorm: Float
  val widthNorm: Float
  val heightNorm: Float

  data class TextElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageId: String,
    override val xNorm: Float,
    override val yNorm: Float,
    override val widthNorm: Float,
    override val heightNorm: Float,
    val text: String,
    val font: PdfFontOption = PdfFontOption.BENGALI_UNICODE,
    val fontSizePt: Float = 14f,
    val colorArgb: Long = 0xFF0F172A,
    val align: TextAlignOption = TextAlignOption.LEFT,
  ) : PdfOverlayElement

  data class WhiteoutElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageId: String,
    override val xNorm: Float,
    override val yNorm: Float,
    override val widthNorm: Float,
    override val heightNorm: Float,
    val colorArgb: Long = 0xFFFFFFFF,
  ) : PdfOverlayElement

  data class ImageElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageId: String,
    override val xNorm: Float,
    override val yNorm: Float,
    override val widthNorm: Float,
    override val heightNorm: Float,
    val localImagePath: String,
    val isSignature: Boolean = false,
  ) : PdfOverlayElement

  data class ShapeElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageId: String,
    override val xNorm: Float,
    override val yNorm: Float,
    override val widthNorm: Float,
    override val heightNorm: Float,
    val kind: ShapeKind,
    val colorArgb: Long = 0xFF1D4ED8,
    val strokeWidthPt: Float = 2.5f,
    val filled: Boolean = false,
  ) : PdfOverlayElement

  data class FreehandElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageId: String,
    override val xNorm: Float,
    override val yNorm: Float,
    override val widthNorm: Float,
    override val heightNorm: Float,
    val pointsNorm: List<Pair<Float, Float>>,
    val colorArgb: Long = 0xFF1D4ED8,
    val strokeWidthPt: Float = 2.5f,
  ) : PdfOverlayElement

  data class StampElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageId: String,
    override val xNorm: Float,
    override val yNorm: Float,
    override val widthNorm: Float,
    override val heightNorm: Float,
    val label: String,
    val colorArgb: Long = 0xFF059669,
  ) : PdfOverlayElement
}

data class EditorSnapshot(
  val pages: List<PdfPageState>,
  val elements: List<PdfOverlayElement>,
  val hiddenDetectedBlockIds: Set<String> = emptySet(),
)

val EditorColorPalette: List<Pair<String, Long>> =
  listOf(
    "Ink Black" to 0xFF0F172A,
    "Cobalt Blue" to 0xFF1D4ED8,
    "Sky Blue" to 0xFF0284C7,
    "Crimson Red" to 0xFFDC2626,
    "Emerald" to 0xFF059669,
    "Amber" to 0xFFD97706,
    "Slate" to 0xFF475569,
    "White" to 0xFFFFFFFF,
  )

fun Long.toComposeColor(): Color = Color(this.toInt())
