package pl.misieklab.nfccardlab.nfc

import java.math.BigInteger
import java.util.Locale

object NfcHexUtils {
    fun parse(input: String, expectedBytes: Int? = null): ByteArray {
        require(input.all { it.isWhitespace() || it in "0123456789abcdefABCDEF" }) { "Only HEX and whitespace are allowed" }
        val raw = input.filterNot(Char::isWhitespace)
        require(raw.isNotEmpty() && raw.length % 2 == 0) { "Enter complete byte pairs" }
        val bytes = raw.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        require(expectedBytes == null || bytes.size == expectedBytes) { "Expected $expectedBytes bytes" }
        return bytes
    }
    fun key(input: String) = parse(input, 6)
    fun block(input: String) = parse(input, 16)
    fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(Locale.ROOT, it.toInt() and 255) }
    fun rows(bytes: ByteArray) = bytes.toList().chunked(8).joinToString("\n") { hex(it.toByteArray()) }
    fun ascii(bytes: ByteArray) = bytes.joinToString("") { if ((it.toInt() and 255) in 32..126) it.toInt().toChar().toString() else "." }
    fun uidAscii(bytes: ByteArray): String? = if (bytes.isNotEmpty() && bytes.all { (it.toInt() and 255) in 32..126 }) ascii(bytes) else null
    fun uidDecimal(bytes: ByteArray) = if (bytes.isEmpty()) "0" else BigInteger(1, bytes).toString()
    // Reformat without truncating: invalid characters are rejected by the editor, oversized paste remains invalid.
    fun editor(input: String) = input.filterNot(Char::isWhitespace).uppercase(Locale.ROOT).chunked(2).joinToString(" ")
}

data class SectorLayout(val sector: Int, val firstBlock: Int, val count: Int) {
    init { require(sector >= 0 && firstBlock >= 0 && count > 0) }
    val trailer: Int get() = firstBlock + count - 1
    fun contains(block: Int) = block >= firstBlock && block <= trailer
}
object BlockPolicy {
    const val PROTECTED = "PROTECTED BLOCK\nManufacturer Block and Sector Trailers are protected by default because an incorrect write can permanently damage tag access."
    fun layoutFor(layouts: List<SectorLayout>, sector: Int, block: Int): SectorLayout {
        val layout = layouts.singleOrNull { it.sector == sector }
        require(layout != null && layout.contains(block)) { "Block does not belong to the selected sector" }
        return layout
    }
    fun validate(layout: SectorLayout, block: Int, developer: Boolean, allowTrailer: Boolean, confirmed: Boolean) {
        require(layout.contains(block)) { "Block does not belong to the selected sector" }
        require(block != 0) { PROTECTED }
        require(block != layout.trailer || (developer && allowTrailer && confirmed)) { PROTECTED }
    }
    // Trailer access bytes use complementary encoding. Reject invalid encodings before any trailer write.
    fun validAccessBits(data: ByteArray): Boolean {
        if (data.size != 16) return false
        val b6 = data[6].toInt() and 255
        val b7 = data[7].toInt() and 255
        val b8 = data[8].toInt() and 255
        return ((b6 and 15) xor (b7 shr 4)) == 15 &&
            ((b6 shr 4) xor (b8 and 15)) == 15 &&
            ((b7 and 15) xor (b8 shr 4)) == 15
    }
}
