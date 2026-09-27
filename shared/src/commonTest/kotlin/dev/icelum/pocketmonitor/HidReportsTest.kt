package dev.icelum.pocketmonitor

import kotlin.test.*

class HidReportsTest {
    @Test fun leftAndRightModifiersUseEightIndependentBits() {
        for (i in 0..7) {
            val report = HidReports.keyboard(HidStroke(0xE0 + i))
            assertEquals(1 shl i, report[0].toInt() and 255)
            assertTrue(report.drop(1).all { it == 0.toByte() })
        }
        val keyboard = HidKeyboardState()
        assertTrue(keyboard.press(0xE1)); assertTrue(keyboard.press(0xE5)); assertTrue(keyboard.press(4))
        assertContentEquals(byteArrayOf(34, 0, 4, 0, 0, 0, 0, 0), keyboard.report())
        keyboard.release(0xE1)
        assertEquals(32, keyboard.report()[0].toInt())
        keyboard.clear()
        assertContentEquals(ByteArray(8), keyboard.report())
    }

    @Test fun rolloverLimitRejectsOnlySeventhOrdinaryKeyWithoutLosingRelease() {
        val keyboard = HidKeyboardState()
        (4..9).forEach { assertTrue(keyboard.press(it)) }
        assertFalse(keyboard.press(10))
        assertTrue(keyboard.press(0xE7))
        assertFalse(keyboard.press(4))
        assertTrue(keyboard.release(4))
        assertTrue(keyboard.press(10))
        assertFalse(keyboard.release(11))
        assertContentEquals(byteArrayOf(-128, 0, 5, 6, 7, 8, 9, 10), keyboard.report())
        assertFalse(keyboard.press(999))
    }

    @Test fun ansiLayoutContains104UniqueKeysAndCorrectShiftNeighbours() {
        val all = AnsiKeyboard.functionRow + AnsiKeyboard.rows.flatten() + AnsiKeyboard.navigation + AnsiKeyboard.keypad.flatten()
        assertEquals(104, all.size)
        assertEquals(104, all.map { it.usage }.toSet().size)
        assertTrue(all.all { HidReports.isKey(it.usage) })
        val shiftRow = AnsiKeyboard.rows.first { row -> row.any { it.usage == 0xE1 } }
        assertEquals(29, shiftRow[shiftRow.indexOfFirst { it.usage == 0xE1 } + 1].usage)
        assertEquals(56, shiftRow[shiftRow.indexOfFirst { it.usage == 0xE5 } - 1].usage)
    }
    @Test fun printableAsciiMapsToUsKeyboardAndShift() {
        assertNotNull(HidReports.text((32..126).map(Int::toChar).joinToString("")))
        assertEquals(listOf(HidStroke(4), HidStroke(4, 2), HidStroke(39), HidStroke(39, 2),
            HidStroke(52), HidStroke(52, 2), HidStroke(49), HidStroke(49, 2), HidStroke(40), HidStroke(43)),
            HidReports.text("aA0)'\"\\|\n\t"))
    }
    @Test fun unsupportedOrOversizeTextIsRejectedAsAWhole() {
        assertNull(HidReports.text("safe prefix中文"))
        assertNull(HidReports.text("emoji😀"))
        assertNull(HidReports.text("a".repeat(201)))
        assertNull(HidReports.text("\u0000"))
        assertEquals(200, HidReports.text("a".repeat(200))!!.size)
    }
    @Test fun keyboardReportsIncludeModifierAndAllKeysUp() {
        assertContentEquals(byteArrayOf(9, 0, 6, 0, 0, 0, 0, 0), HidReports.keyboard(HidStroke(6, 9)))
        assertContentEquals(ByteArray(8), HidReports.keyboard())
    }
    @Test fun relativeMouseClampsSignedAxesAndMasksButtons() {
        assertContentEquals(byteArrayOf(7, 127, -127, -127), HidReports.mouse(255, 500, -500, -999))
        assertContentEquals(ByteArray(4), HidReports.mouse())
    }
}
