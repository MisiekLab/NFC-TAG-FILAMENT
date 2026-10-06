package pl.misieklab.nfccardlab

import org.junit.Assert.*
import org.junit.Test
import pl.misieklab.nfccardlab.nfc.*

class HexAndPolicyTest {
    @Test fun compactKey() { assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, NfcHexUtils.key("FFFFFFFFFFFF")) }
    @Test fun spacedKey() { assertArrayEquals(NfcHexUtils.key("FFFFFFFFFFFF"), NfcHexUtils.key("FF FF FF FF FF FF")) }
    @Test fun lowercaseNewlines() { assertArrayEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte()), NfcHexUtils.parse("ab\ncd")) }
    @Test fun keyLengthRejected() { listOf(5, 7).forEach { n -> assertThrows(IllegalArgumentException::class.java) { NfcHexUtils.key("FF".repeat(n)) } } }
    @Test fun exactBlock() { assertArrayEquals((1..16).map { it.toByte() }.toByteArray(), NfcHexUtils.block("01 02 03 04 05 06 07 08\n09 0A 0B 0C 0D 0E 0F 10")) }
    @Test fun wrongBlockLengths() { listOf(0, 15, 17).forEach { n -> assertThrows(IllegalArgumentException::class.java) { NfcHexUtils.block("01".repeat(n)) } } }
    @Test fun malformedHex() { listOf("F", "GG", "FF:FF", "0xFF", "FF-FF").forEach { assertThrows(IllegalArgumentException::class.java) { NfcHexUtils.parse(it) } } }
    @Test fun formatterUnsigned() { assertEquals("00 7F 80 FF", NfcHexUtils.hex(byteArrayOf(0, 127, 0x80.toByte(), 0xFF.toByte()))) }
    @Test fun rowsHaveEightBytes() { assertEquals(2, NfcHexUtils.rows(ByteArray(16)).lines().size); assertTrue(NfcHexUtils.rows(ByteArray(16)).lines().all { it.split(" ").size == 8 }) }
    @Test fun uidReference() {
        val uid = NfcHexUtils.parse("12 66 FA 25")
        assertEquals("12 66 FA 25", NfcHexUtils.hex(uid))
        assertEquals("308738597", NfcHexUtils.uidDecimal(uid))
        assertEquals(4, uid.size)
        assertNull(NfcHexUtils.uidAscii(uid))
    }
    @Test fun uidUnsignedAndAscii() { assertEquals("4294967295", NfcHexUtils.uidDecimal(ByteArray(4) { -1 })); assertEquals("NFC", NfcHexUtils.uidAscii("NFC".toByteArray())); assertEquals("A.", NfcHexUtils.ascii(byteArrayOf(65, 0))) }
    @Test fun editorNoTruncation() { assertEquals("AB CD EF", NfcHexUtils.editor("abcdef")); assertEquals(17, NfcHexUtils.editor("FF".repeat(17)).split(" ").size) }
    @Test fun oneKMapping() {
        val layouts = (0..15).map { SectorLayout(it, it * 4, 4) }
        layouts.forEach { assertEquals(it.firstBlock + 3, it.trailer) }
        assertEquals(7, BlockPolicy.layoutFor(layouts, 1, 4).trailer)
        assertThrows(IllegalArgumentException::class.java) { BlockPolicy.layoutFor(layouts, 1, 8) }
    }
    @Test fun fourKLargeSectors() {
        val large = SectorLayout(32, 128, 16)
        assertEquals(143, large.trailer)
        assertTrue(large.contains(140))
        BlockPolicy.validate(large, 140, false, false, false)
        assertThrows(IllegalArgumentException::class.java) { BlockPolicy.validate(large, 143, false, false, false) }
    }
    @Test fun manufacturerAlwaysProtected() {
        val layout = SectorLayout(0, 0, 4)
        assertThrows(IllegalArgumentException::class.java) { BlockPolicy.validate(layout, 0, true, true, true) }
    }
    @Test fun trailerNeedsAllThreeGates() {
        val layout = SectorLayout(1, 4, 4)
        for (d in listOf(false, true)) for (a in listOf(false, true)) for (c in listOf(false, true)) {
            if (d && a && c) BlockPolicy.validate(layout, 7, d, a, c)
            else assertThrows(IllegalArgumentException::class.java) { BlockPolicy.validate(layout, 7, d, a, c) }
        }
    }
    @Test fun accessBitsComplementEncoding() {
        val trailer = NfcHexUtils.block("FF FF FF FF FF FF FF 07 80 69 FF FF FF FF FF FF")
        assertTrue(BlockPolicy.validAccessBits(trailer))
        trailer[6] = 0
        assertFalse(BlockPolicy.validAccessBits(trailer))
    }
}
