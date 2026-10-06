package pl.misieklab.nfccardlab.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.misieklab.nfccardlab.*
import pl.misieklab.nfccardlab.nfc.*

private val Cyan = Color(0xFF3EE7D8)
private val Amber = Color(0xFFFFC36B)
private val Red = Color(0xFFFF8585)
private val Green = Color(0xFF8AD6A6)
private val Background = Color(0xFF02050A)
private val CardColor = Color(0xFF0A1525)
private val scheme = darkColorScheme(
    primary = Cyan, background = Background, surface = CardColor,
    onPrimary = Color(0xFF00201D), onBackground = Color(0xFFE8F0FA),
    onSurface = Color(0xFFE8F0FA), error = Red, secondary = Color(0xFF8BA6BD)
)
private enum class Tab { SCAN, DETAILS, MEMORY, WRITE, NDEF, APDU, LOG }
private data class NdefConfirmation(val generation: Long, val kind: RecordKind, val value: String, val type: String, val language: String, val size: Int)

@Composable
fun LabApp(model: LabViewModel) {
    val s by model.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.SCAN) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("interface", android.content.Context.MODE_PRIVATE) }
    var advanced by remember { mutableStateOf(preferences.getBoolean("advanced", false)) }
    var simpleTab by remember { mutableStateOf(SimpleTab.TAG) }
    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { model.export({ context.contentResolver.openOutputStream(it, "wt") }, true) }
    }
    val txtExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { model.export({ context.contentResolver.openOutputStream(it, "wt") }, false) }
    }
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = Background,
            topBar = {
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("NFC", color = Cyan, fontWeight = FontWeight.Black, fontSize = 27.sp)
                            Text("CARD LAB", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                        Column {
                            TextButton(onClick = { showSettings = true }) { Text("⚙ Ustawienia") }
                            if (advanced) Row {
                                TextButton(onClick = { tab = Tab.APDU }) { Text("APDU") }
                                TextButton(onClick = { tab = Tab.LOG }) { Text("LOG") }
                            }
                        }
                    }
                    Text(if (advanced) "CONTACTLESS DIAGNOSTICS" else "Twój tag, tekst lub link", fontSize = 11.sp, color = scheme.secondary)
                    Text(if (advanced) "● ${s.reader}" else if (s.reader == "LIVE") "● NFC aktywne" else "● Włącz NFC w telefonie", color = if (s.reader == "LIVE") Cyan else Amber, fontSize = 12.sp)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = CardColor) {
                    if (!advanced) SimpleTab.entries.forEach { item ->
                        NavigationBarItem(selected = simpleTab == item, onClick = { simpleTab = item },
                            icon = { Text(item.symbol, fontSize = 21.sp) }, label = { Text(item.label) })
                    }
                    else listOf(Tab.SCAN, Tab.DETAILS, Tab.MEMORY, Tab.WRITE, Tab.NDEF).forEach { item ->
                        NavigationBarItem(
                            selected = tab == item, onClick = { tab = item },
                            icon = { Text(when(item) { Tab.SCAN -> "◎"; Tab.DETAILS -> "≡"; Tab.MEMORY -> "▦"; Tab.WRITE -> "✎"; else -> "N" }, fontSize = 21.sp) },
                            label = { Text(item.name, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                val statusColor = when {
                    s.busy -> Cyan
                    s.message.contains("NOT VERIFIED") -> Red
                    s.message.contains("FAILED") || s.message.contains("ERROR") || s.message.contains("LOST") -> Red
                    s.message.contains("PROTECTED") || s.message.contains("RE-TAP") -> Amber
                    s.message.contains("VERIFIED") || s.message.contains("SUCCESS") -> Green
                    else -> scheme.onSurface
                }
                Panel(if (advanced) "OPERATION STATUS" else "Co teraz?") {
                    Text(if (advanced) s.message else SimpleWorkflow.message(s.message, s.busy, s.sessionReady), color = statusColor)
                }
                if (!advanced) SimpleScreen(s, model, simpleTab, { simpleTab = it })
                else when(tab) {
                    Tab.SCAN -> ScanScreen(s, model)
                    Tab.DETAILS -> DetailsScreen(s)
                    Tab.MEMORY -> MemoryScreen(s, model,
                        { jsonExport.launch("nfc-${s.info?.uid?.replace(" ", "") ?: "dump"}.json") },
                        { txtExport.launch("nfc-${s.info?.uid?.replace(" ", "") ?: "dump"}.txt") })
                    Tab.WRITE -> WriteScreen(s, model)
                    Tab.NDEF -> NdefScreen(s, model)
                    Tab.APDU -> ApduScreen(s)
                    Tab.LOG -> Panel("LOCAL OPERATION LOG — LAST 400 EVENTS") {
                        if (s.logs.isEmpty()) Text("No operations yet.")
                        s.logs.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                        Text("Authentication keys and NDEF payloads are never logged.", color = scheme.secondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (showSettings) AlertDialog(
            onDismissRequest = { showSettings = false }, title = { Text("Ustawienia") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tryb zaawansowany", modifier = Modifier.weight(1f))
                        Switch(checked = advanced, enabled = !s.busy, onCheckedChange = {
                            advanced = it
                            preferences.edit().putBoolean("advanced", it).apply()
                            model.settings(false, false)
                        })
                    }
                    Text("Sektory, klucze, pamięć HEX, eksport i dziennik są w trybie zaawansowanym.")
                    if (advanced) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Developer mode")
                        Switch(checked = s.developer, onCheckedChange = { model.settings(it, false) }, enabled = !s.busy)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Allow sector trailer writes", modifier = Modifier.weight(1f))
                        Switch(checked = s.allowTrailer, onCheckedChange = { model.settings(s.developer, it) }, enabled = s.developer && !s.busy)
                    }
                    Text("Block 0 is always protected. Trailer writes require another confirmation and valid access-bit encoding.", color = Amber)
                    Text("Key input and developer permissions are cleared when the app leaves the foreground.")
                    }
                    Text("Pierwszy blok z identyfikatorem taga jest zawsze chroniony.")
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text("USTAWIENIA NFC TELEFONU") }
                }
            }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("GOTOWE") } }
        )
        s.pending?.let { pending ->
            var secondConfirmation by remember(pending) { mutableStateOf(false) }
            var phrase by remember(pending) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = model::cancelWrite,
                title = { Text(if (secondConfirmation) "CONFIRM SECTOR TRAILER" else "WRITE BLOCK ${pending.block}?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("UID", s.info?.uid)
                        Field("Sector / Block", "${pending.sector} / ${pending.block}")
                        Field("Current", DumpExporter.publicHex(BlockData(pending.sector, pending.block, pending.trailer, pending.current)))
                        Field("New", DumpExporter.publicHex(BlockData(pending.sector, pending.block, pending.trailer, pending.data)))
                        if (pending.trailer) {
                            Text("Changing access bits or keys can permanently prevent further access. Key slots are hidden here. Full key-byte readback is unavailable.", color = Amber)
                            if (secondConfirmation) {
                                Text("Type WRITE TRAILER to authorize this sector trailer write.")
                                OutlinedTextField(phrase, { phrase = it }, label = { Text("Confirmation") }, singleLine = true)
                            }
                        } else Text("The current block will be re-read before writing; then the write will be verified.")
                    }
                },
                dismissButton = { TextButton(onClick = model::cancelWrite) { Text("CANCEL") } },
                confirmButton = {
                    TextButton(
                        enabled = !s.busy && (!secondConfirmation || phrase == "WRITE TRAILER"),
                        onClick = { if (pending.trailer && !secondConfirmation) secondConfirmation = true else model.confirmWrite(pending.trailer) }
                    ) { Text(if (pending.trailer && !secondConfirmation) "REVIEW TRAILER" else "WRITE") }
                }
            )
        }
        s.selectedBlock?.let { block ->
            val clipboard = LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = { model.selectBlock(null) },
                title = { Text("BLOCK DETAILS — B${block.block}") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Sector", block.sector.toString())
                        Field("Type", if (block.block == 0) "MANUFACTURER / PROTECTED" else if (block.trailer) "SECTOR TRAILER / PROTECTED BY DEFAULT" else "DATA")
                        BlockBytes(block)
                        if (block.trailer) TrailerFields(block)
                    }
                },
                dismissButton = { TextButton(onClick = { model.selectBlock(null) }) { Text("CLOSE") } },
                confirmButton = {
                    Column {
                        TextButton(enabled = block.bytes != null, onClick = {
                            clipboard.setText(AnnotatedString(DumpExporter.publicHex(block) ?: "NOT READ"))
                        }) { Text("COPY BLOCK") }
                        TextButton(enabled = s.sessionReady && !s.busy, onClick = {
                            model.editSelected(block); model.readBlock()
                        }) { Text("READ") }
                        TextButton(onClick = { model.editSelected(block); tab = Tab.WRITE }) { Text("OPEN WRITE EDITOR") }
                    }
                }
            )
        }
    }
}
@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardColor)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
@Composable
private fun Field(name: String, value: String?) {
    Column {
        Text(name.uppercase(), fontSize = 10.sp, color = scheme.secondary)
        Text(value ?: "UNKNOWN", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
    }
}
@Composable
private fun NoTag() = Panel("WAITING FOR TAG") { Text("Hold your own NFC tag against the back of the phone. Keep it in place for the entire operation.") }
@Composable
private fun ScanScreen(s: LabState, model: LabViewModel) {
    val info = s.info
    if (info == null) { NoTag(); return }
    Panel("TECHNOLOGY") {
        Text(info.classification, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Field("UID HEX", info.uid)
        Field("UID DEC — unsigned big endian", info.uidDecimal)
        Field("UID ASCII", info.uidAscii ?: "NON-PRINTABLE")
        Field("UID LENGTH", "${info.uidLength} B")
        Field("ISO / STANDARD", info.standard)
        Field("ATQA — Android API byte order", info.atqa)
        Field("SAK", info.sak)
        Field("NDEF STATUS", s.ndef?.status ?: info.ndefStatus)
        Field("NDEF WRITABLE", s.ndef?.writable?.let { if (it) "YES" else "NO" } ?: info.writable)
        Field("Tag capacity — NDEF message", (s.ndef?.capacity ?: info.capacity)?.let { "$it B" })
        Field("MIFARE size", info.mifare?.size?.let { "$it B" })
        Field("MIFARE type", info.mifare?.type)
        Field("Sectors / Blocks / Block size", info.mifare?.let { "${it.sectorCount} / ${it.blockCount} / 16 B" })
        Text(if (s.sessionReady) "TAG DETECTED — connections open during I/O" else "LAST OBSERVATION — re-present tag", color = Amber)
        Text("Raw MIFARE writability depends on authentication and sector access conditions.", color = scheme.secondary)
    }
    Panel("TECH LIST") { info.technologies.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) } }
    TextButton(onClick = { model.message("Remove the tag and re-present it to refresh the Android technology list.") }) { Text("RESCAN INSTRUCTIONS") }
}
@Composable
private fun DetailsScreen(s: LabState) {
    val info = s.info ?: run { NoTag(); return }
    Panel("NFC-A") {
        Field("ATQA", info.atqa); Field("SAK", info.sak)
        Field("Max transceive length", info.nfcAMaxTransceive?.let { "$it B" })
    }
    Panel("MIFARE CLASSIC") {
        Field("Classification", info.classification)
        Field("Type", info.mifare?.type)
        Field("Size", info.mifare?.size?.let { "$it B" })
        Field("sectorCount", info.mifare?.sectorCount?.toString())
        Field("blockCount", info.mifare?.blockCount?.toString())
        Text("No silicon model, vendor or authenticity is inferred from SAK.")
    }
}
@Composable
private fun AuthControls(s: LabState, model: LabViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyKind.entries.forEach { kind ->
            FilterChip(selected = s.keyKind == kind, onClick = { model.setKeyKind(kind) }, enabled = !s.busy, label = { Text("KEY $kind") })
        }
    }
    HexEditor("KEY — 6 BYTES (memory only)", s.keyInput, 6, model::setKey, !s.busy, secret = true, onError = model::message)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { model.authenticate() }, enabled = s.sessionReady && !s.busy && validHex(s.keyInput, 6)) { Text("AUTHENTICATE", fontSize = 11.sp) }
        OutlinedButton(onClick = {
            model.setKey(NfcHexUtils.hex(android.nfc.tech.MifareClassic.KEY_DEFAULT))
            model.authenticate(defaultKey = true)
        }, enabled = s.sessionReady && !s.busy) { Text("TRY DEFAULT KEY", fontSize = 11.sp) }
    }
    Text("One explicitly supplied key per attempt. No key search.", color = scheme.secondary, fontSize = 12.sp)
}
@Composable
private fun MemoryScreen(s: LabState, model: LabViewModel, exportJson: () -> Unit, exportTxt: () -> Unit) {
    val info = s.info ?: run { NoTag(); return }
    if (info.mifare == null) {
        Panel("RAW MEMORY") { Text(NOT_SUPPORTED + "\nMifareClassic technology is unavailable. NDEF is available in its own tab when supported.") }
        return
    }
    if (s.eraseReport.isNotEmpty()) Panel("ERASE REPORT") { Text(s.eraseReport) }
    EraseControls(s, model)
    Panel("AUTHENTICATION") {
        SelectionFields(s, model)
        AuthControls(s, model)
        Button(onClick = model::dump, enabled = s.sessionReady && !s.busy && validHex(s.keyInput, 6), modifier = Modifier.fillMaxWidth()) { Text("READ ALL ACCESSIBLE") }
    }
    Panel("EXPORT OWN READ") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = exportJson, enabled = !s.busy) { Text("JSON") }
            OutlinedButton(onClick = exportTxt, enabled = !s.busy) { Text("TXT") }
        }
        Text("Unread blocks stay NOT READ. Trailer key slots are redacted.")
    }
    s.sectors.forEach { sector ->
        var expanded by remember(info.uid, sector.layout.sector) { mutableStateOf(sector.layout.sector == 0) }
        Card(colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SECTOR %02d".format(sector.layout.sector), color = Cyan, fontWeight = FontWeight.Bold)
                    Text("${sector.auth} ${if (expanded) "▴" else "▾"}", color = if (sector.auth.contains("SUCCESS")) Green else Amber, fontSize = 11.sp)
                }
                if (expanded) sector.blocks.forEach { block ->
                    Column(Modifier.fillMaxWidth().clickable { model.selectBlock(block) }.padding(vertical = 6.dp)) {
                        Text("B%03d".format(block.block) + if (block.trailer) "  [SECTOR TRAILER]" else if (block.block == 0) "  [MANUFACTURER]" else "  DATA", color = if (block.trailer || block.block == 0) Amber else Cyan, fontSize = 12.sp)
                        BlockBytes(block)
                        HorizontalDivider(color = Color(0xFF193049))
                    }
                }
            }
        }
    }
}
@Composable
private fun BlockBytes(block: BlockData) {
    val hex = DumpExporter.publicHex(block)
    Text(if (hex == null) block.status else hex.split(' ').chunked(8).joinToString("\n") { it.joinToString(" ") },
        fontFamily = FontFamily.Monospace, fontSize = 16.sp)
    DumpExporter.publicAscii(block)?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = scheme.secondary) }
}
@Composable
private fun EraseControls(s: LabState, model: LabViewModel) {
    val context = LocalContext.current
    val info = s.info ?: return
    var confirmedGeneration by remember { mutableStateOf<Long?>(null) }
    var phrase by remember { mutableStateOf("") }
    LaunchedEffect(s.sessionReady, info.uid) {
        if (!s.sessionReady) { confirmedGeneration = null; phrase = "" }
    }
    if (s.developer && s.allowTrailer && info.uidLength == 4 && info.mifare?.size == 1024) {
        Panel("CONTROLLED BAMBU DATA ERASE") {
            Text("Own Bambu filament tag only. Preflight all 16 sectors and save a backup without keys before any write. Temporarily change access bits, erase 47 data blocks, restore original permissions. UID and keys preserved. Not an NDEF conversion.", color = Amber)
            Button(onClick = { confirmedGeneration = model.generation(); phrase = "" },
                enabled = s.sessionReady && !s.busy, modifier = Modifier.fillMaxWidth()) { Text("ERASE BAMBU DATA") }
        }
    }
    confirmedGeneration?.let { generation ->
        AlertDialog(onDismissRequest = { confirmedGeneration = null },
            title = { Text("CONFIRM DATA ERASE + TRAILER ACCESS CHANGES") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("UID: " + info.uid)
                    Text("16 sectors; 47 data blocks; manufacturer block 0 excluded. Sector trailer key values and GPB preserved. Original access bits restored after each sector. Keep tag in place until complete. Existing filament data will be removed.")
                    Text("Type ERASE " + info.uid.replace(" ", "") + " to authorize data erasure and temporary trailer access changes.")
                    OutlinedTextField(phrase, { phrase = it }, label = { Text("Erase confirmation") }, singleLine = true)
                }
            },
            dismissButton = { TextButton(onClick = { confirmedGeneration = null }) { Text("CANCEL") } },
            confirmButton = {
                TextButton(onClick = {
                    confirmedGeneration = null
                    model.eraseBambuData(context, info.uid, generation)
                }, enabled = phrase == "ERASE " + info.uid.replace(" ", "") && !s.busy) { Text("ERASE DATA") }
            })
    }
}
@Composable
private fun TrailerFields(block: BlockData) {
    Field("KEY A — bytes 0..5", "NOT RELIABLY READABLE — hidden")
    Field("ACCESS BITS + GPB — bytes 6..9", block.bytes?.copyOfRange(6, 10)?.let(NfcHexUtils::hex))
    Field("ACCESS BITS — bytes 6..8", block.bytes?.copyOfRange(6, 9)?.let(NfcHexUtils::hex))
    Field("GPB — byte 9", block.bytes?.copyOfRange(9, 10)?.let(NfcHexUtils::hex))
    Field("KEY B — bytes 10..15", "Hidden; visibility depends on access conditions")
    Text("Zero-filled key slots are not proof of zero-valued keys.", color = Amber)
}
@Composable
private fun SelectionFields(s: LabState, model: LabViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(s.sectorInput, { if (it.all(Char::isDigit)) model.setSector(it) }, modifier = Modifier.weight(1f),
            label = { Text("Sector") }, singleLine = true, enabled = !s.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(s.blockInput, { if (it.all(Char::isDigit)) model.setBlock(it) }, modifier = Modifier.weight(1f),
            label = { Text("Absolute block") }, singleLine = true, enabled = !s.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    val layout = s.info?.mifare?.layouts?.find { it.sector.toString() == s.sectorInput }
    Text(layout?.let { "Sector range ${it.firstBlock}..${it.trailer}; trailer ${it.trailer}" } ?: "Select a valid sector", fontSize = 12.sp, color = scheme.secondary)
}
@Composable
private fun WriteScreen(s: LabState, model: LabViewModel) {
    val info = s.info ?: run { NoTag(); return }
    if (info.mifare == null) { Panel("RAW WRITE") { Text(NOT_SUPPORTED) }; return }
    Panel("TAG") {
        Field("Tag", info.classification); Field("UID", info.uid)
        Text(if (s.sessionReady) "TAG DETECTED — hold in place" else "RE-PRESENT TAG", color = if (s.sessionReady) Cyan else Amber)
    }
    Panel("RAW MIFARE CLASSIC WRITE") {
        Text("Data-block writes replace content and may invalidate NDEF/application data. Use a block reserved for experiments.", color = Amber)
        SelectionFields(s, model)
        AuthControls(s, model)
        val block = s.blockInput.toIntOrNull()
        val layout = info.mifare.layouts.find { it.sector.toString() == s.sectorInput }
        val protected = block == 0 || (block == layout?.trailer && !(s.developer && s.allowTrailer))
        if (protected) Text(BlockPolicy.PROTECTED, color = Amber)
        if (block == layout?.trailer && s.developer && s.allowTrailer) Text("DEVELOPER TRAILER WRITE — changing key slots and access conditions", color = Amber)
        HexEditor("DATA — 16 BYTES", s.dataInput, 16, model::setData, !s.busy, onError = model::message)
        val canRead = s.sessionReady && !s.busy && layout?.contains(block ?: -1) == true && validHex(s.keyInput, 6)
        OutlinedButton(onClick = model::readBlock, enabled = canRead, modifier = Modifier.fillMaxWidth()) { Text("READ BLOCK") }
        val canWrite = canRead && validHex(s.dataInput, 16) && !protected
        OutlinedButton(onClick = model::prepareWrite, enabled = canWrite, modifier = Modifier.fillMaxWidth()) { Text("WRITE") }
        Button(onClick = model::prepareWrite, enabled = canWrite, modifier = Modifier.fillMaxWidth()) { Text("WRITE + VERIFY") }
        Text("Both write buttons always verify. Data blocks require a full 16-byte match.", fontSize = 12.sp, color = scheme.secondary)
    }
}
private fun validHex(input: String, size: Int) = runCatching { NfcHexUtils.parse(input, size) }.isSuccess
@Composable
private fun HexEditor(label: String, text: String, expected: Int, onChange: (String) -> Unit, enabled: Boolean, secret: Boolean = false, onError: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var field by remember { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) { if (text != field.text) field = TextFieldValue(text, TextRange(text.length)) }
    fun accept(value: TextFieldValue) {
        if (value.text.any { !it.isWhitespace() && it !in "0123456789abcdefABCDEF" }) {
            onError("HEX INPUT REJECTED — only 0–9, A–F and whitespace are allowed")
            return
        }
        val digitsBefore = value.text.take(value.selection.start).count { !it.isWhitespace() }
        val formatted = NfcHexUtils.editor(value.text)
        val cursor = (digitsBefore + if (digitsBefore > 0) (digitsBefore - 1) / 2 else 0).coerceAtMost(formatted.length)
        field = TextFieldValue(formatted, TextRange(cursor))
        onChange(formatted)
    }
    val digits = text.count { !it.isWhitespace() }
    OutlinedTextField(field, ::accept, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        enabled = enabled, minLines = if (secret) 1 else 3,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 18.sp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
        isError = digits > expected * 2 || digits % 2 != 0)
    Text("${digits / 2} / $expected bytes" + if (digits % 2 == 1) " — incomplete byte" else "", color = if (validHex(text, expected)) Green else Amber)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = { clipboard.getText()?.text?.let { accept(TextFieldValue(it, TextRange(it.length))) } }, enabled = enabled) { Text("PASTE") }
        TextButton(onClick = { field = TextFieldValue(""); onChange("") }, enabled = enabled) { Text("CLEAR") }
        if (!secret) TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }, enabled = enabled && text.isNotEmpty()) { Text("COPY") }
    }
    if (!secret && validHex(text, expected)) Field("ASCII preview", NfcHexUtils.ascii(NfcHexUtils.parse(text)))
}
@Composable
private fun ConvertControls(s: LabState, model: LabViewModel) {
    val context = LocalContext.current
    val info = s.info ?: return
    var generation by remember { mutableStateOf<Long?>(null) }
    var phrase by remember { mutableStateOf("") }
    LaunchedEffect(s.sessionReady, info.uid) { if (!s.sessionReady) { generation = null; phrase = "" } }
    if (s.conversionReport.isNotEmpty()) Panel("CONVERSION RESULT") { Text(s.conversionReport, color = Amber) }
    if (s.developer && s.allowTrailer && info.uidLength == 4 && info.mifare?.size == 1024 &&
        "android.nfc.tech.Ndef" !in info.technologies) {
        Panel("BAMBU CLASSIC TO NDEF") {
            Text("Cleared own tag only. Changes Key A to standard public NFC keys and changes permissions. Original Key B, UID and manufacturer block preserved. Backup before writes; rollback attempted on failure. Keep the tag still and keep this app open.", color = Amber)
            Button(onClick = { generation = model.generation(); phrase = "" }, enabled = s.sessionReady && !s.busy) { Text("CONVERT CLEARED TAG TO NDEF") }
        }
    }
    generation?.let { token ->
        val expected = "CONVERT " + info.uid.replace(" ", "")
        AlertDialog(onDismissRequest = { generation = null }, title = { Text("CHANGE SECTOR TRAILERS?") },
            text = { Column {
                Text("UID: " + info.uid + "\nReplaces the Bambu format with MAD/NDEF. Key A and access bits change in all 16 sectors. UID / Block 0 and original Key B stay intact. Do not remove the tag or leave the app during conversion.")
                Text("Type: " + expected)
                OutlinedTextField(phrase, { phrase = it.uppercase() }, singleLine = true, label = { Text("Confirmation phrase") })
            } },
            dismissButton = { TextButton(onClick = { generation = null }) { Text("CANCEL") } },
            confirmButton = { TextButton(onClick = { generation = null; model.convertBambuNdef(context, info.uid, token) }, enabled = phrase == expected && !s.busy && s.sessionReady) { Text("CONVERT") } })
    }
}
@Composable
private fun NdefScreen(s: LabState, model: LabViewModel) {
    val info = s.info ?: run { NoTag(); return }
    var kind by remember { mutableStateOf(RecordKind.TEXT) }
    var value by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("text/plain") }
    var language by remember { mutableStateOf("en") }
    var confirmation by remember { mutableStateOf<NdefConfirmation?>(null) }
    var formatGeneration by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(s.sessionReady) { if (!s.sessionReady) { confirmation = null; formatGeneration = null } }
    val hasNdef = "android.nfc.tech.Ndef" in info.technologies
    val formatable = "android.nfc.tech.NdefFormatable" in info.technologies
    Panel("NDEF STATUS") {
        Field("Status", s.ndef?.status ?: info.ndefStatus)
        Field("Writable", s.ndef?.writable?.let { if (it) "YES" else "NO" } ?: info.writable)
        Field("Capacity", (s.ndef?.capacity ?: info.capacity)?.let { "$it B" })
        s.ndef?.records?.forEachIndexed { index, record -> Field("Record $index", record) }
        if (hasNdef) OutlinedButton(onClick = model::readNdef, enabled = s.sessionReady && !s.busy) { Text("READ NDEF") }
        if (!hasNdef && formatable) {
            Text("TAG IS NOT NDEF FORMATTED", color = Amber)
            Button(onClick = { formatGeneration = model.generation() }, enabled = s.sessionReady && !s.busy) { Text("FORMAT AS NDEF") }
        }
        if (!hasNdef && !formatable) Text(NOT_SUPPORTED)
    }
    ConvertControls(s, model)
    if (hasNdef) Panel("CREATE NDEF RECORD") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecordKind.entries.forEach { item -> FilterChip(selected = kind == item, onClick = { kind = item }, label = { Text(item.name) }, enabled = !s.busy) }
        }
        if (kind == RecordKind.TEXT) OutlinedTextField(language, { language = it }, label = { Text("Language") }, enabled = !s.busy, singleLine = true)
        if (kind == RecordKind.MIME || kind == RecordKind.EXTERNAL) OutlinedTextField(type, { type = it }, label = { Text(if (kind == RecordKind.MIME) "MIME type" else "domain:type") }, enabled = !s.busy)
        OutlinedTextField(value, { value = it }, label = { Text(if (kind == RecordKind.URI) "Absolute URL / URI" else if (kind == RecordKind.APPLICATION) "Android package name" else "UTF-8 content") },
            modifier = Modifier.fillMaxWidth(), enabled = !s.busy, minLines = 2)
        val built = runCatching { NdefBuilder.build(kind, value, type, language) }
        val size = built.getOrNull()?.toByteArray()?.size
        val capacity = s.ndef?.capacity ?: info.capacity
        val writable = s.ndef?.writable ?: (info.writable == "YES")
        val valid = size != null && capacity != null && runCatching { NdefBuilder.validateCapacity(writable, capacity, size) }.isSuccess
        Text(if (size == null) built.exceptionOrNull()?.message ?: "Invalid record" else "Message: $size / ${capacity ?: "?"} bytes", color = if (valid) Green else Amber)
        Button(onClick = {
            model.generation()?.let { confirmation = NdefConfirmation(it, kind, value, type, language, size!!) }
        }, enabled = valid && s.sessionReady && !s.busy, modifier = Modifier.fillMaxWidth()) { Text("WRITE NDEF + VERIFY") }
    }
    confirmation?.let { c ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("REPLACE NDEF MESSAGE?") },
            text = { Text("UID: ${info.uid}\nRecord: ${c.kind}\n${c.value}\nSize: ${c.size} bytes\nThis replaces the tag's existing NDEF message.") },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("CANCEL") } },
            confirmButton = { TextButton(onClick = { confirmation = null; model.writeNdef(c.kind, c.value, c.type, c.language, c.generation) }, enabled = !s.busy) { Text("WRITE") } })
    }
    formatGeneration?.let { generation ->
        AlertDialog(onDismissRequest = { formatGeneration = null }, title = { Text("FORMAT AS NDEF?") },
            text = { Text("UID: ${info.uid}\nFormatting can replace existing memory structures. Use only a tag whose contents you can discard.\nA minimal empty Text record will be created; remove and re-present the tag afterward.") },
            dismissButton = { TextButton(onClick = { formatGeneration = null }) { Text("CANCEL") } },
            confirmButton = { TextButton(onClick = { formatGeneration = null; model.formatNdef(generation) }, enabled = !s.busy) { Text("FORMAT") } })
    }
}
@Composable
private fun ApduScreen(s: LabState) {
    Panel("ISO-DEP / APDU DIAGNOSTICS") {
        val info = s.info
        val supported = info?.technologies?.contains("android.nfc.tech.IsoDep") == true
        Text(if (supported) "IsoDep is available through Android NFC API" else NOT_SUPPORTED)
        Field("Max transceive length", info?.isoDepMaxTransceive?.let { "$it B" })
        Field("Historical bytes (NFC-A)", info?.historicalBytes)
        Field("Higher layer response (NFC-B)", info?.hiLayerResponse)
        Text("This version displays diagnostics only. Arbitrary APDU transmission is not implemented.")
    }
}
