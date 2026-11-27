package fi.vladi.valvontapoytakirja

import fi.vladi.valvontapoytakirja.pdf.PdfBitmap
import org.junit.Test
import org.junit.Assert.*

class PdfBitmapTest {
    @Test fun smallImagesRemainUnchanged() {
        assertEquals(1, PdfBitmap.sampleSize(1800, 540, 2400))
    }
    @Test fun largeImagesAreBoundedInBothOrientations() {
        assertEquals(4, PdfBitmap.sampleSize(8000, 6000, 2400))
        assertEquals(4, PdfBitmap.sampleSize(6000, 8000, 2400))
        assertEquals(2, PdfBitmap.sampleSize(2401, 100, 2400))
    }
    @Test fun missingImageReturnsNull() {
        assertNull(PdfBitmap.decode(""))
    }
}
