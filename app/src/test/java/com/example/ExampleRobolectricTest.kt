package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.PdfBoxProcessor
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.model.PageSizePreset
import com.example.model.PdfFontOption
import com.example.model.PdfOverlayElement
import com.example.model.ShapeKind
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name and bilingual owner attribution match requirements`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GS PDF Editor", appName)

    val en = stringsFor(AppLanguage.ENGLISH)
    val bn = stringsFor(AppLanguage.BENGALI)
    assertEquals("Owner Name: Gopal Tanti", en.ownerLine)
    assertEquals("Owner Name: Gopal Tanti", bn.ownerLine)
    assertEquals("GS Online Solution PDF Editor", en.brandTitle)
    assertEquals("GS Online Solution PDF Editor", bn.brandTitle)
  }

  @Test
  fun `pdfbox processor edits exports splits and resizes real pdf`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sourceFile = File(context.cacheDir, "test_source.pdf")
    PDDocument().use { doc ->
      doc.addPage(PDPage(PDRectangle.A4))
      doc.addPage(PDPage(PDRectangle.A4))
      doc.save(sourceFile)
    }

    val pages = PdfBoxProcessor.inspectPdfPages(sourceFile)
    assertEquals(2, pages.size)

    val firstPage = pages.first().copy(extraRotation = 90)
    val editedFile = File(context.cacheDir, "test_edited.pdf")
    val overlays =
      listOf(
        PdfOverlayElement.WhiteoutElement(
          pageId = firstPage.pageId,
          xNorm = 0.1f,
          yNorm = 0.1f,
          widthNorm = 0.2f,
          heightNorm = 0.05f,
        ),
        PdfOverlayElement.TextElement(
          pageId = firstPage.pageId,
          xNorm = 0.15f,
          yNorm = 0.25f,
          widthNorm = 0.5f,
          heightNorm = 0.1f,
          text = "GS Online Solution বাংলা ইউনিকোড",
          font = PdfFontOption.BENGALI_UNICODE,
          fontSizePt = 14f,
        ),
        PdfOverlayElement.ShapeElement(
          pageId = firstPage.pageId,
          xNorm = 0.2f,
          yNorm = 0.4f,
          widthNorm = 0.3f,
          heightNorm = 0.1f,
          kind = ShapeKind.ARROW,
        ),
      )

    PdfBoxProcessor.exportEditedPdf(context, sourceFile, listOf(firstPage), overlays, editedFile)
    assertTrue(editedFile.exists() && editedFile.length() > 0L)

    val editedPages = PdfBoxProcessor.inspectPdfPages(editedFile)
    assertEquals(1, editedPages.size)
    assertEquals(90, editedPages.first().baseRotation)

    val resizedFile = File(context.cacheDir, "test_resized.pdf")
    PdfBoxProcessor.resizePdf(
      sourceFile,
      PageSizePreset.LETTER.widthPt,
      PageSizePreset.LETTER.heightPt,
      resizedFile,
    )
    val resizedPages = PdfBoxProcessor.inspectPdfPages(resizedFile)
    assertEquals(2, resizedPages.size)
    assertEquals(612f, resizedPages.first().baseWidthPt, 0.5f)
  }

  @Test
  fun `page range parser handles ranges and singles`() {
    val parsed = PdfBoxProcessor.parsePageRangeInput("1-3, 5, 2", 6)
    assertEquals(listOf(1, 2, 3, 5), parsed)
  }
}
