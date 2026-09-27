package dev.icelum.pocketmonitor

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.usb.*
import android.os.*
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** Main-thread commands; USB/native work is serialized on a dedicated Looper. */
class UvcCaptureController internal constructor(private val context: Context,
    private val createMonitor: (UvcDeviceListener) -> UvcDeviceMonitor) {
    constructor(context: Context) : this(context, { AndroidUvcDeviceMonitor(context, it) })
    private val usb = context.getSystemService(UsbManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("UvcPreview").apply { start() }
    private val worker = Handler(thread.looper)
    private val session = CaptureSession()
    private val epoch = AtomicLong()
    private var frames = PreviewFrames()
    private val modeStore = SuccessfulModeStore(context)
    private val mutableState = MutableStateFlow(CaptureState(
        usbHostSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)))
    val state = mutableState.asStateFlow()
    private var foreground = false
    private var destroyed = false
    private var monitor: UvcDeviceMonitor? = null
    private var surface: Surface? = null
    private var rememberedDevice: Pair<Int, Int>? = null
    private var recovery = ModeRecovery()
    private var modeSaved = false
    private var openedAt = 0L
    private var previousCount = 0L
    private var previousSampleAt = 0L
    private val diagnosticEvents = ArrayDeque<String>()

    suspend fun diagnosticsWithNativeLogs(): String {
        val snapshot = diagnostics()
        val nativeLogs = withContext(Dispatchers.IO) { NativeCaptureLogs().read(Process.myPid()) }
        return snapshot + "\n" + nativeLogs
    }

    private fun trace(event: String) {
        synchronized(diagnosticEvents) {
            if (diagnosticEvents.size >= 80) diagnosticEvents.removeFirst()
            diagnosticEvents.addLast("${SystemClock.elapsedRealtime()} $event")
        }
        Log.i(TAG, event)
    }

    fun diagnostics(): String = buildString {
        appendLine("Pocket Monitor ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
        state.value.selectedDevice?.let { appendLine("USB ${it.vendorId.toString(16)}:${it.productId.toString(16)}") }
        appendLine("State=${state.value.phase}; mode=${state.value.activeMode?.label}; frames=${frames.count.get()}")
        appendLine("Advertised: ${state.value.modes.joinToString { it.label }}")
        synchronized(diagnosticEvents) { diagnosticEvents.forEach { appendLine(it) } }
    }
    // Accessed only on the native worker thread.
    private var camera: UvcPreviewCamera? = null
    private var controlBlock: UvcConnection? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshDevices()
            val selected = state.value.selectedDeviceId
            if (selected != null && state.value.devices.none { it.id == selected }) {
                disconnect(CapturePhase.Idle, CaptureMessage.Unplugged)
            }
            reconnectRememberedDevice()
        }
    }

    fun start() {
        if (foreground || destroyed) return
        foreground = true
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }, ContextCompat.RECEIVER_EXPORTED)
        updatePermission()
        refreshDevices()
        if (state.value.phase == CapturePhase.Paused) mutableState.value = state.value.copy(phase = CapturePhase.Idle)
        reconnectRememberedDevice()
        main.post(stats)
    }

    fun stop() {
        if (!foreground) return
        foreground = false
        context.unregisterReceiver(receiver)
        main.removeCallbacks(stats)
        disconnect(CapturePhase.Paused, CaptureMessage.ResumeOnReturn)
    }

    fun destroy() {
        if (destroyed) return
        stop()
        destroyed = true
        worker.post { thread.quitSafely() }
    }

    fun updatePermission() {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        mutableState.value = state.value.copy(cameraPermission = allowed)
    }

    fun permissionDenied() {
        updatePermission()
        mutableState.value = state.value.copy(phase = CapturePhase.Error,
            message = CaptureMessage.CameraPermissionRequired)
    }

    fun refreshDevices() {
        val devices = usb.deviceList.values.filter(::isVideoDevice).map { device ->
            CaptureDevice(device.deviceName, device.productName?.takeIf { it.isNotBlank() } ?: "",
                device.vendorId, device.productId)
        }.sortedBy { it.id }
        mutableState.value = state.value.copy(devices = devices)
    }

    private fun reconnectRememberedDevice() {
        if (!foreground || !state.value.cameraPermission || surface == null || monitor != null) return
        val key = rememberedDevice ?: return
        val matches = state.value.devices.filter { it.vendorId == key.first && it.productId == key.second }
        // Do not silently choose between two identical cards.
        val device = matches.singleOrNull()?.let { usb.deviceList[it.id] } ?: return
        // Android may still be showing its USB attach/app-selection dialog. Do not
        // race it with a second permission dialog from automatic reconnection.
        if (usb.hasPermission(device)) connect(device.deviceName)
    }

    fun usbAccessChanged() {
        refreshDevices()
        reconnectRememberedDevice()
    }

    fun connect(id: String) {
        // Repeated taps (including selecting the same card on Devices) must not reset
        // an in-flight startup timeout or interrupt an already running preview.
        if (monitor != null && state.value.selectedDeviceId == id &&
            state.value.phase in setOf(CapturePhase.Permission, CapturePhase.Connecting,
                CapturePhase.WaitingForFrames, CapturePhase.Streaming)) return
        beginConnection(id, null, ModeRecovery())
    }

    private fun beginConnection(id: String, requested: VideoMode?, attempts: ModeRecovery) {
        if (!foreground || destroyed) return
        updatePermission()
        if (!state.value.cameraPermission) { permissionDenied(); return }
        val device = usb.deviceList[id]?.takeIf(::isVideoDevice) ?: run {
            refreshDevices()
            mutableState.value = state.value.copy(phase = CapturePhase.Error, message = CaptureMessage.DeviceMissing)
            return
        }
        disconnect(CapturePhase.Idle)
        rememberedDevice = device.vendorId to device.productId
        recovery = attempts
        modeSaved = false
        val preferred = requested ?: modeStore.read(device.vendorId, device.productId)
        trace("Connect requested=${preferred?.label ?: "default"} retries=${attempts.failures.size}")
        val currentFrames = PreviewFrames()
        frames = currentFrames
        val connectionToken = session.begin(id)
        val token = epoch.incrementAndGet()
        mutableState.value = state.value.copy(phase = CapturePhase.Permission, selectedDeviceId = id,
            activeMode = null, modes = emptyList(), message = if (attempts.failures.isEmpty()) null else CaptureMessage.Recovering,
            recoveryAttempt = attempts.failures.size)
        val currentMonitor = createMonitor(object : UvcDeviceListener {
            private fun current() = foreground && session.accepts(connectionToken, id)
            override fun attached(device: UsbDevice) { if (current()) refreshDevices() }
            override fun detached(device: UsbDevice) {
                if (current() && device.deviceName == id) {
                    disconnect(CapturePhase.Idle, CaptureMessage.Disconnected)
                    refreshDevices()
                }
            }
            override fun opened(device: UsbDevice, connection: UvcConnection) {
                if (!current() || device.deviceName != id || state.value.phase != CapturePhase.Permission) return
                mutableState.value = state.value.copy(phase = CapturePhase.Connecting)
                openCamera(token, id, connection, surface, preferred, attempts, currentFrames)
            }
            override fun closed(device: UsbDevice) {
                if (current() && device.deviceName == id) {
                    disconnect(CapturePhase.Error, CaptureMessage.UsbClosed)
                    refreshDevices()
                }
            }
            override fun cancelled(device: UsbDevice) {
                if (current()) {
                    rememberedDevice = null
                    disconnect(CapturePhase.Error, CaptureMessage.UsbPermissionDenied)
                }
            }
            override fun failed(device: UsbDevice, error: Exception) {
                if (current()) fail(epoch.get(), id, CaptureMessage.UsbOpenFailed, error)
            }
        })
        monitor = currentMonitor
        try {
            currentMonitor.register()
            worker.post {
                if (epoch.get() != token) return@post
                try {
                    trace("USB permission/open begin epoch=$token")
                    currentMonitor.requestPermission(device)
                } catch (e: Exception) {
                    main.post { fail(token, id, CaptureMessage.UsbPermissionFailed, e) }
                }
            }
        } catch (e: Exception) { fail(token, id, CaptureMessage.UsbPermissionFailed, e) }
    }

    fun selectMode(mode: VideoMode) {
        val id = state.value.selectedDeviceId ?: return
        if (mode !in state.value.modes || state.value.busy) return
        if (mode == state.value.activeMode && state.value.phase in
            setOf(CapturePhase.WaitingForFrames, CapturePhase.Streaming)) return
        if (monitor == null) {
            beginConnection(id, mode, ModeRecovery())
            return
        }
        // Keep USB authorization, but discard native stream/decoder state between modes.
        // USB callbacks retain their connection generation; frame callbacks get a new epoch.
        val token = epoch.incrementAndGet()
        val currentFrames = PreviewFrames()
        frames = currentFrames
        recovery = ModeRecovery()
        modeSaved = false
        val modes = state.value.modes
        val output = surface
        mutableState.value = state.value.copy(phase = CapturePhase.Connecting, activeMode = null,
            framesPerSecond = 0, message = null, recoveryAttempt = 0)
        worker.post {
            if (epoch.get() != token) return@post
            try {
                trace("Switch close begin epoch=$token target=${mode.label}")
                checkNotNull(camera) { "Camera is no longer open" }.destroy()
                camera = null
                trace("Switch close complete epoch=$token")
                checkNotNull(controlBlock).stopVideoStream().forEach(::trace)
                if (epoch.get() != token) return@post
                val opened = checkNotNull(controlBlock).createCamera(mode)
                camera = opened
                trace("Switch open begin epoch=$token")
                opened.open()
                trace("Switch negotiate begin epoch=$token")
                opened.setMode(mode)
                trace("Switch negotiate complete epoch=$token")
                startPreview(opened, token, id, output, mode, modes, currentFrames)
            } catch (e: Exception) {
                trace("Switch failed epoch=$token ${e.javaClass.simpleName}: ${e.message}")
                main.post {
                    if (currentPreview(token, id) && !tryLowerMode(id, mode, modes, ModeRecovery()))
                        fail(token, id, CaptureMessage.VideoStartFailed, e)
                }
            } catch (e: LinkageError) {
                main.post { fail(token, id, CaptureMessage.NativeUnavailable, e) }
            }
        }
    }

    fun retry() {
        refreshDevices()
        val selected = state.value.selectedDeviceId
        val device = state.value.devices.find { it.id == selected } ?: state.value.devices.singleOrNull()
        if (device != null) beginConnection(device.id, null, ModeRecovery())
    }

    fun userDisconnect() {
        rememberedDevice = null
        disconnect(CapturePhase.Idle, CaptureMessage.Stopped)
    }

    private fun openCamera(token: Long, id: String, block: UvcConnection, output: Surface?,
        preferred: VideoMode?, attempts: ModeRecovery, currentFrames: PreviewFrames) {
        worker.post {
            if (epoch.get() != token) return@post
            var chosen: VideoMode? = null
            var modes = emptyList<VideoMode>()
            try {
                controlBlock = block
                runCatching { block.diagnostics().forEach(::trace) }
                    .onFailure { trace("USB descriptors unavailable: ${it.javaClass.simpleName}") }
                trace("Camera open begin epoch=$token")
                val opened = block.createCamera(preferred)
                camera = opened
                opened.open()
                modes = opened.modes
                trace("Camera open complete epoch=$token; modes=${modes.joinToString { it.label }}")
                val candidates = attempts.candidates(modes, preferred)
                for (mode in candidates.distinct()) {
                    if (epoch.get() != token) break
                    try {
                        opened.setMode(mode)
                        trace("Negotiated epoch=$token ${mode.label}")
                        chosen = mode
                        break
                    } catch (e: IllegalArgumentException) {
                        trace("Rejected epoch=$token ${mode.label}: ${e.message}")
                        Log.w(TAG, "Rejected mode: $mode", e)
                    }
                }
                check(chosen != null) { "No supported MJPEG/YUY2 preview mode could be negotiated" }
                if (epoch.get() != token) return@post
                startPreview(opened, token, id, output, checkNotNull(chosen), modes, currentFrames)
            } catch (e: Exception) {
                main.post {
                    if (!currentPreview(token, id)) return@post
                    val failedMode = chosen
                    if (failedMode == null || !tryLowerMode(id, failedMode, modes, attempts))
                        fail(token, id, CaptureMessage.VideoStartFailed, e)
                }
            } catch (e: LinkageError) {
                main.post { fail(token, id, CaptureMessage.NativeUnavailable, e) }
            }
        }
    }

    private fun currentPreview(token: Long, id: String): Boolean =
        foreground && epoch.get() == token && session.deviceId == id

    private fun startPreview(opened: UvcPreviewCamera, token: Long, id: String, output: Surface?,
        mode: VideoMode, modes: List<VideoMode>, currentFrames: PreviewFrames) {
        if (epoch.get() != token) return
        // Publish the mode before the native first-frame callback can reach the main thread.
        main.post {
            if (!currentPreview(token, id)) return@post
            openedAt = SystemClock.elapsedRealtime()
            previousSampleAt = openedAt
            previousCount = 0
            mutableState.value = state.value.copy(phase = CapturePhase.WaitingForFrames,
                modes = modes, activeMode = mode)
        }
        opened.setFrameCallback {
            if (epoch.get() == token && currentFrames.record(SystemClock.elapsedRealtime())) main.post {
                if (currentPreview(token, id)) {
                    trace("First frame epoch=$token ${mode.label}")
                    mutableState.value = state.value.copy(phase = CapturePhase.Streaming, message = null)
                }
            }
        }
        // stopPreview releases the native window, so bind it again on every restart.
        if (output != null && output.isValid) {
            output.setFrameRateCompat(mode.fps)
            trace("Preview bind/start begin epoch=$token")
            opened.setDisplay(output)
            opened.start()
            trace("Preview start returned epoch=$token ${mode.label}; awaiting frames")
        }
    }

    fun attachSurface(output: Surface) {
        surface = output
        val token = epoch.get()
        worker.post {
            if (epoch.get() == token && output.isValid) camera?.let {
                it.setDisplay(output)
                it.start()
            }
        }
        reconnectRememberedDevice()
    }

    /** Release TextureView's surface only after native rendering has stopped. */
    fun detachSurface(output: Surface, releaseTexture: () -> Unit) {
        if (surface === output) {
            surface = null
            disconnect(CapturePhase.Paused)
        }
        if (!worker.post { output.release(); releaseTexture() }) {
            // The worker quits only after queued native cleanup has finished.
            output.release()
            releaseTexture()
        }
    }

    private fun disconnect(phase: CapturePhase, message: CaptureMessage? = null) {
        session.invalidate()
        epoch.incrementAndGet()
        val oldMonitor = monitor
        monitor = null
        oldMonitor?.unregister()
        worker.post {
            trace("Cleanup begin")
            val hadCamera = camera != null
            val closed = runCatching { camera?.destroy() }.onFailure { Log.w(TAG, "Close camera", it) }.isSuccess
            camera = null
            if (hadCamera && closed) runCatching { controlBlock?.stopVideoStream()?.forEach(::trace) }
                .onFailure { trace("Bulk stop failed: ${it.javaClass.simpleName}") }
            runCatching { controlBlock?.close() }
            controlBlock = null
            oldMonitor?.destroy()
            trace("Cleanup complete")
        }
        frames = PreviewFrames()
        mutableState.value = state.value.copy(phase = phase, activeMode = null, modes = emptyList(),
            framesPerSecond = 0, message = message, recoveryAttempt = 0)
    }

    private fun fail(token: Long, id: String, message: CaptureMessage, error: Throwable) {
        if (!currentPreview(token, id)) return
        trace("Failure epoch=$token $message ${error.javaClass.simpleName}: ${error.message}")
        Log.e(TAG, message.name, error)
        disconnect(CapturePhase.Error, message)
    }

    private fun tryLowerMode(id: String, failed: VideoMode, modes: List<VideoMode>, attempts: ModeRecovery): Boolean {
        if (!foreground || surface?.isValid != true) return false
        val next = attempts.failed(failed)
        val candidate = next.candidates(modes).firstOrNull() ?: return false
        trace("Recovery ${failed.label} -> ${candidate.label}")
        beginConnection(id, candidate, next)
        return true
    }

    private val stats = object : Runnable {
        override fun run() {
            if (!foreground) return
            val phase = state.value.phase
            if (phase in setOf(CapturePhase.WaitingForFrames, CapturePhase.Streaming, CapturePhase.Stalled)) {
                val now = SystemClock.elapsedRealtime()
                val count = frames.count.get()
                val last = frames.lastAt.get()
                val active = state.value.activeMode
                val id = state.value.selectedDeviceId
                // Only startup failures trigger automatic retries. A later HDMI signal loss must
                // not repeatedly reconnect or overwrite a previously proven format.
                if (count == 0L && now - openedAt >= 8000 && surface?.isValid == true &&
                    active != null && id != null && phase != CapturePhase.Stalled) {
                    trace("No startup frames after 8s epoch=${epoch.get()} ${active.label}")
                    if (tryLowerMode(id, active, state.value.modes, recovery)) {
                        main.postDelayed(this, 1000)
                        return
                    }
                }
                val fps = if (previousSampleAt > 0) ((count - previousCount) * 1000 / (now - previousSampleAt).coerceAtLeast(1)).toInt() else 0
                val updatedPhase = when {
                    last > 0 && now - last < 3000 -> CapturePhase.Streaming
                    (count > 0 && now - last >= 3000) || now - openedAt >= 8000 -> CapturePhase.Stalled
                    else -> CapturePhase.WaitingForFrames
                }
                mutableState.value = state.value.copy(phase = updatedPhase, framesPerSecond = fps.coerceAtLeast(0),
                    message = when {
                        updatedPhase == CapturePhase.Stalled -> CaptureMessage.NoFrames
                        updatedPhase == CapturePhase.WaitingForFrames -> state.value.message
                        else -> null
                    })
                if (!modeSaved && frames.hasStableVideo(now) && active != null) {
                    rememberedDevice?.let { (vendor, product) -> modeStore.write(vendor, product, active) }
                    modeSaved = true
                }
                previousCount = count
                previousSampleAt = now
            }
            main.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val TAG = "PocketMonitor"
        fun isVideoDevice(device: UsbDevice): Boolean = device.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
            (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }
    }
}

private fun Surface.setFrameRateCompat(fps: Int) {
    if (Build.VERSION.SDK_INT >= 30) runCatching { setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
}
