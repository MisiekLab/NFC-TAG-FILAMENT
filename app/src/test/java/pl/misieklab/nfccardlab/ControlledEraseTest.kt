package pl.misieklab.nfccardlab

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import pl.misieklab.nfccardlab.nfc.*

class ControlledEraseTest {
    private val a = (0 until 16).map { ByteArray(6) { (it + 1).toByte() } }
    private val b = (0 until 16).map { ByteArray(6) { (it + 80).toByte() } }
    private fun trailer(control: String = "87 87 87 69") =
        ByteArray(16).apply { NfcHexUtils.parse(control).copyInto(this, 6) }
    private inner class Port : ClassicPort {
        val memory = (0 until 64).associateWith { if (it % 4 == 3) trailer() else ByteArray(16) { (it + 1).toByte() } }.toMutableMap()
        val writes = mutableListOf<Int>()
        var failBlock = -1
        var corruptBlock = -1
        var authFailSector = -1
        var activeSector = -1
        var activeKind = KeyKind.A
        override fun layout(sector: Int) = SectorLayout(sector, sector * 4, 4)
        override fun authenticate(sector: Int, kind: KeyKind, key: ByteArray): Boolean {
            if (sector == authFailSector) return false
            activeSector = sector; activeKind = kind
            return key.contentEquals(if (kind == KeyKind.A) a[sector] else b[sector])
        }
        override fun read(block: Int): ByteArray {
            check(activeSector == block / 4)
            return memory.getValue(block).copyOf().also { if (block == corruptBlock && block in writes) it[0] = 99 }
        }
        override fun write(block: Int, data: ByteArray) {
            check(block != 0 && activeSector == block / 4 && activeKind == KeyKind.B)
            if (block == failBlock) throw IOException("simulated lost write")
            if (block % 4 == 3) {
                check(EraseAccess.changeableWithB(memory.getValue(block)))
                check(data.copyOfRange(0, 6).contentEquals(a[activeSector]))
                check(data.copyOfRange(10, 16).contentEquals(b[activeSector]))
                memory[block] = trailer(NfcHexUtils.hex(data.copyOfRange(6, 10)))
            } else {
                check(EraseAccess.writableWithB(memory.getValue(block / 4 * 4 + 3), block % 4))
                memory[block] = data.copyOf()
            }
            writes += block
        }
    }
    private fun run(p: Port, backup: (List<SectorData>) -> Unit = {}, guard: () -> Unit = {}) =
        ControlledErase.run(p, a, b, WritePolicy(true, true, true), guard, backup) {}

    @Test fun accessBitsDecodeAndTemporaryEncoding() {
        val t = trailer()
        assertEquals(listOf(2, 2, 2, 5), (0..3).map { EraseAccess.condition(t, it) })
        val next = EraseAccess.temporaryControls(t)
        assertEquals("F0 F7 80 69", NfcHexUtils.hex(next.copyOfRange(6, 10)))
        assertEquals(listOf(4, 4, 4, 5), (0..3).map { EraseAccess.condition(next, it) })
        assertEquals("87 87 87 69", NfcHexUtils.hex(t.copyOfRange(6, 10)))
    }
    @Test fun all47DataBlocksZeroManufacturerAndControlsPreserved() {
        val p = Port()
        val manufacturer = p.memory.getValue(0).copyOf()
        var backedUp = false
        val outcome = run(p, { sectors ->
            assertTrue(p.writes.isEmpty()); assertEquals(64, sectors.sumOf { it.blocks.size })
            backedUp = true
        })
        assertTrue(backedUp); assertEquals(47, outcome.verifiedBlocks)
        assertArrayEquals(manufacturer, p.memory.getValue(0))
        assertFalse(0 in p.writes)
        (0 until 64).filter { it != 0 && it % 4 != 3 }.forEach { assertArrayEquals(ByteArray(16), p.memory.getValue(it)) }
        (3 until 64 step 4).forEach { assertArrayEquals(trailer(), p.memory.getValue(it)) }
    }
    @Test fun backupFailurePreventsEveryWrite() {
        val p = Port()
        assertThrows(IOException::class.java) { run(p, { throw IOException("disk full") }) }
        assertTrue(p.writes.isEmpty())
    }
    @Test fun lastSectorAuthFailurePreventsEveryWrite() {
        val p = Port().apply { authFailSector = 15 }
        assertThrows(IllegalArgumentException::class.java) { run(p) }
        assertTrue(p.writes.isEmpty())
    }
    @Test fun immutableLastSectorPreventsEveryWrite() {
        val p = Port().apply { memory[63] = trailer("07 8F 0F 69") }
        assertTrue(BlockPolicy.validAccessBits(p.memory.getValue(63)))
        assertThrows(IllegalArgumentException::class.java) { run(p) }
        assertTrue(p.writes.isEmpty())
    }
    @Test fun dataWriteFailureRestoresAccessBeforeReturning() {
        val p = Port().apply { failBlock = 1 }
        assertThrows(IOException::class.java) { run(p) }
        assertArrayEquals(trailer(), p.memory.getValue(3))
        assertEquals(listOf(3, 3), p.writes)
    }
    @Test fun failedVerificationRestoresAccessAndNeverClaimsSuccess() {
        val p = Port().apply { corruptBlock = 1 }
        assertThrows(IllegalArgumentException::class.java) { run(p) }
        assertArrayEquals(trailer(), p.memory.getValue(3))
        assertFalse(4 in p.writes)
    }
    @Test fun cancellationAfterZeroStillAttemptsRestore() {
        val p = Port()
        assertThrows(IOException::class.java) { run(p, guard = { if (1 in p.writes) throw IOException("cancelled") }) }
        assertArrayEquals(trailer(), p.memory.getValue(3))
        assertFalse(2 in p.writes)
    }
    @Test fun allThreePermissionsRequired() {
        for (policy in listOf(WritePolicy(), WritePolicy(true, false, true), WritePolicy(true, true, false))) {
            val p = Port()
            assertThrows(IllegalArgumentException::class.java) {
                ControlledErase.run(p, a, b, policy, {}, {}) {}
            }
            assertTrue(p.writes.isEmpty())
        }
    }
    @Test fun invalidAccessBitsRejected() {
        assertThrows(IllegalArgumentException::class.java) { EraseAccess.temporaryControls(ByteArray(16)) }
    }
    @Test fun hkdfRfc5869Case1() {
        val output = BambuKeys.hkdf(ByteArray(22) { 0x0b }, NfcHexUtils.parse("000102030405060708090A0B0C"),
            NfcHexUtils.parse("F0F1F2F3F4F5F6F7F8F9"), 42)
        assertEquals("3CB25F25FAACD57A90434F64D0362F2A2D2D0A90CF1A5A4C5DB02D56ECC4C5BF34007208D5B887185865",
            NfcHexUtils.hex(output).replace(" ", ""))
    }
    @Test fun deterministicDerivation16DistinctSixByteKeysPerType() {
        val uid = byteArrayOf(1, 2, 3, 4)
        val aa = BambuKeys.derive(uid, KeyKind.A)
        val bb = BambuKeys.derive(uid, KeyKind.B)
        assertEquals(16, aa.size); assertEquals(16, bb.size)
        assertTrue((aa + bb).all { it.size == 6 })
        assertEquals(32, (aa + bb).map { NfcHexUtils.hex(it) }.toSet().size)
        assertArrayEquals(aa[15], BambuKeys.derive(uid, KeyKind.A)[15])
    }
}
