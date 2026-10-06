package pl.misieklab.nfccardlab

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.TagTechnology
import android.os.Bundle
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.misieklab.nfccardlab.nfc.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NfcSessionTest {
    private fun tag(id: ByteArray = byteArrayOf(1), techs: IntArray = intArrayOf(), extras: Array<Bundle> = emptyArray()): Tag =
        Tag::class.java.getDeclaredMethod("createMockTag", ByteArray::class.java, IntArray::class.java, Array<Bundle>::class.java)
            .invoke(null, id, techs, extras) as Tag
    private class FakeTech(private val tag: Tag) : TagTechnology {
        var opens = 0
        var closes = 0
        var failConnect = false
        private var connected = false
        override fun getTag() = tag
        override fun isConnected() = connected
        override fun connect() { opens++; if (failConnect) throw IOException(); connected = true }
        override fun close() { closes++; connected = false }

    }
    @Test fun referenceTagUsesAndroidMetadata() {
        val a = Bundle().apply { putByteArray("atqa", byteArrayOf(4, 0)); putShort("sak", 8) }
        val t = tag(NfcHexUtils.parse("12 66 FA 25"), intArrayOf(1, 8, 7), arrayOf(a, Bundle(), Bundle()))
        val serviceClass = Tag::class.java.getDeclaredField("mTagService").type
        val service = Proxy.newProxyInstance(serviceClass.classLoader, arrayOf(serviceClass)) { _, method, _ ->
            when(method.name) {
                "getMaxTransceiveLength" -> 253
                "toString" -> "TestTagService"
                else -> when(method.returnType) { java.lang.Boolean.TYPE -> true; java.lang.Integer.TYPE -> 0; else -> null }
            }
        }
        Tag::class.java.getDeclaredField("mTagService").apply { isAccessible = true }.set(t, service)
        val info = NfcTechDetector.inspect(t)
        assertEquals("MIFARE Classic compatible", info.classification)
        assertEquals("12 66 FA 25", info.uid)
        assertEquals(4, info.uidLength)
        assertEquals("04 00", info.atqa)
        assertEquals("08", info.sak)
        assertEquals("ISO/IEC 14443-A", info.standard)
        assertEquals(setOf("android.nfc.tech.MifareClassic", "android.nfc.tech.NfcA", "android.nfc.tech.NdefFormatable"), info.technologies.toSet())
        assertEquals("TAG IS NOT NDEF FORMATTED", info.ndefStatus)
        assertEquals("UNKNOWN", info.writable)
        assertEquals(16, info.mifare!!.sectorCount)
        assertEquals(64, info.mifare.blockCount)
        assertEquals(63, info.mifare.layouts.last().trailer)
    }
    @Test fun closeAfterSuccessfulOperation() = runBlocking {
        val manager = NfcManager(); val tag = tag(); val session = manager.adopt(tag); val port = FakeTech(tag)
        assertTrue(manager.execute(session, { port }) { 42 } is NfcResult.Ok)
        assertEquals(1, port.opens); assertEquals(1, port.closes)
    }
    @Test fun closeEvenWhenConnectFails() = runBlocking {
        val manager = NfcManager(); val tag = tag(); val session = manager.adopt(tag); val port = FakeTech(tag).apply { failConnect = true }
        assertTrue(manager.execute(session, { port }) { 42 } is NfcResult.Error)
        assertEquals(1, port.closes)
    }
    @Test fun tagLostBecomesUiErrorAndCloses() = runBlocking {
        val manager = NfcManager(); val tag = tag(); val session = manager.adopt(tag); val port = FakeTech(tag)
        val result = manager.execute(session, { port }) { throw TagLostException() }
        assertTrue(result is NfcResult.Error && result.lost)
        assertEquals(1, port.closes)
    }
    @Test fun expiredTokenNeverConnects() = runBlocking {
        val manager = NfcManager(); val tag = tag(); val session = manager.adopt(tag); val port = FakeTech(tag)
        manager.invalidate()
        assertTrue(manager.execute(session, { port }) { 42 } is NfcResult.Error)
        assertEquals(0, port.opens)
    }
    @Test fun changedTagInvalidatesOldConfirmation() {
        val manager = NfcManager(); val old = manager.adopt(tag())
        manager.adopt(tag(byteArrayOf(2)))
        assertThrows(TagLostException::class.java) { manager.assertCurrent(old) }
    }
    @Test fun concurrentOperationsAreSerialized() = runBlocking {
        val manager = NfcManager(); val tag = tag(); val session = manager.adopt(tag)
        val count = AtomicInteger(); val peak = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val first = async {
            manager.execute(session, { FakeTech(tag) }) {
                val n = count.incrementAndGet(); peak.updateAndGet { maxOf(it, n) }; entered.complete(Unit)
                delay(60); count.decrementAndGet()
            }
        }
        entered.await()
        val second = async {
            manager.execute(session, { FakeTech(tag) }) {
                val n = count.incrementAndGet(); peak.updateAndGet { maxOf(it, n) }
                count.decrementAndGet()
            }
        }
        first.await(); second.await()
        assertEquals(1, peak.get())
    }
    @Test fun cancellationAlwaysCloses() = runBlocking {
        val manager = NfcManager(); val tag = tag(); val session = manager.adopt(tag); val port = FakeTech(tag)
        val entered = CompletableDeferred<Unit>()
        val job = launch { manager.execute(session, { port }) { entered.complete(Unit); awaitCancellation() } }
        entered.await(); job.cancelAndJoin()
        assertEquals(1, port.closes)
    }
}
