package fi.vladi.valvontapoytakirja

// Main-thread confined. Replacing a pending key moves it behind earlier barriers.
internal class CustomWriteQueue<T> {
    private val entries = linkedMapOf<String,T>()
    val size get() = entries.size
    fun isNotEmpty() = entries.isNotEmpty()
    fun put(key: String, value: T) { entries.remove(key); entries[key] = value }
    fun take(): Pair<String,T>? {
        val entry = entries.entries.firstOrNull() ?: return null
        val result = entry.key to entry.value
        entries.remove(entry.key)
        return result
    }
}
