package pl.misieklab.nfccardlab

import android.nfc.NdefRecord
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.misieklab.nfccardlab.nfc.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NdefAndExportTest {
    @Test fun textRecordUtf8() {
        val record = NdefBuilder.build(RecordKind.TEXT, "Test NFC — żółć", language = "pl").records.single()
        assertEquals(NdefRecord.TNF_WELL_KNOWN, record.tnf)
        assertArrayEquals(NdefRecord.RTD_TEXT, record.type)
        assertEquals("TEXT: Test NFC — żółć", NdefBuilder.describe(record))
        assertEquals(2, record.payload[0].toInt())
    }
    @Test fun minimalTextIsValid() { assertTrue(NdefBuilder.build(RecordKind.TEXT, "").toByteArray().isNotEmpty()) }
    @Test fun uriRecord() {
        val r = NdefBuilder.build(RecordKind.URI, "https://example.com").records.single()
        assertArrayEquals(NdefRecord.RTD_URI, r.type)
        assertEquals("https://example.com", r.toUri().toString())
    }
    @Test fun relativeUriRejected() { assertThrows(IllegalArgumentException::class.java) { NdefBuilder.build(RecordKind.URI, "example.com") } }
    @Test fun mimeRecord() {
        val r = NdefBuilder.build(RecordKind.MIME, "lab", "text/plain").records.single()
        assertEquals(NdefRecord.TNF_MIME_MEDIA, r.tnf)
        assertEquals("text/plain", r.toMimeType())
        assertArrayEquals("lab".toByteArray(), r.payload)
    }
    @Test fun externalType() {
        val r = NdefBuilder.build(RecordKind.EXTERNAL, "lab", "example.com:lab").records.single()
        assertEquals(NdefRecord.TNF_EXTERNAL_TYPE, r.tnf)
        assertEquals("example.com:lab", String(r.type))
    }
    @Test fun invalidTypesRejected() {
        assertThrows(IllegalArgumentException::class.java) { NdefBuilder.build(RecordKind.MIME, "", "bad") }
        assertThrows(IllegalArgumentException::class.java) { NdefBuilder.build(RecordKind.EXTERNAL, "", "bad") }
        assertThrows(IllegalArgumentException::class.java) { NdefBuilder.build(RecordKind.TEXT, "", language = "a".repeat(64)) }
    }
    @Test fun capacityExactFit() { val size = NdefBuilder.build(RecordKind.TEXT, "Test NFC").toByteArray().size; NdefBuilder.validateCapacity(true, size, size) }
    @Test fun overCapacityBlocked() { assertThrows(IllegalArgumentException::class.java) { NdefBuilder.validateCapacity(true, 15, 16) } }
    @Test fun readOnlyBlocked() { assertThrows(IllegalArgumentException::class.java) { NdefBuilder.validateCapacity(false, 100, 10) } }
    @Test fun exportsRedactTrailerKeysAndKeepData() {
        val layout = SectorLayout(1, 4, 4)
        val info = NfcTagInfo("12 66 FA 25", "308738597", null, 4, listOf("android.nfc.tech.MifareClassic"), "MIFARE Classic compatible",
            "ISO/IEC 14443-A", "04 00", "08", 253, MifareInfo("TYPE_CLASSIC", 1024, 16, 64, listOf(layout)), "UNKNOWN", "UNKNOWN", null, null, null, null)
        val trailer = NfcHexUtils.block("A1 A2 A3 A4 A5 A6 FF 07 80 69 B1 B2 B3 B4 B5 B6")
        val sectors = listOf(SectorData(layout, "AUTH A: SUCCESS", listOf(BlockData(1, 4, false, ByteArray(16)), BlockData(1, 7, true, trailer))))
        val json = DumpExporter.json(info, sectors)
        val txt = DumpExporter.txt(info, sectors)
        assertFalse(json.contains("A1 A2 A3")); assertFalse(json.contains("B1 B2 B3"))
        assertFalse(txt.contains("A1 A2 A3")); assertTrue(txt.contains("FF 07 80 69"))
        assertEquals("12 66 FA 25", JSONObject(json).getString("uid"))
        assertEquals(2, JSONObject(json).getJSONArray("sectors").getJSONObject(0).getJSONArray("blocks").length())
    }
}
