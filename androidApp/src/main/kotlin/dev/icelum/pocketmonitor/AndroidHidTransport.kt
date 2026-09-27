package dev.icelum.pocketmonitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

internal fun inputAvailability(context: Context): InputPhase {
    if (Build.VERSION.SDK_INT < 28) return InputPhase.Unsupported
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return InputPhase.Unsupported
    if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context,
            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return InputPhase.PermissionRequired
    return try { if (adapter.isEnabled) InputPhase.Off else InputPhase.BluetoothOff }
    catch (_: SecurityException) { InputPhase.PermissionRequired }
}

/** Only constructed on API 28+. Permission is checked before creation and revocation is handled. */
@RequiresApi(28)
@SuppressLint("MissingPermission")
internal class AndroidHidTransport(private val context: Context) : HidTransport {
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter!!
    private var proxy: BluetoothHidDevice? = null
    private var listener: HidTransport.Listener? = null
    private var closed = false
    private var receiverRegistered = false
    private var keyboard = HidReports.keyboard()
    private var mouse = HidReports.mouse()
    private var leds: Byte = 0
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) != BluetoothAdapter.STATE_ON) {
                if (!closed) listener?.failed()
            }
        }
    }
    private val service = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
            if (closed) { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, service); return }
            proxy = service as BluetoothHidDevice
            try {
                val sdp = BluetoothHidDeviceAppSdpSettings("Pocket Monitor", "Keyboard and touchpad",
                    "Pocket Monitor", BluetoothHidDevice.SUBCLASS1_COMBO, HidReports.descriptor)
                if (!proxy!!.registerApp(sdp, null, null, ContextCompat.getMainExecutor(context), callback)) listener?.failed()
            } catch (_: Exception) { listener?.failed() }
        }
        override fun onServiceDisconnected(profile: Int) { if (!closed) listener?.failed() }
    }
    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (closed) return
            if (registered) {
                listener?.ready()
                // A restored host must still match the user's explicit selection.
                if (pluggedDevice != null) listener?.connection(pluggedDevice.address, true)
            } else listener?.failed()
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (closed) return
            if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_DISCONNECTED) {
                listener?.connection(device.address, state == BluetoothProfile.STATE_CONNECTED)
            }
        }
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            if (closed) return
            safe {
                val bytes = when {
                    type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == HidReports.KEYBOARD -> keyboard
                    type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == HidReports.MOUSE -> mouse
                    type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && id.toInt() == HidReports.KEYBOARD -> byteArrayOf(leds)
                    else -> null
                }
                if (bytes == null) proxy?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                else if (bufferSize != 0 && bufferSize < bytes.size) proxy?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_PARAM)
                else proxy?.replyReport(device, type, id, bytes)
            }
        }
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            if (closed) return
            safe {
                if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && id.toInt() == HidReports.KEYBOARD && data.size == 1) {
                    leds = data[0]; proxy?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
                } else proxy?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_PARAM)
            }
        }
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            if (!closed && reportId.toInt() == HidReports.KEYBOARD && data.size == 1) leds = data[0]
        }
        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            // This composite descriptor uses report protocol, not boot/BIOS input.
            if (!closed && protocol == BluetoothHidDevice.PROTOCOL_BOOT_MODE) listener?.failed()
        }
        override fun onVirtualCableUnplug(device: BluetoothDevice) { if (!closed) listener?.failed() }
    }
    private inline fun safe(block: () -> Unit) { try { block() } catch (_: Exception) { listener?.failed() } }
    override fun open(listener: HidTransport.Listener) {
        this.listener = listener
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
        if (!adapter.getProfileProxy(context, service, BluetoothProfile.HID_DEVICE)) listener.failed()
    }
    override fun hosts(): List<InputHost> = adapter.bondedDevices.map { InputHost(it.address, it.name ?: it.address) }
        .sortedWith(compareBy({ it.name }, { it.id }))
    override fun connect(id: String): Boolean = proxy?.connect(adapter.getRemoteDevice(id)) == true
    override fun disconnect(id: String) { proxy?.disconnect(adapter.getRemoteDevice(id)) }
    override fun send(id: String, report: Int, bytes: ByteArray): Boolean {
        val sent = proxy?.sendReport(adapter.getRemoteDevice(id), report, bytes) == true
        if (sent) {
            if (report == HidReports.KEYBOARD) keyboard = bytes.copyOf()
            if (report == HidReports.MOUSE) mouse = HidReports.mouse(buttons = bytes[0].toInt())
        }
        return sent
    }
    override fun close() {
        if (closed) return
        closed = true
        if (receiverRegistered) { context.unregisterReceiver(receiver); receiverRegistered = false }
        val old = proxy; proxy = null
        if (old != null) {
            runCatching { old.unregisterApp() }
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, old)
        }
        listener = null
    }
}
