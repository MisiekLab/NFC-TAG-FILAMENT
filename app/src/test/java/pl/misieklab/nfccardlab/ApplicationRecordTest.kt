package pl.misieklab.nfccardlab

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.misieklab.nfccardlab.nfc.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], manifest = Config.NONE)
class ApplicationRecordTest {
    @Test fun standardAarHasExactPackageAndExternalType() {
        val record = NdefBuilder.build(RecordKind.APPLICATION, "com.android.chrome").records.single()
        assertEquals(NdefRecord.TNF_EXTERNAL_TYPE, record.tnf)
        assertArrayEquals("android.com:pkg".toByteArray(Charsets.US_ASCII), record.type)
        assertArrayEquals("com.android.chrome".toByteArray(Charsets.UTF_8), record.payload)
    }
    @Test fun appRecordRoundTripsExactlyAndDescribesAsApplication() {
        val msg = NdefBuilder.build(RecordKind.APPLICATION, "pl.misieklab.nfccardlab")
        val read = NdefMessage(msg.toByteArray())
        assertArrayEquals(msg.toByteArray(), read.toByteArray())
        assertEquals("APPLICATION: pl.misieklab.nfccardlab", NdefBuilder.describe(read.records.single()))
    }
    @Test fun badPackageNamesCannotProduceWritableMessage() {
        listOf("", "Chrome", "com.android chrome", "https://example.com", "com..chrome", " com.android.chrome", "com.android.chrome\n").forEach {
            assertThrows(IllegalArgumentException::class.java) { NdefBuilder.build(RecordKind.APPLICATION, it) }
        }
    }
    @Test fun aarCapacityAccountsForFullMessageNotOnlyPackage() {
        val packageName = "com.android.chrome"
        val messageSize = NdefBuilder.build(RecordKind.APPLICATION, packageName).toByteArray().size
        assertTrue(messageSize > packageName.toByteArray().size)
        NdefBuilder.validateCapacity(true, messageSize, messageSize)
        assertThrows(IllegalArgumentException::class.java) { NdefBuilder.validateCapacity(true, messageSize - 1, messageSize) }
        assertThrows(IllegalArgumentException::class.java) { NdefBuilder.validateCapacity(false, messageSize, messageSize) }
    }
}
