package com.raiwesy.ai.data.model

/**
 * OpenAI-compatible request/response models used by the Raiwesy AI backend.
 * Only the fields the app needs are declared; Gson silently ignores the rest.
 */

/** Chat completion request (streaming and non-streaming share this shape). */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatRequestMessage>,
    val stream: Boolean,
    val temperature: Float,
    val max_tokens: Int
)

/** A single chat message on the wire. */
data class ChatRequestMessage(
    val role: String,
    val content: String
)

/** Full (non-streaming) chat completion response. */
data class ChatCompletionResponse(
    val id: String?,
    val model: String?,
    val choices: List<CompletionChoice>?
)

data class CompletionChoice(
    val index: Int?,
    val message: ChatRequestMessage?,
    val finish_reason: String?
)

/** One Server-Sent-Events chunk of a streaming response. */
data class ChatChunk(
    val id: String?,
    val model: String?,
    val choices: List<ChunkChoice>?
)

data class ChunkChoice(
    val index: Int?,
    val delta: ChunkDelta?,
    val finish_reason: String?
)

/** Incremental content fragment inside a streaming chunk. */
data class ChunkDelta(
    val role: String?,
    val content: String?
)
