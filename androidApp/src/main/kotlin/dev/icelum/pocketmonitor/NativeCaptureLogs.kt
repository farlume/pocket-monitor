package dev.icelum.pocketmonitor

import java.util.concurrent.TimeUnit

/** Read on an IO thread, only when the user opens connection diagnostics. */
internal class NativeCaptureLogs(
    private val startProcess: (List<String>) -> Process = { ProcessBuilder(it).redirectErrorStream(true).start() },
) {
    fun read(pid: Int): String {
        val tags = setOf("libUVCCamera", "UVCCamera", "libuvc", "libusb")
        val command = listOf("/system/bin/logcat", "-d", "-b", "main", "-v", "threadtime",
            "--pid=$pid", "-t", "160") + tags.map { "$it:V" } + "*:S"
        val output = StringBuilder()
        var process: Process? = null
        var reader: Thread? = null
        var readFailed = false
        return try {
            val running = startProcess(command)
            process = running
            reader = Thread({
                try {
                    running.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            // Defence in depth: do not trust logcat's PID/tag filtering alone.
                            val match = LOG_LINE.matchEntire(line)
                            if (match != null && match.groupValues[1].toIntOrNull() == pid &&
                                match.groupValues[2] in tags) synchronized(output) {
                                output.append(line.take(MAX_CHARS - 1)).append('\n')
                                if (output.length > MAX_CHARS) output.delete(0, output.length - MAX_CHARS)
                            }
                        }
                    }
                } catch (_: Exception) { readFailed = true }
            }, "UvcDiagnosticLogs").apply { isDaemon = true; start() }
            val exited = running.waitFor(2, TimeUnit.SECONDS)
            if (!exited) running.destroyForcibly()
            reader.join(250)
            val status = when {
                !exited -> "timed out"
                reader.isAlive -> "reader incomplete"
                readFailed -> "read failed"
                running.exitValue() != 0 -> "unavailable (exit=${running.exitValue()})"
                else -> "collected"
            }
            synchronized(output) {
                "Native UVC logs: $status; PID=$pid; at most $MAX_CHARS characters\n" +
                    output.toString().ifEmpty { "No matching native records; this does not prove native startup succeeded.\n" }
            }
        } catch (e: Exception) {
            "Native UVC logs unavailable: ${e.javaClass.simpleName}\n"
        } finally {
            process?.let {
                runCatching { it.destroyForcibly() }
                runCatching { it.inputStream.close() }
                runCatching { it.errorStream.close() }
                runCatching { it.outputStream.close() }
            }
            reader?.interrupt()
        }
    }

    private companion object {
        const val MAX_CHARS = 24_000
        val LOG_LINE = Regex("^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+(\\d+)\\s+\\d+\\s+[VDIWEF]\\s+([^ :]+)\\s*:.*$")
    }
}
