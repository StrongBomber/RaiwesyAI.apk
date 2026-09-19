package com.raiwesy.ai.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.raiwesy.ai.BuildConfig
import com.raiwesy.ai.core.network.ApiException
import com.raiwesy.ai.core.network.MissingApiKeyException
import com.raiwesy.ai.core.network.AiApi
import com.raiwesy.ai.core.util.ApiKeyManager
import com.raiwesy.ai.data.model.ChatChunk
import com.raiwesy.ai.data.model.ChatCompletionRequest
import com.raiwesy.ai.data.model.ChatRequestMessage
import java.io.IOException
import kotlin.io.bufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Data layer: talks to the AI API (Retrofit + OkHttp) and exposes
 * chat operations to the ViewModel (MVVM).
 *
 * The primary path is token streaming (SSE) for fast perceived response;
 * a non-streaming call is kept as a fallback/validation helper.
 */
class ChatRepository(
    private val api: AiApi,
    private val keyManager: ApiKeyManager
) {

    private val gson = Gson()

    /** Fails fast (before any network I/O) when no API key is available. */
    private fun requireKey() {
        if (keyManager.effectiveKey() == null) throw MissingApiKeyException()
    }

    /**
     * Streams the assistant reply for the given history.
     *
     * Emits [ChatStreamEvent.Content] for every content fragment and a final
     * [ChatStreamEvent.Done]. Throws [ApiException] on non-2xx responses,
     * [MissingApiKeyException] when no key is configured, or an
     * [IOException]/[java.net.ConnectException] etc. on network failure.
     */
    fun streamChat(history: List<ChatRequestMessage>): Flow<ChatStreamEvent> = flow {
        requireKey()

        val request = ChatCompletionRequest(
            model = BuildConfig.MODEL,
            messages = history,
            stream = true,
            temperature = TEMPERATURE,
            max_tokens = MAX_TOKENS
        )

        // The Authorization header is injected by the OkHttp auth interceptor
        // (see AppContainer) so the key never ends up in the request body.
        val response = api.chatStream(request)
        if (!response.isSuccessful) {
            val detail = runCatching {
                response.errorBody()?.string()?.let { extractErrorDetail(it) }
            }.getOrNull()
            throw ApiException(response.code(), detail)
        }

        val body = response.body() ?: throw IOException("Sunucu boş yanıt döndürdü.")
        body.use { responseBody ->
            // charStream() zaten java.io.Reader döndürür (okhttp).
            val reader = java.io.BufferedReader(responseBody.charStream())
            var line = reader.readLine()
            while (line != null) {
                if (line.startsWith(SSE_DATA_PREFIX)) {
                    val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
                    if (payload == SSE_DONE) break
                    val delta = parseSseChunk(gson, payload)
                    if (delta != null) {
                        emit(ChatStreamEvent.Content(delta))
                    }
                }
                line = reader.readLine()
            }
        }
        emit(ChatStreamEvent.Done)
    }.flowOn(Dispatchers.IO)

    /**
     * Non-streaming chat completion (fallback path).
     * @return the full assistant reply
     */
    suspend fun chatCompletion(history: List<ChatRequestMessage>): String {
        requireKey()

        val request = ChatCompletionRequest(
            model = BuildConfig.MODEL,
            messages = history,
            stream = false,
            temperature = TEMPERATURE,
            max_tokens = MAX_TOKENS
        )

        val response = api.chatCompletion(request)
        if (!response.isSuccessful) {
            val detail = runCatching {
                response.errorBody()?.string()?.let { extractErrorDetail(it) }
            }.getOrNull()
            throw ApiException(response.code(), detail)
        }
        val content = response.body()?.choices?.firstOrNull()?.message?.content
            ?: throw IOException("Model boş yanıt döndürdü.")
        return content
    }

    companion object {
        const val TEMPERATURE = 0.7f
        const val MAX_TOKENS = 1024

        private const val SSE_DATA_PREFIX = "data:"
        private const val SSE_DONE = "[DONE]"

        private val companionGson = Gson()

        /**
         * Parses one SSE `data:` payload and returns the content fragment,
         * or null when the payload carries no content (role-only chunk,
         * `[DONE]`, malformed JSON, ...).
         */
        fun parseSseChunk(gson: Gson, payload: String): String? {
            if (payload.isBlank() || payload == SSE_DONE) return null
            return try {
                val chunk: ChatChunk = gson.fromJson(payload, ChatChunk::class.java)
                chunk.choices?.firstOrNull()?.delta?.content
            } catch (t: Throwable) {
                null
            }
        }

        /**
         * Extracts a human-readable detail from an error body.
         * Supports the OpenAI style (`{"error":{"message":...}}`) and the
         * alternative style (`{"detail":...}` / `{"message":...}`).
         */
        fun extractErrorDetail(body: String?): String? {
            if (body.isNullOrBlank()) return null
            return try {
                val obj = companionGson.fromJson(body, JsonObject::class.java)
                val openAiMessage = obj?.getAsJsonObject("error")?.get("message")
                val detailField = obj?.get("detail")
                val plainMessage = obj?.get("message")
                (openAiMessage ?: detailField ?: plainMessage)
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    ?.take(300)
                    ?: body.take(200)
            } catch (t: Throwable) {
                body.take(200)
            }
        }
    }
}
