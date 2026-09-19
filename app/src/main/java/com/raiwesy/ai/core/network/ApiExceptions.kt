package com.raiwesy.ai.core.network

/**
 * Thrown when the NVIDIA API answers with a non-2xx status code.
 *
 * @param code HTTP status code (401, 429, 500, ...)
 * @param serverMessage optional human-readable detail extracted from the error body
 */
class ApiException(
    val code: Int,
    val serverMessage: String? = null
) : Exception("API hatası (HTTP $code)")

/** Thrown when no API key is available (neither built-in nor user-provided). */
class MissingApiKeyException : Exception("NVIDIA API anahtarı tanımlı değil.")
