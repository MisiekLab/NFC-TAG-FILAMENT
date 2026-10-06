package pl.misieklab.nfccardlab

import org.junit.Assert.*
import org.junit.Test
import pl.misieklab.nfccardlab.nfc.*

class SimpleWorkflowTest {
    private fun tag(uid: String = "42 27 04 26", ndef: Boolean = false, size: Int = 1024, uidLength: Int = 4) =
        NfcTagInfo(uid, "", null, uidLength, listOf("android.nfc.tech.MifareClassic") + if (ndef) listOf("android.nfc.tech.Ndef") else emptyList(),
            "MIFARE Classic compatible", "ISO/IEC 14443-A", "04 00", "08", 253,
            MifareInfo("TYPE_CLASSIC", size, 16, 64, emptyList()), "", "UNKNOWN", null, null, null, null)
    @Test fun readyTagCannotBeErasedOrConvertedEvenWithMatchingReceipt() {
        assertFalse(SimpleWorkflow.canPrepareBambu(tag(ndef = true)))
        assertFalse(SimpleWorkflow.canConvert(tag(ndef = true), "42 27 04 26"))
    }
    @Test fun conversionRequiresVerifiedEraseForSameUid() {
        assertFalse(SimpleWorkflow.canConvert(tag(), null))
        assertFalse(SimpleWorkflow.canConvert(tag(), "12 66 FA 25"))
        assertTrue(SimpleWorkflow.canConvert(tag(), "42 27 04 26"))
    }
    @Test fun unsupportedLayoutAndUidAreNotOfferedBambuPreparation() {
        assertFalse(SimpleWorkflow.canPrepareBambu(tag(size = 4096)))
        assertFalse(SimpleWorkflow.canPrepareBambu(tag(uidLength = 7)))
        assertFalse(SimpleWorkflow.canPrepareBambu(null))
    }
    @Test fun failedVerificationNeverDisplayedAsSuccess() {
        assertTrue(SimpleWorkflow.message("CONVERSION NOT VERIFIED", false, true).startsWith("Nie potwierdzono"))
        assertTrue(SimpleWorkflow.message("VERIFY FAILED", false, true).startsWith("Nie potwierdzono"))
    }
    @Test fun rawVerificationStillRequiresRetap() {
        assertTrue(SimpleWorkflow.message("CONVERSION RAW VERIFIED", false, false).contains("ponownie"))
    }
    @Test fun lostTagInstructionsRemainVisible() {
        assertTrue(SimpleWorkflow.message("TAG LOST", false, false).contains("Utracono kontakt"))
    }
    @Test fun previousSuccessNeverSuggestsRemovalDuringNewOperation() {
        assertTrue(SimpleWorkflow.message("NDEF WRITE VERIFIED", true, true).contains("Trzymaj tag"))
        assertFalse(SimpleWorkflow.message("NDEF WRITE VERIFIED", true, true).contains("Możesz odsunąć"))
    }
    @Test fun verifiedWriteAllowsRemoval() {
        assertTrue(SimpleWorkflow.message("NDEF WRITE VERIFIED", false, true).contains("Możesz odsunąć"))
    }
}
