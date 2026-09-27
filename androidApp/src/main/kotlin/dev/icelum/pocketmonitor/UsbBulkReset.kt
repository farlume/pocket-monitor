package dev.icelum.pocketmonitor

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection

internal object UsbBulkReset {
    init { System.loadLibrary("pocketmonitor_usb") }
    external fun clearHalt(fd: Int, endpoint: Int): Int
    external fun speed(fd: Int): Int
}

internal fun describeUsbVideo(device: UsbDevice, speed: Int): List<String> = buildList {
    val name = when (speed) {
        1 -> "low (1.5 Mbps)"
        2 -> "full (12 Mbps)"
        3 -> "high (480 Mbps)"
        4 -> "wireless"
        5 -> "super (5 Gbps)"
        6 -> "super-plus"
        else -> "unknown/unavailable"
    }
    add("USB link speed=$name result=$speed; link rate is not measured video throughput")
    for (i in 0 until device.interfaceCount) {
        val intf = device.getInterface(i)
        if (intf.interfaceClass != UsbConstants.USB_CLASS_VIDEO || intf.interfaceSubclass != 2) continue
        add("Video interface=${intf.id} alt=${intf.alternateSetting} endpoints=${intf.endpointCount}")
        for (j in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(j)
            val type = when (ep.type) {
                UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
                UsbConstants.USB_ENDPOINT_XFER_ISOC -> "isochronous"
                UsbConstants.USB_ENDPOINT_XFER_INT -> "interrupt"
                else -> "control"
            }
            add("Video endpoint=0x${ep.address.toString(16)} type=$type maxPacket=${ep.maxPacketSize} interval=${ep.interval}")
        }
    }
}

/** Run only after native camera destruction, never concurrently with streaming.
 * UVC bulk stop follows the endpoint halt-clear convention used by host drivers.
 * Do not touch audio, control, interrupt, OUT or isochronous endpoints.
 */
internal fun stopBulkVideo(device: UsbDevice, connection: UsbDeviceConnection,
    clearHalt: (Int, Int) -> Int): List<String> {
    val results = mutableListOf<String>()
    val seen = mutableSetOf<Pair<Int, Int>>()
    for (i in 0 until device.interfaceCount) {
        val intf = device.getInterface(i)
        if (intf.interfaceClass != UsbConstants.USB_CLASS_VIDEO || intf.interfaceSubclass != 2) continue
        val endpoints = (0 until intf.endpointCount).map(intf::getEndpoint).filter {
            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN &&
                seen.add(intf.id to it.address)
        }
        if (endpoints.isEmpty()) continue
        // Never forcibly take an interface from another driver/app.
        if (!connection.claimInterface(intf, false)) {
            results += "Bulk stop interface=${intf.id}: claim failed"
            continue
        }
        try {
            endpoints.forEach { endpoint ->
                val result = clearHalt(connection.fileDescriptor, endpoint.address)
                results += "Bulk stop interface=${intf.id} endpoint=0x${endpoint.address.toString(16)} result=$result"
            }
        } finally {
            if (!connection.releaseInterface(intf)) results += "Bulk stop interface=${intf.id}: release failed"
        }
    }
    return results.ifEmpty { listOf("Bulk stop: no bulk video IN endpoints") }
}
