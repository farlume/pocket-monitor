package dev.icelum.pocketmonitor

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface HidTransport {
    interface Listener {
        fun ready()
        fun connection(id: String, connected: Boolean)
        fun failed()
    }
    fun open(listener: Listener)
    fun hosts(): List<InputHost>
    fun connect(id: String): Boolean
    fun disconnect(id: String)
    fun send(id: String, report: Int, bytes: ByteArray): Boolean
    fun close()
}

/** Main-thread owner. A new transport and epoch isolate every foreground session. */
internal class BluetoothInputController(
    private val availability: () -> InputPhase,
    private val factory: () -> HidTransport,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private val mutable = MutableStateFlow(InputState())
    val state = mutable.asStateFlow()
    private var transport: HidTransport? = null
    private var foreground = false
    private var epoch = 0
    private var selected: String? = null
    private val queue = ArrayDeque<Pair<Int, ByteArray>>()
    private var sending = false
    private var physicalInput = false
    private val keyboard = HidKeyboardState()
    private val pump = Runnable { pump() }
    private val timeout = Runnable { fail() }
    private var motionX = 0
    private var motionY = 0
    private var motionWheel = 0
    private var motionPending = false
    private val motion = Runnable { flushMotion() }

    fun start() { foreground = true; if (transport == null) mutable.value = InputState(availability()) }
    fun enable() {
        if (!foreground || transport != null) return
        val available = availability()
        if (available != InputPhase.Off) { mutable.value = InputState(available); return }
        val token = ++epoch
        mutable.value = InputState(InputPhase.Starting)
        handler.postDelayed(timeout, 12_000)
        try {
            val backend = factory()
            transport = backend
            backend.open(object : HidTransport.Listener {
                override fun ready() {
                    if (token != epoch || !foreground || state.value.phase != InputPhase.Starting) return
                    handler.removeCallbacks(timeout)
                    guarded { mutable.value = InputState(InputPhase.Ready, backend.hosts()) }
                }
                override fun connection(id: String, connected: Boolean) {
                    if (token != epoch || !foreground) return
                    if (id != selected) {
                        if (connected) guarded { backend.disconnect(id) }
                        return
                    }
                    guarded {
                        handler.removeCallbacks(timeout)
                        cancelInput()
                        if (connected) {
                            mutable.value = mutable.value.copy(phase = InputPhase.Connected)
                            // Clear state retained by the host after a previous disconnect.
                            if (!backend.send(id, HidReports.KEYBOARD, HidReports.keyboard()) ||
                                !backend.send(id, HidReports.MOUSE, HidReports.mouse())) fail()
                        } else {
                            selected = null
                            mutable.value = InputState(InputPhase.Ready, backend.hosts())
                        }
                    }
                }
                override fun failed() { if (token == epoch) fail() }
            })
        } catch (_: Exception) { fail() }
    }

    fun refresh() {
        if (transport == null) { if (foreground) mutable.value = InputState(availability()); return }
        guarded { mutable.value = mutable.value.copy(hosts = transport!!.hosts()) }
    }

    fun connect(id: String) {
        if (state.value.phase != InputPhase.Ready) return
        guarded {
            val host = transport!!.hosts().singleOrNull { it.id == id } ?: return@guarded
            selected = id
            mutable.value = mutable.value.copy(phase = InputPhase.Connecting, host = host)
            handler.postDelayed(timeout, 20_000)
            if (!transport!!.connect(id)) fail()
        }
    }

    // Disabling closes the registration too: late callbacks cannot reconnect a stopped session.
    fun disable() { shutdown(); mutable.value = InputState(availability()) }
    fun stop() { foreground = false; shutdown(); mutable.value = InputState() }
    private fun fail() { shutdown(); mutable.value = InputState(InputPhase.Error) }
    private fun shutdown() {
        ++epoch
        handler.removeCallbacks(timeout)
        cancelInput()
        val old = transport
        transport = null
        selected?.let { id ->
            runCatching { old?.send(id, HidReports.KEYBOARD, HidReports.keyboard()) }
            runCatching { old?.send(id, HidReports.MOUSE, HidReports.mouse()) }
            runCatching { old?.disconnect(id) }
        }
        selected = null
        runCatching { old?.close() }
    }
    private fun cancelInput() {
        handler.removeCallbacks(pump)
        queue.clear(); sending = false
        physicalInput = false
        keyboard.clear()
        clearMotion()
        mutable.value = mutable.value.copy(sending = false)
    }
    private inline fun guarded(block: () -> Unit) { try { block() } catch (_: Exception) { fail() } }

    fun key(stroke: HidStroke): Boolean {
        if (!HidReports.isKey(stroke.usage) || stroke.modifiers !in 0..255) return false
        return enqueue(listOf(HidReports.KEYBOARD to HidReports.keyboard(stroke),
            HidReports.KEYBOARD to HidReports.keyboard()))
    }
    fun keyDown(usage: Int): Boolean = physicalKey(usage, true)
    fun keyUp(usage: Int): Boolean = physicalKey(usage, false)
    private fun physicalKey(usage: Int, down: Boolean): Boolean {
        if (state.value.phase != InputPhase.Connected || (sending && !physicalInput)) return false
        // Overflow must release the host, never discard just a key-up report.
        if (queue.size >= 64) { cancelSending(); return false }
        if (!(if (down) keyboard.press(usage) else keyboard.release(usage))) return false
        clearMotion()
        queue.addLast(HidReports.KEYBOARD to keyboard.report())
        physicalInput = true
        if (!sending) { sending = true; pump() }
        return true
    }
    fun text(value: String): Boolean {
        val strokes = HidReports.text(value) ?: return false
        if (strokes.isEmpty() || sending) return false
        return enqueue(strokes.flatMap { listOf(HidReports.KEYBOARD to HidReports.keyboard(it),
            HidReports.KEYBOARD to HidReports.keyboard()) })
    }
    fun click(right: Boolean) = enqueue(listOf(HidReports.MOUSE to HidReports.mouse(if (right) 2 else 1),
        HidReports.MOUSE to HidReports.mouse()))
    fun move(x: Int, y: Int, wheel: Int = 0) {
        if (sending || state.value.phase != InputPhase.Connected) return
        motionX = (motionX + x).coerceIn(-1024, 1024)
        motionY = (motionY + y).coerceIn(-1024, 1024)
        motionWheel = (motionWheel + wheel).coerceIn(-127, 127)
        if (!motionPending) { motionPending = true; handler.postDelayed(motion, 16) }
    }
    private fun clearMotion() {
        handler.removeCallbacks(motion)
        motionPending = false; motionX = 0; motionY = 0; motionWheel = 0
    }
    private fun flushMotion() {
        motionPending = false
        if (state.value.phase != InputPhase.Connected || sending) { clearMotion(); return }
        val x = motionX.coerceIn(-127, 127)
        val y = motionY.coerceIn(-127, 127)
        val wheel = motionWheel
        motionX -= x; motionY -= y; motionWheel = 0
        guarded {
            if (!transport!!.send(selected!!, HidReports.MOUSE, HidReports.mouse(x = x, y = y, wheel = wheel))) fail()
            else if (motionX != 0 || motionY != 0) { motionPending = true; handler.postDelayed(motion, 16) }
        }
    }
    fun cancelSending() {
        if (state.value.phase != InputPhase.Connected) return
        cancelInput()
        guarded {
            if (!transport!!.send(selected!!, HidReports.KEYBOARD, HidReports.keyboard()) ||
                !transport!!.send(selected!!, HidReports.MOUSE, HidReports.mouse())) fail()
        }
    }
    private fun enqueue(reports: List<Pair<Int, ByteArray>>): Boolean {
        if (state.value.phase != InputPhase.Connected || sending || keyboard.isPressed) return false
        clearMotion()
        queue.addAll(reports)
        sending = true
        mutable.value = mutable.value.copy(sending = true)
        pump()
        return true
    }
    private fun pump() {
        if (state.value.phase != InputPhase.Connected) { cancelInput(); return }
        val next = queue.removeFirstOrNull()
        if (next == null) { sending = false; physicalInput = false; mutable.value = mutable.value.copy(sending = false); return }
        guarded {
            if (!transport!!.send(selected!!, next.first, next.second)) { fail(); return@guarded }
            handler.postDelayed(pump, 20)
        }
    }
}
