package pl.misieklab.nfccardlab.nfc

import android.nfc.TagLostException
import android.nfc.tech.MifareClassic
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface ClassicPort {
    fun layout(sector: Int): SectorLayout
    fun authenticate(sector: Int, kind: KeyKind, key: ByteArray): Boolean
    fun read(block: Int): ByteArray
    fun write(block: Int, data: ByteArray)
}
internal class AndroidClassicPort(private val classic: MifareClassic) : ClassicPort {
    override fun layout(sector: Int) = SectorLayout(sector, classic.sectorToBlock(sector), classic.getBlockCountInSector(sector))
    override fun authenticate(sector: Int, kind: KeyKind, key: ByteArray) =
        if (kind == KeyKind.A) classic.authenticateSectorWithKeyA(sector, key) else classic.authenticateSectorWithKeyB(sector, key)
    override fun read(block: Int) = classic.readBlock(block)
    override fun write(block: Int, data: ByteArray) = classic.writeBlock(block, data)
}
data class WritePolicy(val developer: Boolean = false, val allowTrailer: Boolean = false, val trailerConfirmed: Boolean = false)
data class WriteOutcome(val block: BlockData, val verified: Boolean, val status: String)
data class DumpOutcome(val sectors: List<SectorData>, val complete: Boolean, val status: String)

object ClassicTransaction {
    fun read(port: ClassicPort, sector: Int, block: Int, kind: KeyKind, key: ByteArray): BlockData {
        require(key.size == 6) { "Expected 6-byte key" }
        val layout = port.layout(sector)
        require(layout.contains(block)) { "Block does not belong to the selected sector" }
        require(port.authenticate(sector, kind, key)) { "AUTH FAILED" }
        val bytes = port.read(block)
        require(bytes.size == 16) { "Invalid block length returned by NFC API" }
        return BlockData(sector, block, block == layout.trailer, bytes, "READ OK")
    }
    fun write(
        port: ClassicPort, sector: Int, block: Int, kind: KeyKind, key: ByteArray,
        data: ByteArray, expectedCurrent: ByteArray, policy: WritePolicy, beforeWrite: () -> Unit
    ): WriteOutcome {
        require(key.size == 6) { "Expected 6-byte key" }
        require(data.size == 16 && expectedCurrent.size == 16) { "Expected 16-byte block" }
        val layout = port.layout(sector)
        BlockPolicy.validate(layout, block, policy.developer, policy.allowTrailer, policy.trailerConfirmed)
        if (block == layout.trailer) require(BlockPolicy.validAccessBits(data)) { "INVALID TRAILER ACCESS BITS" }
        require(port.authenticate(sector, kind, key)) { "AUTH FAILED" }
        val current = port.read(block)
        require(current.contentEquals(expectedCurrent)) { "BLOCK CHANGED — read again and confirm the new current data" }
        beforeWrite()
        port.write(block, data)
        if (block == layout.trailer) {
            val newKey = if (kind == KeyKind.A) data.copyOfRange(0, 6) else data.copyOfRange(10, 16)
            try {
                if (!port.authenticate(sector, kind, newKey)) {
                    return WriteOutcome(BlockData(sector, block, true, status = "REAUTH FAILED"), false,
                        "TRAILER WRITTEN; REAUTH FAILED — do not retry blindly")
                }
                val observed = port.read(block)
                val controlsVerified = observed.size == 16 && observed.copyOfRange(6, 10).contentEquals(data.copyOfRange(6, 10))
                return WriteOutcome(BlockData(sector, block, true, observed, "READ OK"), controlsVerified,
                    if (controlsVerified) "TRAILER ACCESS/GPB VERIFIED; NEW KEY AUTH OK; FULL KEY BYTES NOT READABLE"
                    else "VERIFY FAILED — trailer control bytes differ")
            } finally { newKey.fill(0) }
        }
        val observed = port.read(block)
        val verified = data.contentEquals(observed)
        return WriteOutcome(BlockData(sector, block, false, observed, "READ OK"), verified,
            if (verified) "WRITE VERIFIED" else "VERIFY FAILED")
    }
}

class MifareClassicManager(private val arbiter: NfcManager, private val log: (String) -> Unit) {
    suspend fun authenticate(token: TagSession, sector: Int, kind: KeyKind, key: ByteArray) =
        arbiter.execute(token, MifareClassic::get) { mc ->
            mc.sectorToBlock(sector)
            require(key.size == 6) { "Expected 6-byte key" }
            log("MIFARE CLASSIC CONNECT")
            val ok = AndroidClassicPort(mc).authenticate(sector, kind, key)
            log("AUTH SECTOR $sector KEY $kind " + if (ok) "OK" else "FAILED")
            ok
        }
    suspend fun read(token: TagSession, sector: Int, block: Int, kind: KeyKind, key: ByteArray) =
        arbiter.execute(token, MifareClassic::get) { mc ->
            log("MIFARE CLASSIC CONNECT")
            val result = ClassicTransaction.read(AndroidClassicPort(mc), sector, block, kind, key)
            log("AUTH SECTOR $sector KEY $kind OK")
            log("READ BLOCK $block OK")
            result
        }
    suspend fun write(token: TagSession, sector: Int, block: Int, kind: KeyKind, key: ByteArray,
        data: ByteArray, current: ByteArray, policy: WritePolicy) =
        arbiter.execute(token, MifareClassic::get) { mc ->
            log("MIFARE CLASSIC CONNECT")
            val context = currentCoroutineContext()
            val outcome = ClassicTransaction.write(AndroidClassicPort(mc), sector, block, kind, key, data, current, policy) {
                context.ensureActive()
                arbiter.assertCurrent(token)
                log("AUTH SECTOR $sector KEY $kind OK")
                log("WRITE BLOCK $block")
            }
            log(outcome.status)
            outcome
        }
    suspend fun dump(token: TagSession, info: MifareInfo, kind: KeyKind, key: ByteArray) =
        arbiter.execute(token, MifareClassic::get) { mc ->
            require(key.size == 6) { "Expected 6-byte key" }
            val sectors = info.layouts.map { layout ->
                SectorData(layout, blocks = (layout.firstBlock..layout.trailer).map { BlockData(layout.sector, it, it == layout.trailer) })
            }.toMutableList()
            val port = AndroidClassicPort(mc)
            log("MIFARE CLASSIC CONNECT")
            for ((index, sector) in sectors.withIndex()) {
                currentCoroutineContext().ensureActive()
                arbiter.assertCurrent(token)
                try {
                    val auth = port.authenticate(sector.layout.sector, kind, key)
                    log("AUTH SECTOR ${sector.layout.sector} KEY $kind " + if (auth) "OK" else "FAILED")
                    if (!auth) {
                        sectors[index] = sector.copy(auth = "AUTH FAILED")
                        continue
                    }
                    val blocks = sector.blocks.toMutableList()
                    sectors[index] = sector.copy(auth = "AUTH $kind: SUCCESS", blocks = blocks.toList())
                    for ((bi, block) in blocks.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        try {
                            val bytes = port.read(block.block)
                            require(bytes.size == 16) { "Invalid NFC block length" }
                            blocks[bi] = block.copy(bytes = bytes, status = "READ OK")
                            log("READ BLOCK ${block.block} OK")
                        } catch (e: TagLostException) { throw e }
                          catch (_: IOException) { blocks[bi] = block.copy(status = "READ DENIED / I/O ERROR") }
                          catch (_: IllegalArgumentException) { blocks[bi] = block.copy(status = "READ INVALID") }
                        sectors[index] = sector.copy(auth = "AUTH $kind: SUCCESS", blocks = blocks.toList())
                    }
                } catch (_: TagLostException) {
                    log("TAG LOST")
                    return@execute DumpOutcome(sectors, false, LOST_MESSAGE)
                } catch (_: IOException) {
                    sectors[index] = sector.copy(auth = "AUTH I/O ERROR")
                }
            }
            DumpOutcome(sectors, true, "MEMORY DUMP COMPLETE — accessible blocks only")
        }
}
