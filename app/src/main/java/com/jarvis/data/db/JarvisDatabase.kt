package com.jarvis.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Audit log (Global Safety Rule 7). Every message stored here has already been
// redacted by com.jarvis.core.security.Redactor.
// ---------------------------------------------------------------------------
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val category: String,
    val message: String,
)

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE category = :category ORDER BY id DESC LIMIT :limit")
    fun byCategory(category: String, limit: Int): Flow<List<AuditLogEntity>>

    @Query("DELETE FROM audit_logs")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun count(): Int
}

// ---------------------------------------------------------------------------
// Chat history (Phase 1 keeps a local transcript only).
// ---------------------------------------------------------------------------
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val role: String,
    val text: String,
    // ---- added in database version 2 (self setup module) ----
    val artifactName: String? = null,
    val artifactPath: String? = null,
    val artifactMime: String? = null,
)

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    /** Newest [limit] rows, returned oldest-first for easy display. */
    @Query(
        "SELECT * FROM chat_messages WHERE id IN " +
            "(SELECT id FROM chat_messages ORDER BY id DESC LIMIT :limit) ORDER BY id ASC"
    )
    fun recent(limit: Int): Flow<List<ChatMessageEntity>>

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Database(
    entities = [
        AuditLogEntity::class,
        ChatMessageEntity::class,
        SavedSetupEntity::class,
        SetupStepEntity::class,
        LearningNoteEntity::class,
        CommandLogEntity::class,
        ArtifactEntity::class,
    ],
    version = 2,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun auditLogDao(): AuditLogDao

    abstract fun chatDao(): ChatDao

    abstract fun setupDao(): SetupDao

    abstract fun setupStepDao(): SetupStepDao

    abstract fun learningNoteDao(): LearningNoteDao

    abstract fun commandLogDao(): CommandLogDao

    abstract fun artifactDao(): ArtifactDao

    companion object {
        @Volatile
        private var instance: JarvisDatabase? = null

        /**
         * v1 -> v2: adds the self-setup / learning module.
         * Nothing from Phase 1 is dropped: chat rows are kept and only gain
         * three nullable artifact columns.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. chat messages gain an optional artifact
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `artifactName` TEXT")
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `artifactPath` TEXT")
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `artifactMime` TEXT")

                // 2. saved setups
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_setups` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`sourceName` TEXT NOT NULL, " +
                        "`sourceKind` TEXT NOT NULL, " +
                        "`workspacePath` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`summary` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )

                // 3. setup steps
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `setup_steps` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`setupId` INTEGER NOT NULL, " +
                        "`stepIndex` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`command` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`output` TEXT NOT NULL, " +
                        "`exitCode` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )

                // 4. encrypted learning notes
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `learning_notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`mistakeEnc` TEXT NOT NULL, " +
                        "`correctEnc` TEXT NOT NULL, " +
                        "`hint` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL)"
                )

                // 5. command log
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `command_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`setupId` INTEGER NOT NULL, " +
                        "`command` TEXT NOT NULL, " +
                        "`outcome` TEXT NOT NULL, " +
                        "`exitCode` INTEGER NOT NULL)"
                )

                // 6. artifacts (generated files / videos)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `artifacts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, " +
                        "`mime` TEXT NOT NULL, " +
                        "`size` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): JarvisDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
