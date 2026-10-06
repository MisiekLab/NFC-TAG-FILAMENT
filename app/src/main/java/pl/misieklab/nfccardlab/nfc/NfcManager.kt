package pl.misieklab.nfccardlab.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.TagTechnology
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TagSession(val generation: Long, val tag: Tag)

// One arbiter for ALL tag I/O, including scans. Session tokens invalidate stale write confirmations.
class NfcManager {
    private val ioMutex = Mutex()
    private val monitor = Any()
    private var generation = 0L
    private var session: TagSession? = null
    private var active: TagTechnology? = null

    fun adopt(tag: Tag): TagSession = synchronized(monitor) {
        TagSession(++generation, tag).also { session = it }
    }
    fun assertCurrent(token: TagSession) = synchronized(monitor) {
        if (session !== token) throw TagLostException()
    }
    fun invalidate(): TagTechnology? = synchronized(monitor) {
        generation++
        session = null
        active.also { active = null }
    }
    suspend fun closeDetached(technology: TagTechnology?) = withContext(Dispatchers.IO) {
        try { technology?.close() } catch (_: Exception) { /* Never log credentials or payloads. */ }
    }
    suspend fun <T> inspect(token: TagSession, block: (Tag) -> T): NfcResult<T> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            try {
                assertCurrent(token)
                NfcResult.Ok(block(token.tag))
            } catch (e: Exception) { nfcError(e) }
        }
    }
    suspend fun <P : TagTechnology, T> execute(
        token: TagSession, factory: (Tag) -> P?, operation: suspend (P) -> T
    ): NfcResult<T> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            var tech: P? = null
            try {
                currentCoroutineContext().ensureActive()
                assertCurrent(token)
                tech = factory(token.tag) ?: throw UnsupportedOperationException()
                synchronized(monitor) {
                    assertCurrent(token)
                    active = tech
                }
                tech.connect()
                assertCurrent(token)
                NfcResult.Ok(operation(tech))
            } catch (e: Exception) {
                nfcError(e)
            } finally {
                try { tech?.close() } catch (_: Exception) { }
                synchronized(monitor) { if (active === tech) active = null }
            }
        }
    }
    fun enable(activity: Activity, callback: NfcAdapter.ReaderCallback): String {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return "NFC HARDWARE UNAVAILABLE"
        if (!adapter.isEnabled) return "NFC DISABLED — enable NFC in Settings"
        return try {
            // Do not skip the platform NDEF check: Ndef/NdefFormatable tech discovery is required.
            adapter.enableReaderMode(activity, callback,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) })
            "LIVE"
        } catch (_: SecurityException) { "NFC ACCESS DENIED" }
          catch (_: IllegalArgumentException) { "NFC READER UNAVAILABLE" }
    }
    fun disable(activity: Activity) {
        try { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
        catch (_: SecurityException) { }
        catch (_: IllegalArgumentException) { }
    }
}
