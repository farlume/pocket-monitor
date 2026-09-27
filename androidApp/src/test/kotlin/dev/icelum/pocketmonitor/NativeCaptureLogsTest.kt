package dev.icelum.pocketmonitor

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class NativeCaptureLogsTest {
    private fun line(pid: Int = 123, tag: String = "libUVCCamera", message: String = "failed start_streaming (-6)") =
        "09-27 17:00:00.123 $pid 124 E $tag: $message\n"

    @Test fun onlyRequestedProcessAndUvcTagsAreIncluded() {
        val process = FakeProcess(line() + line(pid = 999, message = "foreign app") +
            line(tag = "BluetoothInput", message = "private input") + "logcat: error details\n")
        var command = emptyList<String>()
        val report = NativeCaptureLogs { command = it; process }.read(123)
        assertTrue(report.contains("failed start_streaming (-6)"))
        assertFalse(report.contains("foreign app"))
        assertFalse(report.contains("private input"))
        assertFalse(report.contains("error details"))
        assertTrue(command.contains("--pid=123"))
        assertTrue(command.contains("*:S"))
        assertTrue(process.destroyed)
    }

    @Test fun outputIsBoundedEvenWhenNativeLibraryFloodsLogs() {
        val process = FakeProcess(line(message = "x".repeat(2000)).repeat(100) + line(message = "latest startup failure"))
        val report = NativeCaptureLogs { process }.read(123)
        assertTrue(report.length <= 24_100)
        assertTrue(report.contains("latest startup failure"))
        assertTrue(process.destroyed)
    }

    @Test fun emptyLogIsNotReportedAsSuccessfulVideoStartup() {
        val report = NativeCaptureLogs { FakeProcess("") }.read(123)
        assertTrue(report.contains("does not prove native startup succeeded"))
    }

    @Test fun unavailableLogcatReportsFailureWithoutCrashing() {
        val report = NativeCaptureLogs { throw SecurityException("sensitive detail") }.read(123)
        assertTrue(report.contains("unavailable: SecurityException"))
        assertFalse(report.contains("sensitive detail"))
    }

    @Test fun hungProcessIsKilledAndTimeoutIsReported() {
        val process = FakeProcess(line(), finished = false)
        val report = NativeCaptureLogs { process }.read(123)
        assertTrue(report.contains("timed out"))
        assertTrue(process.destroyed)
    }

    @Test fun nonzeroExitIsVisible() {
        val report = NativeCaptureLogs { FakeProcess("", code = 1) }.read(123)
        assertTrue(report.contains("unavailable (exit=1)"))
    }

    private class FakeProcess(text: String, val finished: Boolean = true, val code: Int = 0) : Process() {
        var destroyed = false
        private val input = ByteArrayInputStream(text.toByteArray())
        override fun getInputStream() = input
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor() = code
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            assertTrue(unit.toMillis(timeout) <= 2000)
            return finished
        }
        override fun exitValue() = code
        override fun destroy() { destroyed = true }
        override fun destroyForcibly(): Process { destroy(); return this }
    }
}
