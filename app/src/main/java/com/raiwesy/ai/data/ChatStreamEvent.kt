package com.raiwesy.ai.data

/**
 * Events emitted by [com.raiwesy.ai.data.ChatRepository.streamChat].
 */
sealed interface ChatStreamEvent {

    /** A new content fragment (token / phrase) of the assistant reply. */
    data class Content(val text: String) : ChatStreamEvent

    /** The stream finished successfully. */
    data object Done : ChatStreamEvent
}
