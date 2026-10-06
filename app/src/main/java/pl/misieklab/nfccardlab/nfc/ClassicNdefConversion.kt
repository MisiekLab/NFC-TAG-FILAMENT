package pl.misieklab.nfccardlab.nfc

/** NXP AN10787 MAD1 and AN1305 mapping, for an explicitly authorized, cleared Classic 1K. */
object ClassicNdefMapping {
    fun publicKeyA(sector: Int) = NfcHexUtils.parse(if (sector == 0) "A0A1A2A3A4A5" else "D3F7D3F7D3F7")
    fun controls(sector: Int) = NfcHexUtils.parse(if (sector == 0) "787788C1" else "7F078840")
    // AN10787 hardware preset E3 is bit-reflected C7 in this software CRC representation.
    fun crc(bytes: ByteArray): Byte {
        var value = 0xc7
        for (byte in bytes) {
            value = value xor (byte.toInt() and 255)
            repeat(8) { value = ((value shl 1) xor if (value and 128 != 0) 0x1d else 0) and 255 }
        }
        return value.toByte()
    }
    fun mad(): ByteArray = ByteArray(32).apply {
        this[1] = 1 // sector 1 is the publisher sector
        for (sector in 1..15) { this[sector * 2] = 3; this[sector * 2 + 1] = 0xe1.toByte() }
        this[0] = crc(copyOfRange(1, 32))
    }
    fun tlv(message: ByteArray): ByteArray {
        require(message.isNotEmpty() && message.size <= 254) { "A short, nonempty NDEF message is required" }
        val result = byteArrayOf(3, message.size.toByte()) + message + byteArrayOf(0xfe.toByte())
        require(result.size <= 720) { "NDEF capacity exceeded" }
        return result.copyOf(720)
    }
}
data class ConversionReport(val sectors: List<SectorData>)
object ClassicNdefConversion {
    fun run(port: ClassicPort, keysA: List<ByteArray>, keysB: List<ByteArray>, message: ByteArray,
        policy: WritePolicy, guard: () -> Unit, backup: (List<SectorData>) -> Unit,
        phase: (String) -> Unit): ConversionReport {
        require(policy.developer && policy.allowTrailer && policy.trailerConfirmed) { "Explicit trailer authorization required" }
        require(keysA.size == 16 && keysB.size == 16 && (keysA + keysB).all { it.size == 6 })
        val payload = ClassicNdefMapping.tlv(message)
        val mad = ClassicNdefMapping.mad()
        fun auth(sector: Int, a: ByteArray) {
            require(port.authenticate(sector, KeyKind.A, a)) { "Key A authentication failed in sector $sector" }
            require(port.authenticate(sector, KeyKind.B, keysB[sector])) { "Key B authentication failed in sector $sector" }
        }
        val before = (0..15).map { sector ->
            guard()
            val layout = port.layout(sector)
            require(layout.firstBlock == sector * 4 && layout.count == 4) { "Only Classic 1K supported" }
            auth(sector, keysA[sector])
            val blocks = (layout.firstBlock..layout.trailer).map { block ->
                guard()
                val bytes = port.read(block)
                require(bytes.size == 16)
                if (block != 0 && block != layout.trailer) require(bytes.all { it == 0.toByte() }) { "Tag must be cleared first; data present in block $block" }
                BlockData(sector, block, block == layout.trailer, bytes.copyOf(), "CONVERSION BACKUP")
            }
            require(EraseAccess.changeableWithB(blocks.last().bytes!!)) { "Trailer cannot be changed with Key B in sector $sector" }
            phase("CONVERT PREFLIGHT SECTOR $sector OK")
            SectorData(layout, "AUTH A+B: SUCCESS", blocks)
        }
        backup(before) // No tag writes are allowed before the durable backup succeeds.
        phase("CONVERSION BACKUP SAVED")
        val touched = linkedSetOf<Int>()
        fun writeTrailer(sector: Int, a: ByteArray, controls: ByteArray) {
            require(controls.size == 4)
            require(port.authenticate(sector, KeyKind.B, keysB[sector])) { "Trailer authentication failed" }
            val bytes = a + controls + keysB[sector]
            require(BlockPolicy.validAccessBits(bytes))
            try { port.write(port.layout(sector).trailer, bytes) } finally { bytes.fill(0) }
            auth(sector, a)
            require(port.read(port.layout(sector).trailer).copyOfRange(6, 10).contentEquals(controls)) { "Trailer verification failed in sector $sector" }
        }
        fun bridge(sector: Int) {
            val gpb = before[sector].blocks.last().bytes!![9]
            writeTrailer(sector, keysA[sector], byteArrayOf(0x78, 0x77, 0x88.toByte(), gpb))
        }
        fun writeData(sector: Int, block: Int, bytes: ByteArray) {
            require(block != 0 && block != port.layout(sector).trailer && bytes.size == 16)
            require(port.authenticate(sector, KeyKind.B, keysB[sector])) { "Data authentication failed" }
            port.write(block, bytes)
            require(port.read(block).contentEquals(bytes)) { "Data verification failed in block $block" }
        }
        try {
            for (sector in 1..15) {
                guard(); touched += sector; bridge(sector)
                guard(); writeTrailer(sector, ClassicNdefMapping.publicKeyA(sector), ClassicNdefMapping.controls(sector))
                for (relative in 0..2) {
                    guard()
                    val offset = ((sector - 1) * 3 + relative) * 16
                    writeData(sector, sector * 4 + relative, payload.copyOfRange(offset, offset + 16))
                }
                phase("NDEF DATA VERIFIED SECTOR $sector")
            }
            // Publish MAD last, after every NFC data sector has been verified.
            guard(); touched += 0; bridge(0)
            guard(); writeData(0, 1, mad.copyOfRange(0, 16))
            guard(); writeData(0, 2, mad.copyOfRange(16, 32))
            guard(); writeTrailer(0, ClassicNdefMapping.publicKeyA(0), ClassicNdefMapping.controls(0))
            val after = before.map { sector ->
                val index = sector.layout.sector
                guard(); auth(index, ClassicNdefMapping.publicKeyA(index))
                val blocks = sector.blocks.map { block ->
                    guard()
                    val bytes = port.read(block.block)
                    require(bytes.size == 16)
                    val expected = when {
                        block.block == 0 -> block.bytes!!
                        block.trailer -> bytes.copyOf().apply { ClassicNdefMapping.controls(index).copyInto(this, 6) }
                        block.block in 1..2 -> mad.copyOfRange((block.block - 1) * 16, block.block * 16)
                        else -> {
                            val offset = ((index - 1) * 3 + block.block - index * 4) * 16
                            payload.copyOfRange(offset, offset + 16)
                        }
                    }
                    require(bytes.contentEquals(expected)) { "Final verification failed in block " + block.block }
                    block.copy(bytes = bytes, status = if (block.block == 0) "MANUFACTURER PRESERVED" else "NDEF MAPPING VERIFIED")
                }
                sector.copy(auth = "PUBLIC A + ORIGINAL B VERIFIED", blocks = blocks)
            }
            phase("CONVERSION RAW VERIFIED; REMOVE AND RE-TAP FOR ANDROID NDEF DETECTION")
            return ConversionReport(after)
        } catch (failure: Exception) {
            // Key B is never changed. It remains available for rollback from bridge/final controls.
            // Deliberately omit the cancellation guard: attempt restoration even if the job was cancelled.
            var restored = true
            for (sector in touched.toList().reversed()) {
                try {
                    bridge(sector)
                    for (block in before[sector].blocks.filter { !it.trailer && it.block != 0 }) writeData(sector, block.block, block.bytes!!)
                    writeTrailer(sector, keysA[sector], before[sector].blocks.last().bytes!!.copyOfRange(6, 10))
                    require(port.read(port.layout(sector).firstBlock).contentEquals(before[sector].blocks.first().bytes!!))
                    runCatching { phase("ROLLBACK VERIFIED SECTOR $sector") }
                } catch (_: Exception) {
                    restored = false
                    runCatching { phase("ROLLBACK FAILED SECTOR $sector; KEEP BACKUP; RE-PRESENT TAG") }
                }
            }
            val status = if (restored) "CONVERSION FAILED; ORIGINAL STATE RESTORED" else "CONVERSION FAILED; ROLLBACK INCOMPLETE; USE BACKUP"
            runCatching { phase(status) }
            throw IllegalStateException(status, failure)
        }
    }
}
