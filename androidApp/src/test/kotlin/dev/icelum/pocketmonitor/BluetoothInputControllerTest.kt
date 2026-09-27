package dev.icelum.pocketmonitor

import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BluetoothInputControllerTest {
    @Test fun physicalChordKeepsRightShiftUntilItsOwnRelease() {
        val fake = Fake(); val controller = connected(fake)
        assertTrue(controller.keyDown(0xE1))
        assertTrue(controller.keyDown(0xE5))
        assertTrue(controller.keyDown(29))
        assertFalse(controller.text("x"))
        assertFalse(controller.state.value.sending) // physical keys remain interactive
        assertTrue(controller.keyUp(0xE1))
        assertTrue(controller.keyUp(29))
        assertTrue(controller.keyUp(0xE5))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(listOf(2, 34, 34, 32, 32, 0), fake.sent.map { it.second[0].toInt() and 255 })
        assertEquals(listOf(0, 0, 29, 29, 0, 0), fake.sent.map { it.second[2].toInt() })
        assertTrue(controller.text("x"))
        controller.stop()
    }

    @Test fun heldKeyRejectsBatchTextAfterQueueDrainsAndStopReleasesIt() {
        val fake = Fake(); val controller = connected(fake)
        controller.keyDown(4)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertFalse(controller.text("x"))
        assertFalse(controller.key(HidStroke(5)))
        controller.stop()
        val count = fake.sent.size
        assertFalse(controller.keyUp(4))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(count, fake.sent.size)
        assertEquals(ByteArray(8).toList(), fake.sent[fake.sent.size - 2].second)
    }

    @Test fun physicalQueueOverflowReleasesEverythingInsteadOfDroppingKeyUp() {
        val fake = Fake(); val controller = connected(fake)
        controller.keyDown(4)
        repeat(32) { controller.keyUp(4); controller.keyDown(4) }
        assertFalse(controller.keyUp(4))
        assertEquals(ByteArray(8).toList(), fake.sent[fake.sent.size - 2].second)
        val count = fake.sent.size
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(count, fake.sent.size)
        assertTrue(controller.keyDown(5))
        controller.stop()
    }

    @Test fun physicalInputCannotInterleaveWithTextAndInvalidKeysSendNothing() {
        val fake = Fake(); val controller = connected(fake)
        assertFalse(controller.key(HidStroke(0xFF)))
        assertFalse(controller.keyDown(999))
        assertTrue(fake.sent.isEmpty())
        controller.text("ab")
        assertFalse(controller.keyDown(0xE5))
        assertFalse(controller.keyUp(4))
        controller.cancelSending()
        assertTrue(controller.keyDown(0xE5))
        controller.stop()
    }
    private class Fake : HidTransport {
        lateinit var listener: HidTransport.Listener
        var closed = false
        var sendOk = true
        var connectOk = true
        val sent = mutableListOf<Pair<Int, List<Byte>>>()
        val disconnected = mutableListOf<String>()
        var hostList = listOf(InputHost("computer", "Computer"), InputHost("other", "Other"))
        override fun open(listener: HidTransport.Listener) { this.listener = listener }
        override fun hosts() = hostList
        override fun connect(id: String) = connectOk
        override fun disconnect(id: String) { disconnected += id }
        override fun send(id: String, report: Int, bytes: ByteArray): Boolean {
            sent += report to bytes.toList(); return sendOk
        }
        override fun close() { closed = true }
    }
    private fun connected(fake: Fake): BluetoothInputController {
        val controller = BluetoothInputController({ InputPhase.Off }, { fake })
        controller.start(); controller.enable(); fake.listener.ready(); controller.connect("computer")
        fake.listener.connection("computer", true); fake.sent.clear()
        return controller
    }
    @Test fun permissionAndUnsupportedPhonesNeverOpenTransport() {
        for (phase in listOf(InputPhase.Unsupported, InputPhase.PermissionRequired, InputPhase.BluetoothOff)) {
            val controller = BluetoothInputController({ phase }, { error("must not open") })
            controller.start(); controller.enable()
            assertEquals(phase, controller.state.value.phase)
            controller.stop()
        }
    }
    @Test fun onlyExplicitCurrentPairedHostCanConnect() {
        val fake = Fake(); val controller = BluetoothInputController({ InputPhase.Off }, { fake })
        controller.start(); controller.enable(); fake.listener.ready()
        fake.listener.connection("other", true)
        assertEquals(listOf("other"), fake.disconnected)
        controller.connect("missing")
        assertEquals(InputPhase.Ready, controller.state.value.phase)
        controller.connect("computer"); fake.listener.connection("computer", true)
        assertEquals(InputPhase.Connected, controller.state.value.phase)
        controller.stop()
    }
    @Test fun textIsPacedWithKeyReleaseAndConcurrentInputRejected() {
        val fake = Fake(); val controller = connected(fake)
        assertTrue(controller.text("aA"))
        assertFalse(controller.text("b"))
        assertFalse(controller.key(HidStroke(6)))
        controller.move(8, 9)
        assertEquals(1, fake.sent.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(listOf(
            HidReports.keyboard(HidStroke(4)).toList(), ByteArray(8).toList(),
            HidReports.keyboard(HidStroke(4, 2)).toList(), ByteArray(8).toList()), fake.sent.map { it.second })
        assertFalse(controller.state.value.sending)
        controller.stop()
    }
    @Test fun stopReleasesKeysCancelsQueueAndIgnoresLateCallbacks() {
        val fake = Fake(); val controller = connected(fake)
        controller.text("abc")
        controller.stop()
        val afterStop = fake.sent.toList()
        fake.listener.connection("computer", true); fake.listener.ready(); fake.listener.failed()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(afterStop, fake.sent)
        assertEquals(HidReports.KEYBOARD to ByteArray(8).toList(), afterStop[afterStop.size - 2])
        assertEquals(HidReports.MOUSE to ByteArray(4).toList(), afterStop.last())
        assertEquals(InputPhase.Off, controller.state.value.phase)
        assertTrue(fake.closed)
    }
    @Test fun failedKeyReleaseTerminatesSessionInsteadOfContinuingText() {
        val fake = Fake(); val controller = connected(fake)
        controller.text("abc"); fake.sendOk = false
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25))
        assertEquals(InputPhase.Error, controller.state.value.phase)
        assertTrue(fake.closed)
        assertFalse(controller.state.value.sending)
    }
    @Test fun cancelReleasesMouseButtonAndDropsPendingInput() {
        val fake = Fake(); val controller = connected(fake)
        assertTrue(controller.click(true))
        controller.cancelSending()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(listOf(2.toByte(), 0.toByte(), 0.toByte(), 0.toByte()), fake.sent.first().second)
        assertEquals(HidReports.MOUSE to ByteArray(4).toList(), fake.sent.last())
        assertFalse(controller.state.value.sending)
        assertTrue(controller.text("b"))
        controller.stop()
    }
    @Test fun registrationAndConnectionTimeoutsCloseTransport() {
        val fake = Fake(); val controller = BluetoothInputController({ InputPhase.Off }, { fake })
        controller.start(); controller.enable()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13))
        assertTrue(fake.closed); assertEquals(InputPhase.Error, controller.state.value.phase)
        val second = Fake(); val next = BluetoothInputController({ InputPhase.Off }, { second })
        next.start(); next.enable(); second.listener.ready(); next.connect("computer")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21))
        assertTrue(second.closed); assertEquals(InputPhase.Error, next.state.value.phase)
    }
    @Test fun staleCallbacksCannotAffectNewSessionAndNoAutomaticReconnect() {
        val first = Fake(); val second = Fake(); var backend = first
        val controller = BluetoothInputController({ InputPhase.Off }, { backend })
        controller.start(); controller.enable(); first.listener.ready()
        controller.stop(); backend = second; controller.start()
        assertEquals(InputPhase.Off, controller.state.value.phase)
        controller.enable(); second.listener.ready()
        first.listener.failed(); first.listener.connection("computer", true)
        assertEquals(InputPhase.Ready, controller.state.value.phase)
        assertFalse(second.closed)
        controller.stop()
    }
    @Test fun motionIsCoalescedAndDiscardedOnStop() {
        val fake = Fake(); val controller = connected(fake)
        controller.move(10, 20); controller.move(20, 30)
        assertTrue(fake.sent.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        assertEquals(listOf(HidReports.MOUSE to byteArrayOf(0, 30, 50, 0).toList()), fake.sent)
        controller.move(500, 500); controller.stop()
        val count = fake.sent.size
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(count, fake.sent.size)
    }
    @Test fun invalidTextSendsNothingAndDisconnectDropsQueue() {
        val fake = Fake(); val controller = connected(fake)
        assertFalse(controller.text("hello中文")); assertTrue(fake.sent.isEmpty())
        controller.text("abc"); fake.listener.connection("computer", false)
        val count = fake.sent.size
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(count, fake.sent.size)
        assertEquals(InputPhase.Ready, controller.state.value.phase)
        controller.stop()
    }
}
