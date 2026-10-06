package pl.misieklab.nfccardlab.nfc

data class MifareInfo(val type: String, val size: Int, val sectorCount: Int, val blockCount: Int, val layouts: List<SectorLayout>)
data class NfcTagInfo(
    val uid: String, val uidDecimal: String, val uidAscii: String?, val uidLength: Int,
    val technologies: List<String>, val classification: String, val standard: String,
    val atqa: String?, val sak: String?, val nfcAMaxTransceive: Int?,
    val mifare: MifareInfo?, val ndefStatus: String, val writable: String,
    val capacity: Int?, val isoDepMaxTransceive: Int?, val historicalBytes: String?, val hiLayerResponse: String?
)
data class BlockData(val sector: Int, val block: Int, val trailer: Boolean, val bytes: ByteArray? = null, val status: String = "NOT READ")
data class SectorData(val layout: SectorLayout, val auth: String = "NOT AUTHENTICATED", val blocks: List<BlockData>)
data class NdefSnapshot(val status: String, val writable: Boolean?, val capacity: Int?, val records: List<String>)
enum class KeyKind { A, B }
