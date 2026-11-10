package com.jarvis.domain.learning

import com.jarvis.core.AuditLogger
import com.jarvis.core.security.CryptoBox
import com.jarvis.data.db.LearningNoteDao
import com.jarvis.data.db.LearningNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Module item 3: learning from mistakes.
 *
 * Every note is stored AES-GCM encrypted (Keystore key), survives restarts and
 * reboots, and is injected into *every* future prompt before Jarvis acts.
 */
class LearningRepository(
    private val dao: LearningNoteDao,
    private val crypto: CryptoBox = CryptoBox("jarvis_learning_key_v1"),
    private val audit: AuditLogger,
) {

    data class Note(
        val id: Long,
        val mistake: String,
        val correct: String,
        val createdAt: Long,
        val updatedAt: Long,
        val active: Boolean,
    )

    /** Decrypted, newest first. Rows that cannot be decrypted are skipped. */
    val notes: Flow<List<Note>> = dao.all().map { rows -> rows.mapNotNull { decrypt(it) } }

    suspend fun remember(mistake: String, correct: String): Result<Long> {
        val safeMistake = mistake.trim().take(600)
        val safeCorrect = correct.trim().take(600)
        if (safeCorrect.isEmpty()) {
            return Result.failure(IllegalArgumentException("Correct behaviour khali hai."))
        }
        val mistakeEnc = crypto.encrypt(safeMistake)
            ?: return Result.failure(IllegalStateException("Secure storage ne note encrypt nahi kiya."))
        val correctEnc = crypto.encrypt(safeCorrect)
            ?: return Result.failure(IllegalStateException("Secure storage ne note encrypt nahi kiya."))

        val now = System.currentTimeMillis()
        val id = dao.insert(
            LearningNoteEntity(
                mistakeEnc = mistakeEnc,
                correctEnc = correctEnc,
                hint = "",
                createdAt = now,
                updatedAt = now,
                active = true,
            )
        )
        audit.info("LEARN", "note #$id saved (encrypted)")
        return Result.success(id)
    }

    suspend fun update(id: Long, mistake: String, correct: String): Boolean {
        val existing = dao.allOnce().firstOrNull { it.id == id } ?: return false
        val mistakeEnc = crypto.encrypt(mistake.trim().take(600)) ?: return false
        val correctEnc = crypto.encrypt(correct.trim().take(600)) ?: return false
        dao.update(
            existing.copy(
                mistakeEnc = mistakeEnc,
                correctEnc = correctEnc,
                updatedAt = System.currentTimeMillis(),
            )
        )
        audit.info("LEARN", "note #$id updated")
        return true
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        audit.info("LEARN", "note #$id deleted")
    }

    /** The lines that go into every system prompt. */
    suspend fun activeRules(limit: Int = 25): List<String> {
        return dao.activeOnce()
            .mapNotNull { decrypt(it) }
            .take(limit)
            .map { note ->
                "Galat: ${note.mistake.take(220)} || Sahi: ${note.correct.take(220)}"
            }
    }

    suspend fun count(): Int = dao.count()

    private fun decrypt(entity: LearningNoteEntity): Note? {
        val mistake = crypto.decrypt(entity.mistakeEnc) ?: return null
        val correct = crypto.decrypt(entity.correctEnc) ?: return null
        return Note(
            id = entity.id,
            mistake = mistake,
            correct = correct,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            active = entity.active,
        )
    }
}
