package com.raiwesy.ai.core.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raiwesy.ai.data.ChatMessage
import com.raiwesy.ai.data.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Chat history store: in-memory [StateFlow] + SharedPreferences persistence.
 *
 * Performance note: streaming deltas ([appendContent]) update memory only and
 * do NOT touch the disk; persistence happens when a message is added,
 * finalized, removed or cleared. This keeps token streaming fast.
 */
class MessageStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _messages = MutableStateFlow(load())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun add(message: ChatMessage) {
        _messages.value = _messages.value + message
        persist()
    }

    fun remove(id: String) {
        _messages.value = _messages.value.filterNot { it.id == id }
        persist()
    }

    fun clear() {
        _messages.value = emptyList()
        persist()
    }

    /** Appends a streaming fragment (in-memory only, no disk I/O). */
    fun appendContent(id: String, delta: String) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(content = it.content + delta) else it
        }
    }

    /** Marks a streaming message as finished and persists the history. */
    fun finalizeMessage(id: String) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(streaming = false) else it
        }
        persist()
    }

    fun contentOf(id: String): String =
        _messages.value.firstOrNull { it.id == id }?.content.orEmpty()

    private fun load(): List<ChatMessage> {
        return try {
            val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            val loaded = gson.fromJson<List<ChatMessage>>(json, type)
                ?: return emptyList()
            // Normalize stale state (e.g. the app was killed mid-stream):
            //  - never restore the "streaming" flag,
            //  - drop empty assistant placeholders,
            //  - defend against null content (Gson + Kotlin null-safety).
            loaded.map { it.copy(streaming = false, content = it.content.orEmpty()) }
                .filter { !(it.role == Role.ASSISTANT && it.content.isEmpty()) }
        } catch (t: Throwable) {
            Log.e(TAG, "Mesaj geçmişi yüklenemedi", t)
            emptyList()
        }
    }

    private fun persist() {
        try {
            prefs.edit()
                .putString(KEY_MESSAGES, gson.toJson(_messages.value))
                .apply()
        } catch (t: Throwable) {
            Log.e(TAG, "Mesaj geçmişi kaydedilemedi", t)
        }
    }

    private companion object {
        const val TAG = "MessageStore"
        const val PREFERENCES_FILE = "raiwesy_message_store"
        const val KEY_MESSAGES = "messages_json"
    }
}
