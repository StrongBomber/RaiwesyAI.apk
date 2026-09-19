package com.raiwesy.ai.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure parsing helpers of [ChatRepository]
 * (no network / Android dependencies).
 */
class ChatRepositoryTest {

    private val gson = Gson()

    @Test
    fun `parses content delta from sse chunk`() {
        val payload = """{"id":"chatcmpl-1","model":"z-ai/glm-5.3","choices":[{"index":0,"delta":{"role":"assistant","content":"Mer"},"finish_reason":null}]}"""
        assertEquals("Mer", ChatRepository.parseSseChunk(gson, payload))
    }

    @Test
    fun `returns null for done marker`() {
        assertNull(ChatRepository.parseSseChunk(gson, "[DONE]"))
    }

    @Test
    fun `returns null for role-only chunk`() {
        val payload = """{"id":"chatcmpl-2","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}"""
        assertNull(ChatRepository.parseSseChunk(gson, payload))
    }

    @Test
    fun `returns null for empty content chunk`() {
        val payload = """{"id":"chatcmpl-3","choices":[{"index":0,"delta":{"content":""},"finish_reason":"stop"}]}"""
        assertEquals("", ChatRepository.parseSseChunk(gson, payload))
    }

    @Test
    fun `returns null for malformed payload`() {
        assertNull(ChatRepository.parseSseChunk(gson, "bu-bir-json-degil"))
    }

    @Test
    fun `extracts openai style error detail`() {
        val body = """{"error":{"message":"Invalid API key","type":"authentication_error"}}"""
        assertEquals("Invalid API key", ChatRepository.extractErrorDetail(body))
    }

    @Test
    fun `extracts nvidia style error detail`() {
        val body = """{"detail":"Insufficient credits"}"""
        assertEquals("Insufficient credits", ChatRepository.extractErrorDetail(body))
    }

    @Test
    fun `falls back to raw body for unknown error shape`() {
        val body = """{"unknown":{"x":1}}"""
        assertEquals(body, ChatRepository.extractErrorDetail(body))
    }

    @Test
    fun `extractErrorDetail handles null and blank`() {
        assertNull(ChatRepository.extractErrorDetail(null))
        assertNull(ChatRepository.extractErrorDetail("  "))
    }
}
