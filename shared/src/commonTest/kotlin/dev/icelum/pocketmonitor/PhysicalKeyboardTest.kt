package dev.icelum.pocketmonitor

import kotlin.test.*

class PhysicalKeyboardTest {
    @Test fun physicalBoardsHaveExactCountsNoDuplicateUsagesOrOverlappingKeys() {
        listOf(PhysicalKeyboards.tkl87 to 87, PhysicalKeyboards.compact84 to 84).forEach { (board, count) ->
            assertEquals(count, board.keys.size)
            assertEquals(count, board.keys.map { it.key.usage }.toSet().size)
            board.keys.forEach { cap ->
                assertTrue(cap.x >= 0f && cap.y >= 0f && cap.x + cap.key.width <= board.width && cap.y + 1f <= board.height)
                assertTrue(cap.key.usage < 0 || HidReports.isKey(cap.key.usage))
                board.keys.filter { it != cap }.forEach { other ->
                    assertFalse(cap.x < other.x + other.key.width && other.x < cap.x + cap.key.width &&
                        cap.y < other.y + 1f && other.y < cap.y + 1f, "Overlapping keys: $cap / $other")
                }
            }
            assertEquals(6.25f, board.keys.single { it.key.usage == 44 }.key.width)
        }
    }

    @Test fun tklKeepsPhysicalFunctionGroupsNavigationAndInvertedTArrows() {
        val keys = PhysicalKeyboards.tkl87.keys.associateBy { it.key.usage }
        assertEquals(2f, keys.getValue(58).x)
        assertEquals(6.5f, keys.getValue(62).x)
        assertEquals(11f, keys.getValue(66).x)
        assertEquals(15.5f, keys.getValue(73).x)
        assertEquals(16.5f, keys.getValue(82).x)
        assertEquals(keys.getValue(81).y - 1f, keys.getValue(82).y)
        assertEquals(listOf(15.5f, 16.5f, 17.5f), listOf(80, 81, 79).map { keys.getValue(it).x })
    }

    @Test fun k2KeepsNarrowRightShiftAndActualRightColumnOrder() {
        val keys = PhysicalKeyboards.compact84.keys.associateBy { it.key.usage }
        assertEquals(1.75f, keys.getValue(0xE5).key.width)
        assertEquals(listOf(75, 78, 74, 77), keys.values.filter { it.x == 15f && it.y in 1f..4f }.sortedBy { it.y }.map { it.key.usage })
        listOf(PhysicalKeyboards.tkl87, PhysicalKeyboards.compact84).forEach { board ->
            val row = board.keys.filter { it.key.usage in listOf(0xE1, 29, 56, 0xE5) }.sortedBy { it.x }
            assertEquals(listOf(0xE1, 29, 56, 0xE5), row.map { it.key.usage })
        }
    }
}
