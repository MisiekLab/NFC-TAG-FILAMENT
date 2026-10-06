package pl.misieklab.nfccardlab.nfc

import org.json.JSONArray
import org.json.JSONObject

object DumpExporter {
    // Authentication credentials are never part of this API. Key fields in trailers are also redacted.
    fun publicHex(block: BlockData): String? = block.bytes?.let { bytes ->
        if (!block.trailer) NfcHexUtils.hex(bytes)
        else bytes.mapIndexed { index, b -> if (index in 6..9) NfcHexUtils.hex(byteArrayOf(b)) else "??" }.joinToString(" ")
    }
    fun publicAscii(block: BlockData): String? = block.bytes?.let { bytes ->
        if (!block.trailer) NfcHexUtils.ascii(bytes)
        else bytes.mapIndexed { index, b -> if (index in 6..9) NfcHexUtils.ascii(byteArrayOf(b)) else "?" }.joinToString("")
    }
    fun json(info: NfcTagInfo?, sectors: List<SectorData>): String {
        require(info != null) { "Scan a tag first" }
        val root = JSONObject().put("uid", info.uid).put("uidDecimalBigEndian", info.uidDecimal)
            .put("technologies", JSONArray(info.technologies)).put("classification", info.classification)
            .put("atqa", info.atqa ?: JSONObject.NULL).put("sak", info.sak ?: JSONObject.NULL)
        info.mifare?.let {
            root.put("mifare", JSONObject().put("type", it.type).put("size", it.size)
                .put("sectorCount", it.sectorCount).put("blockCount", it.blockCount).put("blockSize", 16))
        }
        root.put("trailerKeysRedacted", true)
        root.put("sectors", JSONArray(sectors.map { sector ->
            JSONObject().put("sector", sector.layout.sector).put("authStatus", sector.auth)
                .put("firstBlock", sector.layout.firstBlock).put("blockCount", sector.layout.count)
                .put("blocks", JSONArray(sector.blocks.map { block ->
                    JSONObject().put("block", block.block).put("trailer", block.trailer)
                        .put("status", block.status).put("hex", publicHex(block) ?: JSONObject.NULL)
                        .put("ascii", publicAscii(block) ?: JSONObject.NULL)
                }))
        }))
        return root.toString(2)
    }
    fun txt(info: NfcTagInfo?, sectors: List<SectorData>): String {
        require(info != null) { "Scan a tag first" }
        return buildString {
            appendLine("NFC Card Lab — observed memory dump")
            appendLine("UID: ${info.uid}\nUID DEC (big endian): ${info.uidDecimal}\n${info.classification}")
            appendLine("Technologies: ${info.technologies.joinToString()}")
            appendLine("Trailer key slots redacted; unreadable blocks have no invented data.")
            for (sector in sectors) {
                appendLine("\nSECTOR ${sector.layout.sector} — ${sector.auth}")
                for (block in sector.blocks) {
                    appendLine("B${block.block} ${if (block.trailer) "TRAILER" else "DATA"} ${block.status}")
                    appendLine(publicHex(block) ?: "NOT READ")
                    publicAscii(block)?.let(::appendLine)
                }
            }
        }
    }
}
