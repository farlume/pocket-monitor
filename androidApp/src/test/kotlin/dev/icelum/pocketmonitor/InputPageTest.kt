package dev.icelum.pocketmonitor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputPageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun initializeResources() = initializeComposeResources()

    private fun keyboard(actions: InputActions = InputActions(), language: AppLanguage = AppLanguage.English) {
        compose.setContent {
            AppLocale(language) {
                MonitorTheme(AppPreferences()) {
                    InputPage(InputState(InputPhase.Connected, host = InputHost("pc", "MacBook Pro")), actions)
                }
            }
        }
    }

    @Test fun macSetupSendsUnmodifiedPositionKeysAndRightShiftIsIndependent() {
        val strokes = mutableListOf<HidStroke>()
        keyboard(InputActions(key = { strokes += it }))
        compose.onNodeWithTag("modifier_32").performScrollTo().performClick()
        compose.onNodeWithTag("key_4").performScrollTo().performClick()
        assertEquals(HidStroke(4, 32), strokes.single())
        compose.onNodeWithTag("input_mac_setup").performScrollTo().performClick()
        compose.onNodeWithTag("setup_key_29").performScrollTo().performClick()
        compose.onNodeWithTag("setup_key_56").performScrollTo().performClick()
        assertEquals(listOf(HidStroke(4, 32), HidStroke(29), HidStroke(56)), strokes)
    }

    @Test fun holdModeSendsDownBeforeLiftAndReleasesOnLift() {
        val events = mutableListOf<Pair<String, Int>>()
        keyboard(InputActions(keyDown = { events += "down" to it }, keyUp = { events += "up" to it }))
        compose.onNodeWithTag("input_hold_mode").performScrollTo().performClick()
        compose.onNodeWithTag("key_225").performScrollTo().performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(listOf("down" to 225), events) }
        compose.onNodeWithTag("key_225").performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf("down" to 225, "up" to 225), events) }
    }

    @Test fun cancelledTouchReleasesHeldKey() {
        val events = mutableListOf<Pair<String, Int>>()
        keyboard(InputActions(keyDown = { events += "down" to it }, keyUp = { events += "up" to it }))
        compose.onNodeWithTag("input_hold_mode").performScrollTo().performClick()
        compose.onNodeWithTag("key_225").performScrollTo().performTouchInput { down(center); cancel() }
        compose.runOnIdle { assertEquals(listOf("down" to 225, "up" to 225), events) }
    }

    @Test fun twoFingersHoldShiftWhilePressingAndReleasingZ() {
        val events = mutableListOf<Pair<String, Int>>()
        keyboard(InputActions(keyDown = { events += "down" to it }, keyUp = { events += "up" to it }))
        compose.onNodeWithTag("input_hold_mode").performScrollTo().performClick()
        compose.onNodeWithTag("key_225").performScrollTo()
        val shift = compose.onNodeWithTag("key_225").fetchSemanticsNode().boundsInRoot.center
        val z = compose.onNodeWithTag("key_29").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(0, shift); down(1, z); up(1); up(0)
        }
        compose.runOnIdle { assertEquals(listOf("down" to 225, "down" to 29, "up" to 29, "up" to 225), events) }
    }

    @Test fun renderConnectedKeyboardPortrait() {
        keyboard()
        saveKeyboard("keyboard-en-portrait")
    }

    @Test @Config(qualifiers = "zh-rCN-w900dp-h500dp-160dpi") fun renderConnectedKeyboardLandscapeChinese() {
        keyboard(language = AppLanguage.Chinese)
        compose.onNodeWithTag("key_44").performScrollTo()
        saveKeyboard("keyboard-zh-landscape")
    }

    private fun saveKeyboard(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/screenshots/$name.png").apply { parentFile?.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun sendsOneShotShortcutAndRejectsUnicodeWithoutPartialSending() {
        val strokes = mutableListOf<HidStroke>()
        val texts = mutableListOf<String>()
        compose.setContent {
            AppLocale(AppLanguage.English) {
                MonitorTheme(AppPreferences()) {
                    InputPage(InputState(InputPhase.Connected, host = InputHost("pc", "Computer")),
                        InputActions(key = { strokes += it }, text = { texts += it; true }))
                }
            }
        }
        compose.onNodeWithTag("modifier_1").performScrollTo().performClick()
        compose.onNodeWithTag("key_4").performScrollTo().performClick()
        assertEquals(listOf(HidStroke(4, HidReports.CTRL)), strokes)
        compose.onNodeWithTag("modifier_1").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("input_text").performScrollTo().performTextInput("hello中文")
        compose.onNodeWithTag("input_send").performScrollTo().assertIsNotEnabled()
        assertTrue(texts.isEmpty())
        compose.onNodeWithTag("input_text").performScrollTo().performTextReplacement("hello")
        compose.onNodeWithTag("input_send").performScrollTo().performClick()
        assertEquals(listOf("hello"), texts)
        compose.onNodeWithTag("input_text").performScrollTo().assert(hasText(""))
    }

    @Test fun previewShortcutOpensInputPanelWithoutMountingAnotherPreview() {
        var mounts = 0
        compose.setContent {
            AppLocale(AppLanguage.English) {
                MonitorScreen(CaptureState(), false, {}, {}, {}, {}, {}, {}, {
                    androidx.compose.runtime.DisposableEffect(Unit) { mounts++; onDispose {} }
                    Box(it)
                })
            }
        }
        compose.onNodeWithTag("open_input").performClick()
        compose.onNodeWithTag("input_enable").assertIsDisplayed()
        assertEquals(1, mounts)
    }

    @Test fun touchpadSendsMovementAndClick() {
        var clicks = 0
        var movements = 0
        compose.setContent {
            AppLocale(AppLanguage.English) {
                MonitorTheme(AppPreferences()) {
                    InputPage(InputState(InputPhase.Connected, host = InputHost("pc", "Computer")),
                        InputActions(move = { x, y, _ -> if (x != 0 || y != 0) movements++ }, click = { clicks++ }))
                }
            }
        }
        compose.onNodeWithTag("input_mouse_mode").performScrollTo().performClick()
        compose.onNodeWithTag("input_touchpad").performScrollTo().performTouchInput { click() }
        assertEquals(1, clicks)
        compose.onNodeWithTag("input_touchpad").performTouchInput { swipeLeft() }
        assertTrue(movements > 0)
    }

    @Test @Config(sdk = [26]) fun android8ReportsUnsupportedWithoutAccessingHidApi() {
        assertEquals(InputPhase.Unsupported, inputAvailability(compose.activity))
        compose.setContent {
            AppLocale(AppLanguage.English) {
                MonitorTheme(AppPreferences()) { InputPage(InputState(InputPhase.Unsupported), InputActions()) }
            }
        }
        compose.onNodeWithTag("input_enable").assertDoesNotExist()
        compose.onNodeWithTag("input_status").assertTextEquals("Requires Android 9+ and Bluetooth HID Device support.")
    }
}
