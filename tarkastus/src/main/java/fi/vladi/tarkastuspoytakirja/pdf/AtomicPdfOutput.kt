package fi.vladi.tarkastuspoytakirja.pdf

import android.util.AtomicFile
import java.io.File
import java.io.OutputStream

internal object AtomicPdfOutput {
    @Synchronized
    fun write(target: File, render: (OutputStream) -> Unit) {
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            render(stream)
            stream.flush()
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
    }
}
