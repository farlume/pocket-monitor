package dev.icelum.pocketmonitor

enum class InputPhase { Off, Unsupported, PermissionRequired, BluetoothOff, Starting, Ready, Connecting, Connected, Error }
data class InputHost(val id: String, val name: String)
data class InputState(
    val phase: InputPhase = InputPhase.Off,
    val hosts: List<InputHost> = emptyList(),
    val host: InputHost? = null,
    val sending: Boolean = false,
)

/** USB HID usages, independent of Android and the host's keyboard layout. */
data class HidStroke(val usage: Int, val modifiers: Int = 0)
object HidReports {
    const val KEYBOARD = 1
    const val MOUSE = 2
    const val CTRL = 1
    const val SHIFT = 2
    const val ALT = 4
    const val META = 8
    const val RIGHT_CTRL = 16
    const val RIGHT_SHIFT = 32
    const val RIGHT_ALT = 64
    const val RIGHT_META = 128
    const val MAX_TEXT = 200

    fun isKey(usage: Int) = usage in 4..0x65 || usage in 0xE0..0xE7
    fun keyboard(stroke: HidStroke? = null): ByteArray {
        if (stroke == null) return ByteArray(8)
        require(isKey(stroke.usage) && stroke.modifiers in 0..255)
        val modifier = if (stroke.usage in 0xE0..0xE7) 1 shl (stroke.usage - 0xE0) else 0
        return byteArrayOf((stroke.modifiers or modifier).toByte(), 0,
            (if (modifier == 0) stroke.usage else 0).toByte(), 0, 0, 0, 0, 0)
    }

    fun keyboard(keys: Set<Int>): ByteArray {
        require(keys.all(::isKey))
        val ordinary = keys.filter { it < 0xE0 }.sorted()
        require(ordinary.size <= 6)
        val report = ByteArray(8)
        keys.filter { it >= 0xE0 }.forEach { report[0] = (report[0].toInt() or (1 shl (it - 0xE0))).toByte() }
        ordinary.forEachIndexed { i, key -> report[i + 2] = key.toByte() }
        return report
    }
    fun mouse(buttons: Int = 0, x: Int = 0, y: Int = 0, wheel: Int = 0): ByteArray = byteArrayOf(
        (buttons and 7).toByte(), x.coerceIn(-127, 127).toByte(),
        y.coerceIn(-127, 127).toByte(), wheel.coerceIn(-127, 127).toByte())

    // US keyboard positions. Reject the whole text before sending unsupported characters.
    fun text(value: String): List<HidStroke>? {
        if (value.length > MAX_TEXT) return null
        return value.map { c ->
            when (c) {
                in 'a'..'z' -> HidStroke(4 + (c - 'a'))
                in 'A'..'Z' -> HidStroke(4 + (c - 'A'), SHIFT)
                in '1'..'9' -> HidStroke(30 + (c - '1'))
                '0' -> HidStroke(39)
                '\n' -> HidStroke(40)
                '\t' -> HidStroke(43)
                ' ' -> HidStroke(44)
                else -> {
                    val plain = "-=[]\\;\u0027`,./"
                    val shifted = "_+{}|:\"~<>?"
                    val usages = listOf(45, 46, 47, 48, 49, 51, 52, 53, 54, 55, 56)
                    val digitShift = "!@#$%^&*()"
                    when {
                        c in plain -> HidStroke(usages[plain.indexOf(c)])
                        c in shifted -> HidStroke(usages[shifted.indexOf(c)], SHIFT)
                        c in digitShift -> HidStroke(30 + digitShift.indexOf(c), SHIFT)
                        else -> return null
                    }
                }
            }
        }
    }

    // Keyboard (8-byte input + LED output) and relative mouse (buttons, X, Y, wheel).
    val descriptor: ByteArray = intArrayOf(
        0x05,0x01, 0x09,0x06, 0xA1,0x01, 0x85,KEYBOARD,
        0x05,0x07, 0x19,0xE0, 0x29,0xE7, 0x15,0x00, 0x25,0x01,
        0x75,0x01, 0x95,0x08, 0x81,0x02,
        0x95,0x01, 0x75,0x08, 0x81,0x01,
        0x95,0x05, 0x75,0x01, 0x05,0x08, 0x19,0x01, 0x29,0x05, 0x91,0x02,
        0x95,0x01, 0x75,0x03, 0x91,0x01,
        0x95,0x06, 0x75,0x08, 0x15,0x00, 0x25,0x65,
        0x05,0x07, 0x19,0x00, 0x29,0x65, 0x81,0x00, 0xC0,
        0x05,0x01, 0x09,0x02, 0xA1,0x01, 0x85,MOUSE,
        0x09,0x01, 0xA1,0x00, 0x05,0x09, 0x19,0x01, 0x29,0x03,
        0x15,0x00, 0x25,0x01, 0x95,0x03, 0x75,0x01, 0x81,0x02,
        0x95,0x01, 0x75,0x05, 0x81,0x01,
        0x05,0x01, 0x09,0x30, 0x09,0x31, 0x09,0x38,
        0x15,0x81, 0x25,0x7F, 0x75,0x08, 0x95,0x03, 0x81,0x06, 0xC0,0xC0,
    ).map(Int::toByte).toByteArray()
}

/** Six ordinary keys plus all eight independent modifiers, matching the descriptor. */
class HidKeyboardState {
    private val keys = mutableSetOf<Int>()
    val isPressed get() = keys.isNotEmpty()
    fun press(usage: Int): Boolean {
        if (!HidReports.isKey(usage) || usage in keys ||
            (usage < 0xE0 && keys.count { it < 0xE0 } >= 6)) return false
        keys += usage
        return true
    }
    fun release(usage: Int): Boolean = keys.remove(usage)
    fun clear() = keys.clear()
    fun report(): ByteArray = HidReports.keyboard(keys)
}
