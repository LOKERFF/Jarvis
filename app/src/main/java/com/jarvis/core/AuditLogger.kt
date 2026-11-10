package com.jarvis.core

import android.util.Log
import com.jarvis.core.security.Redactor
import com.jarvis.data.db.AuditLogDao
import com.jarvis.data.db.AuditLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Global Safety Rule 7: every action, command and AI call is recorded.
 *
 * Two sinks: logcat (for debugging) and the Room audit table (Settings > Logs).
 * The message is always pushed through [Redactor] first, so a secret can never
 * reach either sink.
 */
class AuditLogger(
    private val dao: AuditLogDao,
    private val scope: CoroutineScope,
) {

    fun log(category: String, message: String, level: Level = Level.INFO) {
        val safe = Redactor.redactText(message)
        Log.println(level.priority, "Jarvis/$category", safe)
        scope.launch {
            runCatching {
                dao.insert(
                    AuditLogEntity(
                        timestamp = System.currentTimeMillis(),
                        level = level.name,
                        category = category,
                        message = safe,
                    )
                )
            }
        }
    }

    fun info(category: String, message: String) = log(category, message, Level.INFO)

    fun warn(category: String, message: String) = log(category, message, Level.WARN)

    fun error(category: String, message: String) = log(category, message, Level.ERROR)

    enum class Level(val priority: Int) {
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR),
    }
}
