package pl.misieklab.nfccardlab.nfc

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Published deterministic Bambu Research Group / queengooborg KDF; no key search.
object BambuKeys {
    fun hkdf(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32))
        fun hmac(key: ByteArray, message: ByteArray) = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256")); doFinal(message)
        }
        val prk = hmac(salt, input)
        var previous = ByteArray(0)
        val output = ByteArray(length)
        try {
            var offset = 0
            var counter = 1
            while (offset < length) {
                val next = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
                previous.fill(0); previous = next
                val size = minOf(next.size, length - offset)
                next.copyInto(output, offset, 0, size)
                offset += size; counter++
            }
            return output
        } finally { prk.fill(0); previous.fill(0) }
    }
    fun derive(uid: ByteArray, kind: KeyKind): List<ByteArray> {
        require(uid.size == 4) { "Bambu derivation requires a 4-byte UID" }
        val salt = byteArrayOf(0x9a.toByte(), 0x75, 0x9c.toByte(), 0xf2.toByte(),
            0xc4.toByte(), 0xf7.toByte(), 0xca.toByte(), 0xff.toByte(), 0x22, 0x2c,
            0xb9.toByte(), 0x76, 0x9b.toByte(), 0x41, 0xbc.toByte(), 0x96.toByte())
        val info = ("RFID-" + kind.name).toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val material = hkdf(uid, salt, info, 96)
        return try { (0 until 16).map { material.copyOfRange(it * 6, it * 6 + 6) } }
        finally { material.fill(0) }
    }
}
object EraseAccess {
    fun condition(trailer: ByteArray, group: Int): Int {
        require(trailer.size == 16 && group in 0..3 && BlockPolicy.validAccessBits(trailer)) { "Invalid access bits" }
        val c1 = ((trailer[7].toInt() and 255) shr (4 + group)) and 1
        val c2 = ((trailer[8].toInt() and 255) shr group) and 1
        val c3 = ((trailer[8].toInt() and 255) shr (4 + group)) and 1
        return c1 * 4 + c2 * 2 + c3
    }
    fun writableWithB(t: ByteArray, group: Int) = condition(t, group) in setOf(0, 4, 6, 3)
    fun changeableWithB(t: ByteArray) = condition(t, 3) in setOf(3, 5)
    fun temporaryControls(t: ByteArray): ByteArray {
        require(changeableWithB(t)) { "Trailer access bits cannot be changed with Key B" }
        val result = t.copyOf()
        val c1 = (((t[7].toInt() and 255) shr 4) and 8) or 7
        val c2 = t[8].toInt() and 8
        val c3 = ((t[8].toInt() and 255) shr 4) and 8
        result[6] = (((c2 xor 15) shl 4) or (c1 xor 15)).toByte()
        result[7] = ((c1 shl 4) or (c3 xor 15)).toByte()
        result[8] = ((c3 shl 4) or c2).toByte()
        require(BlockPolicy.validAccessBits(result) && condition(result, 3) == condition(t, 3))
        return result
    }
}
data class EraseReport(val sectors: List<SectorData>, val verifiedBlocks: Int)
object ControlledErase {
    fun run(port: ClassicPort, keysA: List<ByteArray>, keysB: List<ByteArray>,
        policy: WritePolicy, guard: () -> Unit, backup: (List<SectorData>) -> Unit,
        phase: (String) -> Unit): EraseReport {
        require(policy.developer && policy.allowTrailer && policy.trailerConfirmed) { "Trailer authorization required" }
        require(keysA.size == 16 && keysB.size == 16 && (keysA + keysB).all { it.size == 6 })
        fun authenticateBoth(sector: Int) {
            require(port.authenticate(sector, KeyKind.A, keysA[sector])) { "Key A authentication failed in sector $sector" }
            require(port.authenticate(sector, KeyKind.B, keysB[sector])) { "Key B authentication failed in sector $sector" }
        }
        val before = (0 until 16).map { sector ->
            guard()
            val layout = port.layout(sector)
            require(layout.firstBlock == sector * 4 && layout.count == 4) { "Only Classic 1K layout supported" }
            authenticateBoth(sector)
            val blocks = (layout.firstBlock..layout.trailer).map { block ->
                guard(); val bytes = port.read(block)
                require(bytes.size == 16)
                BlockData(sector, block, block == layout.trailer, bytes.copyOf(), "BACKUP READ")
            }
            val trailer = blocks.last().bytes!!
            require(BlockPolicy.validAccessBits(trailer)) { "Invalid access bits in sector $sector" }
            val change = blocks.filter { !it.trailer && it.block != 0 }
                .any { !EraseAccess.writableWithB(trailer, it.block - layout.firstBlock) }
            require(!change || EraseAccess.changeableWithB(trailer)) { "Sector $sector has immutable access conditions" }
            phase("PREFLIGHT SECTOR $sector OK")
            SectorData(layout, "AUTH A+B: SUCCESS", blocks)
        }
        backup(before)
        phase("BACKUP SAVED; ERASING DATA")
        var verified = 0
        fun writeControls(sector: Int, layout: SectorLayout, controls: ByteArray) {
            require(controls.size == 16 && BlockPolicy.validAccessBits(controls))
            require(port.authenticate(sector, KeyKind.B, keysB[sector])) { "Trailer authentication failed" }
            val data = controls.copyOf()
            keysA[sector].copyInto(data, 0); keysB[sector].copyInto(data, 10)
            try { port.write(layout.trailer, data) } finally { data.fill(0) }
            authenticateBoth(sector)
            val readback = port.read(layout.trailer)
            require(readback.copyOfRange(6, 10).contentEquals(controls.copyOfRange(6, 10))) { "Trailer controls verification failed" }
        }
        for (sector in before) {
            guard()
            val index = sector.layout.sector
            val original = sector.blocks.last().bytes!!
            authenticateBoth(index)
            require(port.read(sector.layout.trailer).copyOfRange(6, 10)
                .contentEquals(original.copyOfRange(6, 10))) { "Access conditions changed since backup" }
            val dataBlocks = sector.blocks.filter { !it.trailer && it.block != 0 }
            val change = dataBlocks.any { !EraseAccess.writableWithB(original, it.block - sector.layout.firstBlock) }
            var attempted = false
            try {
                if (change) {
                    guard(); phase("TEMPORARY ACCESS SECTOR $index")
                    attempted = true
                    writeControls(index, sector.layout, EraseAccess.temporaryControls(original))
                }
                for (block in dataBlocks) {
                    val number = block.block
                    guard()
                    val current = port.read(number)
                    require(current.contentEquals(block.bytes!!)) { "Data changed since backup in block $number" }
                    if (current.any { it != 0.toByte() }) { guard(); port.write(number, ByteArray(16)) }
                    require(port.read(number).contentEquals(ByteArray(16))) { "Erase verification failed at block $number" }
                    verified++
                    phase("ZERO VERIFIED BLOCK $number ($verified/47)")
                }
            } finally {
                if (attempted) {
                    // Do not check coroutine cancellation here: restoration must still be attempted.
                    try {
                        phase("RESTORING ACCESS SECTOR $index")
                        writeControls(index, sector.layout, original)
                        phase("ACCESS RESTORED SECTOR $index")
                    } catch (e: Exception) {
                        phase("RESTORE FAILED SECTOR $index; RE-PRESENT TAG; USE BACKUP")
                        throw IllegalStateException("RESTORE FAILED SECTOR $index; data may be partially erased; use backup", e)
                    }
                }
            }
        }
        val after = before.map { sector ->
            guard(); authenticateBoth(sector.layout.sector)
            val blocks = sector.blocks.map { block ->
                guard(); val bytes = port.read(block.block); require(bytes.size == 16)
                val number = block.block
                if (number == 0) require(bytes.contentEquals(block.bytes!!)) { "Manufacturer block changed" }
                else if (block.trailer) require(bytes.copyOfRange(6, 10).contentEquals(block.bytes!!.copyOfRange(6, 10))) { "Original access bits not restored" }
                else require(bytes.contentEquals(ByteArray(16))) { "Final zero verification failed at block $number" }
                block.copy(bytes = bytes, status = if (number == 0 || block.trailer) "PROTECTED / PRESERVED" else "ZERO VERIFIED")
            }
            sector.copy(auth = "AUTH A+B: SUCCESS", blocks = blocks)
        }
        require(verified == 47)
        phase("ERASE VERIFIED: 47 DATA BLOCKS; UID AND KEYS PRESERVED; ACCESS RESTORED")
        return EraseReport(after, verified)
    }
}
