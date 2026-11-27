package fi.vladi.valvontapoytakirja
import org.junit.Test
import org.junit.Assert.*
class CustomWriteQueueTest {
    @Test fun rapidTypingKeepsLatestSnapshot() {
        val q = CustomWriteQueue<Int>()
        repeat(10000) { q.put("document",it) }
        assertEquals(1,q.size)
        assertEquals("document" to 9999,q.take())
        assertNull(q.take())
    }
    @Test fun replacementPreservesBarrierOrder() {
        val q = CustomWriteQueue<Int>()
        q.put("document",1); q.put("barrier",2); q.put("document",3)
        assertEquals("barrier" to 2,q.take())
        assertEquals("document" to 3,q.take())
    }
    @Test fun independentDocumentsAreRetained() {
        val q = CustomWriteQueue<String>()
        q.put("a","old"); q.put("b","other"); q.put("a","new")
        assertEquals("b" to "other",q.take())
        assertEquals("a" to "new",q.take())
    }
}
