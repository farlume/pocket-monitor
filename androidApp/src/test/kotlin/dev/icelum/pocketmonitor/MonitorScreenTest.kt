package dev.icelum.pocketmonitor

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.CompletableDeferred

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonitorScreenTest {
    @Before fun initializeResources() = initializeComposeResources()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun idleScreenExplainsWiringAndOffersPermission() {
        var requested = false
        compose.setContent {
            MonitorScreen(CaptureState(), false, {}, { requested = true }, {}, {}, {}, {}, { Box(it) })
        }
        compose.onNodeWithText("等待画面接入").assertIsDisplayed()
        compose.onNodeWithTag("connect").performClick()
        assertTrue(requested)
        compose.onNodeWithTag("help").performClick()
        compose.onNodeWithText("把手机变成显示屏").assertIsDisplayed()
        compose.onNodeWithText("知道了").performClick()
        compose.waitForIdle()
        val output = File("build/reports/screenshots/phone-idle.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun fullScreenCanBeExitedWithoutVideo() {
        compose.setContent {
            var fullscreen by remember { mutableStateOf(false) }
            MonitorScreen(CaptureState(), fullscreen, { fullscreen = it }, {}, {}, {}, {}, {}, { Box(it) })
        }
        compose.onNodeWithContentDescription("全屏").performClick()
        compose.onNodeWithTag("exit_fullscreen").assertIsDisplayed().performClick()
        compose.onNodeWithText("随身屏").assertIsDisplayed()
    }

    @Test fun waitingForFramesOffersStopInsteadOfStartingAnotherConnection() {
        var connects = 0
        var stops = 0
        compose.setContent {
            MonitorScreen(CaptureState(phase = CapturePhase.WaitingForFrames, cameraPermission = true),
                false, {}, { connects++ }, {}, { stops++ }, {}, {}, { Box(it) })
        }
        compose.onNodeWithText("等待视频帧").assertIsDisplayed()
        compose.onNodeWithTag("connect").assertDoesNotExist()
        compose.onNodeWithText("停止预览").performScrollTo().performClick()
        assertEquals(1, stops)
        assertEquals(0, connects)
    }

    @Test fun settingsOnlyOffersAdvertisedModesAndReturnsSelection() {
        val first = VideoMode(1280, 720, 30, VideoEncoding.Mjpeg)
        val second = VideoMode(640, 480, 15, VideoEncoding.Yuy2)
        var chosen: VideoMode? = null
        compose.setContent {
            MonitorScreen(CaptureState(cameraPermission = true, modes = listOf(first, second), activeMode = first),
                false, {}, {}, {}, {}, { chosen = it }, {}, { Box(it) })
        }
        compose.onNodeWithContentDescription("视频设置").performClick()
        compose.onNodeWithText(second.label).performScrollTo().performClick()
        assertEquals(second, chosen)
        compose.onNodeWithText("1920 × 1080 · 60 fps · MJPEG").assertDoesNotExist()
    }

    @Test fun devicePageExposesDiagnosticsAfterCaptureFailure() {
        compose.setContent {
            MonitorScreen(CaptureState(phase = CapturePhase.Error), false, {}, {}, {}, {}, {}, {}, { Box(it) },
                onDiagnostics = { "Switch close complete; waiting for frames" })
        }
        compose.onNodeWithTag("nav_devices").performClick()
        compose.onNodeWithText("连接诊断").performClick()
        compose.onNodeWithText("Switch close complete; waiting for frames").assertIsDisplayed()
        compose.onNodeWithText("复制诊断").performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals("Switch close complete; waiting for frames", clipboard.primaryClip?.getItemAt(0)?.text.toString())
        }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("Switch close complete; waiting for frames").assertDoesNotExist()
    }

    @Test fun pendingDiagnosticsCanBeDismissedWithoutPublishingStaleReport() {
        val first = CompletableDeferred<String>()
        var requests = 0
        compose.setContent {
            MonitorScreen(CaptureState(phase = CapturePhase.Error), false, {}, {}, {}, {}, {}, {}, { Box(it) },
                onDiagnostics = { if (++requests == 1) first.await() else "New connection report" })
        }
        compose.onNodeWithTag("nav_devices").performClick()
        compose.onNodeWithText("连接诊断").performClick()
        compose.onNodeWithText("正在收集连接诊断…").assertIsDisplayed()
        compose.onNodeWithText("复制诊断").assertIsNotEnabled()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("连接诊断").performClick()
        compose.onNodeWithText("New connection report").assertIsDisplayed()
        compose.runOnIdle { first.complete("Stale connection report") }
        compose.onNodeWithText("Stale connection report").assertDoesNotExist()
        compose.onNodeWithText("New connection report").assertIsDisplayed()
    }
}
