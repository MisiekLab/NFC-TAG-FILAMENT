package pl.misieklab.nfccardlab

import pl.misieklab.nfccardlab.nfc.NfcTagInfo

/** Presentation and gating only; NFC preflight remains the authority for actual writes. */
object SimpleWorkflow {
    fun hasNdef(info: NfcTagInfo?) = info?.technologies?.contains("android.nfc.tech.Ndef") == true
    fun canPrepareBambu(info: NfcTagInfo?) = info != null && !hasNdef(info) &&
        info.uidLength == 4 && info.mifare?.let { it.size == 1024 && it.sectorCount == 16 && it.blockCount == 64 } == true
    fun canConvert(info: NfcTagInfo?, erasedUid: String?) = canPrepareBambu(info) && erasedUid != null && info?.uid == erasedUid
    fun message(raw: String, busy: Boolean, connected: Boolean): String = when {
        busy -> "Pracuję… Trzymaj tag nieruchomo przy tylnej części telefonu."
        raw.contains("NOT VERIFIED") || raw.contains("FAILED") || raw.contains("ERROR") ->
            "Nie potwierdzono powodzenia operacji. Nie traktuj taga jako gotowego. Szczegóły znajdziesz w trybie zaawansowanym."
        raw.contains("LOST") || raw.contains("SESSION EXPIRED") ->
            "Utracono kontakt z tagiem. Przyłóż go ponownie i trzymaj przy tylnej części telefonu do zakończenia operacji."
        raw.startsWith("NDEF WRITE VERIFIED") -> "Zapisano i sprawdzono! Możesz odsunąć tag."
        raw.startsWith("ERASE VERIFIED") -> "Stare dane usunięte i sprawdzone. Teraz wybierz „Przygotuj do linków”."
        raw.contains("CONVERSION RAW VERIFIED") || raw.contains("FORMAT COMMAND COMPLETED") ->
            "Odsuń tag, a potem przyłóż ten sam tag ponownie. Sprawdzimy, czy jest gotowy do zapisu linków."
        raw.contains("NDEF FORMAT VERIFIED") -> "Tag jest gotowy. Przejdź do zakładki „Zapis”."
        raw.startsWith("Different UID") -> "To inny tag. Przyłóż ponownie tag, który właśnie przygotowano."
        raw.contains("PROTECTED") -> "Ten blok jest chroniony. Identyfikatora taga nie można nadpisać."
        raw == "TAG DETECTED" || raw.startsWith("NDEF READ") || raw == "NDEF EMPTY" ->
            "Tag rozpoznany. Wybierz, co chcesz zrobić poniżej."
        !connected -> "Przyłóż tag do tylnej części telefonu. Jeśli nie jest wykrywany, przesuń go powoli."
        else -> "Sprawdź wynik operacji w trybie zaawansowanym przed kolejną zmianą taga."
    }
}
