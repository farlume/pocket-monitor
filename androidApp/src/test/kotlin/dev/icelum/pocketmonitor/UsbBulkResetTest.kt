package dev.icelum.pocketmonitor

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [UsbBulkResetTest.ConnectionShadow::class])
class UsbBulkResetTest {
    private val connection = Shadow.newInstanceOf(UsbDeviceConnection::class.java)
    private val shadow = Shadow.extract<ConnectionShadow>(connection)

    @Test fun diagnosticsDescribeOnlyVideoStreamingEndpointsAndActualLinkSpeed() {
        val lines = describeUsbVideo(device(intf(1), intf(2, klass = 1)), 3).joinToString("\n")
        assertTrue(lines.contains("high (480 Mbps)"))
        assertTrue(lines.contains("Video interface=1"))
        assertTrue(lines.contains("endpoint=0x81 type=bulk"))
        assertFalse(lines.contains("Video interface=2"))
    }

    @Test fun unsupportedSpeedQueryDoesNotGuessUsbVersion() {
        val lines = describeUsbVideo(device(intf(1)), -25).joinToString("\n")
        assertTrue(lines.contains("unknown/unavailable result=-25"))
        assertTrue(lines.contains("Video interface=1"))
    }

    @Test fun onlyVideoStreamingBulkInputIsClearedOnce() {
        val stream = intf(1, endpoints = arrayOf(endpoint(0x81, 2), endpoint(0x02, 2),
            endpoint(0x83, 1), endpoint(0x84, 3)))
        val device = device(stream, stream,
            intf(2, klass = 1, endpoints = arrayOf(endpoint(0x85, 2))),
            intf(3, subclass = 1, endpoints = arrayOf(endpoint(0x86, 2))))
        val calls = mutableListOf<Pair<Int, Int>>()
        val results = stopBulkVideo(device, connection) { fd, ep -> calls += fd to ep; 0 }
        assertEquals(listOf(42 to 0x81), calls)
        assertEquals(listOf(1), shadow.claimed)
        assertEquals(listOf(1), shadow.released)
        assertEquals(listOf("Bulk stop interface=1 endpoint=0x81 result=0"), results)
    }

    @Test fun claimFailureDoesNotIssueIoctlOrReleaseAnUnownedInterface() {
        shadow.canClaim = false
        val result = stopBulkVideo(device(intf(1)), connection) { _, _ -> error("Must not clear") }
        assertEquals(listOf("Bulk stop interface=1: claim failed"), result)
        assertTrue(shadow.released.isEmpty())
    }

    @Test fun ioctlFailureIsReportedAndInterfaceReleased() {
        val result = stopBulkVideo(device(intf(1)), connection) { _, _ -> -19 }
        assertEquals(listOf("Bulk stop interface=1 endpoint=0x81 result=-19"), result)
        assertEquals(listOf(1), shadow.released)
    }

    @Test fun thrownNativeErrorStillReleasesInterface() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            stopBulkVideo(device(intf(1)), connection) { _, _ -> throw UnsatisfiedLinkError() }
        }
        assertEquals(listOf(1), shadow.released)
    }

    @Test fun isochronousVideoDoesNotNeedBulkStop() {
        val result = stopBulkVideo(device(intf(1, endpoints = arrayOf(endpoint(0x81, 1)))), connection) {
            _, _ -> error("Must not clear")
        }
        assertEquals(listOf("Bulk stop: no bulk video IN endpoints"), result)
        assertTrue(shadow.claimed.isEmpty())
    }

    @Test fun releaseFailureIsVisibleInDiagnostics() {
        shadow.canRelease = false
        val result = stopBulkVideo(device(intf(1)), connection) { _, _ -> 0 }
        assertEquals("Bulk stop interface=1: release failed", result.last())
    }

    private fun device(vararg interfaces: UsbInterface) = Shadow.newInstanceOf(UsbDevice::class.java).apply {
        ReflectionHelpers.setField(this, "mInterfaces", interfaces)
    }

    private fun intf(id: Int, klass: Int = 14, subclass: Int = 2,
        endpoints: Array<UsbEndpoint> = arrayOf(endpoint(0x81, 2))) =
        Shadow.newInstanceOf(UsbInterface::class.java).apply {
            ReflectionHelpers.setField(this, "mId", id)
            ReflectionHelpers.setField(this, "mClass", klass)
            ReflectionHelpers.setField(this, "mSubclass", subclass)
            ReflectionHelpers.setField(this, "mEndpoints", endpoints)
        }

    private fun endpoint(address: Int, type: Int) = Shadow.newInstanceOf(UsbEndpoint::class.java).apply {
        ReflectionHelpers.setField(this, "mAddress", address)
        ReflectionHelpers.setField(this, "mAttributes", type)
    }

    @Implements(UsbDeviceConnection::class)
    class ConnectionShadow {
        var canClaim = true
        var canRelease = true
        val claimed = mutableListOf<Int>()
        val released = mutableListOf<Int>()
        @Implementation fun getFileDescriptor() = 42
        @Implementation fun claimInterface(intf: UsbInterface, force: Boolean): Boolean {
            assertFalse(force)
            claimed += intf.id
            return canClaim
        }
        @Implementation fun releaseInterface(intf: UsbInterface): Boolean {
            released += intf.id
            return canRelease
        }
    }
}
