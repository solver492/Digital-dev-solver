package com.digitalsolverland.opencode.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Holds the Retrofit instance for one (url, password) pair.
 *
 * `opencode serve` protects the API with HTTP Basic auth:
 *   username = "opencode"
 *   password = the token printed by `opencode serve`
 */
class ApiClient private constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    @Volatile private var authHeader: String? = null
    @Volatile private var api: OpenCodeApi? = null
    @Volatile private var cachedBaseUrl: String? = null
    @Volatile private var cachedPassword: String? = null
    @Volatile private var currentBaseUrl: String = ""

    private val authInterceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
        authHeader?.let { builder.header("Authorization", it) }
        chain.proceed(builder.build())
    }

    private class RetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var response: Response? = null
            var lastException: IOException? = null

            // Don't retry POST/PUT/PATCH mutations aggressively (except safe idempotent ones)
            val method = request.method
            val shouldRetry = method == "GET" || method == "HEAD"

            val maxRetries = if (shouldRetry) 3 else 1
            for (attempt in 0 until maxRetries) {
                try {
                    response?.close()
                    response = chain.proceed(request)
                    if (response!!.isSuccessful) {
                        return response!!
                    }
                    // Retry on server errors and rate limits for idempotent requests
                    if (shouldRetry && (response!!.code == 408 || response!!.code == 429 || response!!.code >= 500)) {
                        if (attempt < maxRetries - 1) {
                            val retryAfter = response!!.header("Retry-After")?.toLongOrNull() ?: 0L
                            val delayMs = if (retryAfter > 0) retryAfter * 1000 else min(1000L * (1L shl attempt), 4000L)
                            Thread.sleep(delayMs)
                            continue
                        }
                    }
                    return response!!
                } catch (e: IOException) {
                    lastException = e
                    if (attempt < maxRetries - 1 && shouldRetry) {
                        val delayMs = min(500L * (1L shl attempt), 2000L)
                        try {
                            Thread.sleep(delayMs)
                        } catch (_: InterruptedException) {
                        }
                        continue
                    }
                    throw e
                }
            }
            return response ?: throw lastException ?: IOException("Unknown error")
        }
    }

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(RetryInterceptor())
            .addInterceptor(logging)
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }

    fun api(baseUrl: String, password: String): OpenCodeApi {
        val normalized = normalize(baseUrl)
        val current = api
        if (current != null && cachedBaseUrl == normalized && cachedPassword == password) {
            return current
        }
        synchronized(this) {
            if (api != null && cachedBaseUrl == normalized && cachedPassword == password) {
                return api!!
            }
            currentBaseUrl = normalized
            cachedBaseUrl = normalized
            cachedPassword = password
            authHeader = if (password.isBlank()) null else basicAuth(password)
            val contentType = "application/json".toMediaType()
            api = Retrofit.Builder()
                .baseUrl(normalized)
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(OpenCodeApi::class.java)
            return api!!
        }
    }

    fun baseUrl(): String = currentBaseUrl

    private fun basicAuth(password: String): String {
        val raw = "opencode:$password"
        return "Basic " + android.util.Base64.encodeToString(
            raw.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
    }

    /** Accepts "192.168.1.10:4096", adds http:// and a trailing slash. */
    fun normalize(url: String): String {
        var base = url.trim()
        if (base.isBlank()) base = "http://127.0.0.1:4096"
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://$base"
        }
        base = base.trimEnd('/')
        if (!base.endsWith("/")) base += "/"
        return base
    }

    companion object {
        @Volatile private var INSTANCE: ApiClient? = null

        fun getInstance(): ApiClient =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient().also { INSTANCE = it }
            }
    }
}
