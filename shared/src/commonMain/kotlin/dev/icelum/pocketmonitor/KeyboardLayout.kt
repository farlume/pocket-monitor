package dev.icelum.pocketmonitor

data class KeyboardKey(val legend: String, val usage: Int, val width: Float = 1f)

/** ANSI physical positions; legends are keycap symbols, not generated text. */
object AnsiKeyboard {
    private fun letters(value: String) = value.map { KeyboardKey(it.uppercase(), 4 + (it - 'a')) }
    val functionRow = listOf(KeyboardKey("Esc", 41)) + (1..12).map { KeyboardKey("F$it", 57 + it) } +
        listOf(KeyboardKey("PrtSc", 70), KeyboardKey("ScrLk", 71), KeyboardKey("Pause", 72))
    val rows = listOf(
        listOf(KeyboardKey("` ~", 53)) + "1234567890".mapIndexed { i, c -> KeyboardKey(c.toString(), 30 + i) } +
            listOf(KeyboardKey("- _", 45), KeyboardKey("= +", 46), KeyboardKey("⌫", 42, 2f)),
        listOf(KeyboardKey("Tab", 43, 1.5f)) + letters("qwertyuiop") +
            listOf(KeyboardKey("[ {", 47), KeyboardKey("] }", 48), KeyboardKey("\\ |", 49, 1.5f)),
        listOf(KeyboardKey("Caps", 57, 1.75f)) + letters("asdfghjkl") +
            listOf(KeyboardKey("; :", 51), KeyboardKey("' \"", 52), KeyboardKey("Enter", 40, 2.25f)),
        listOf(KeyboardKey("L Shift", 0xE1, 2.25f)) + letters("zxcvbnm") +
            listOf(KeyboardKey(", <", 54), KeyboardKey(". >", 55), KeyboardKey("/ ?", 56), KeyboardKey("R Shift", 0xE5, 2.75f)),
        listOf(KeyboardKey("L Ctrl", 0xE0, 1.25f), KeyboardKey("L ⌘/Win", 0xE3, 1.5f),
            KeyboardKey("L ⌥/Alt", 0xE2, 1.5f), KeyboardKey("Space", 44, 5.5f),
            KeyboardKey("R ⌥/Alt", 0xE6, 1.5f), KeyboardKey("R ⌘/Win", 0xE7, 1.5f),
            KeyboardKey("Menu", 101), KeyboardKey("R Ctrl", 0xE4, 1.25f)),
    )
    val navigation = listOf(KeyboardKey("Ins", 73), KeyboardKey("Home", 74), KeyboardKey("PgUp", 75),
        KeyboardKey("Del", 76), KeyboardKey("End", 77), KeyboardKey("PgDn", 78),
        KeyboardKey("←", 80), KeyboardKey("↑", 82), KeyboardKey("↓", 81), KeyboardKey("→", 79))
    val keypad = listOf(
        listOf(KeyboardKey("Num", 83), KeyboardKey("/", 84), KeyboardKey("*", 85), KeyboardKey("−", 86)),
        listOf(KeyboardKey("7", 95), KeyboardKey("8", 96), KeyboardKey("9", 97), KeyboardKey("+", 87)),
        listOf(KeyboardKey("4", 92), KeyboardKey("5", 93), KeyboardKey("6", 94)),
        listOf(KeyboardKey("1", 89), KeyboardKey("2", 90), KeyboardKey("3", 91)),
        listOf(KeyboardKey("0", 98, 2f), KeyboardKey(".", 99), KeyboardKey("Enter", 88)),
    )
}
