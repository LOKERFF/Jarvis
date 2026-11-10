package com.jarvis.core.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The real terminal behind Jarvis.
 *
 * It runs `/system/bin/sh` inside the app's own private folder, exactly like the
 * Phase 2 terminal will, so:
 *  - no root is needed and none is possible;
 *  - other apps' private data is unreachable;
 *  - everything can be shown, streamed and killed from inside Jarvis.
 *
 * Output is streamed chunk by chunk to the caller (the UI shows it live) and
 * capped so a runaway command cannot exhaust memory. Every process is
 * registered so the Emergency Stop can destroy it.
 */
class ShellRunner {

    data class Outcome(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean,
        val durationMs: Long,
    ) {
        val ok: Boolean get() = exitCode == 0 && !timedOut
    }

    private val running: MutableSet<Process> =
        Collections.newSetFromMap(ConcurrentHashMap<Process, Boolean>())

    /**
     * @param onOutput called from a background thread for every chunk; marshal
     *        to the main thread in the caller if needed.
     */
    suspend fun run(
        command: String,
        workDir: File,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onOutput: (String) -> Unit = {},
    ): Outcome = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val work = workDir.apply { if (!exists()) mkdirs() }

        val process = try {
            ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(work)
                .redirectErrorStream(true)
                .start()
        } catch (t: Throwable) {
            return@withContext Outcome(
                exitCode = -1,
                output = "Command start nahi ho payi: ${t.message ?: "unknown error"}",
                timedOut = false,
                durationMs = System.currentTimeMillis() - started,
            )
        }

        running.add(process)
        val output = StringBuilder()
        val readerThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    val buffer = CharArray(2048)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read <= 0) break
                        val chunk = String(buffer, 0, read)
                        synchronized(output) {
                            if (output.length < MAX_OUTPUT_CHARS) {
                                output.append(chunk.take(MAX_OUTPUT_CHARS - output.length))
                            }
                        }
                        runCatching { onOutput(chunk) }
                    }
                }
            } catch (t: Throwable) {
                // stream closed because the process was killed - expected
            }
        }
        readerThread.isDaemon = true
        readerThread.start()

        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            false
        }
        if (!finished) {
            runCatching { process.destroyForcibly() }
        }
        runCatching { readerThread.join(1200) }
        running.remove(process)

        val exit = try {
            if (finished) process.exitValue() else -1
        } catch (t: Throwable) {
            -1
        }

        Outcome(
            exitCode = exit,
            output = synchronized(output) { output.toString() },
            timedOut = !finished,
            durationMs = System.currentTimeMillis() - started,
        )
    }

    /** Emergency Stop: kill every shell Jarvis started. */
    fun cancelAll() {
        running.forEach { process ->
            runCatching { process.destroyForcibly() }
        }
        running.clear()
    }

    fun hasRunning(): Boolean = running.isNotEmpty()

    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000L
        const val MAX_OUTPUT_CHARS = 200_000
    }
}
