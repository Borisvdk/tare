package app.tare.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import app.tare.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Keeps the last crash on the phone so the owner can copy it into a bug report. There's no
 * crash-reporting service: Java crashes are written by the default uncaught-exception
 * handler, native crashes and ANRs are picked up from [ApplicationExitInfo] on the next start.
 */
object CrashReports {
    /** Whether the previous run of the app ended in a crash or ANR (set by [install]). */
    var lastExitWasCrash = false
        private set

    private const val FILE = "last_crash.txt"
    private const val PREFS = "crash_reports"
    private const val SEEN_AT = "seen_at"
    private const val EXIT_CHECKED_AT = "exit_checked_at"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                write(app, "Crash on thread ${thread.name}", trace)
            }
            previous?.uncaughtException(thread, error)
        }
        runCatching { collectExitInfo(app) }
    }

    /** A failure that was caught but is worth a report (the camera not starting, say). */
    fun record(context: Context, title: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        write(context.applicationContext, title, trace)
        // It's shown where it happened; don't offer it again as a crash on the next start.
        markSeen(context)
    }

    /** The last report, if it hasn't been shown yet. */
    fun unseen(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        val seenAt = prefs(context).getLong(SEEN_AT, 0)
        return if (file.lastModified() > seenAt) file.readText() else null
    }

    fun last(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun markSeen(context: Context) {
        prefs(context).edit().putLong(SEEN_AT, System.currentTimeMillis()).apply()
    }

    // Native crashes (camera drivers, ML Kit) and ANRs never reach the Java handler, and
    // crashes from before this handler existed (an older version) only show up here.
    private fun collectExitInfo(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val checkedAt = prefs(context).getLong(EXIT_CHECKED_AT, 0)
        val reasons = setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR)
        val history = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        lastExitWasCrash = history.maxByOrNull { it.timestamp }?.reason in reasons
        val exits = history.filter { it.timestamp > checkedAt && it.reason in reasons }
        prefs(context).edit().putLong(EXIT_CHECKED_AT, System.currentTimeMillis()).apply()
        val exit = exits.maxByOrNull { it.timestamp } ?: return
        // A Java crash the handler already wrote down, with its full stack trace.
        val own = File(context.filesDir, FILE)
        if (exit.reason == ApplicationExitInfo.REASON_CRASH && own.exists() && own.lastModified() >= exit.timestamp - 10_000) return
        val kind = when (exit.reason) {
            ApplicationExitInfo.REASON_ANR -> "App not responding"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
            else -> "Crash"
        }
        val trace = runCatching { exit.traceInputStream?.use { readableText(it.readBytes()) } }.getOrNull().orEmpty()
        write(context, "$kind: ${exit.description.orEmpty()}", trace, at = exit.timestamp)
    }

    // ANR traces are text; native tombstones are protobuf, whose strings (libraries, symbols,
    // abort messages) are what matters, so keep the printable runs.
    private fun readableText(bytes: ByteArray): String {
        val text = String(bytes, Charsets.ISO_8859_1)
        if (text.startsWith("\n") || text.startsWith("-") || text.contains("\"main\"")) return text.lines().take(150).joinToString("\n")
        return Regex("[\\x20-\\x7E]{6,}").findAll(text).map { it.value }.distinct().take(150).joinToString("\n")
    }

    private fun write(context: Context, title: String, trace: String, at: Long = System.currentTimeMillis()) {
        val header = buildString {
            appendLine(title)
            appendLine("Tare ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${Instant.ofEpochMilli(at)}")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
            appendLine()
        }
        File(context.filesDir, FILE).writeText(header + trace.take(20_000))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
