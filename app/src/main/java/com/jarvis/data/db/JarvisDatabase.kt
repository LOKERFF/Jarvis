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
    entities = [AuditLogEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun auditLogDao(): AuditLogDao

    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: JarvisDatabase? = null

        fun get(context: Context): JarvisDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
