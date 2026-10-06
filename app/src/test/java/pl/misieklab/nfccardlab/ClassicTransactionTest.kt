package pl.misieklab.nfccardlab

import org.junit.Assert.*
import org.junit.Test
import pl.misieklab.nfccardlab.nfc.*

class ClassicTransactionTest {
    private class Port : ClassicPort {
        val events = mutableListOf<String>()
        var auth = true
        var corrupt = false
        var bytes = ByteArray(16)
        override fun layout(sector: Int): SectorLayout { events += "layout"; return SectorLayout(sector, sector * 4, 4) }
        override fun authenticate(sector: Int, kind: KeyKind, key: ByteArray): Boolean { events += "auth $kind"; return auth }
        override fun read(block: Int): ByteArray { events += "read"; return bytes.copyOf().also { if (corrupt && "write" in events) it[0] = 99 } }
        override fun write(block: Int, data: ByteArray) { events += "write"; bytes = data.copyOf() }
    }
    private val key = ByteArray(6) { -1 }
    private val data = ByteArray(16) { 42 }
    @Test fun safeWriteAuthThenReadWriteReadCompare() {
        val p = Port()
        val result = ClassicTransaction.write(p, 1, 4, KeyKind.A, key, data, ByteArray(16), WritePolicy()) { p.events += "session check" }
        assertTrue(result.verified)
        assertEquals("WRITE VERIFIED", result.status)
        assertEquals(listOf("layout", "auth A", "read", "session check", "write", "read"), p.events)
    }
    @Test fun keyB() {
        val p = Port()
        ClassicTransaction.read(p, 1, 4, KeyKind.B, key)
        assertTrue("auth B" in p.events)
    }
    @Test fun protectedBlocksNeverAuthenticateOrWrite() {
        listOf(0 to 0, 1 to 7).forEach { (sector, block) ->
            val p = Port()
            assertThrows(IllegalArgumentException::class.java) { ClassicTransaction.write(p, sector, block, KeyKind.A, key, data, ByteArray(16), WritePolicy()) {} }
            assertEquals(listOf("layout"), p.events)
        }
    }
    @Test fun blockZeroProtectedInDeveloperMode() {
        val p = Port()
        assertThrows(IllegalArgumentException::class.java) { ClassicTransaction.write(p, 0, 0, KeyKind.A, key, data, ByteArray(16), WritePolicy(true, true, true)) {} }
        assertFalse("write" in p.events)
    }
    @Test fun failedAuthNeverWrites() {
        val p = Port().apply { auth = false }
        assertThrows(IllegalArgumentException::class.java) { ClassicTransaction.write(p, 1, 4, KeyKind.A, key, data, ByteArray(16), WritePolicy()) {} }
        assertFalse("read" in p.events); assertFalse("write" in p.events)
    }
    @Test fun changedCurrentNeverWrites() {
        val p = Port().apply { bytes[0] = 1 }
        assertThrows(IllegalArgumentException::class.java) { ClassicTransaction.write(p, 1, 4, KeyKind.A, key, data, ByteArray(16), WritePolicy()) {} }
        assertFalse("write" in p.events)
    }
    @Test fun expiredSessionNeverWrites() {
        val p = Port()
        assertThrows(IllegalStateException::class.java) { ClassicTransaction.write(p, 1, 4, KeyKind.A, key, data, ByteArray(16), WritePolicy()) { throw IllegalStateException("expired") } }
        assertFalse("write" in p.events)
    }
    @Test fun mismatchShowsVerifyFailed() {
        val p = Port().apply { corrupt = true }
        val result = ClassicTransaction.write(p, 1, 4, KeyKind.A, key, data, ByteArray(16), WritePolicy()) {}
        assertFalse(result.verified); assertEquals("VERIFY FAILED", result.status)
    }
    @Test fun invalidDataNeverTouchesPort() {
        val p = Port()
        assertThrows(IllegalArgumentException::class.java) { ClassicTransaction.write(p, 1, 4, KeyKind.A, key, ByteArray(15), ByteArray(16), WritePolicy()) {} }
        assertTrue(p.events.isEmpty())
    }
    @Test fun invalidTrailerAccessBitsNeverWrites() {
        val p = Port()
        assertThrows(IllegalArgumentException::class.java) { ClassicTransaction.write(p, 1, 7, KeyKind.A, key, ByteArray(16), ByteArray(16), WritePolicy(true, true, true)) {} }
        assertFalse("auth A" in p.events); assertFalse("write" in p.events)
    }
    @Test fun trailerReportDoesNotClaimFullKeyByteVerification() {
        val p = Port()
        val trailer = NfcHexUtils.block("FF FF FF FF FF FF FF 07 80 69 FF FF FF FF FF FF")
        val result = ClassicTransaction.write(p, 1, 7, KeyKind.A, key, trailer, ByteArray(16), WritePolicy(true, true, true)) {}
        assertTrue(result.verified)
        assertTrue(result.status.contains("FULL KEY BYTES NOT READABLE"))
        assertNotEquals("WRITE VERIFIED", result.status)
        assertEquals(2, p.events.count { it == "auth A" })
    }
}
