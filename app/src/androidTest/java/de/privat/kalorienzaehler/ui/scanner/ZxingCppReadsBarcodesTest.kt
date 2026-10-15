package de.privat.kalorienzaehler.ui.scanner

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import de.privat.kalorienzaehler.domain.barcode.BarcodeNormalizer
import de.privat.kalorienzaehler.domain.barcode.BarcodeParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.google.zxing.BarcodeFormat as WriterFormat

/**
 * Checks on a real device what no JVM test can: that the native zxing-cpp library loads and reads the four retail
 * formats, and how it reports them (text and format). Codes are drawn with the zxing writer (test dependency only)
 * and read by the same reader the camera uses. It does not need a camera or the camera permission.
 *
 * Run with `./gradlew connectedDebugAndroidTest` on a device or emulator. NOTE: Gradle uninstalls the app afterwards,
 * which deletes its data: do not run it on the phone that holds the real diary.
 */
@RunWith(AndroidJUnit4::class)
class ZxingCppReadsBarcodesTest {

    private val reader = retailBarcodeReader()

    private fun draw(text: String, format: WriterFormat): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(text, format, 600, 240)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
        }
        // A quiet zone around the code, as on a pack.
        val margin = 60
        val bitmap = Bitmap.createBitmap(matrix.width + 2 * margin, matrix.height + 2 * margin, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixels(pixels, 0, matrix.width, margin, margin, matrix.width, matrix.height)
        return bitmap
    }

    private fun readAndNormalize(text: String, format: WriterFormat): Pair<String, String> {
        val bitmap = draw(text, format)
        val results = reader.read(bitmap, Rect(0, 0, bitmap.width, bitmap.height), 0)
        assertEquals("exactly one code in $text", 1, results.size)
        val result = results.single()
        val appFormat = checkNotNull(result.format.toBarcodeFormat()) { "unexpected format ${result.format}" }
        val parse = BarcodeNormalizer.fromScan(checkNotNull(result.text), appFormat)
        assertTrue("$text read as '${result.text}' (${result.format}) is not valid: $parse", parse is BarcodeParse.Valid)
        return result.text!! to (parse as BarcodeParse.Valid).barcode.code
    }

    @Test
    fun readsEan13() {
        val (read, stored) = readAndNormalize("4006381333931", WriterFormat.EAN_13)
        assertEquals("4006381333931", read)
        assertEquals("4006381333931", stored)
    }

    @Test
    fun readsEan8() {
        val (_, stored) = readAndNormalize("40084107", WriterFormat.EAN_8)
        assertEquals("40084107", stored)
    }

    @Test
    fun readsUpcA() {
        val (_, stored) = readAndNormalize("036000291452", WriterFormat.UPC_A)
        // Whether the library reports 12 digits or an EAN-13 with a leading zero, the code ends up findable.
        assertTrue(stored == "036000291452" || stored == "0036000291452")
    }

    @Test
    fun readsUpcEAndTheAppExpandsItToTheUpcA() {
        val (_, stored) = readAndNormalize("04252614", WriterFormat.UPC_E)
        assertEquals("042100005264", stored)
    }

    @Test
    fun ignoresOtherFormats() {
        val bitmap = draw("HELLO123", WriterFormat.CODE_128)
        assertEquals(emptyList<Any>(), reader.read(bitmap, Rect(0, 0, bitmap.width, bitmap.height), 0))
    }
}
