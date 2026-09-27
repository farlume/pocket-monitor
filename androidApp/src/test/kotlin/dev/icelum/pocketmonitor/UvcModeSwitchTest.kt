package dev.icelum.pocketmonitor

import android.Manifest
import android.app.Application
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UvcModeSwitchTest {
    private val hd = VideoMode(1280, 720, 30, VideoEncoding.Mjpeg)
    private val fullHd = VideoMode(1920, 1080, 30, VideoEncoding.Mjpeg)
    private val low = VideoMode(640, 480, 30, VideoEncoding.Mjpeg)
    private val monitors = mutableListOf<FakeMonitor>()
    private var failPermission = false
    private lateinit var capture: UvcCaptureController
    private lateinit var worker: Handler
    private lateinit var device: UsbDevice
    private lateinit var surface: Surface
    private lateinit var texture: SurfaceTexture

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        device = Shadow.newInstanceOf(UsbDevice::class.java).apply {
            ReflectionHelpers.setField(this, "mName", "/dev/bus/usb/001/002")
            ReflectionHelpers.setField(this, "mProductName", "Test capture card")
            ReflectionHelpers.setField(this, "mClass", UsbConstants.USB_CLASS_VIDEO)
            ReflectionHelpers.setField(this, "mVendorId", 1234)
            ReflectionHelpers.setField(this, "mProductId", 5678)
        }
        shadowOf(app.getSystemService(UsbManager::class.java)).addOrUpdateUsbDevice(device, true)
        capture = UvcCaptureController(app) { listener -> FakeMonitor(listener).also(monitors::add) }
        worker = ReflectionHelpers.getField(capture, "worker")
        shadowOf(worker.looper).pause()
        texture = SurfaceTexture(0)
        surface = Surface(texture)
        capture.attachSurface(surface)
        capture.start()
        capture.connect(device.deviceName)
        drain()
        assertEquals(CapturePhase.WaitingForFrames, capture.state.value.phase)
        monitors.single().camera.frame()
        drain()
        assertEquals(CapturePhase.Streaming, capture.state.value.phase)
    }

    @After fun tearDown() {
        capture.detachSurface(surface) { texture.release() }
        drain()
        capture.destroy()
        shadowOf(worker.looper).idle()
    }

    @Test fun switchingModesRetainsUsbAndRebindsDisplayAfterStopping() {
        val monitor = monitors.single()
        val camera = monitor.camera
        val oldFrame = camera.frame
        capture.selectMode(fullHd)
        oldFrame()
        drain()
        assertEquals(CapturePhase.WaitingForFrames, capture.state.value.phase)
        assertEquals(fullHd, capture.state.value.activeMode)
        assertEquals(1, monitors.size)
        assertEquals(1, monitor.requests)
        assertFalse(monitor.closed)
        assertEquals(1, camera.opens)
        assertTrue(camera.destroyed)
        assertEquals(fullHd, monitor.camera.preferred)
        assertEquals(listOf("mode", "callback", "display", "start"), monitor.camera.events)
        assertEquals(2, monitor.cameras.size)
        oldFrame()
        drain()
        assertEquals(CapturePhase.WaitingForFrames, capture.state.value.phase)
        monitor.camera.frame()
        drain()
        assertEquals(CapturePhase.Streaming, capture.state.value.phase)
        capture.selectMode(hd)
        drain()
        monitor.camera.frame()
        drain()
        assertEquals(hd, capture.state.value.activeMode)
        assertEquals(1, monitors.size)
        assertEquals(3, monitor.cameras.size)
        assertTrue(monitor.cameras.dropLast(1).all { it.destroyed })
        assertEquals(2, monitor.streamStops)
    }

    @Test fun selectingCurrentStreamingModeDoesNotInterruptVideo() {
        val camera = monitors.single().camera
        val events = camera.events.toList()
        capture.selectMode(hd)
        drain()
        assertEquals(events, camera.events)
        assertEquals(CapturePhase.Streaming, capture.state.value.phase)
    }

    @Test fun repeatedConnectAndModeSelectionDoNotRestartWaitingStream() {
        capture.selectMode(fullHd)
        drain()
        val camera = monitors.single().camera
        val events = camera.events.toList()
        capture.connect(device.deviceName)
        capture.selectMode(fullHd)
        drain()
        assertEquals(1, monitors.size)
        assertEquals(events, camera.events)
        assertEquals(CapturePhase.WaitingForFrames, capture.state.value.phase)
        camera.frame()
        drain()
        capture.connect(device.deviceName)
        drain()
        assertEquals(1, monitors.size)
        assertEquals(events, camera.events)
        assertEquals(CapturePhase.Streaming, capture.state.value.phase)
    }

    @Test fun repeatedConnectDuringPermissionDoesNotCreateAnotherMonitor() {
        capture.retry()
        assertEquals(CapturePhase.Permission, capture.state.value.phase)
        capture.connect(device.deviceName)
        assertEquals(2, monitors.size)
        drain()
        assertEquals(CapturePhase.WaitingForFrames, capture.state.value.phase)
        assertEquals(1, monitors.last().requests)
    }

    @Test fun workerPermissionExceptionBecomesRecoverableError() {
        failPermission = true
        capture.retry()
        drain()
        assertEquals(CapturePhase.Error, capture.state.value.phase)
        assertEquals(CaptureMessage.UsbPermissionFailed, capture.state.value.message)
        assertTrue(monitors.last().destroyed)
        failPermission = false
        capture.retry()
        drain()
        monitors.last().camera.frame()
        drain()
        assertEquals(CapturePhase.Streaming, capture.state.value.phase)
    }

    @Test fun backgroundCancelsQueuedSwitchAndClosesBeforeReleasingSurface() {
        val camera = monitors.single().camera
        capture.selectMode(fullHd)
        capture.stop()
        drain()
        assertEquals(1, camera.starts)
        assertEquals(CapturePhase.Paused, capture.state.value.phase)
        assertTrue(monitors.single().closed)
        camera.frame()
        drain()
        assertEquals(CapturePhase.Paused, capture.state.value.phase)
    }

    @Test fun usbCloseStillAppliesAfterStreamEpochChanges() {
        val monitor = monitors.single()
        capture.selectMode(fullHd)
        drain()
        monitor.listener.closed(device)
        drain()
        assertEquals(CapturePhase.Error, capture.state.value.phase)
        assertEquals(CaptureMessage.UsbClosed, capture.state.value.message)
        assertTrue(monitor.closed)
    }

    @Test fun staleUsbAndFrameCallbacksCannotAffectReconnectedCamera() {
        val old = monitors.single()
        capture.selectMode(fullHd)
        drain()
        capture.retry()
        drain()
        old.listener.closed(device)
        old.listener.cancelled(device)
        old.camera.frame()
        drain()
        assertEquals(CapturePhase.WaitingForFrames, capture.state.value.phase)
        assertFalse(monitors.last().closed)
        monitors.last().camera.frame()
        drain()
        assertEquals(CapturePhase.Streaming, capture.state.value.phase)
    }

    @Test fun switchFailureFallsBackWithFreshConnectionAfterCleanup() {
        val first = monitors.single()
        first.reject = fullHd
        capture.selectMode(fullHd)
        drain()
        assertTrue(first.closed)
        assertTrue(first.destroyed)
        assertEquals(2, monitors.size)
        assertEquals(hd, capture.state.value.activeMode)
        assertEquals(1, capture.state.value.recoveryAttempt)
        assertEquals(CapturePhase.WaitingForFrames, capture.state.value.phase)
    }

    @Test fun noFramesAfterSwitchRetriesAtMostTwiceAndThenStalls() {
        capture.selectMode(fullHd)
        drain()
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            drain()
        }
        assertEquals(3, monitors.size)
        assertEquals(low, capture.state.value.activeMode)
        assertEquals(CapturePhase.Stalled, capture.state.value.phase)
        assertEquals(CaptureMessage.NoFrames, capture.state.value.message)
    }

    @Test fun automaticReconnectWaitsForSystemUsbAuthorization() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val usb = shadowOf(app.getSystemService(UsbManager::class.java))
        capture.stop()
        drain()
        usb.addOrUpdateUsbDevice(device, false)
        capture.start()
        capture.usbAccessChanged()
        drain()
        assertEquals(1, monitors.size)
        usb.addOrUpdateUsbDevice(device, true)
        capture.usbAccessChanged()
        drain()
        assertEquals(2, monitors.size)
        assertEquals(CapturePhase.WaitingForFrames, capture.state.value.phase)
        capture.usbAccessChanged()
        drain()
        assertEquals(2, monitors.size)
    }

    @Test fun diagnosticsKeepSwitchStagesAndFirstFrameSeparate() {
        capture.selectMode(fullHd)
        drain()
        val waiting = capture.diagnostics()
        assertTrue(waiting.contains("Switch close complete"))
        assertTrue(waiting.contains("Switch negotiate complete"))
        assertTrue(waiting.contains("awaiting frames"))
        assertFalse(waiting.lineSequence().any { it.contains("First frame") && it.contains(fullHd.label) })
        monitors.single().camera.frame()
        drain()
        assertTrue(capture.diagnostics().lineSequence().any { it.contains("First frame") && it.contains(fullHd.label) })
    }

    private fun drain() {
        repeat(5) {
            shadowOf(worker.looper).idle()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private inner class FakeMonitor(val listener: UvcDeviceListener) : UvcDeviceMonitor, UvcConnection {
        val cameras = mutableListOf<FakeCamera>()
        val camera get() = cameras.last()
        var reject: VideoMode? = null
        var streamStops = 0
        var requests = 0
        var closed = false
        var destroyed = false
        override fun register() = Unit
        override fun unregister() = Unit
        override fun requestPermission(device: UsbDevice) {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            requests++
            if (failPermission) throw SecurityException("USB access revoked")
            Handler(Looper.getMainLooper()).post { listener.opened(device, this) }
        }
        override fun createCamera(preferred: VideoMode?): UvcPreviewCamera {
            assertTrue(cameras.all { it.destroyed })
            assertEquals(cameras.size, streamStops)
            return FakeCamera().also {
                it.preferred = preferred
                it.reject = reject
                cameras += it
            }
        }
        override fun stopVideoStream(): List<String> {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            assertTrue(cameras.all { it.destroyed })
            streamStops++
            return listOf("Bulk stop test result=0")
        }
        override fun close() {
            assertTrue(camera.destroyed)
            closed = true
        }
        override fun destroy() {
            if (cameras.isNotEmpty()) assertTrue(closed)
            destroyed = true
        }
    }

    private inner class FakeCamera : UvcPreviewCamera {
        val events = mutableListOf<String>()
        var preferred: VideoMode? = null
        var opens = 0
        var starts = 0
        var destroyed = false
        var running = false
        var boundSurface: Surface? = null
        var reject: VideoMode? = null
        var frame: () -> Unit = {}
        override val modes = listOf(hd, fullHd, low)
        override fun open() { opens++ }
        override fun setMode(mode: VideoMode) {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            check(!running) { "Cannot negotiate while streaming" }
            require(mode != reject)
            events += "mode"
        }
        override fun setFrameCallback(callback: () -> Unit) { frame = callback; events += "callback" }
        override fun setDisplay(surface: Surface) { boundSurface = surface; events += "display" }
        override fun start() {
            check(boundSurface?.isValid == true) { "stop clears native display; restart must bind it again" }
            running = true
            starts++
            events += "start"
        }
        override fun stop() { running = false; boundSurface = null; events += "stop" }
        override fun destroy() {
            assertTrue(surface.isValid)
            running = false
            destroyed = true
        }
    }
}
