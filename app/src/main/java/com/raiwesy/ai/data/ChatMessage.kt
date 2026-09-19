package com.raiwesy.ai.data

import com.google.gson.annotations.SerializedName

/**
 * Message role. [apiValue] is the wire value expected by the
 * OpenAI-compatible NVIDIA API ("user" / "assistant" / "system").
 */
enum class Role(val apiValue: String) {
    @SerializedName("user")
    USER("user"),

    @SerializedName("assistant")
    ASSISTANT("assistant"),

    @SerializedName("system")
    SYSTEM("system")
}

/**
 * A single chat message (UI domain model).
 *
 * @param id unique identifier (UUID)
 * @param role who sent the message
 * @param content the message text (grows token by token while streaming)
 * @param timestamp creation time in epoch millis
 * @param streaming true while the assistant reply is still being streamed
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val timestamp: Long,
    val streaming: Boolean = false
)
