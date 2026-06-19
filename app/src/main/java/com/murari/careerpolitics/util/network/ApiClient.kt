package com.murari.careerpolitics.util.network

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight HTTP client wrapping [HttpURLConnection].
 *
 * All methods are **blocking** — call them from a coroutine on [Dispatchers.IO].
 */
object ApiClient {

    private const val DEFAULT_CONNECT_TIMEOUT = 10_000
    private const val DEFAULT_READ_TIMEOUT = 10_000

    data class Response(
        val code: Int,
        val body: String
    ) {
        val isSuccess: Boolean get() = code in 200..299
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    fun post(
        url: String,
        body: JSONObject,
        cookies: String? = null,
        headers: Map<String, String> = emptyMap(),
        connectTimeout: Int = DEFAULT_CONNECT_TIMEOUT,
        readTimeout: Int = DEFAULT_READ_TIMEOUT
    ): Response = execute("POST", url, body, cookies, headers, connectTimeout, readTimeout)

    fun delete(
        url: String,
        body: JSONObject? = null,
        cookies: String? = null,
        headers: Map<String, String> = emptyMap(),
        connectTimeout: Int = DEFAULT_CONNECT_TIMEOUT,
        readTimeout: Int = DEFAULT_READ_TIMEOUT
    ): Response = execute("DELETE", url, body, cookies, headers, connectTimeout, readTimeout)

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private fun execute(
        method: String,
        url: String,
        body: JSONObject?,
        cookies: String?,
        headers: Map<String, String>,
        connectTimeout: Int,
        readTimeout: Int
    ): Response {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            this.connectTimeout = connectTimeout
            this.readTimeout = readTimeout
            doOutput = body != null
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            cookies?.let { setRequestProperty("Cookie", it) }
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        return try {
            body?.let { json ->
                connection.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val responseBody = readStream(connection)
            Response(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun readStream(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream?.let {
            BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
        } ?: ""
    }
}