package pl.misieklab.nfccardlab

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import pl.misieklab.nfccardlab.nfc.*

class ClassicNdefConversionTest {
    private val a = (0..15).map { s -> ByteArray(6) { (s + 1).toByte() } }
    private val b = (0..15).map { s -> ByteArray(6) { (s + 81).toByte() } }
    private val message = NfcHexUtils.parse("D1 01 03 54 02 65 6E")
    private inner class Port : ClassicPort {
        val memory = (0..63).associateWith { block ->
            when { block == 0 -> ByteArray(16) { (it + 12).toByte() }
                block % 4 == 3 -> a[block / 4] + NfcHexUtils.parse("87878769") + b[block / 4]
                else -> ByteArray(16) }
        }.toMutableMap()
        val writes = mutableListOf<Int>()
        var authFail = -1
        var failOnce = -1
        var lost = false
        var active = -1
        var kind = KeyKind.A
        override fun layout(sector: Int) = SectorLayout(sector, sector * 4, 4)
        override fun authenticate(sector: Int, kind: KeyKind, key: ByteArray): Boolean {
            if (lost) throw IOException("tag lost")
            val t = memory.getValue(sector * 4 + 3)
            val result = sector != authFail && key.contentEquals(t.copyOfRange(if (kind == KeyKind.A) 0 else 10, if (kind == KeyKind.A) 6 else 16))
            active = if (result) sector else -1; this.kind = kind
            return result
        }
        override fun read(block: Int): ByteArray {
            check(active == block / 4)
            return memory.getValue(block).copyOf().also { if (block % 4 == 3) { it.fill(0, 0, 6); it.fill(0, 10, 16) } }
        }
        override fun write(block: Int, data: ByteArray) {
            check(block != 0 && active == block / 4 && kind == KeyKind.B)
            if (block == failOnce) { failOnce = -1; throw IOException("simulated one-shot failure") }
            val t = memory.getValue(block / 4 * 4 + 3)
            if (block % 4 == 3) {
                val access = EraseAccess.condition(t, 3)
                check(access in setOf(3, 5))
                check(BlockPolicy.validAccessBits(data))
                memory[block] = if (access == 5) t.copyOf().apply { data.copyInto(this, 6, 6, 10) } else data.copyOf()
            } else {
                check(EraseAccess.writableWithB(t, block % 4)); memory[block] = data.copyOf()
            }
            writes += block
        }
    }
    private fun run(p: Port, backup: (List<SectorData>) -> Unit = {}, guard: () -> Unit = {}) =
        ClassicNdefConversion.run(p, a, b, message, WritePolicy(true, true, true), guard, backup) {}
    @Test fun madCrcMatchesPublishedNxpExample() {
        // AN10787 Table 11 is drawn byte 15 -> byte 0; CRC byte is 89.
        val bytes = NfcHexUtils.parse("01 01 08 01 08 01 08 00 00 00 00 00 00 04 00 03 10 03 10 02 10 02 10 00 00 00 00 00 00 11 30")
        assertEquals(0x89, ClassicNdefMapping.crc(bytes).toInt() and 255)
    }
    @Test fun madNfcAidByteOrderAndCrc() {
        val mad = ClassicNdefMapping.mad()
        assertEquals(32, mad.size); assertEquals(0x14, mad[0].toInt() and 255)
        assertEquals(1, mad[1].toInt())
        for (i in 1..15) assertArrayEquals(NfcHexUtils.parse("03E1"), mad.copyOfRange(i * 2, i * 2 + 2))
    }
    @Test fun controlsAreValidAndRemainRecoverableUsingB() {
        for (i in 0..15) {
            val t = ClassicNdefMapping.publicKeyA(i) + ClassicNdefMapping.controls(i) + b[i]
            assertTrue(BlockPolicy.validAccessBits(t)); assertEquals(3, EraseAccess.condition(t, 3))
            for (g in 0..2) assertTrue(EraseAccess.writableWithB(t, g))
        }
    }
    @Test fun conversionPreservesManufacturerBAndPublishesMadLast() {
        val p = Port(); val original = p.memory.getValue(0).copyOf()
        var saved = false
        val result = run(p, { assertTrue(p.writes.isEmpty()); assertEquals(64, it.sumOf { s -> s.blocks.size }); saved = true })
        assertTrue(saved); assertEquals(64, result.sectors.sumOf { it.blocks.size })
        assertArrayEquals(original, p.memory.getValue(0)); assertFalse(0 in p.writes)
        for (s in 0..15) {
            val t = p.memory.getValue(s * 4 + 3)
            assertArrayEquals(b[s], t.copyOfRange(10, 16))
            assertArrayEquals(ClassicNdefMapping.publicKeyA(s), t.copyOfRange(0, 6))
            assertArrayEquals(ClassicNdefMapping.controls(s), t.copyOfRange(6, 10))
        }
        assertEquals(listOf(3, 1, 2, 3), p.writes.takeLast(4))
        assertArrayEquals(ClassicNdefMapping.mad(), p.memory.getValue(1) + p.memory.getValue(2))
        assertArrayEquals((byteArrayOf(3, 7) + message + byteArrayOf(0xfe.toByte())).copyOf(16), p.memory.getValue(4))
    }
    @Test fun backupFailurePreventsWrites() {
        val p = Port(); assertThrows(IOException::class.java) { run(p, { throw IOException("disk full") }) }; assertTrue(p.writes.isEmpty())
    }
    @Test fun lastSectorAuthFailurePreventsWrites() {
        val p = Port().apply { authFail = 15 }; assertThrows(IllegalArgumentException::class.java) { run(p) }; assertTrue(p.writes.isEmpty())
    }
    @Test fun nonzeroDataPreventsWrites() {
        val p = Port().apply { memory[62] = ByteArray(16) { 1 } }; assertThrows(IllegalArgumentException::class.java) { run(p) }; assertTrue(p.writes.isEmpty())
    }
    @Test fun invalidLastSectorControlsPreventWrites() {
        val p = Port().apply { memory.getValue(63)[6] = 0 }; assertThrows(IllegalArgumentException::class.java) { run(p) }; assertTrue(p.writes.isEmpty())
    }
    @Test fun oneShotDataFailureRestoresEveryChangedSector() {
        val p = Port().apply { failOnce = 8 }; val original = p.memory.mapValues { it.value.copyOf() }
        val error = assertThrows(IllegalStateException::class.java) { run(p) }
        assertTrue(error.message!!.contains("ORIGINAL STATE RESTORED"))
        original.forEach { (block, data) -> assertArrayEquals(data, p.memory.getValue(block)) }
    }
    @Test fun madPublishFailureRestoresWholeTag() {
        val p = Port().apply { failOnce = 2 }; val original = p.memory.mapValues { it.value.copyOf() }
        assertThrows(IllegalStateException::class.java) { run(p) }
        original.forEach { (block, data) -> assertArrayEquals(data, p.memory.getValue(block)) }
    }
    @Test fun cancellationStillAttemptsRollback() {
        val p = Port(); val original = p.memory.mapValues { it.value.copyOf() }
        assertThrows(IllegalStateException::class.java) { run(p, guard = { if (4 in p.writes) throw IOException("cancel") }) }
        original.forEach { (block, data) -> assertArrayEquals(data, p.memory.getValue(block)) }
    }
    @Test fun tagLostNeverClaimsRollbackSuccess() {
        val p = Port()
        val error = assertThrows(IllegalStateException::class.java) { run(p, guard = { if (4 in p.writes) { p.lost = true; throw IOException("lost") } }) }
        assertTrue(error.message!!.contains("ROLLBACK INCOMPLETE"))
    }
    @Test fun explicitTrailerConsentRequired() {
        val p = Port()
        assertThrows(IllegalArgumentException::class.java) { ClassicNdefConversion.run(p, a, b, message, WritePolicy(), {}, {}) {} }
        assertTrue(p.writes.isEmpty())
    }
    @Test fun tlvCapacityAndLengthValidation() {
        assertThrows(IllegalArgumentException::class.java) { ClassicNdefMapping.tlv(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { ClassicNdefMapping.tlv(ByteArray(255)) }
        val bytes = ClassicNdefMapping.tlv(message); assertEquals(720, bytes.size); assertEquals(3, bytes[0].toInt()); assertEquals(7, bytes[1].toInt())
    }
}
