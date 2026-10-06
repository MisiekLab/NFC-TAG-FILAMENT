package pl.misieklab.nfccardlab.nfc

import android.nfc.FormatException
import android.nfc.TagLostException
import java.io.IOException
import kotlinx.coroutines.CancellationException

sealed interface NfcResult<out T> {
    data class Ok<T>(val value: T) : NfcResult<T>
    data class Error(val message: String, val lost: Boolean = false) : NfcResult<Nothing>
}
const val NOT_SUPPORTED = "NOT SUPPORTED BY ANDROID NFC API"
const val LOST_MESSAGE = "TAG LOST\nHold the NFC tag against the back of the phone until the operation is complete."
fun nfcError(error: Exception): NfcResult.Error = when (error) {
    is CancellationException -> throw error
    is TagLostException -> NfcResult.Error(LOST_MESSAGE, true)
    is FormatException -> NfcResult.Error("INVALID NDEF FORMAT")
    is SecurityException -> NfcResult.Error("NFC ACCESS DENIED OR STALE TAG — re-present the tag", true)
    is IOException -> NfcResult.Error("NFC I/O ERROR — hold the tag in place and re-present it. A write may have partially completed; read before retrying.")
    is IllegalArgumentException -> NfcResult.Error(error.message ?: "INVALID INPUT")
    is UnsupportedOperationException -> NfcResult.Error(NOT_SUPPORTED)
    else -> NfcResult.Error("NFC OPERATION FAILED — re-present the tag")
}
