package dev.icelum.pocketmonitor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()
    @Before fun initializeResources() = initializeComposeResources()
    @After fun restoreLocale() = Locale.setDefault(originalLocale)

    @Test fun tabsAndAppearanceChangesKeepPreviewMountedAndSaveSettings() {
        var mounts = 0
        var disposals = 0
        var viewCreations = 0
        val store = AppPreferencesStore(compose.activity)
        compose.setContent {
            var preferences by remember { mutableStateOf(AppPreferences(language = AppLanguage.English)) }
            AppLocale(preferences.language) {
                MonitorScreen(CaptureState(), false, {}, {}, {}, {}, {}, {}, {
                    DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                    AndroidView(factory = { context -> viewCreations++; View(context) }, modifier = it)
                }, preferences, { store.write(it); preferences = it })
            }
        }
        compose.onNodeWithTag("nav_devices").performClick()
        compose.onNodeWithText("No UVC video device found. Check OTG, cables and power.").assertIsDisplayed()
        compose.onNodeWithTag("connect").assertDoesNotExist()
        compose.onNodeWithTag("nav_input").performClick()
        compose.onNodeWithTag("input_enable").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("theme_dark").performClick()
        compose.onNodeWithTag("accent_ocean").performClick()
        compose.onNodeWithTag("show_frame_stats").performScrollTo().performClick()
        compose.onNodeWithTag("keep_screen_on").performScrollTo().performClick()
        compose.onNodeWithTag("nav_preview").performClick()
        compose.onNodeWithText("Waiting for video").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, mounts)
            assertEquals(0, disposals)
            assertEquals(1, viewCreations)
            assertEquals(AppPreferences(ThemeMode.Dark, AccentColor.Ocean, AppLanguage.English, false, false), store.read())
        }
    }

    @Test fun languageChangesImmediatelyAndCanReturnToSystemLanguage() {
        var mounts = 0
        var viewCreations = 0
        compose.setContent {
            var preferences by remember { mutableStateOf(AppPreferences(language = AppLanguage.English)) }
            AppLocale(preferences.language) {
                MonitorScreen(CaptureState(), false, {}, {}, {}, {}, {}, {}, {
                    DisposableEffect(Unit) { mounts++; onDispose {} }
                    AndroidView(factory = { context -> viewCreations++; View(context) }, modifier = it)
                }, preferences, { preferences = it })
            }
        }
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("language_chinese").performScrollTo().performClick()
        compose.onNodeWithText("跟随系统语言").assertIsDisplayed()
        compose.onNodeWithTag("nav_preview").performClick()
        compose.onNodeWithText("等待画面接入").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("language_system").performScrollTo().performClick()
        compose.onNodeWithText("System language").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, mounts); assertEquals(1, viewCreations) }
    }

    @Test fun ambiguousCardsOpenDeviceTabInsteadOfConnectingArbitrarily() {
        val first = CaptureDevice("usb-a", "UVC", 1, 2)
        val second = first.copy(id = "usb-b")
        var selected: String? = null
        compose.setContent {
            AppLocale(AppLanguage.English) {
                MonitorScreen(CaptureState(cameraPermission = true, devices = listOf(first, second)),
                    false, {}, { selected = it }, {}, {}, {}, {}, { Box(it) })
            }
        }
        compose.onNodeWithTag("connect").performClick()
        assertNull(selected)
        compose.onNodeWithText("Device 2 · usb-b").performClick()
        assertEquals("usb-b", selected)
        compose.onNodeWithTag("nav_preview").assertIsSelected()
    }

    @Test fun renderEnglishAndChinesePages() {
        var preferences by mutableStateOf(AppPreferences(ThemeMode.Light, AccentColor.Ocean, AppLanguage.English))
        compose.setContent {
            AppLocale(preferences.language) {
                MonitorScreen(CaptureState(), false, {}, {}, {}, {}, {}, {}, { Box(it) },
                    preferences, { preferences = it }, appVersion = "0.1.5", inputState = InputState(InputPhase.Ready))
            }
        }
        for (language in listOf(AppLanguage.English, AppLanguage.Chinese)) {
            compose.runOnIdle { preferences = preferences.copy(language = language) }
            val suffix = if (language == AppLanguage.English) "en" else "zh"
            compose.onNodeWithTag("nav_preview").performClick()
            screenshot("preview-$suffix")
            compose.onNodeWithTag("nav_devices").performClick()
            screenshot("devices-$suffix")
            compose.onNodeWithTag("nav_input").performClick()
            screenshot("input-$suffix")
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithTag("theme_dark").performClick()
            screenshot("settings-$suffix")
            compose.onNodeWithTag("theme_light").performClick()
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/reports/screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
