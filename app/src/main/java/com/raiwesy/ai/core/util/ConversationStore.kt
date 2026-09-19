package com.raiwesy.ai.core.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.raiwesy.ai.data.ChatMessage
import com.raiwesy.ai.data.Conversation
import com.raiwesy.ai.data.Role
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Multi-conversation history store (ChatGPT style): in-memory [StateFlow]
 * plus JSON file persistence ([filesDir]/conversations.json).
 *
 * - streaming deltas ([appendContent]) update memory only; disk I/O happens
 *   when a message is added, finalized, removed, cleared or a conversation
 *   changes - this keeps token streaming fast.
 * - on first launch the legacy single-history (raiwesy_message_store prefs)
 *   is migrated into a conversation so old chats are not lost.
 */
class ConversationStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val appContext = context.applicationContext
    private val gson = Gson()

    private val _conversations = MutableStateFlow(emptyList<Conversation>())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    init {
        load()
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    fun active(): Conversation? = _conversations.value.firstOrNull { it.id == _activeId.value }

    fun exists(id: String): Boolean = _conversations.value.any { it.id == id }

    // ------------------------------------------------------------------
    // Conversation operations
    // ------------------------------------------------------------------

    /** Creates a fresh (empty) conversation and makes it active. */
    fun newConversation(): Conversation {
        val now = System.currentTimeMillis()
        val fresh = Conversation(
            id = UUID.randomUUID().toString(),
            title = DEFAULT_TITLE,
            createdAt = now,
            updatedAt = now
        )
        _conversations.value = _conversations.value + fresh
        _activeId.value = fresh.id
        persist()
        return fresh
    }

    /** Makes the given conversation active (no-op when unknown/already active). */
    fun activate(id: String) {
        if (id == _activeId.value) return
        if (_conversations.value.none { it.id == id }) return
        _activeId.value = id
        persist()
    }

    /**
     * Deletes a conversation. When the deleted one was active, the most
     * recently used remaining conversation becomes active (or a fresh one
     * is created when the list becomes empty).
     */
    fun deleteConversation(id: String) {
        _conversations.value = _conversations.value.filterNot { it.id == id }
        if (_activeId.value == id) {
            val next = _conversations.value.maxByOrNull { it.updatedAt }?.id
            if (next != null) {
                _activeId.value = next
            } else {
                val now = System.currentTimeMillis()
                val fresh = Conversation(
                    id = UUID.randomUUID().toString(),
                    title = DEFAULT_TITLE,
                    createdAt = now,
                    updatedAt = now
                )
                _conversations.value = listOf(fresh)
                _activeId.value = fresh.id
            }
        }
        persist()
    }

    // ------------------------------------------------------------------
    // Message operations (always scoped to a conversation)
    // ------------------------------------------------------------------

    /**
     * Adds a message. The conversation title is auto-set from the first
     * user message; [updatedAt] keeps the list ordered.
     */
    fun addMessage(convId: String, message: ChatMessage) {
        _conversations.value = _conversations.value.map { conv ->
            if (conv.id != convId) conv
            else conv.copy(
                messages = conv.messages + message,
                updatedAt = message.timestamp,
                title = autoTitle(conv, message)
            )
        }
        persist()
    }

    /** Appends a streaming fragment (in-memory only, no disk I/O). */
    fun appendContent(convId: String, messageId: String, delta: String) {
        _conversations.value = _conversations.value.map { conv ->
            if (conv.id != convId) conv
            else conv.copy(
                messages = conv.messages.map { m ->
                    if (m.id == messageId) m.copy(content = m.content + delta) else m
                }
            )
        }
    }

    /** Marks a streaming message as finished and persists the history. */
    fun finalizeMessage(convId: String, messageId: String) {
        _conversations.value = _conversations.value.map { conv ->
            if (conv.id != convId) conv
            else conv.copy(
                messages = conv.messages.map { m ->
                    if (m.id == messageId) m.copy(streaming = false) else m
                },
                updatedAt = System.currentTimeMillis()
            )
        }
        persist()
    }

    fun removeMessage(convId: String, messageId: String) {
        _conversations.value = _conversations.value.map { conv ->
            if (conv.id != convId) conv
            else conv.copy(messages = conv.messages.filterNot { it.id == messageId })
        }
        persist()
    }

    /** Removes all messages of a conversation (keeps the conversation itself). */
    fun clearMessages(convId: String) {
        _conversations.value = _conversations.value.map { conv ->
            if (conv.id != convId) conv
            else conv.copy(
                messages = emptyList(),
                title = DEFAULT_TITLE,
                updatedAt = System.currentTimeMillis()
            )
        }
        persist()
    }

    fun contentOf(convId: String, messageId: String): String =
        _conversations.value
            .firstOrNull { it.id == convId }
            ?.messages
            ?.firstOrNull { it.id == messageId }
            ?.content
            .orEmpty()

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun autoTitle(conv: Conversation, message: ChatMessage): String {
        if (conv.messages.isNotEmpty()) return conv.title
        if (message.role != Role.USER) return conv.title
        val plain = message.content.replace('\n', ' ').trim()
        if (plain.isEmpty()) return conv.title
        return if (plain.length > TITLE_LIMIT) plain.take(TITLE_LIMIT) + "…" else plain
    }

    private fun load() {
        try {
            if (file.exists()) {
                val payload = gson.fromJson(file.readText(), StorePayload::class.java)
                val loaded = (payload?.conversations.orEmpty())
                    // Normalize stale state (app killed mid-stream):
                    //  - never restore the "streaming" flag,
                    //  - drop empty assistant placeholders.
                    .map { conv ->
                        conv.copy(
                            messages = conv.messages
                                .map { it.copy(streaming = false, content = it.content.orEmpty()) }
                                .filterNot { it.role == Role.ASSISTANT && it.content.isEmpty() }
                        )
                    }
                _conversations.value = loaded
                _activeId.value = payload?.activeId?.takeIf { id -> loaded.any { it.id == id } }
                    ?: loaded.maxByOrNull { it.updatedAt }?.id
                return
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Sohbetler yüklenemedi", t)
        }

        // No store file yet: migrate the legacy single-history, if any.
        val legacy = loadLegacy()
        if (legacy.isNotEmpty()) {
            val conv = Conversation(
                id = UUID.randomUUID().toString(),
                title = LEGACY_TITLE,
                createdAt = legacy.first().timestamp,
                updatedAt = legacy.last().timestamp,
                messages = legacy
            )
            _conversations.value = listOf(conv)
            _activeId.value = conv.id
            persist()
            return
        }

        // Brand new install: start with one empty conversation.
        newConversation()
    }

    private fun loadLegacy(): List<ChatMessage> {
        return try {
            val prefs = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            val json = prefs.getString(LEGACY_KEY, null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<ChatMessage>>() {}.type
            val loaded = gson.fromJson<List<ChatMessage>>(json, type).orEmpty()
            loaded
                .map { it.copy(streaming = false, content = it.content.orEmpty()) }
                .filterNot { it.role == Role.ASSISTANT && it.content.isEmpty() }
        } catch (t: Throwable) {
            Log.e(TAG, "Eski sohbet geçmişi okunamadı", t)
            emptyList()
        }
    }

    private fun persist() {
        try {
            val payload = StorePayload(
                activeId = _activeId.value,
                conversations = _conversations.value.sortedByDescending { it.updatedAt }
            )
            file.writeText(gson.toJson(payload))
        } catch (t: Throwable) {
            Log.e(TAG, "Sohbetler kaydedilemedi", t)
        }
    }

    private data class StorePayload(
        val activeId: String? = null,
        val conversations: List<Conversation> = emptyList()
    )

    private companion object {
        const val TAG = "ConversationStore"
        const val FILE_NAME = "conversations.json"
        const val DEFAULT_TITLE = "Yeni sohbet"
        const val LEGACY_TITLE = "Önceki sohbet"
        const val LEGACY_PREFS = "raiwesy_message_store"
        const val LEGACY_KEY = "messages_json"
        const val TITLE_LIMIT = 40
    }
}
