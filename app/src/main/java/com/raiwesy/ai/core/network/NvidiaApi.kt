package com.raiwesy.ai.core.network

import com.raiwesy.ai.data.model.ChatCompletionRequest
import com.raiwesy.ai.data.model.ChatCompletionResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * OpenAI-compatible chat completions client for the NVIDIA integrate API.
 *
 * Base URL: https://integrate.api.nvidia.com/v1/
 * Model:   z-ai/glm-5.3
 */
interface NvidiaApi {

    /**
     * Non-streaming chat completion (fallback + simple requests).
     */
    @POST("chat/completions")
    suspend fun chatCompletion(@Body request: ChatCompletionRequest): Response<ChatCompletionResponse>

    /**
     * Streaming (Server-Sent-Events) chat completion.
     *
     * The [ResponseBody] is a live stream: consume it line by line and close
     * it (use `.use { }`) when the response is done or the request is cancelled.
     */
    @Streaming
    @POST("chat/completions")
    suspend fun chatStream(@Body request: ChatCompletionRequest): Response<ResponseBody>
}
