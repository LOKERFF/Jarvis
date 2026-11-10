package com.jarvis.domain.model

import com.jarvis.data.db.ChatMessageEntity

/** A file or video attached to a chat bubble. */
data class ChatArtifact(
    val name: String,
    val path: String,
    val mime: String,
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
    val isImage: Boolean get() = mime.startsWith("image/")
}

data class ChatMessage(
    val id: Long,
    val timestamp: Long,
    val role: Role,
    val text: String,
    val artifact: ChatArtifact? = null,
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
            artifact = entity.artifactPath?.let { path ->
                ChatArtifact(
                    name = entity.artifactName ?: "file",
                    path = path,
                    mime = entity.artifactMime ?: "application/octet-stream",
                )
            },
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
