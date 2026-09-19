package com.raiwesy.ai.data

/**
 * One conversation thread (ChatGPT style): a title plus the messages in it.
 *
 * @param id unique identifier (UUID)
 * @param title shown in the conversation list (auto-set from the first message)
 * @param createdAt creation time in epoch millis
 * @param updatedAt last activity time in epoch millis (drives list ordering)
 * @param messages the messages of this conversation
 */
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage> = emptyList()
)
