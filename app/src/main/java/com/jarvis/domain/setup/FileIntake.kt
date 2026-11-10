package com.jarvis.domain.setup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jarvis.core.security.Redactor
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Reads the file the user attached.
 *
 * Rules:
 *  - the file is copied into the app's private folder first (never worked on
 *    in place, so a failed setup cannot damage the original);
 *  - zip extraction is zip-slip safe (no absolute paths, no ".."), capped in
 *    entry count and total size;
 *  - text previews are redacted before they can reach an AI provider.
 */
class FileIntake(private val context: Context) {

    enum class Kind { ZIP, TEXT, IMAGE, PDF, BINARY }

    data class Attachment(
        val file: File,
        val displayName: String,
        val kind: Kind,
        val sizeBytes: Long,
        val mimeType: String,
    )

    fun intake(uri: Uri): Attachment? {
        return try {
            val name = resolveName(uri) ?: "upload_${System.currentTimeMillis()}.txt"
            val safeName = sanitizeName(name)
            val dir = File(context.filesDir, "uploads").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}_$safeName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            } ?: return null

            val kind = classify(safeName)
            Attachment(
                file = target,
                displayName = safeName,
                kind = kind,
                sizeBytes = target.length(),
                mimeType = mimeFor(safeName),
            )
        } catch (t: Throwable) {
            null
        }
    }

    /** Extracts a zip into [destination]; returns the number of files written. */
    fun extractZip(attachment: Attachment, destination: File): Int {
        destination.mkdirs()
        var written = 0
        var totalBytes = 0L
        ZipInputStream(attachment.file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (written > MAX_ENTRIES || totalBytes > MAX_TOTAL_BYTES) {
                    throw IllegalStateException("Zip bahut bada hai (limit: $MAX_ENTRIES files / 200 MB).")
                }
                val name = entry.name.replace('\\', '/')
                if (name.contains("..") || name.startsWith("/") || name.contains(":")) {
                    zip.closeEntry()
                    continue // zip-slip attempt: skip silently, it is logged by the caller
                }
                val target = File(destination, name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            totalBytes += read
                            if (totalBytes > MAX_TOTAL_BYTES) {
                                throw IllegalStateException("Zip extract 200 MB se zyada ho gaya, rok diya.")
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                    written++
                }
                zip.closeEntry()
            }
        }
        return written
    }

    /** Redacted text preview (null for binary files). */
    fun textPreview(file: File, limit: Int = MAX_PREVIEW_CHARS): String? {
        return try {
            if (file.length() > MAX_TEXT_FILE_BYTES) {
                file.inputStream().use { input ->
                    val bytes = ByteArray(limit * 2)
                    val read = input.read(bytes)
                    val text = String(bytes, 0, maxOf(read, 0), Charsets.UTF_8)
                    Redactor.redactText(text.take(limit))
                }
            } else {
                Redactor.redactText(file.readText().take(limit))
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** Compact folder listing used as the AI's view of a zip. */
    fun treeOf(root: File, maxEntries: Int = MAX_TREE_ENTRIES): String {
        val builder = StringBuilder()
        var count = 0
        fun walk(dir: File, prefix: String) {
            val children = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                ?: return
            for (child in children) {
                if (count >= maxEntries) return
                builder.append(prefix).append(if (child.isDirectory) "📁 " else "📄 ")
                    .append(child.name)
                if (!child.isDirectory) builder.append(" (").append(child.length()).append("B)")
                builder.append('\n')
                count++
                if (child.isDirectory) walk(child, "$prefix  ")
            }
        }
        if (root.isDirectory) walk(root, "")
        return builder.toString().ifBlank { "(empty)" }
    }

    fun mimeFor(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".txt") || lower.endsWith(".log") -> "text/plain"
            lower.endsWith(".md") -> "text/markdown"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".xml") -> "application/xml"
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".js") -> "application/javascript"
            lower.endsWith(".kt") || lower.endsWith(".kts") -> "text/x-kotlin"
            lower.endsWith(".java") -> "text/x-java"
            lower.endsWith(".py") -> "text/x-python"
            lower.endsWith(".sh") -> "text/x-shellscript"
            lower.endsWith(".yml") || lower.endsWith(".yaml") -> "text/yaml"
            lower.endsWith(".gradle") -> "text/plain"
            lower.endsWith(".properties") -> "text/plain"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mp3") -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    private fun classify(name: String): Kind {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".zip") -> Kind.ZIP
            lower.endsWith(".pdf") -> Kind.PDF
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".webp") -> Kind.IMAGE
            lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".apk") ||
                lower.endsWith(".jar") || lower.endsWith(".bin") -> Kind.BINARY
            TEXT_EXTENSIONS.any { lower.endsWith(it) } -> Kind.TEXT
            else -> Kind.TEXT // try to read it; the preview returns null if it is binary
        }
    }

    private fun resolveName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (t: Throwable) {
            null
        } ?: uri.lastPathSegment
    }

    private fun sanitizeName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").trim()
        return cleaned.ifBlank { "file_${System.currentTimeMillis()}.txt" }.take(80)
    }

    companion object {
        const val MAX_PREVIEW_CHARS = 12_000
        const val MAX_TEXT_FILE_BYTES = 2_000_000L
        const val MAX_ENTRIES = 3_000
        const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
        const val MAX_TREE_ENTRIES = 160

        private val TEXT_EXTENSIONS = listOf(
            ".txt", ".md", ".json", ".xml", ".html", ".htm", ".css", ".js", ".ts",
            ".kt", ".kts", ".java", ".py", ".sh", ".yml", ".yaml", ".gradle", ".properties",
            ".toml", ".env", ".gitignore", ".cfg", ".ini", ".log", ".sql", ".csv", ".svg",
        )
    }
}
