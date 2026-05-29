package com.example.applicationledcontrol.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Esp32HttpClient {

    private const val DEFAULT_TIMEOUT_MS = 4000

    suspend fun ping(host: String): String {
        return request(host, "/ping")
    }

    suspend fun sendCommand(host: String, command: String): String {
        return request(host, "/command", mapOf("value" to command))
    }

    fun normalizeHost(host: String): String {
        return host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
    }

    private suspend fun request(
        host: String,
        path: String,
        query: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val normalizedHost = normalizeHost(host)
        require(normalizedHost.isNotBlank()) { "ESP32 host is empty" }

        val url = URL(buildUrl(normalizedHost, path, query))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = DEFAULT_TIMEOUT_MS
            readTimeout = DEFAULT_TIMEOUT_MS
            doInput = true
            useCaches = false
        }

        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (responseCode in 200..299) {
                body.ifBlank { "OK" }
            } else {
                throw IOException(body.ifBlank { "HTTP $responseCode" })
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildUrl(
        host: String,
        path: String,
        query: Map<String, String>
    ): String {
        val queryString = if (query.isEmpty()) {
            ""
        } else {
            query.entries.joinToString(prefix = "?", separator = "&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        }

        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "http://$host$normalizedPath$queryString"
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}
