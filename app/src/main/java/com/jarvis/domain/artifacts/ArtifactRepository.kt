package com.jarvis.domain.artifacts

import android.content.Context
import com.jarvis.core.AuditLogger
import com.jarvis.core.media.LocalVideoMaker
import com.jarvis.data.db.ArtifactDao
import com.jarvis.data.db.ArtifactEntity
import com.jarvis.domain.setup.FileIntake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns "bhejo txt.txt" / "index.html banao" into a real file inside the app's
 * private storage, registers it in Room and returns everything the chat bubble
 * needs (open / share / play).
 */
class ArtifactRepository(
    private val context: Context,
    private val dao: ArtifactDao,
    private val intake: FileIntake,
    private val videoMaker: LocalVideoMaker,
    private val audit: AuditLogger,
) {

    data class Created(
        val artifact: ArtifactEntity,
        val file: File,
    )

    private fun artifactsDir(): File = File(context.filesDir, "artifacts").apply { mkdirs() }

    suspend fun createTextFile(rawName: String, content: String): Result<Created> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = sanitize(rawName)
                val file = uniqueFile(name)
                file.writeText(content, Charsets.UTF_8)
                val mime = intake.mimeFor(file.name)
                val now = System.currentTimeMillis()
                val id = dao.insert(
                    ArtifactEntity(
                        name = file.name,
                        path = file.absolutePath,
                        mime = mime,
                        size = file.length(),
                        createdAt = now,
                    )
                )
                audit.info("FILE", "created ${file.name} (${file.length()} bytes)")
                Created(ArtifactEntity(id, file.name, file.absolutePath, mime, file.length(), now), file)
            }
        }

    suspend fun createVideo(
        title: String,
        lines: List<String>,
        seconds: Int,
        onProgress: (Int) -> Unit = {},
    ): Result<Created> = withContext(Dispatchers.IO) {
        val slug = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "jarvis_video" }
            .take(32)
        val file = uniqueFile("${slug}.mp4", avoidCollision = true)

        val made = videoMaker.makeVideo(
            title = title,
            lines = lines,
            seconds = seconds,
            output = file,
            onProgress = onProgress,
        )

        made.fold(
            onSuccess = {
                val now = System.currentTimeMillis()
                val id = dao.insert(
                    ArtifactEntity(
                        name = file.name,
                        path = file.absolutePath,
                        mime = "video/mp4",
                        size = file.length(),
                        createdAt = now,
                    )
                )
                audit.info("VIDEO", "created ${file.name} (${file.length()} bytes)")
                Result.success(
                    Created(
                        ArtifactEntity(id, file.name, file.absolutePath, "video/mp4", file.length(), now),
                        file,
                    )
                )
            },
            onFailure = { error ->
                Result.failure(
                    IllegalStateException(
                        "Video banane me dikkat aayi: ${error.message ?: "encoder unavailable"}. " +
                            "Is phone ka video encoder kaam nahi kar raha, isliye maine jhoot nahi bola."
                    )
                )
            },
        )
    }

    suspend fun byId(id: Long): ArtifactEntity? = withContext(Dispatchers.IO) { dao.byId(id) }

    fun fileOf(path: String): File = File(path)

    // ---------------------------------------------------------------- helpers

    private fun sanitize(rawName: String): String {
        val trimmed = rawName.trim().replace('\\', '/').substringAfterLast('/')
        val cleaned = trimmed.replace(Regex("[^A-Za-z0-9._\\- ]"), "_")
        val safe = cleaned.ifBlank { "jarvis_file.txt" }.take(60)
        return if (safe.substringAfterLast('.', "").isEmpty()) "$safe.txt" else safe
    }

    private fun uniqueFile(name: String, avoidCollision: Boolean = true): File {
        val dir = artifactsDir()
        var file = File(dir, name)
        if (!avoidCollision) return file
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 2
        while (file.exists()) {
            val candidate = if (ext.isEmpty()) "${base}_$index" else "${base}_$index.$ext"
            file = File(dir, candidate)
            index++
        }
        return file
    }
}
