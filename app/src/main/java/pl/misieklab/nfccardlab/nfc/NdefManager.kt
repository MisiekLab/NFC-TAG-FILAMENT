package pl.misieklab.nfccardlab.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.nio.charset.Charset
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class RecordKind { TEXT, URI, MIME, EXTERNAL, APPLICATION }
object NdefBuilder {
    fun build(kind: RecordKind, value: String, type: String = "", language: String = "en"): NdefMessage {
        val record = when (kind) {
            RecordKind.APPLICATION -> {
                require(value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
                    "Wybierz aplikację lub wpisz poprawną nazwę pakietu, np. com.android.chrome"
                }
                NdefRecord.createApplicationRecord(value)
            }
            RecordKind.TEXT -> {
                require(language.matches(Regex("[a-zA-Z]{2,8}(-[a-zA-Z0-9]{1,8})*")) && language.toByteArray(Charsets.US_ASCII).size <= 63) { "Invalid language code" }
                NdefRecord.createTextRecord(language, value)
            }
            RecordKind.URI -> {
                val uri = android.net.Uri.parse(value)
                require(value.isNotBlank() && !uri.scheme.isNullOrBlank() && value.none(Char::isWhitespace)) { "Enter an absolute URI, for example https://example.com" }
                NdefRecord.createUri(uri)
            }
            RecordKind.MIME -> {
                require(type.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+"))) { "Enter a MIME type, for example text/plain" }
                NdefRecord.createMime(type, value.toByteArray(Charsets.UTF_8))
            }
            RecordKind.EXTERNAL -> {
                val parts = type.lowercase(Locale.ROOT).split(':', limit = 2)
                require(parts.size == 2 && parts[0].matches(Regex("[a-z0-9.-]+")) && parts[1].matches(Regex("[a-z0-9._-]+"))) { "Enter domain:type, for example example.com:lab" }
                NdefRecord.createExternal(parts[0], parts[1], value.toByteArray(Charsets.UTF_8))
            }
        }
        return NdefMessage(arrayOf(record))
    }
    fun validateCapacity(writable: Boolean, capacity: Int, messageBytes: Int) {
        require(writable) { "NDEF TAG IS READ ONLY" }
        require(capacity >= 0 && messageBytes in 1..capacity) { "NDEF MESSAGE TOO LARGE: $messageBytes / $capacity bytes" }
    }
    fun describe(record: NdefRecord): String {
        if (record.tnf == NdefRecord.TNF_EXTERNAL_TYPE &&
            record.type.contentEquals("android.com:pkg".toByteArray(Charsets.US_ASCII))) {
            return "APPLICATION: " + String(record.payload, Charsets.UTF_8)
        }
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            val p = record.payload
            if (p.isEmpty()) return "TEXT: invalid empty payload"
            val flags = p[0].toInt() and 255
            val langLength = flags and 63
            if (p.size < 1 + langLength) return "TEXT: invalid payload"
            val charset = if (flags and 128 == 0) Charsets.UTF_8 else Charset.forName("UTF-16")
            return "TEXT: " + String(p, 1 + langLength, p.size - 1 - langLength, charset)
        }
        record.toUri()?.let { return "URI: $it" }
        val label = when(record.tnf) {
            NdefRecord.TNF_MIME_MEDIA -> "MIME " + String(record.type, Charsets.US_ASCII)
            NdefRecord.TNF_EXTERNAL_TYPE -> "EXTERNAL " + String(record.type, Charsets.US_ASCII)
            else -> "TNF ${record.tnf} / TYPE ${NfcHexUtils.hex(record.type)}"
        }
        return "$label\nPayload HEX: ${NfcHexUtils.hex(record.payload)}\nUTF-8 preview: ${String(record.payload, Charsets.UTF_8)}"
    }
}
class NdefManager(private val arbiter: NfcManager, private val log: (String) -> Unit) {
    suspend fun read(token: TagSession) = arbiter.execute(token, Ndef::get) { ndef ->
        val message = ndef.ndefMessage
        log("NDEF READ OK")
        NdefSnapshot(if (message == null) "NDEF EMPTY" else "NDEF READ OK", ndef.isWritable, ndef.maxSize,
            message?.records?.map(NdefBuilder::describe) ?: emptyList())
    }
    suspend fun write(token: TagSession, message: NdefMessage) = arbiter.execute(token, Ndef::get) { ndef ->
        NdefBuilder.validateCapacity(ndef.isWritable, ndef.maxSize, message.toByteArray().size)
        currentCoroutineContext().ensureActive()
        arbiter.assertCurrent(token)
        ndef.writeNdefMessage(message)
        log("NDEF WRITE")
        val observed = ndef.ndefMessage
        val verified = observed != null && observed.toByteArray().contentEquals(message.toByteArray())
        log(if (verified) "NDEF WRITE VERIFIED" else "VERIFY FAILED")
        Pair(verified, NdefSnapshot(if (verified) "NDEF WRITE VERIFIED" else "VERIFY FAILED",
            ndef.isWritable, ndef.maxSize, observed?.records?.map(NdefBuilder::describe) ?: emptyList()))
    }
    suspend fun format(token: TagSession) = arbiter.execute(token, NdefFormatable::get) { formatable ->
        currentCoroutineContext().ensureActive()
        arbiter.assertCurrent(token)
        formatable.format(NdefBuilder.build(RecordKind.TEXT, "", language = "en"))
        log("NDEF FORMAT COMMAND COMPLETED — RE-TAP REQUIRED")
        "FORMAT COMMAND COMPLETED — remove and re-present the same tag to verify Ndef technology"
    }
}
