package dev.icelum.pocketmonitor

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w1100dp-h1200dp-160dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun initializeResources() = initializeComposeResources()
    private var preferences by mutableStateOf(AppPreferences())
    private var sending by mutableStateOf(false)
    private val feedback = mutableListOf<HapticFeedbackType>()

    private fun keyboard(actions: InputActions = InputActions()) {
        compose.setContent {
            AppLocale(AppLanguage.English) {
                CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { feedback += hapticFeedbackType }
                }) {
                    MonitorTheme(preferences) {
                        InputPage(InputState(InputPhase.Connected, host = InputHost("pc", "Computer"), sending = sending),
                            actions, preferences = preferences, onPreferences = { preferences = it })
                    }
                }
            }
        }
    }

    @Test fun touchHasOneHapticPerPressAndCancelledOrDisabledTouchesDoNotType() {
        val strokes = mutableListOf<HidStroke>()
        keyboard(InputActions(key = { strokes += it }))
        compose.onNodeWithTag("key_4").performScrollTo().performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(1, feedback.size); assertTrue(strokes.isEmpty()) }
        compose.onNodeWithTag("key_4").performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(HidStroke(4)), strokes); assertEquals(1, feedback.size) }
        compose.onNodeWithTag("key_4").performTouchInput { down(center); cancel() }
        compose.runOnIdle { assertEquals(1, strokes.size); assertEquals(2, feedback.size) }
        compose.runOnIdle { preferences = preferences.copy(keyboardHaptics = false) }
        compose.onNodeWithTag("key_4").performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, strokes.size); assertEquals(2, feedback.size); sending = true }
        compose.onNodeWithTag("key_4").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, strokes.size); assertEquals(2, feedback.size) }
    }

    @Test fun layoutAndThemeAreIndependentAndSwitchingLayoutClearsInput() {
        var cancellations = 0
        keyboard(InputActions(cancel = { cancellations++ }))
        compose.onNodeWithTag("keyboard_theme").performScrollTo().performClick()
        compose.onNodeWithTag("keyboard_theme_BlackGold").performClick()
        compose.runOnIdle { assertEquals(KeyboardTheme.BlackGold, preferences.keyboardTheme); assertEquals(KeyboardLayout.Tkl87, preferences.keyboardLayout) }
        compose.onNodeWithTag("keyboard_layout").performClick()
        compose.onNodeWithTag("keyboard_layout_Compact84").performClick()
        compose.runOnIdle { assertEquals(KeyboardLayout.Compact84, preferences.keyboardLayout); assertEquals(KeyboardTheme.BlackGold, preferences.keyboardTheme); assertTrue(cancellations > 0) }
        compose.onNodeWithTag("key_71").assertDoesNotExist()
        compose.onNodeWithTag("key_${PhysicalKeyboards.LIGHT}").assertExists()
    }

    @Test fun fnReleaseBeforeArrowStillReleasesOriginalTranslatedUsage() {
        val events = mutableListOf<Pair<String, Int>>()
        keyboard(InputActions(keyDown = { events += "down" to it }, keyUp = { events += "up" to it }))
        compose.onNodeWithTag("input_hold_mode").performScrollTo().performClick()
        compose.onNodeWithTag("key_${PhysicalKeyboards.FN}").performScrollTo()
        val fn = compose.onNodeWithTag("key_${PhysicalKeyboards.FN}").fetchSemanticsNode().boundsInRoot.center
        val arrow = compose.onNodeWithTag("key_80").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(0, fn); down(1, arrow) }
        compose.runOnIdle { assertEquals(listOf("down" to 74), events) }
        compose.onRoot().performTouchInput { up(0) }
        compose.onRoot().performTouchInput { up(1) }
        compose.runOnIdle { assertEquals(listOf("down" to 74, "up" to 74), events) }
    }

    @Test fun releaseAllClearsLatchedFnAndAccessibilityClickHasFeedback() {
        val strokes = mutableListOf<HidStroke>()
        keyboard(InputActions(key = { strokes += it }))
        compose.onNodeWithTag("key_${PhysicalKeyboards.FN}").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("input_release_keys").performScrollTo().performClick()
        compose.onNodeWithTag("key_${PhysicalKeyboards.FN}").assertIsNotSelected()
        compose.onNodeWithTag("key_80").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(HidStroke(80)), strokes); assertEquals(2, feedback.size) }
    }

    @Test fun fullscreenModifierKeycapsLatchForOneShortcutAndThenClear() {
        val strokes = mutableListOf<HidStroke>()
        keyboard(InputActions(key = { strokes += it }))
        compose.onNodeWithTag("keyboard_expand").performScrollTo().performClick()
        compose.onNodeWithTag("key_224").performClick().assertIsSelected()
        compose.onNodeWithTag("key_225").performClick().assertIsSelected()
        compose.onNodeWithTag("key_4").performClick()
        compose.onNodeWithTag("key_224").assertIsNotSelected()
        compose.onNodeWithTag("key_225").assertIsNotSelected()
        compose.onNodeWithTag("key_4").performClick()
        compose.runOnIdle { assertEquals(listOf(HidStroke(4, 3), HidStroke(4)), strokes) }
    }

    @Test fun fullscreenKeepsTypingAndLayoutControlsAndClosesSafely() {
        val strokes = mutableListOf<HidStroke>()
        var cancellations = 0
        keyboard(InputActions(key = { strokes += it }, cancel = { cancellations++ }))
        compose.onNodeWithTag("keyboard_expand").performScrollTo().performClick()
        compose.onNodeWithTag("key_4").performClick()
        compose.onNodeWithTag("expanded_keyboard_layout").performClick()
        compose.onNodeWithTag("expanded_keyboard_layout_Compact84").performClick()
        compose.onNodeWithTag("keyboard_close").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(HidStroke(4)), strokes); assertEquals(KeyboardLayout.Compact84, preferences.keyboardLayout); assertTrue(cancellations > 0) }
    }

    @Test fun renderAllPhysicalThemesAndPressedKeyFromActualUi() {
        keyboard()
        KeyboardTheme.entries.forEach { theme ->
            compose.runOnIdle { preferences = preferences.copy(keyboardTheme = theme,
                keyboardLayout = if (theme == KeyboardTheme.Graphite) KeyboardLayout.Compact84 else KeyboardLayout.Tkl87) }
            saveBoard("keyboard-${theme.name.lowercase()}")
        }
        compose.onNodeWithTag("key_4").performTouchInput { down(center) }
        saveBoard("keyboard-sea-pressed")
        compose.onNodeWithTag("key_4").performTouchInput { up() }
    }

    private fun saveBoard(name: String) {
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("physical_keyboard").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(full))
            val board = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
            File("build/reports/screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
                board.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            board.recycle(); full.recycle()
        }
    }
}
