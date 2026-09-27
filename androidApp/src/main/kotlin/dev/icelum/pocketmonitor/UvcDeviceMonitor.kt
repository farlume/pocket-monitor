package dev.icelum.pocketmonitor

import android.content.Context
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.serenegiant.usb.*

internal interface UvcDeviceListener {
    fun attached(device: UsbDevice)
    fun detached(device: UsbDevice)
    fun opened(device: UsbDevice, connection: UvcConnection)
    fun closed(device: UsbDevice)
    fun cancelled(device: UsbDevice)
    fun failed(device: UsbDevice, error: Exception)
}

internal interface UvcDeviceMonitor {
    fun register()
    fun unregister()
    fun requestPermission(device: UsbDevice)
    fun destroy()
}

internal interface UvcConnection {
    fun diagnostics(): List<String> = emptyList()
    fun createCamera(preferred: VideoMode?): UvcPreviewCamera
    fun stopVideoStream(): List<String>
    fun close()
}

/** All camera operations, including stopping before changing mode, run on UvcPreview. */
internal interface UvcPreviewCamera {
    fun open()
    val modes: List<VideoMode>
    fun setMode(mode: VideoMode)
    fun setFrameCallback(callback: () -> Unit)
    fun setDisplay(surface: Surface)
    fun start()
    fun stop()
    fun destroy()
}

internal class AndroidUvcDeviceMonitor(context: Context, listener: UvcDeviceListener) : UvcDeviceMonitor {
    private val monitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice) = listener.attached(device)
        override fun onDetach(device: UsbDevice) = listener.detached(device)
        override fun onDeviceOpen(device: UsbDevice, block: USBMonitor.UsbControlBlock, createNew: Boolean) =
            listener.opened(device, object : UvcConnection {
                override fun diagnostics(): List<String> = describeUsbVideo(device,
                    UsbBulkReset.speed(block.connection.fileDescriptor))
                override fun createCamera(preferred: VideoMode?): UvcPreviewCamera = AndroidUvcPreviewCamera(block, preferred)
                override fun stopVideoStream(): List<String> = stopBulkVideo(device, block.connection, UsbBulkReset::clearHalt)
                override fun close() = block.close(true)
            })
        override fun onDeviceClose(device: UsbDevice, block: USBMonitor.UsbControlBlock) = listener.closed(device)
        override fun onCancel(device: UsbDevice) = listener.cancelled(device)
        override fun onError(device: UsbDevice, e: USBMonitor.USBException) = listener.failed(device, e)
    }, Handler(Looper.getMainLooper()))

    override fun register() = monitor.register()
    override fun unregister() = monitor.unregister()
    override fun requestPermission(device: UsbDevice) = monitor.requestPermission(device)
    override fun destroy() = monitor.destroy()
}

private class AndroidUvcPreviewCamera(private val block: USBMonitor.UsbControlBlock,
    preferred: VideoMode?) : UvcPreviewCamera {
    private val camera = UVCCamera(UVCParam(preferred?.let {
        Size(it.encoding.uvcType, it.width, it.height, it.fps, listOf(it.fps))
    }, UVCCamera.getRecommendedPlatformQuirks()))

    override fun open() {
        val result = camera.open(block)
        check(result == 0) { "UVC open returned $result" }
    }

    override val modes: List<VideoMode> get() = preferredModes(camera.supportedSizeList.orEmpty().flatMap { size ->
        val encoding = VideoEncoding.entries.find { it.uvcType == size.type } ?: return@flatMap emptyList()
        (size.fpsList.orEmpty() + size.fps).distinct().filter { it > 0 }.map {
            VideoMode(size.width, size.height, it, encoding)
        }
    })

    override fun setMode(mode: VideoMode) = camera.setPreviewSize(
        Size(mode.encoding.uvcType, mode.width, mode.height, mode.fps, listOf(mode.fps)))
    override fun setFrameCallback(callback: () -> Unit) =
        camera.setFrameCallback({ _ -> callback() }, UVCCamera.PIXEL_FORMAT_RAW)
    override fun setDisplay(surface: Surface) = camera.setPreviewDisplay(surface)
    override fun start() = camera.startPreview()
    override fun stop() = camera.stopPreview()
    override fun destroy() = camera.destroy(true)
}
