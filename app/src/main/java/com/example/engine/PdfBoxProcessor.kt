package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.R
import com.example.model.CompressionLevel
import com.example.model.PageSizePreset
import com.example.model.PdfFontOption
import com.example.model.PdfOverlayElement
import com.example.model.PdfPageState
import com.example.model.ShapeKind
import com.example.model.TextAlignOption
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfBoxProcessor {

  suspend fun inspectPdfPages(file: File): List<PdfPageState> =
    withContext(Dispatchers.IO) {
      PDDocument.load(file).use { doc ->
        (0 until doc.numberOfPages).map { idx ->
          val page = doc.getPage(idx)
          val box = page.cropBox ?: page.mediaBox
          PdfPageState(
            sourcePageIndex = idx,
            baseWidthPt = box.width,
            baseHeightPt = box.height,
            baseRotation = ((page.rotation % 360) + 360) % 360,
            extraRotation = 0,
          )
        }
      }
    }

  suspend fun exportEditedPdf(
    context: Context,
    sourceFile: File,
    pages: List<PdfPageState>,
    elements: List<PdfOverlayElement>,
    outputFile: File,
  ): File =
    withContext(Dispatchers.IO) {
      PDDocument.load(sourceFile).use { sourceDoc ->
        PDDocument().use { targetDoc ->
          val bengaliFont =
            runCatching {
              context.resources.openRawResource(R.font.noto_sans_bengali).use {
                PDType0Font.load(targetDoc, it)
              }
            }.getOrNull()
          val sansFont =
            runCatching {
              context.resources.openRawResource(R.font.plus_jakarta_sans).use {
                PDType0Font.load(targetDoc, it)
              }
            }.getOrNull()
          val monoFont =
            runCatching {
              context.resources.openRawResource(R.font.jetbrains_mono).use {
                PDType0Font.load(targetDoc, it)
              }
            }.getOrNull()

          val fontMap =
            mapOf(
              PdfFontOption.BENGALI_UNICODE to (bengaliFont ?: sansFont ?: PDType1Font.HELVETICA),
              PdfFontOption.MODERN_SANS to (sansFont ?: PDType1Font.HELVETICA),
              PdfFontOption.SERIF to PDType1Font.TIMES_ROMAN,
              PdfFontOption.MONOSPACE to (monoFont ?: PDType1Font.COURIER),
            )

          for (pageState in pages) {
            if (pageState.sourcePageIndex !in 0 until sourceDoc.numberOfPages) continue
            val imported = targetDoc.importPage(sourceDoc.getPage(pageState.sourcePageIndex))
            imported.rotation = pageState.totalRotation

            val pageElements = elements.filter { it.pageId == pageState.pageId }
            if (pageElements.isEmpty()) continue

            val cropBox = imported.cropBox ?: imported.mediaBox
            val boxOriginX = cropBox.lowerLeftX
            val boxOriginY = cropBox.lowerLeftY
            val boxW = cropBox.width
            val boxH = cropBox.height
            val visW = pageState.visualWidthPt
            val visH = pageState.visualHeightPt

            PDPageContentStream(
              targetDoc,
              imported,
              PDPageContentStream.AppendMode.APPEND,
              true,
              true,
            ).use { cs ->
              cs.saveGraphicsState()
              // Map visual top-left space (0..visW, 0..visH with Y pointing up from visual bottom-left)
              // into unrotated page user space
              when (pageState.totalRotation) {
                90 -> {
                  cs.transform(Matrix(0f, 1f, -1f, 0f, boxOriginX + boxW, boxOriginY))
                }
                180 -> {
                  cs.transform(Matrix(-1f, 0f, 0f, -1f, boxOriginX + boxW, boxOriginY + boxH))
                }
                270 -> {
                  cs.transform(Matrix(0f, -1f, 1f, 0f, boxOriginX, boxOriginY + boxH))
                }
                else -> {
                  cs.transform(Matrix(1f, 0f, 0f, 1f, boxOriginX, boxOriginY))
                }
              }

              for (el in pageElements) {
                when (el) {
                  is PdfOverlayElement.WhiteoutElement -> {
                    val x = el.xNorm * visW
                    val w = el.widthNorm * visW
                    val h = el.heightNorm * visH
                    val y = visH - (el.yNorm * visH) - h
                    val (r, g, b) = argbToRgb(el.colorArgb)
                    cs.setNonStrokingColor(r, g, b)
                    cs.addRect(x, y, w, h)
                    cs.fill()
                  }

                  is PdfOverlayElement.ShapeElement -> {
                    val x = el.xNorm * visW
                    val w = el.widthNorm * visW
                    val h = el.heightNorm * visH
                    val y = visH - (el.yNorm * visH) - h
                    val (r, g, b) = argbToRgb(el.colorArgb)

                    when (el.kind) {
                      ShapeKind.RECT -> {
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth(el.strokeWidthPt)
                        cs.addRect(x, y, w, h)
                        if (el.filled) {
                          cs.setNonStrokingColor(r, g, b)
                          cs.fillAndStroke()
                        } else {
                          cs.stroke()
                        }
                      }

                      ShapeKind.CIRCLE -> {
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth(el.strokeWidthPt)
                        drawEllipse(cs, x, y, w, h)
                        if (el.filled) {
                          cs.setNonStrokingColor(r, g, b)
                          cs.fillAndStroke()
                        } else {
                          cs.stroke()
                        }
                      }

                      ShapeKind.LINE -> {
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth(el.strokeWidthPt)
                        cs.moveTo(x, y + h)
                        cs.lineTo(x + w, y)
                        cs.stroke()
                      }

                      ShapeKind.ARROW -> {
                        cs.setStrokingColor(r, g, b)
                        cs.setNonStrokingColor(r, g, b)
                        cs.setLineWidth(el.strokeWidthPt)
                        val startX = x
                        val startY = y + h
                        val endX = x + w
                        val endY = y
                        cs.moveTo(startX, startY)
                        cs.lineTo(endX, endY)
                        cs.stroke()

                        val angle = atan2(endY - startY, endX - startX)
                        val headLen = (el.strokeWidthPt * 4.2f).coerceIn(8f, 18f)
                        val a1 = angle + Math.toRadians(150.0).toFloat()
                        val a2 = angle - Math.toRadians(150.0).toFloat()
                        cs.moveTo(endX, endY)
                        cs.lineTo(endX + headLen * cos(a1), endY + headLen * sin(a1))
                        cs.lineTo(endX + headLen * cos(a2), endY + headLen * sin(a2))
                        cs.closePath()
                        cs.fill()
                      }

                      ShapeKind.HIGHLIGHT -> {
                        cs.saveGraphicsState()
                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f }
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(r, g, b)
                        cs.addRect(x, y, w, h)
                        cs.fill()
                        cs.restoreGraphicsState()
                      }
                    }
                  }

                  is PdfOverlayElement.FreehandElement -> {
                    if (el.pointsNorm.size >= 2) {
                      val (r, g, b) = argbToRgb(el.colorArgb)
                      cs.setStrokingColor(r, g, b)
                      cs.setLineWidth(el.strokeWidthPt)
                      cs.setLineCapStyle(1)
                      cs.setLineJoinStyle(1)
                      val first = el.pointsNorm.first()
                      cs.moveTo(first.first * visW, visH - first.second * visH)
                      for (pt in el.pointsNorm.drop(1)) {
                        cs.lineTo(pt.first * visW, visH - pt.second * visH)
                      }
                      cs.stroke()
                    }
                  }

                  is PdfOverlayElement.ImageElement -> {
                    val imgFile = File(el.localImagePath)
                    if (imgFile.exists()) {
                      val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                      if (bmp != null) {
                        val pdImage = LosslessFactory.createFromImage(targetDoc, bmp)
                        val x = el.xNorm * visW
                        val w = el.widthNorm * visW
                        val h = el.heightNorm * visH
                        val y = visH - (el.yNorm * visH) - h
                        cs.drawImage(pdImage, x, y, w, h)
                        bmp.recycle()
                      }
                    }
                  }

                  is PdfOverlayElement.StampElement -> {
                    val x = el.xNorm * visW
                    val w = el.widthNorm * visW
                    val h = el.heightNorm * visH
                    val y = visH - (el.yNorm * visH) - h
                    val (r, g, b) = argbToRgb(el.colorArgb)
                    cs.setStrokingColor(r, g, b)
                    cs.setLineWidth(2.2f)
                    cs.addRect(x, y, w, h)
                    cs.stroke()
                    val stampFont = fontMap[PdfFontOption.MODERN_SANS] ?: PDType1Font.HELVETICA_BOLD
                    val fontSize = (h * 0.45f).coerceIn(10f, 22f)
                    val safeText = filterEncodableText(el.label, stampFont)
                    val textW = runCatching { stampFont.getStringWidth(safeText) / 1000f * fontSize }.getOrDefault(w * 0.6f)
                    cs.beginText()
                    cs.setFont(stampFont, fontSize)
                    cs.setNonStrokingColor(r, g, b)
                    cs.newLineAtOffset(x + (w - textW) / 2f, y + (h - fontSize * 0.72f) / 2f)
                    cs.showText(safeText)
                    cs.endText()
                  }

                  is PdfOverlayElement.TextElement -> {
                    val primaryFont = fontMap[el.font] ?: PDType1Font.HELVETICA
                    val boxX = el.xNorm * visW
                    val boxW = el.widthNorm * visW
                    val boxTopY = visH - (el.yNorm * visH)
                    val fontSize = el.fontSizePt.coerceIn(7f, 72f)
                    val lineHeight = fontSize * 1.25f
                    val (r, g, b) = argbToRgb(el.colorArgb)

                    val wrappedLines = wrapLinesForBox(el.text, primaryFont, fontSize, boxW)
                    var cursorY = boxTopY - fontSize
                    for (line in wrappedLines) {
                      val safeLine = filterEncodableText(line, primaryFont)
                      if (safeLine.isNotEmpty()) {
                        val lineW =
                          runCatching { primaryFont.getStringWidth(safeLine) / 1000f * fontSize }
                            .getOrDefault(0f)
                        val offsetX =
                          when (el.align) {
                            TextAlignOption.LEFT -> boxX
                            TextAlignOption.CENTER -> boxX + max(0f, (boxW - lineW) / 2f)
                            TextAlignOption.RIGHT -> boxX + max(0f, boxW - lineW)
                          }
                        cs.beginText()
                        cs.setFont(primaryFont, fontSize)
                        cs.setNonStrokingColor(r, g, b)
                        cs.newLineAtOffset(offsetX, cursorY)
                        cs.showText(safeLine)
                        cs.endText()
                      }
                      cursorY -= lineHeight
                    }
                  }
                }
              }

              cs.restoreGraphicsState()
            }
          }

          targetDoc.save(outputFile)
        }
      }
      outputFile
    }

  suspend fun mergePdfs(
    context: Context,
    uris: List<Uri>,
    outputFile: File,
  ): File =
    withContext(Dispatchers.IO) {
      val merger = PDFMergerUtility()
      val tempFiles = mutableListOf<File>()
      try {
        uris.forEachIndexed { idx, uri ->
          val tmp = PdfRendererAndOcr.copyUriToCacheFile(context, uri, "merge_in_$idx.pdf")
          tempFiles.add(tmp)
          merger.addSource(tmp)
        }
        merger.destinationFileName = outputFile.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
      } finally {
        tempFiles.forEach { it.delete() }
      }
      outputFile
    }

  suspend fun splitPdfExtract(
    sourceFile: File,
    oneBasedPages: List<Int>,
    outputFile: File,
  ): File =
    withContext(Dispatchers.IO) {
      PDDocument.load(sourceFile).use { src ->
        PDDocument().use { dst ->
          for (p in oneBasedPages) {
            val idx = p - 1
            if (idx in 0 until src.numberOfPages) {
              dst.importPage(src.getPage(idx))
            }
          }
          dst.save(outputFile)
        }
      }
      outputFile
    }

  suspend fun splitPdfEveryN(
    sourceFile: File,
    pagesPerSplit: Int,
    outputDir: File,
    baseName: String,
  ): List<File> =
    withContext(Dispatchers.IO) {
      val chunk = pagesPerSplit.coerceAtLeast(1)
      val outFiles = mutableListOf<File>()
      PDDocument.load(sourceFile).use { src ->
        var start = 0
        var part = 1
        while (start < src.numberOfPages) {
          val end = min(start + chunk, src.numberOfPages)
          val out = File(outputDir, "${baseName}_part_$part.pdf")
          PDDocument().use { dst ->
            for (i in start until end) {
              dst.importPage(src.getPage(i))
            }
            dst.save(out)
          }
          outFiles.add(out)
          start = end
          part++
        }
      }
      outFiles
    }

  suspend fun compressPdf(
    sourceFile: File,
    level: CompressionLevel,
    outputFile: File,
  ): File =
    withContext(Dispatchers.IO) {
      val qualityFloat = level.jpegQuality / 100f
      PDDocument.load(sourceFile).use { doc ->
        for (page in doc.pages) {
          val resources = page.resources ?: continue
          for (name in resources.xObjectNames) {
            val xObj = runCatching { resources.getXObject(name) }.getOrNull()
            if (xObj is PDImageXObject) {
              val bmp = runCatching { xObj.image }.getOrNull() ?: continue
              val scaled = downscaleIfNeeded(bmp, level.maxImageDim)
              val compressedImg =
                runCatching { JPEGFactory.createFromImage(doc, scaled, qualityFloat) }.getOrNull()
              if (compressedImg != null) {
                resources.put(name, compressedImg)
              }
              if (scaled != bmp) scaled.recycle()
              bmp.recycle()
            }
          }
        }
        doc.save(outputFile)
      }

      // If strong compression didn't reduce size (e.g. heavy vector/scanned streams),
      // keep the smaller file or re-render pages cleanly for HIGH level if larger than original
      if (level == CompressionLevel.HIGH && outputFile.length() >= sourceFile.length() * 0.92) {
        val rasterOut = File(outputFile.parentFile, "raster_${outputFile.name}")
        val pageCount = PDDocument.load(sourceFile).use { it.numberOfPages }
        PDDocument().use { rasterDoc ->
          for (i in 0 until pageCount) {
            val bmp =
              PdfRendererAndOcr.renderPageBitmap(sourceFile, i, 0, level.maxImageDim) ?: continue
            val page = PDPage(PDRectangle(bmp.width.toFloat() * 0.72f, bmp.height.toFloat() * 0.72f))
            rasterDoc.addPage(page)
            val pdImg = JPEGFactory.createFromImage(rasterDoc, bmp, qualityFloat)
            PDPageContentStream(rasterDoc, page).use { cs ->
              cs.drawImage(pdImg, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
            }
            bmp.recycle()
          }
          rasterDoc.save(rasterOut)
        }
        if (rasterOut.exists() && rasterOut.length() < outputFile.length()) {
          rasterOut.copyTo(outputFile, overwrite = true)
        }
        rasterOut.delete()
      }
      outputFile
    }

  suspend fun resizePdf(
    sourceFile: File,
    targetWidthPt: Float,
    targetHeightPt: Float,
    outputFile: File,
  ): File =
    withContext(Dispatchers.IO) {
      PDDocument.load(sourceFile).use { src ->
        PDDocument().use { dst ->
          val layerUtil = LayerUtility(dst)
          for (i in 0 until src.numberOfPages) {
            val srcPage = src.getPage(i)
            val srcBox = srcPage.cropBox ?: srcPage.mediaBox
            val newPage = PDPage(PDRectangle(targetWidthPt, targetHeightPt))
            dst.addPage(newPage)

            val form = layerUtil.importPageAsForm(src, srcPage)
            val scale = min(targetWidthPt / srcBox.width, targetHeightPt / srcBox.height)
            val scaledW = srcBox.width * scale
            val scaledH = srcBox.height * scale
            val offsetX = (targetWidthPt - scaledW) / 2f
            val offsetY = (targetHeightPt - scaledH) / 2f

            PDPageContentStream(dst, newPage).use { cs ->
              cs.saveGraphicsState()
              cs.transform(Matrix(scale, 0f, 0f, scale, offsetX, offsetY))
              cs.drawForm(form)
              cs.restoreGraphicsState()
            }
          }
          dst.save(outputFile)
        }
      }
      outputFile
    }

  suspend fun imagesToPdf(
    context: Context,
    imageUris: List<Uri>,
    pageSizePreset: PageSizePreset?, // null means fit page to image dimensions
    marginPt: Float,
    outputFile: File,
  ): File =
    withContext(Dispatchers.IO) {
      PDDocument().use { doc ->
        for (uri in imageUris) {
          val bmp = PdfRendererAndOcr.decodeBitmapWithExif(context, uri, 2200)
          val pageW: Float
          val pageH: Float
          if (pageSizePreset == null) {
            pageW = bmp.width.toFloat() * 0.72f + marginPt * 2f
            pageH = bmp.height.toFloat() * 0.72f + marginPt * 2f
          } else {
            pageW = pageSizePreset.widthPt
            pageH = pageSizePreset.heightPt
          }
          val page = PDPage(PDRectangle(pageW, pageH))
          doc.addPage(page)

          val availW = (pageW - marginPt * 2f).coerceAtLeast(20f)
          val availH = (pageH - marginPt * 2f).coerceAtLeast(20f)
          val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
          val drawW = bmp.width * scale
          val drawH = bmp.height * scale
          val drawX = (pageW - drawW) / 2f
          val drawY = (pageH - drawH) / 2f

          val pdImage = JPEGFactory.createFromImage(doc, bmp, 0.90f)
          PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
          }
          bmp.recycle()
        }
        doc.save(outputFile)
      }
      outputFile
    }

  suspend fun copyFileToOutputUri(context: Context, source: File, destUri: Uri) =
    withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(destUri)?.use { out ->
        source.inputStream().use { input -> input.copyTo(out) }
      } ?: error("Cannot write to destination")
    }

  fun parsePageRangeInput(input: String, maxPage: Int): List<Int> {
    val result = linkedSetOf<Int>()
    val parts = input.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    for (part in parts) {
      if ('-' in part) {
        val bounds = part.split('-').mapNotNull { it.trim().toIntOrNull() }
        if (bounds.size == 2) {
          val a = bounds[0].coerceIn(1, maxPage)
          val b = bounds[1].coerceIn(1, maxPage)
          val range = if (a <= b) a..b else b..a
          result.addAll(range)
        }
      } else {
        part.toIntOrNull()?.let { p ->
          if (p in 1..maxPage) result.add(p)
        }
      }
    }
    return result.toList()
  }

  private fun downscaleIfNeeded(bmp: Bitmap, maxDim: Int): Bitmap {
    val w = bmp.width
    val h = bmp.height
    if (w <= maxDim && h <= maxDim) return bmp
    val scale = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
    return Bitmap.createScaledBitmap(
      bmp,
      (w * scale).toInt().coerceAtLeast(1),
      (h * scale).toInt().coerceAtLeast(1),
      true,
    )
  }

  private fun drawEllipse(cs: PDPageContentStream, x: Float, y: Float, w: Float, h: Float) {
    val ox = 0.5522847f * (w / 2f)
    val oy = 0.5522847f * (h / 2f)
    val cx = x + w / 2f
    val cy = y + h / 2f
    val xe = x + w
    val ye = y + h
    cs.moveTo(x, cy)
    cs.curveTo(x, cy + oy, cx - ox, ye, cx, ye)
    cs.curveTo(cx + ox, ye, xe, cy + oy, xe, cy)
    cs.curveTo(xe, cy - oy, cx + ox, y, cx, y)
    cs.curveTo(cx - ox, y, x, cy - oy, x, cy)
    cs.closePath()
  }

  private fun wrapLinesForBox(
    text: String,
    font: PDFont,
    fontSize: Float,
    maxWidthPt: Float,
  ): List<String> {
    val lines = mutableListOf<String>()
    val paragraphs = text.split('\n')
    for (paragraph in paragraphs) {
      if (paragraph.isEmpty()) {
        lines.add("")
        continue
      }
      val words = paragraph.split(' ')
      var current = StringBuilder()
      for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        val width =
          runCatching { font.getStringWidth(filterEncodableText(candidate, font)) / 1000f * fontSize }
            .getOrDefault(candidate.length * fontSize * 0.5f)
        if (width <= maxWidthPt || current.isEmpty()) {
          current = StringBuilder(candidate)
        } else {
          lines.add(current.toString())
          current = StringBuilder(word)
        }
      }
      if (current.isNotEmpty()) lines.add(current.toString())
    }
    return lines
  }

  private fun filterEncodableText(text: String, font: PDFont): String {
    val sb = StringBuilder(text.length)
    for (ch in text) {
      if (ch == '\r' || ch == '\t') continue
      val ok =
        runCatching {
          font.encode(ch.toString())
          true
        }.getOrDefault(false)
      sb.append(if (ok) ch else ' ')
    }
    return sb.toString()
  }

  private fun argbToRgb(argb: Long): Triple<Int, Int, Int> {
    val c = argb.toInt()
    val r = (c shr 16) and 0xFF
    val g = (c shr 8) and 0xFF
    val b = c and 0xFF
    return Triple(r, g, b)
  }
}
