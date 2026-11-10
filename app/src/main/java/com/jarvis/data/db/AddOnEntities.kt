package com.jarvis.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// SELF SETUP MODULE TABLES (database version 2)
// ---------------------------------------------------------------------------

/** One saved, permanent setup job (survives restarts and reboots). */
@Entity(tableName = "saved_setups")
data class SavedSetupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceName: String,
    val sourceKind: String,
    val workspacePath: String,
    /** running | success | failed | cancelled */
    val status: String,
    val summary: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Every planned step with its verified result. */
@Entity(tableName = "setup_steps")
data class SetupStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setupId: Long,
    val stepIndex: Int,
    val title: String,
    val kind: String,
    val command: String,
    val path: String,
    /** pending | running | done | failed | skipped | waiting_confirm */
    val status: String,
    val output: String,
    val exitCode: Int,
    val updatedAt: Long,
)

/**
 * A learned correction. Both texts are stored AES-GCM encrypted (Android
 * Keystore); `hint` keeps an encrypted-safe short label for the list UI so we
 * never have to show ciphertext.
 */
@Entity(tableName = "learning_notes")
data class LearningNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mistakeEnc: String,
    val correctEnc: String,
    val hint: String,
    val createdAt: Long,
    val updatedAt: Long,
    val active: Boolean,
)

/** Every command Jarvis ran, with time and result (module rule 2). */
@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val setupId: Long,
    val command: String,
    val outcome: String,
    val exitCode: Int,
)

/** Files and videos Jarvis produced for the chat. */
@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val mime: String,
    val size: Long,
    val createdAt: Long,
)

// ---------------------------------------------------------------------------
// DAOs
// ---------------------------------------------------------------------------

@Dao
interface SetupDao {
    @Insert
    suspend fun insert(setup: SavedSetupEntity): Long

    @Query("UPDATE saved_setups SET status = :status, summary = :summary, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, summary: String, updatedAt: Long)

    @Query("SELECT * FROM saved_setups ORDER BY id DESC")
    fun all(): Flow<List<SavedSetupEntity>>

    @Query("SELECT * FROM saved_setups WHERE id = :id")
    suspend fun byId(id: Long): SavedSetupEntity?

    @Query("DELETE FROM saved_setups WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SetupStepDao {
    @Insert
    suspend fun insertAll(steps: List<SetupStepEntity>)

    @Query("UPDATE setup_steps SET status = :status, output = :output, exitCode = :exitCode, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateResult(id: Long, status: String, output: String, exitCode: Int, updatedAt: Long)

    @Query("SELECT * FROM setup_steps WHERE setupId = :setupId ORDER BY stepIndex ASC")
    fun forSetup(setupId: Long): Flow<List<SetupStepEntity>>

    @Query("SELECT * FROM setup_steps WHERE setupId = :setupId ORDER BY stepIndex ASC")
    suspend fun forSetupOnce(setupId: Long): List<SetupStepEntity>

    @Query("DELETE FROM setup_steps WHERE setupId = :setupId")
    suspend fun deleteForSetup(setupId: Long)
}

@Dao
interface LearningNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: LearningNoteEntity): Long

    @Update
    suspend fun update(note: LearningNoteEntity)

    @Query("DELETE FROM learning_notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM learning_notes ORDER BY id DESC")
    fun all(): Flow<List<LearningNoteEntity>>

    @Query("SELECT * FROM learning_notes ORDER BY id DESC")
    suspend fun allOnce(): List<LearningNoteEntity>

    @Query("SELECT * FROM learning_notes WHERE active = 1 ORDER BY id DESC")
    suspend fun activeOnce(): List<LearningNoteEntity>

    @Query("SELECT COUNT(*) FROM learning_notes")
    suspend fun count(): Int
}

@Dao
interface CommandLogDao {
    @Insert
    suspend fun insert(log: CommandLogEntity): Long

    @Query("SELECT * FROM command_logs ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<CommandLogEntity>>

    @Query("DELETE FROM command_logs")
    suspend fun clearAll()
}

@Dao
interface ArtifactDao {
    @Insert
    suspend fun insert(artifact: ArtifactEntity): Long

    @Query("SELECT * FROM artifacts WHERE id = :id")
    suspend fun byId(id: Long): ArtifactEntity?

    @Query("SELECT * FROM artifacts ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ArtifactEntity>>
}
