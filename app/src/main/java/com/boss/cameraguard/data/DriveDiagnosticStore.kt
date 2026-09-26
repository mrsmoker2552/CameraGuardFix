package com.boss.cameraguard.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class DriveDiagnosticSnapshot(
    val sessionId: String,
    val lineCount: Long,
    val gpsFixes: Long,
    val engineEvents: Long,
    val targetEvents: Long,
    val warningEvents: Long,
    val pairEvents: Long,
    val serviceEvents: Long,
    val writeFailures: Long,
    val fileBytes: Long,
    val lastCategory: String,
    val lastTimestamp: String
)

object DriveDiagnosticStore {

    private const val FILE_NAME = "cameraguard-drive-diagnostic.log"
    private const val MAX_FILE_BYTES = 6L * 1024L * 1024L

    @Volatile
    private var appContext: Context? = null

    private val lock = Any()

    private var sessionId: String = "not-started"
    private var lineCount: Long = 0L
    private var gpsFixes: Long = 0L
    private var engineEvents: Long = 0L
    private var targetEvents: Long = 0L
    private var warningEvents: Long = 0L
    private var pairEvents: Long = 0L
    private var serviceEvents: Long = 0L
    private var writeFailures: Long = 0L
    private var lastCategory: String = "NONE"
    private var lastTimestamp: String = "--"

    fun initialize(context: Context) {
        synchronized(lock) {
            appContext = context.applicationContext
            rebuildCountersFromExistingFileLocked()
        }

        log(
            "SYSTEM",
            "diagnostics_initialized pid=${android.os.Process.myPid()}"
        )
    }

    /**
     * Starts a fresh diagnostic session. This is deliberately synchronous:
     * when NEW LOG returns, the session header is already durable on disk.
     */
    fun clear() {
        val context = appContext ?: return

        synchronized(lock) {
            sessionId = UUID.randomUUID().toString().take(8)
            lineCount = 0L
            gpsFixes = 0L
            engineEvents = 0L
            targetEvents = 0L
            warningEvents = 0L
            pairEvents = 0L
            serviceEvents = 0L
            writeFailures = 0L
            lastCategory = "NONE"
            lastTimestamp = "--"

            runCatching {
                File(context.filesDir, FILE_NAME).writeText("")
            }.onFailure {
                writeFailures++
            }
        }

        log(
            "SYSTEM",
            "new_drive_session session=$sessionId pid=${android.os.Process.myPid()}"
        )
    }

    /**
     * Writes one complete line with fsync. This diagnostic build favors data
     * integrity over tiny I/O savings so a process/service kill does not leave
     * us with a one-line or half-written drive log.
     */
    fun log(category: String, message: String) {
        val context = appContext ?: return

        val safeCategory = category
            .replace('|', '_')
            .replace('\n', '_')
            .replace('\r', '_')

        val sanitized = message
            .replace('\n', ' ')
            .replace('\r', ' ')

        synchronized(lock) {
            val stamp = timestamp()
            val line = "$stamp|$safeCategory|session=$sessionId $sanitized\n"

            runCatching {
                val file = File(context.filesDir, FILE_NAME)
                trimIfNeededLocked(file)

                FileOutputStream(file, true).use { output ->
                    output.write(line.toByteArray(Charsets.UTF_8))
                    output.flush()
                    output.fd.sync()
                }

                lineCount++
                updateCountersLocked(safeCategory)
                lastCategory = safeCategory
                lastTimestamp = stamp
            }.onFailure {
                writeFailures++
            }
        }
    }

    fun snapshot(): DriveDiagnosticSnapshot {
        val context = appContext

        synchronized(lock) {
            val bytes =
                if (context == null) {
                    0L
                } else {
                    runCatching {
                        File(context.filesDir, FILE_NAME).length()
                    }.getOrDefault(0L)
                }

            return DriveDiagnosticSnapshot(
                sessionId = sessionId,
                lineCount = lineCount,
                gpsFixes = gpsFixes,
                engineEvents = engineEvents,
                targetEvents = targetEvents,
                warningEvents = warningEvents,
                pairEvents = pairEvents,
                serviceEvents = serviceEvents,
                writeFailures = writeFailures,
                fileBytes = bytes,
                lastCategory = lastCategory,
                lastTimestamp = lastTimestamp
            )
        }
    }

    fun exportText(): String {
        val context = appContext
            ?: return "CameraGuard diagnostics not initialized."

        synchronized(lock) {
            val file = File(context.filesDir, FILE_NAME)
            val snapshot = snapshotLocked(file)

            val header = buildString {
                appendLine("# CameraGuard diagnostic export")
                appendLine("# session=${snapshot.sessionId}")
                appendLine("# lines=${snapshot.lineCount}")
                appendLine("# gpsFixes=${snapshot.gpsFixes}")
                appendLine("# engineEvents=${snapshot.engineEvents}")
                appendLine("# targetEvents=${snapshot.targetEvents}")
                appendLine("# warningEvents=${snapshot.warningEvents}")
                appendLine("# pairEvents=${snapshot.pairEvents}")
                appendLine("# serviceEvents=${snapshot.serviceEvents}")
                appendLine("# writeFailures=${snapshot.writeFailures}")
                appendLine("# fileBytes=${snapshot.fileBytes}")
                appendLine("# last=${snapshot.lastTimestamp}|${snapshot.lastCategory}")
                appendLine("# ---")
            }

            return if (file.exists()) {
                header + file.readText()
            } else {
                header + "No diagnostic data recorded.\n"
            }
        }
    }

    private fun updateCountersLocked(category: String) {
        when {
            category == "GPS" || category == "ACTIVITY_GPS" -> gpsFixes++
            category.startsWith("ENGINE") || category == "TARGET" -> engineEvents++
            category.contains("TARGET") -> targetEvents++
            category.contains("WARN") -> warningEvents++
            category.startsWith("PAIR") -> pairEvents++
            category.startsWith("SERVICE") -> serviceEvents++
        }
    }

    private fun trimIfNeededLocked(file: File) {
        if (!file.exists() || file.length() <= MAX_FILE_BYTES) {
            return
        }

        val keepBytes = (MAX_FILE_BYTES / 2L).toInt()
        val text = file.readText()
        val keep = text.takeLast(keepBytes)
        file.writeText("--- LOG TRIMMED ---\n$keep")
    }

    private fun rebuildCountersFromExistingFileLocked() {
        val context = appContext ?: return
        val file = File(context.filesDir, FILE_NAME)

        lineCount = 0L
        gpsFixes = 0L
        engineEvents = 0L
        targetEvents = 0L
        warningEvents = 0L
        pairEvents = 0L
        serviceEvents = 0L
        writeFailures = 0L
        lastCategory = "NONE"
        lastTimestamp = "--"

        if (!file.exists()) {
            sessionId = "boot-${UUID.randomUUID().toString().take(8)}"
            return
        }

        runCatching {
            file.useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('|', limit = 3)
                    if (parts.size >= 3) {
                        lineCount++
                        val category = parts[1]
                        updateCountersLocked(category)
                        lastTimestamp = parts[0]
                        lastCategory = category

                        val marker = "session="
                        val index = parts[2].indexOf(marker)
                        if (index >= 0) {
                            val tail = parts[2].substring(index + marker.length)
                            val parsed = tail.substringBefore(' ')
                            if (parsed.isNotBlank()) {
                                sessionId = parsed
                            }
                        }
                    }
                }
            }
        }.onFailure {
            writeFailures++
        }

        if (sessionId == "not-started") {
            sessionId = "boot-${UUID.randomUUID().toString().take(8)}"
        }
    }

    private fun snapshotLocked(file: File): DriveDiagnosticSnapshot {
        return DriveDiagnosticSnapshot(
            sessionId = sessionId,
            lineCount = lineCount,
            gpsFixes = gpsFixes,
            engineEvents = engineEvents,
            targetEvents = targetEvents,
            warningEvents = warningEvents,
            pairEvents = pairEvents,
            serviceEvents = serviceEvents,
            writeFailures = writeFailures,
            fileBytes = if (file.exists()) file.length() else 0L,
            lastCategory = lastCategory,
            lastTimestamp = lastTimestamp
        )
    }

    private fun timestamp(): String {
        val formatter =
            SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                Locale.US
            )

        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date())
    }
}
