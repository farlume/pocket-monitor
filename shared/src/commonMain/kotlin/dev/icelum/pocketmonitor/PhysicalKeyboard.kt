package dev.icelum.pocketmonitor

enum class KeyboardLayout { Tkl87, Compact84 }
enum class KeyboardTheme { Ivory, Graphite, BlackGold, Sea }

/** Coordinates are key pitches (u), including the gap around each cap. */
data class PhysicalKey(val key: KeyboardKey, val x: Float, val y: Float)
data class PhysicalKeyboard(val width: Float, val height: Float, val keys: List<PhysicalKey>)

object PhysicalKeyboards {
    // Fn and the K2 lamp are local controls, never keyboard HID usages.
    const val FN = -1
    const val LIGHT = -2

    private fun key(legend: String, usage: Int, width: Float = 1f) = KeyboardKey(legend, usage, width)
    private fun MutableList<PhysicalKey>.row(y: Float, keys: List<KeyboardKey>, start: Float = 0f) {
        var x = start
        keys.forEach { add(PhysicalKey(it, x, y)); x += it.width }
    }

    val tkl87 = PhysicalKeyboard(18.5f, 6.5f, buildList {
        row(0f, listOf(key("Esc", 41)))
        (0..2).forEach { group -> row(0f, (1..4).map { key("F${group * 4 + it}", 57 + group * 4 + it) }, 2f + group * 4.5f) }
        row(0f, listOf(key("PrtSc", 70), key("ScrLk", 71), key("Pause", 72)), 15.5f)
        AnsiKeyboard.rows.take(4).forEachIndexed { i, keys -> row(i + 1.5f, keys) }
        row(5.5f, listOf(key("Ctrl", 0xE0, 1.25f), key("Win", 0xE3, 1.25f), key("Alt", 0xE2, 1.25f),
            key("", 44, 6.25f), key("Alt", 0xE6, 1.25f), key("Win", 0xE7, 1.25f), key("Fn", FN, 1.25f), key("Ctrl", 0xE4, 1.25f)))
        row(1.5f, listOf(key("Ins", 73), key("Home", 74), key("PgUp", 75)), 15.5f)
        row(2.5f, listOf(key("Del", 76), key("End", 77), key("PgDn", 78)), 15.5f)
        row(4.5f, listOf(key("↑", 82)), 16.5f)
        row(5.5f, listOf(key("←", 80), key("↓", 81), key("→", 79)), 15.5f)
    })

    // Keychron K2 ANSI: the right column is PgUp / PgDn / Home / End.
    val compact84 = PhysicalKeyboard(16f, 6f, buildList {
        row(0f, listOf(key("Esc", 41)) + (1..12).map { key("F$it", 57 + it) } +
            listOf(key("PrtSc", 70), key("Del", 76), key("☼", LIGHT)))
        AnsiKeyboard.rows.take(3).forEachIndexed { i, keys -> row(i + 1f, keys) }
        row(1f, listOf(key("PgUp", 75)), 15f)
        row(2f, listOf(key("PgDn", 78)), 15f)
        row(3f, listOf(key("Home", 74)), 15f)
        row(4f, AnsiKeyboard.rows[3].dropLast(1) + listOf(key("R Shift", 0xE5, 1.75f), key("↑", 82), key("End", 77)))
        row(5f, listOf(key("Ctrl", 0xE0, 1.25f), key("Win", 0xE3, 1.25f), key("Alt", 0xE2, 1.25f),
            key("", 44, 6.25f), key("Alt", 0xE6), key("Fn", FN), key("Ctrl", 0xE4), key("←", 80), key("↓", 81), key("→", 79)))
    })

    fun fnUsage(usage: Int): Int = when (usage) {
        80 -> 74 // left -> Home
        79 -> 77 // right -> End
        82 -> 75 // up -> Page Up
        81 -> 78 // down -> Page Down
        42 -> 76 // Backspace -> Delete
        76 -> 73 // Delete -> Insert
        else -> usage
    }

    fun forLayout(layout: KeyboardLayout) = when (layout) {
        KeyboardLayout.Tkl87 -> tkl87
        KeyboardLayout.Compact84 -> compact84
    }
}
