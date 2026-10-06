package pl.misieklab.nfccardlab

import android.net.Uri
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.OutputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import pl.misieklab.nfccardlab.nfc.*

data class PendingWrite(val session: TagSession, val sector: Int, val block: Int, val current: ByteArray, val data: ByteArray, val trailer: Boolean)
data class LabState(
    val reader: String = "PAUSED", val info: NfcTagInfo? = null,
    val sessionReady: Boolean = false, val busy: Boolean = false, val message: String = "Hold your tag against the back of the phone.",
    val sectors: List<SectorData> = emptyList(), val ndef: NdefSnapshot? = null,
    val erasedUid: String? = null, val conversionReport: String = "", val eraseReport: String = "", val logs: List<String> = emptyList(), val sectorInput: String = "1", val blockInput: String = "4",
    val keyInput: String = "", val keyKind: KeyKind = KeyKind.A, val dataInput: String = "",
    val developer: Boolean = false, val allowTrailer: Boolean = false, val pending: PendingWrite? = null,
    val pendingFormatUid: String? = null, val selectedBlock: BlockData? = null
)
class LabViewModel : ViewModel() {
    val manager = NfcManager()
    private val _state = MutableStateFlow(LabState())
    val state = _state.asStateFlow()
    private val gate = AtomicBoolean(false)
    private var session: TagSession? = null
    private var operation: Job? = null
    private val classic = MifareClassicManager(manager, ::log)
    private val ndef = NdefManager(manager, ::log)

    private fun log(event: String) {
        val line = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + event
        _state.update { it.copy(logs = (it.logs + line).takeLast(400)) }
    }
    fun readerStatus(status: String) { _state.update { it.copy(reader = status) } }
    fun message(text: String) { _state.update { it.copy(message = text) } }
    private fun task(block: suspend () -> Unit) {
        if (!gate.compareAndSet(false, true)) return
        _state.update { it.copy(busy = true) }
        operation = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(nfcError(e)) }
            finally { gate.set(false); _state.update { it.copy(busy = false) } }
        }
    }
    private fun error(error: NfcResult.Error) {
        message(error.message)
        log(if (error.lost) "TAG LOST / SESSION EXPIRED" else "OPERATION FAILED")
        if (error.lost) expireSession()
    }
    private fun expireSession() {
        val detached = manager.invalidate()
        session = null
        _state.update { it.copy(sessionReady = false, pending = null) }
        viewModelScope.launch { manager.closeDetached(detached) }
    }
    fun pause() {
        operation?.cancel()
        expireSession()
        _state.update { it.copy(reader = "PAUSED", keyInput = "", developer = false, allowTrailer = false, pending = null) }
    }
    override fun onCleared() {
        val detached = manager.invalidate()
        if (detached != null) Thread { try { detached.close() } catch (_: Exception) { } }.start()
        super.onCleared()
    }
    fun onTag(tag: Tag) {
        viewModelScope.launch {
            if (_state.value.reader != "LIVE" || gate.get()) return@launch
            task {
                val token = manager.adopt(tag)
                session = token
                val previousFormatUid = _state.value.pendingFormatUid
                when (val result = manager.inspect(token, NfcTechDetector::inspect)) {
                    is NfcResult.Error -> error(result)
                    is NfcResult.Ok -> {
                        val info = result.value
                        val sectors = info.mifare?.layouts?.map { layout ->
                            SectorData(layout, blocks = (layout.firstBlock..layout.trailer).map { BlockData(layout.sector, it, it == layout.trailer) })
                        } ?: emptyList()
                        _state.update { it.copy(info = info, sectors = sectors, sessionReady = true, ndef = null,
                            pending = null, selectedBlock = null, dataInput = "", message = "TAG DETECTED") }
                        log("TAG DETECTED")
                        log("UID ${info.uid}")
                        if ("android.nfc.tech.Ndef" in info.technologies) {
                            applyNdef(ndef.read(token))
                        }
                        if (previousFormatUid != null) {
                            if (previousFormatUid == info.uid) {
                                val formatted = "android.nfc.tech.Ndef" in info.technologies
                                val status = if (formatted) "NDEF FORMAT VERIFIED — Ndef technology detected on re-tap" else "FORMAT NOT VERIFIED — Ndef technology absent"
                                message(status)
                                log(status)
                                _state.update { it.copy(pendingFormatUid = null) }
                            } else message("Different UID detected — re-present UID $previousFormatUid for format verification")
                        }
                    }
                }
            }
        }
    }
    fun setSector(value: String) { _state.update { it.copy(sectorInput = value, pending = null) } }
    fun setBlock(value: String) { _state.update { it.copy(blockInput = value, pending = null) } }
    fun setKey(value: String) { _state.update { it.copy(keyInput = value, pending = null) } }
    fun setKeyKind(value: KeyKind) { _state.update { it.copy(keyKind = value, pending = null) } }
    fun setData(value: String) { _state.update { it.copy(dataInput = value, pending = null) } }
    fun settings(developer: Boolean, allowTrailer: Boolean) {
        _state.update { it.copy(developer = developer, allowTrailer = developer && allowTrailer, pending = null) }
    }
    fun selectBlock(block: BlockData?) {
        _state.update { it.copy(selectedBlock = block) }
    }
    fun editSelected(block: BlockData) {
        _state.update { it.copy(sectorInput = block.sector.toString(), blockInput = block.block.toString(),
            dataInput = if (!block.trailer && block.bytes != null) NfcHexUtils.hex(block.bytes) else "", selectedBlock = null, pending = null) }
    }
    private fun selection(s: LabState): Pair<Int, Int> {
        val sector = s.sectorInput.toIntOrNull() ?: throw IllegalArgumentException("Enter a sector number")
        val block = s.blockInput.toIntOrNull() ?: throw IllegalArgumentException("Enter an absolute block number")
        BlockPolicy.layoutFor(s.info?.mifare?.layouts ?: throw UnsupportedOperationException(), sector, block)
        return sector to block
    }
    private fun updateBlock(block: BlockData, auth: String? = null) {
        _state.update { s -> s.copy(sectors = s.sectors.map { sector ->
            if (sector.layout.sector != block.sector) sector else sector.copy(
                auth = auth ?: sector.auth, blocks = sector.blocks.map { if (it.block == block.block) block else it })
        }, selectedBlock = s.selectedBlock?.let { if (it.block == block.block) block else it }) }
    }
    fun eraseBambuData(context: android.content.Context, expectedUid: String, expectedGeneration: Long, guidedConsent: Boolean = false) = task {
        val token = session ?: throw IllegalArgumentException("Re-present the tag")
        val s = _state.value
        val info = s.info ?: throw IllegalArgumentException("Scan first")
        require(guidedConsent || (s.developer && s.allowTrailer)) { "Enable Developer mode and trailer authorization" }
        require("android.nfc.tech.Ndef" !in info.technologies) { "Tag is already NDEF; use normal NDEF writing" }
        require(info.uid == expectedUid && token.generation == expectedGeneration) { "Tag changed; confirm again" }
        require(token.tag.id.size == 4 && info.mifare?.size == 1024 &&
            info.mifare.sectorCount == 16 && info.mifare.blockCount == 64) { "Only a 4-byte UID Classic 1K Bambu tag is supported" }
        val keysA = BambuKeys.derive(token.tag.id, KeyKind.A)
        val keysB = BambuKeys.derive(token.tag.id, KeyKind.B)
        val folder = java.io.File(context.applicationContext.filesDir, "erase-backups")
        val stem = "erase-" + expectedUid.replace(" ", "") + "-" + System.currentTimeMillis()
        val backupFile = java.io.File(folder, "$stem-before.json")
        val stateFile = java.io.File(folder, "$stem-state.txt")
        val resultFile = java.io.File(folder, "$stem-after.json")
        fun durable(file: java.io.File, text: String) {
            java.io.FileOutputStream(file).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8)); out.fd.sync()
            }
        }
        _state.update { it.copy(erasedUid = null, eraseReport = "PREPARING BACKUP — no writes yet") }
        try {
            val coroutine = currentCoroutineContext()
            val result = manager.execute(token, MifareClassic::get) { mc ->
                require(mc.size == 1024 && mc.sectorCount == 16 && mc.blockCount == 64)
                val report = ControlledErase.run(AndroidClassicPort(mc), keysA, keysB,
                    WritePolicy(guidedConsent || s.developer, guidedConsent || s.allowTrailer, true),
                    guard = {
                        coroutine.ensureActive()
                        manager.assertCurrent(token)
                        require(NfcHexUtils.hex(token.tag.id) == expectedUid) { "UID changed" }
                    },
                    backup = { sectors ->
                        require(folder.isDirectory || folder.mkdirs()) { "Backup folder unavailable" }
                        require(backupFile.createNewFile()) { "Backup filename already exists" }
                        durable(backupFile, DumpExporter.json(info, sectors))
                        _state.update { it.copy(sectors = sectors) }
                        log("REDACTED BACKUP SAVED: " + backupFile.name)
                    },
                    phase = { phase ->
                        log(phase)
                        _state.update { it.copy(message = phase, eraseReport = phase + "\nBackup: " + backupFile.name) }
                        runCatching { durable(stateFile, "UID: $expectedUid\n$phase\nBackup: " + backupFile.name) }
                    })
                durable(resultFile, DumpExporter.json(info, report.sectors))
                report
            }
            when (result) {
                is NfcResult.Ok -> _state.update { it.copy(sectors = result.value.sectors, erasedUid = expectedUid,
                    message = "ERASE VERIFIED — 47 DATA BLOCKS ZERO; KEYS/UID PRESERVED; ACCESS RESTORED",
                    eraseReport = "ERASE VERIFIED — 47 DATA BLOCKS ZERO; KEYS/UID PRESERVED; ACCESS RESTORED\nBackup: " + backupFile.name) }
                is NfcResult.Error -> {
                    _state.update { it.copy(eraseReport = "ERASE NOT VERIFIED\n" + it.eraseReport + "\n" + result.message) }
                    error(result)
                }
            }
        } finally {
            keysA.forEach { it.fill(0) }; keysB.forEach { it.fill(0) }
            _state.update { it.copy(developer = false, allowTrailer = false, keyInput = "") }
        }
    }
    fun convertBambuNdef(context: android.content.Context, expectedUid: String, expectedGeneration: Long, guidedConsent: Boolean = false) = task {
        val token = session ?: throw IllegalArgumentException("Re-present the tag")
        val s = _state.value
        val info = s.info ?: throw IllegalArgumentException("Scan first")
        require(guidedConsent || (s.developer && s.allowTrailer)) { "Enable Developer mode and trailer authorization" }
        require("android.nfc.tech.Ndef" !in info.technologies) { "Tag is already NDEF; use normal NDEF writing" }
        require(info.uid == expectedUid && token.generation == expectedGeneration) { "Tag changed; confirm again" }
        require(token.tag.id.size == 4 && info.mifare?.size == 1024 && info.mifare.sectorCount == 16 && info.mifare.blockCount == 64)
        require(!guidedConsent || SimpleWorkflow.canConvert(info, s.erasedUid)) { "Erase and verify this tag before guided conversion" }
        val keysA = BambuKeys.derive(token.tag.id, KeyKind.A)
        val keysB = BambuKeys.derive(token.tag.id, KeyKind.B)
        val folder = java.io.File(context.applicationContext.filesDir, "conversion-backups")
        val stem = "convert-" + expectedUid.replace(" ", "") + "-" + System.currentTimeMillis()
        val backupFile = java.io.File(folder, "$stem-before.json")
        val stateFile = java.io.File(folder, "$stem-state.txt")
        val resultFile = java.io.File(folder, "$stem-after.json")
        fun durable(file: java.io.File, text: String) {
            java.io.FileOutputStream(file).use { out -> out.write(text.toByteArray(Charsets.UTF_8)); out.fd.sync() }
        }
        _state.update { it.copy(conversionReport = "CONVERSION PREFLIGHT — no writes yet") }
        try {
            val coroutine = currentCoroutineContext()
            val initialMessage = NdefBuilder.build(RecordKind.TEXT, "", language = "en").toByteArray()
            val result = manager.execute(token, MifareClassic::get) { mc ->
                require(mc.size == 1024 && mc.sectorCount == 16 && mc.blockCount == 64)
                val report = ClassicNdefConversion.run(AndroidClassicPort(mc), keysA, keysB, initialMessage,
                    WritePolicy(guidedConsent || s.developer, guidedConsent || s.allowTrailer, true),
                    guard = { coroutine.ensureActive(); manager.assertCurrent(token); require(NfcHexUtils.hex(token.tag.id) == expectedUid) },
                    backup = { sectors ->
                        require(folder.isDirectory || folder.mkdirs()) { "Backup folder unavailable" }
                        require(backupFile.createNewFile()) { "Backup filename already exists" }
                        durable(backupFile, DumpExporter.json(info, sectors))
                        log("REDACTED CONVERSION BACKUP SAVED: " + backupFile.name)
                        _state.update { it.copy(sectors = sectors) }
                    },
                    phase = { phase ->
                        log(phase)
                        _state.update { it.copy(message = phase, conversionReport = phase + "\nBackup: " + backupFile.name) }
                        runCatching { durable(stateFile, "UID: $expectedUid\n$phase\nBackup: " + backupFile.name) }
                    })
                durable(resultFile, DumpExporter.json(info, report.sectors))
                report
            }
            when (result) {
                is NfcResult.Ok -> {
                    _state.update { it.copy(sectors = result.value.sectors, erasedUid = null, pendingFormatUid = expectedUid,
                        message = "CONVERSION RAW VERIFIED — remove and re-present tag to verify Android NDEF") }
                    expireSession()
                }
                is NfcResult.Error -> {
                    _state.update { it.copy(erasedUid = null, conversionReport = "CONVERSION NOT VERIFIED\n" + it.conversionReport + "\n" + result.message) }
                    error(result)
                    expireSession()
                }
            }
        } finally {
            keysA.forEach { it.fill(0) }; keysB.forEach { it.fill(0) }
            _state.update { it.copy(developer = false, allowTrailer = false, keyInput = "") }
        }
    }
    fun authenticate(defaultKey: Boolean = false) = task {
        val token = session ?: throw IllegalArgumentException("Re-present the tag")
        val s = _state.value
        val (sector, _) = selection(s)
        val key = if (defaultKey) MifareClassic.KEY_DEFAULT.copyOf() else NfcHexUtils.key(s.keyInput)
        try {
            when (val result = classic.authenticate(token, sector, s.keyKind, key)) {
                is NfcResult.Error -> error(result)
                is NfcResult.Ok -> {
                    val status = if (result.value) "AUTH ${s.keyKind}: SUCCESS" else "AUTH FAILED"
                    _state.update { it.copy(sectors = it.sectors.map { row -> if (row.layout.sector == sector) row.copy(auth = status) else row }, message = status) }
                }
            }
        } finally { key.fill(0) }
    }
    fun readBlock() = task {
        val token = session ?: throw IllegalArgumentException("Re-present the tag")
        val s = _state.value
        val (sector, block) = selection(s)
        val key = NfcHexUtils.key(s.keyInput)
        try {
            when(val result = classic.read(token, sector, block, s.keyKind, key)) {
                is NfcResult.Error -> error(result)
                is NfcResult.Ok -> { updateBlock(result.value, "AUTH ${s.keyKind}: SUCCESS"); message("READ BLOCK $block OK") }
            }
        } finally { key.fill(0) }
    }
    fun dump() = task {
        val token = session ?: throw IllegalArgumentException("Re-present the tag")
        val s = _state.value
        val info = s.info?.mifare ?: throw UnsupportedOperationException()
        val key = NfcHexUtils.key(s.keyInput)
        try {
            when(val result = classic.dump(token, info, s.keyKind, key)) {
                is NfcResult.Error -> error(result)
                is NfcResult.Ok -> {
                    _state.update { it.copy(sectors = result.value.sectors, message = result.value.status) }
                    if (!result.value.complete) expireSession()
                }
            }
        } finally { key.fill(0) }
    }
    // Read current contents before showing a confirmation. No writing occurs here.
    fun prepareWrite() = task {
        val token = session ?: throw IllegalArgumentException("Re-present the tag")
        val s = _state.value
        val (sector, block) = selection(s)
        val layout = BlockPolicy.layoutFor(s.info!!.mifare!!.layouts, sector, block)
        BlockPolicy.validate(layout, block, s.developer, s.allowTrailer, confirmed = true)
        val data = NfcHexUtils.block(s.dataInput)
        if (block == layout.trailer) require(BlockPolicy.validAccessBits(data)) { "INVALID TRAILER ACCESS BITS" }
        val key = NfcHexUtils.key(s.keyInput)
        try {
            when(val result = classic.read(token, sector, block, s.keyKind, key)) {
                is NfcResult.Error -> error(result)
                is NfcResult.Ok -> {
                    updateBlock(result.value, "AUTH ${s.keyKind}: SUCCESS")
                    _state.update { it.copy(pending = PendingWrite(token, sector, block, result.value.bytes!!, data, result.value.trailer), message = "Review current and new block data") }
                }
            }
        } finally { key.fill(0) }
    }
    fun cancelWrite() { _state.update { it.copy(pending = null) } }
    fun confirmWrite(trailerConfirmed: Boolean) = task {
        val s = _state.value
        val pending = s.pending ?: throw IllegalArgumentException("Read the block and confirm again")
        _state.update { it.copy(pending = null) }
        manager.assertCurrent(pending.session)
        val key = NfcHexUtils.key(s.keyInput)
        try {
            when(val result = classic.write(pending.session, pending.sector, pending.block, s.keyKind, key,
                pending.data, pending.current, WritePolicy(s.developer, s.allowTrailer, trailerConfirmed))) {
                is NfcResult.Error -> error(result)
                is NfcResult.Ok -> {
                    updateBlock(result.value.block)
                    message(result.value.status)
                }
            }
        } finally { key.fill(0) }
    }
    private fun applyNdef(result: NfcResult<NdefSnapshot>) {
        when(result) {
            is NfcResult.Error -> error(result)
            is NfcResult.Ok -> _state.update { it.copy(ndef = result.value, message = result.value.status) }
        }
    }
    fun readNdef() = task {
        val token = session ?: throw IllegalArgumentException("Re-present the tag")
        applyNdef(ndef.read(token))
    }
    fun writeNdef(kind: RecordKind, value: String, type: String, language: String, expectedGeneration: Long) = task {
        val token = session ?: throw IllegalArgumentException("Re-present the tag")
        require(token.generation == expectedGeneration) { "TAG CHANGED — confirm again" }
        val message = NdefBuilder.build(kind, value, type, language)
        when(val result = ndef.write(token, message)) {
            is NfcResult.Error -> error(result)
            is NfcResult.Ok -> _state.update { it.copy(ndef = result.value.second, message = result.value.second.status) }
        }
    }
    fun formatNdef(expectedGeneration: Long) = task {
        val token = session ?: throw IllegalArgumentException("Re-present the tag")
        require(token.generation == expectedGeneration) { "TAG CHANGED — confirm again" }
        when(val result = ndef.format(token)) {
            is NfcResult.Error -> error(result)
            is NfcResult.Ok -> {
                _state.update { it.copy(pendingFormatUid = it.info?.uid, message = result.value) }
                expireSession()
            }
        }
    }
    fun generation(): Long? = session?.generation
    fun export(open: () -> OutputStream?, json: Boolean) {
        val snapshot = _state.value
        task {
            try {
                withContext(Dispatchers.IO) {
                    val content = if (json) DumpExporter.json(snapshot.info, snapshot.sectors) else DumpExporter.txt(snapshot.info, snapshot.sectors)
                    (open() ?: throw java.io.IOException()).bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                }
                message("EXPORT SAVED")
                log("EXPORT " + if (json) "JSON" else "TXT")
            } catch (_: SecurityException) { message("EXPORT ACCESS DENIED") }
              catch (_: java.io.IOException) { message("EXPORT FAILED") }
        }
    }
}
