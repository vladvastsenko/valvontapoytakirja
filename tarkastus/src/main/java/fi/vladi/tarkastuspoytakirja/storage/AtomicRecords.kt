package fi.vladi.tarkastuspoytakirja.storage

import android.util.AtomicFile
import java.io.File

object AtomicRecords {
    private val lock = Any()
    fun read(file: File): String = synchronized(lock) {
        AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    fun write(file: File, value: String) = synchronized(lock) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
    }
    fun delete(file: File) = synchronized(lock) { AtomicFile(file).delete() }
    fun list(directory: File): List<File> = synchronized(lock) {
        directory.listFiles().orEmpty().filter {
            it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".json.bak"))
        }.map { File(directory, it.name.removeSuffix(".bak")) }.distinct()
    }
}
