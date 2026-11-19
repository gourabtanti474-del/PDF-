package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import android.media.ExifInterface
import com.example.model.DetectedTextBlock
import com.example.model.ImageExportFormat
import com.example.model.PdfPageState
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object PdfRendererAndOcr {
  private val rendererMutex = Mutex()

  suspend fun copyUriToCacheFile(
    context: Context,
    uri: Uri,
    fileName: String,
  ): File =
    withContext(Dispatchers.IO) {
      val target = File(context.cacheDir, fileName)
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(target).use { output -> input.copyTo(output) }
      } ?: error("Unable to open selected file")
      target
    }

  fun getDisplayName(context: Context, uri: Uri): String {
    var name: String? = null
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
      cursor ->
      if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) name = cursor.getString(idx)
      }
    }
    return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
  }

  fun getFileSize(context: Context, uri: Uri): Long {
    var size = -1L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (idx >= 0) size = cursor.getLong(idx)
      }
    }
    if (size <= 0L) {
      context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { size = it.length }
    }
    return size.coerceAtLeast(0L)
  }

  suspend fun renderPageBitmap(
    file: File,
    sourcePageIndex: Int,
    extraRotationDegrees: Int = 0,
    maxDimensionPx: Int = 1400,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      if (!file.exists()) return@withContext null
      rendererMutex.withLock {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
          PdfRenderer(pfd).use { renderer ->
            if (sourcePageIndex !in 0 until renderer.pageCount) return@withLock null
            renderer.openPage(sourcePageIndex).use { page ->
              val scale =
                min(
                  maxDimensionPx.toFloat() / max(page.width, 1),
                  maxDimensionPx.toFloat() / max(page.height, 1),
                ).coerceIn(0.35f, 3.0f)
              val targetW = (page.width * scale).toInt().coerceAtLeast(1)
              val targetH = (page.height * scale).toInt().coerceAtLeast(1)
              val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
              val canvas = Canvas(bitmap)
              canvas.drawColor(android.graphics.Color.WHITE)
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

              val normalizedRot = ((extraRotationDegrees % 360) + 360) % 360
              if (normalizedRot == 0) {
                bitmap
              } else {
                val matrix = Matrix().apply { postRotate(normalizedRot.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                rotated
              }
            }
          }
        }
      }
    }

  suspend fun extractTextBlocks(
    file: File,
    pages: List<PdfPageState>,
  ): Map<String, List<DetectedTextBlock>> =
    withContext(Dispatchers.IO) {
      val result = mutableMapOf<String, List<DetectedTextBlock>>()
      if (!file.exists()) return@withContext result
      PDDocument.load(file).use { doc ->
        for (pageState in pages) {
          val pageIndex = pageState.sourcePageIndex
          if (pageIndex !in 0 until doc.numberOfPages) continue
          val pdPage = doc.getPage(pageIndex)
          val mediaBox = pdPage.cropBox ?: pdPage.mediaBox
          val pageRot = ((pdPage.rotation % 360) + 360) % 360
          val unrotatedVisualW = if (pageRot == 90 || pageRot == 270) mediaBox.height else mediaBox.width
          val unrotatedVisualH = if (pageRot == 90 || pageRot == 270) mediaBox.width else mediaBox.height

          val blocks = mutableListOf<DetectedTextBlock>()
          val stripper =
            object : PDFTextStripper() {
              init {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
                sortByPosition = true
              }

              override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                val trimmed = text.trim()
                if (trimmed.isEmpty() || textPositions.isEmpty()) return
                var minX = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var minTop = Float.MAX_VALUE
                var maxBottom = Float.MIN_VALUE
                var fontSum = 0f

                for (tp in textPositions) {
                  val x = tp.xDirAdj
                  val w = tp.widthDirAdj
                  val baselineY = tp.yDirAdj
                  val h = tp.heightDir.coerceAtLeast(6f)
                  val top = baselineY - h * 1.05f
                  val bottom = baselineY + h * 0.25f
                  minX = min(minX, x)
                  maxX = max(maxX, x + w)
                  minTop = min(minTop, top)
                  maxBottom = max(maxBottom, bottom)
                  fontSum += tp.fontSizeInPt.coerceAtLeast(8f)
                }

                val xNormBase = (minX / unrotatedVisualW).coerceIn(0f, 0.98f)
                val yNormBase = (minTop / unrotatedVisualH).coerceIn(0f, 0.98f)
                val wNormBase = ((maxX - minX) / unrotatedVisualW).coerceIn(0.02f, 1f - xNormBase)
                val hNormBase = ((maxBottom - minTop) / unrotatedVisualH).coerceIn(0.015f, 1f - yNormBase)

                val (rx, ry, rw, rh) =
                  rotateNormalizedRect(
                    xNormBase,
                    yNormBase,
                    wNormBase,
                    hNormBase,
                    pageState.extraRotation,
                  )

                blocks.add(
                  DetectedTextBlock(
                    id = UUID.randomUUID().toString(),
                    pageId = pageState.pageId,
                    text = trimmed,
                    xNorm = rx,
                    yNorm = ry,
                    widthNorm = rw,
                    heightNorm = rh,
                    approxFontSizePt = (fontSum / textPositions.size).coerceIn(7f, 48f),
                    fromOcr = false,
                  )
                )
              }
            }
          stripper.getText(doc)
          result[pageState.pageId] = blocks
        }
      }
      result
    }

  private fun rotateNormalizedRect(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    extraRotationDegrees: Int,
  ): FloatArray {
    return when (((extraRotationDegrees % 360) + 360) % 360) {
      90 -> floatArrayOf(1f - (y + h), x, h, w)
      180 -> floatArrayOf(1f - (x + w), 1f - (y + h), w, h)
      270 -> floatArrayOf(y, 1f - (x + w), h, w)
      else -> floatArrayOf(x, y, w, h)
    }
  }

  suspend fun runOcrOnBitmap(bitmap: Bitmap, pageId: String): List<DetectedTextBlock> =
    withContext(Dispatchers.Default) {
      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(image).awaitTask()
        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)
        val blocks = mutableListOf<DetectedTextBlock>()
        for (block in visionText.textBlocks) {
          for (line in block.lines) {
            val box = line.boundingBox ?: continue
            val text = line.text.trim()
            if (text.isEmpty()) continue
            val xNorm = (box.left / bw).coerceIn(0f, 0.98f)
            val yNorm = (box.top / bh).coerceIn(0f, 0.98f)
            val wNorm = (box.width() / bw).coerceIn(0.02f, 1f - xNorm)
            val hNorm = (box.height() / bh).coerceIn(0.015f, 1f - yNorm)
            val approxPt = (hNorm * 792f * 0.72f).coerceIn(8f, 40f)
            blocks.add(
              DetectedTextBlock(
                id = UUID.randomUUID().toString(),
                pageId = pageId,
                text = text,
                xNorm = xNorm,
                yNorm = yNorm,
                widthNorm = wNorm,
                heightNorm = hNorm,
                approxFontSizePt = approxPt,
                fromOcr = true,
              )
            )
          }
        }
        blocks
      } finally {
        recognizer.close()
      }
    }

  suspend fun decodeBitmapWithExif(context: Context, uri: Uri, maxDim: Int = 2200): Bitmap =
    withContext(Dispatchers.IO) {
      val rawBytes =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("Unable to read image")
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
      var sampleSize = 1
      while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
        sampleSize *= 2
      }
      val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      val decoded =
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
          ?: error("Invalid image format")
      val orientation =
        ExifInterface(rawBytes.inputStream()).getAttributeInt(
          ExifInterface.TAG_ORIENTATION,
          ExifInterface.ORIENTATION_NORMAL,
        )
      val angle =
        when (orientation) {
          ExifInterface.ORIENTATION_ROTATE_90 -> 90f
          ExifInterface.ORIENTATION_ROTATE_180 -> 180f
          ExifInterface.ORIENTATION_ROTATE_270 -> 270f
          else -> 0f
        }
      if (angle == 0f) {
        decoded
      } else {
        val m = Matrix().apply { postRotate(angle) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (rotated != decoded) decoded.recycle()
        rotated
      }
    }

  suspend fun saveImageToCacheAndMediaStore(
    context: Context,
    bitmap: Bitmap,
    baseName: String,
    format: ImageExportFormat,
  ): Pair<File, Uri?> =
    withContext(Dispatchers.IO) {
      val fileName = "$baseName.${format.extension}"
      val cacheOut = File(context.cacheDir, fileName)
      val compressFormat =
        if (format == ImageExportFormat.JPG) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
      val quality = if (format == ImageExportFormat.JPG) 92 else 100

      FileOutputStream(cacheOut).use { fos -> bitmap.compress(compressFormat, quality, fos) }

      val mediaUri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val values =
            ContentValues().apply {
              put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
              put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
              put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GS PDF Editor")
              put(MediaStore.Images.Media.IS_PENDING, 1)
            }
          val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
          if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
              bitmap.compress(compressFormat, quality, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
          }
          uri
        } else {
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheOut)
        }
      cacheOut to mediaUri
    }

  fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter =
      object : PrintDocumentAdapter() {
        override fun onLayout(
          oldAttributes: PrintAttributes?,
          newAttributes: PrintAttributes,
          cancellationSignal: CancellationSignal?,
          callback: LayoutResultCallback,
          extras: Bundle?,
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
          }
          val info =
            PrintDocumentInfo.Builder(jobName)
              .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
              .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
              .build()
          callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
          pages: Array<out PageRange>,
          destination: ParcelFileDescriptor,
          cancellationSignal: CancellationSignal?,
          callback: WriteResultCallback,
        ) {
          try {
            FileInputStream(pdfFile).use { input ->
              FileOutputStream(destination.fileDescriptor).use { output ->
                input.copyTo(output)
              }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          } catch (e: Exception) {
            callback.onWriteFailed(e.message)
          }
        }
      }
    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
  }

  fun getShareableUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

  fun getMimeType(file: File): String {
    val ext = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
  }
}

private suspend fun <T> Task<T>.awaitTask(): T =
  suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { exc -> cont.resumeWithException(exc) }
    addOnCanceledListener { cont.cancel() }
  }
