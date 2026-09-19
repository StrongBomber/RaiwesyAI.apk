package com.raiwesy.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raiwesy.ai.BuildConfig
import com.raiwesy.ai.core.network.ApiException
import com.raiwesy.ai.core.network.MissingApiKeyException
import com.raiwesy.ai.core.util.ApiKeyManager
import com.raiwesy.ai.core.util.AppearanceMode
import com.raiwesy.ai.core.util.ConversationStore
import com.raiwesy.ai.data.ChatMessage
import com.raiwesy.ai.data.ChatStreamEvent
import com.raiwesy.ai.data.Conversation
import com.raiwesy.ai.data.Role
import com.raiwesy.ai.data.model.ChatRequestMessage
import com.raiwesy.ai.di.AppContainer
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state of the chat screen (MVVM "View state").
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val isStreaming: Boolean = false,
    val isOnline: Boolean = true,
    val apiKeyConfigured: Boolean = false,
    val error: String? = null,
    val snackbar: SnackbarEvent? = null,
    val messageToDelete: ChatMessage? = null,
    val clearChatRequested: Boolean = false,
    val themeMode: AppearanceMode = AppearanceMode.SYSTEM,
    // ---- multi-conversation ----
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val conversationToDelete: Conversation? = null
)

/** Transient snackbar event; [id] changes so the UI re-triggers on repeats. */
data class SnackbarEvent(val id: Long, val message: String)

/**
 * Chat ViewModel - the "V-M" bridge of the MVVM architecture.
 *
 * Responsibilities:
 *  - exposing the immutable [ChatUiState] to the UI,
 *  - orchestrating the repository (data layer) for send/stop/retry,
 *  - mapping every failure to a friendly Turkish error message,
 *  - conversation operations (new / open / delete / clear),
 *  - message operations (delete, copy trigger) and settings (key, theme).
 */
class ChatViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.chatRepository
    private val store: ConversationStore = container.conversationStore
    private val keyManager: ApiKeyManager = container.keyManager
    private val networkMonitor = container.networkMonitor

    private var streamJob: Job? = null
    private var lastUserText: String? = null
    private var snackbarId = 0L

    private val _state = MutableStateFlow(
        ChatUiState(
            isOnline = networkMonitor.isOnline.value,
            apiKeyConfigured = keyManager.effectiveKey() != null,
            themeMode = keyManager.appearanceMode(),
            conversations = store.conversations.value.sortedByDescending { it.updatedAt },
            activeConversationId = store.activeId.value
        )
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                store.conversations,
                store.activeId,
                networkMonitor.isOnline
            ) { convs, active, online ->
                Triple(convs, active, online)
            }.collect { (convs, active, online) ->
                val activeConv = convs.firstOrNull { it.id == active }
                _state.update {
                    it.copy(
                        conversations = convs.sortedByDescending { c -> c.updatedAt },
                        activeConversationId = active,
                        messages = activeConv?.messages.orEmpty(),
                        isOnline = online
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    /** Sends the user text and streams the assistant reply (active conversation). */
    fun send(rawText: String) {
        val text = rawText.trim()
        val current = _state.value
        if (text.isEmpty() || current.isSending) return

        if (!current.isOnline) {
            _state.update {
                it.copy(error = "İnternet bağlantısı yok. Bağlantınızı kontrol edip tekrar deneyin.")
            }
            return
        }

        if (keyManager.effectiveKey() == null) {
            _state.update {
                it.copy(
                    error = "API anahtarı tanımlı değil. " +
                        "Ayarlar bölümünden anahtarınızı ekleyin."
                )
            }
            return
        }

        // Guarantee there is an active conversation to write into.
        val convId = ensureActiveConversation()

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )
        store.addMessage(convId, userMessage)
        lastUserText = text

        val assistantId = UUID.randomUUID().toString()
        store.addMessage(
            convId,
            ChatMessage(
                id = assistantId,
                role = Role.ASSISTANT,
                content = "",
                timestamp = System.currentTimeMillis(),
                streaming = true
            )
        )

        val history = buildHistory(convId)
        _state.update { it.copy(isSending = true, isStreaming = true, error = null) }

        streamJob = viewModelScope.launch {
            try {
                repository.streamChat(history).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Content ->
                            store.appendContent(convId, assistantId, event.text)

                        is ChatStreamEvent.Done ->
                            store.finalizeMessage(convId, assistantId)
                    }
                }
                _state.update { it.copy(isSending = false, isStreaming = false) }
            } catch (c: CancellationException) {
                // User pressed "Durdur"
                if (store.contentOf(convId, assistantId).isNotBlank()) {
                    store.finalizeMessage(convId, assistantId)
                    showMessage("Yanıt durduruldu.")
                } else {
                    store.removeMessage(convId, assistantId)
                }
                _state.update { it.copy(isSending = false, isStreaming = false) }
                throw c
            } catch (t: Throwable) {
                // Crash prevention: every failure is converted to a friendly
                // error state; the app never dies because of a network error.
                store.removeMessage(convId, assistantId)
                _state.update {
                    it.copy(isSending = false, isStreaming = false, error = friendlyError(t))
                }
            }
        }
    }

    /** Stops an in-progress stream (keeps the partial reply). */
    fun stop() {
        streamJob?.cancel()
    }

    /** Re-sends the last user message (used by the error banner "Tekrar dene"). */
    fun retry() {
        val text = lastUserText ?: return
        if (_state.value.isSending) return
        send(text)
    }

    private fun ensureActiveConversation(): String {
        store.active()?.let { return it.id }
        return store.newConversation().id
    }

    private fun buildHistory(convId: String): List<ChatRequestMessage> {
        val system = ChatRequestMessage(Role.SYSTEM.apiValue, BuildConfig.SYSTEM_PROMPT)
        // Last N messages of the conversation only: keeps requests small and fast.
        val recent = store.conversations.value
            .firstOrNull { c -> c.id == convId }
            ?.messages.orEmpty()
            .filter { it.content.isNotBlank() }
            .takeLast(HISTORY_LIMIT)
            .map { ChatRequestMessage(it.role.apiValue, it.content) }
        return listOf(system) + recent
    }

    // ------------------------------------------------------------------
    // Conversation operations (ChatGPT style)
    // ------------------------------------------------------------------

    /** Starts a new, empty conversation and makes it active. */
    fun newConversation() {
        store.newConversation()
        lastUserText = null
        _state.update { it.copy(error = null) }
    }

    /** Opens an existing conversation. */
    fun openConversation(id: String) {
        if (id == _state.value.activeConversationId) return
        if (!store.exists(id)) return
        store.activate(id)
        lastUserText = null
        _state.update { it.copy(error = null) }
    }

    fun requestDeleteConversation(conversation: Conversation) {
        _state.update { it.copy(conversationToDelete = conversation) }
    }

    fun cancelDeleteConversation() {
        _state.update { it.copy(conversationToDelete = null) }
    }

    fun confirmDeleteConversation() {
        val conversation = _state.value.conversationToDelete ?: return
        if (conversation.id == _state.value.activeConversationId && streamJob != null) {
            stop()
        }
        store.deleteConversation(conversation.id)
        lastUserText = null
        _state.update {
            it.copy(
                conversationToDelete = null,
                error = null,
                isSending = false,
                isStreaming = false
            )
        }
        showMessage("Sohbet silindi.")
    }

    // ------------------------------------------------------------------
    // Message operations
    // ------------------------------------------------------------------

    fun requestDelete(message: ChatMessage) {
        _state.update { it.copy(messageToDelete = message) }
    }

    fun cancelDelete() {
        _state.update { it.copy(messageToDelete = null) }
    }

    fun confirmDelete() {
        val message = _state.value.messageToDelete ?: return
        val convId = _state.value.activeConversationId ?: return
        store.removeMessage(convId, message.id)
        _state.update { it.copy(messageToDelete = null) }
        showMessage("Mesaj silindi.")
    }

    fun requestClearChat() {
        _state.update { it.copy(clearChatRequested = true) }
    }

    fun cancelClearChat() {
        _state.update { it.copy(clearChatRequested = false) }
    }

    /** Clears all messages of the ACTIVE conversation (keeps the conversation). */
    fun confirmClearChat() {
        val convId = _state.value.activeConversationId ?: return
        if (streamJob != null) stop()
        store.clearMessages(convId)
        lastUserText = null
        _state.update {
            it.copy(clearChatRequested = false, error = null, isSending = false, isStreaming = false)
        }
        showMessage("Sohbet temizlendi.")
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    /** Saves (or clears, when empty) the user-provided API key. */
    fun saveApiKey(rawKey: String) {
        val key = rawKey.trim()
        if (key.isEmpty()) {
            keyManager.clearKey()
            showMessage("Cihazdaki anahtar kaldırıldı.")
        } else {
            keyManager.setKey(key)
            showMessage("API anahtarı kaydedildi.")
        }
        _state.update {
            it.copy(apiKeyConfigured = keyManager.effectiveKey() != null)
        }
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        keyManager.setAppearanceMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    fun hasEmbeddedKey(): Boolean = keyManager.hasEmbeddedKey()

    /**
     * Returns true (and clears the mark) when the app crashed within the
     * last 5 minutes - used for the friendly restart dialog.
     */
    fun consumeCrashMark(): Boolean {
        val ts = keyManager.lastCrashTimestamp()
        val recent = ts > 0L && System.currentTimeMillis() - ts < CRASH_MARK_WINDOW_MS
        if (recent) keyManager.clearCrashMark()
        return recent
    }

    // ------------------------------------------------------------------
    // Errors / snackbar
    // ------------------------------------------------------------------

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** Public snackbar trigger (e.g. "Kopyalandı" from the copy button). */
    fun showMessage(message: String) {
        snackbarId += 1
        _state.update { it.copy(snackbar = SnackbarEvent(snackbarId, message)) }
    }

    private fun friendlyError(t: Throwable): String = when (t) {
        is MissingApiKeyException ->
            "API anahtarı tanımlı değil. Ayarlar bölümünden ekleyin."

        is ApiException -> when (t.code) {
            401 -> "API anahtarı geçersiz veya süresi dolmuş (401). Ayarlar'dan kontrol edin."
            403 -> "Bu API anahtarıyla erişim izni yok (403)."
            404 -> "Hizmet bu isteği işleyemedi (404). Tekrar deneyin."
            429 -> "Hız limiti aşıldı (429). Birkaç saniye bekleyip tekrar deneyin."
            in 500..599 -> "Sunucu şu anda sorunlu (HTTP ${t.code}). Tekrar deneyin."
            else -> "Sunucu hatası (HTTP ${t.code}). Tekrar deneyin." +
                (t.serverMessage?.let { " $it" } ?: "")
        }

        is SocketTimeoutException, is UnknownHostException, is ConnectException ->
            "Sunucuya ulaşılamadı. İnternet bağlantınızı kontrol edin."

        is SSLException -> "Güvenli bağlantı kurulamadı."
        is IOException -> "Bağlantı hatası oluştu. Tekrar deneyin."
        else -> "Beklenmeyen bir hata oluştu. Tekrar deneyin."
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }

    private companion object {
        /** How many past messages are sent as context (performance). */
        const val HISTORY_LIMIT = 8

        const val CRASH_MARK_WINDOW_MS = 5 * 60 * 1000L
    }
}
