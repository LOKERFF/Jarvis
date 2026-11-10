package com.jarvis.domain.model

import com.jarvis.data.db.ChatMessageEntity

data class ChatMessage(
    val id: Long,
    val timestamp: Long,
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun fromEntity(entity: ChatMessageEntity): ChatMessage = ChatMessage(
            id = entity.id,
            timestamp = entity.timestamp,
            role = when (entity.role) {
                ROLE_USER -> Role.USER
                "system" -> Role.SYSTEM
                else -> Role.ASSISTANT
            },
            text = entity.text,
        )
    }
}

/** Orb visual state, driven by the engine. */
enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR,
}
