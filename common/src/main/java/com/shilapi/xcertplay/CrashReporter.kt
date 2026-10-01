package com.shilapi.xcertplay

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions into the diagnostics log directory so the next
 * "Save diagnostic report" includes the crash stack trace.
 *
 * Chained: the previous default handler still runs afterwards, so the system's
 * normal crash teardown (dialog / process kill) is unchanged. All I/O is
 * defensive; a failure here can never mask the original crash.
 */
internal object CrashReporter {
    const val LOG_NAME = "crash.log"
    private const val MAX_BYTES = 256 * 1024L

    @Volatile
    private var installed = false

    fun install(appContext: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val logsDir = File(appContext.filesDir, "logs")
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(logsDir, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeCrash(logsDir: File, thread: Thread, error: Throwable) {
        logsDir.mkdirs()
        val file = File(logsDir, LOG_NAME)
        if (file.length() > MAX_BYTES) file.writeText("")
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        file.appendText("--- crash $stamp thread=${thread.name} ---\n")
        var current: Throwable? = error
        while (current != null) {
            appendLine(file, current.toString())
            for (element in current.stackTrace) appendLine(file, "    at $element")
            current = current.cause
            if (current != null) appendLine(file, "Caused by:")
        }
        file.appendText("\n")
    }

    private fun appendLine(file: File, line: String) {
        val safe = DiagnosticRedactor.redact(line) ?: return
        file.appendText(safe + "\n")
    }
}
