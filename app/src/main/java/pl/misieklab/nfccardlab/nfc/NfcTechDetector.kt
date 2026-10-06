package pl.misieklab.nfccardlab.nfc

import android.nfc.Tag
import android.nfc.tech.*

object NfcTechDetector {
    fun inspect(tag: Tag): NfcTagInfo {
        val a = NfcA.get(tag)
        val classic = MifareClassic.get(tag)
        val ndef = Ndef.get(tag)
        val iso = IsoDep.get(tag)
        val mifare = classic?.let { mc ->
            val type = when (mc.type) {
                MifareClassic.TYPE_CLASSIC -> "TYPE_CLASSIC"
                MifareClassic.TYPE_PLUS -> "TYPE_PLUS"
                MifareClassic.TYPE_PRO -> "TYPE_PRO"
                else -> "TYPE_UNKNOWN"
            }
            MifareInfo(type, mc.size, mc.sectorCount, mc.blockCount,
                (0 until mc.sectorCount).map { SectorLayout(it, mc.sectorToBlock(it), mc.getBlockCountInSector(it)) })
        }
        return NfcTagInfo(
            NfcHexUtils.hex(tag.id), NfcHexUtils.uidDecimal(tag.id), NfcHexUtils.uidAscii(tag.id), tag.id.size,
            tag.techList.toList(),
            when {
                classic != null -> "MIFARE Classic compatible"
                iso != null -> "ISO-DEP"
                NfcV.get(tag) != null -> "NFC-V"
                NfcF.get(tag) != null -> "NFC-F"
                a != null -> "NFC-A"
                NfcB.get(tag) != null -> "NFC-B"
                else -> "NFC tag"
            },
            when {
                a != null && iso != null -> "ISO/IEC 14443-A / ISO/IEC 14443-4"
                a != null -> "ISO/IEC 14443-A"
                NfcB.get(tag) != null -> "ISO/IEC 14443-B"
                NfcV.get(tag) != null -> "ISO/IEC 15693"
                NfcF.get(tag) != null -> "JIS X 6319-4"
                else -> "UNKNOWN"
            },
            a?.atqa?.let(NfcHexUtils::hex), a?.sak?.let { "%02X".format(it.toInt() and 255) },
            a?.maxTransceiveLength, mifare,
            when { ndef != null -> "NDEF"; NdefFormatable.get(tag) != null -> "TAG IS NOT NDEF FORMATTED"; else -> NOT_SUPPORTED },
            ndef?.let { if (it.isWritable) "YES" else "NO" } ?: "UNKNOWN", ndef?.maxSize,
            iso?.maxTransceiveLength, iso?.historicalBytes?.let(NfcHexUtils::hex), iso?.hiLayerResponse?.let(NfcHexUtils::hex)
        )
    }
}
