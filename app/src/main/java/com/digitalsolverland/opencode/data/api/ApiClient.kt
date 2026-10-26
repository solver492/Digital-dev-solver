package com.digitalsolverland.opencode.data.api

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class ApiClient private constructor() {
    private var retrofit: Retrofit? = null
    private var api: OpenCodeApi? = null
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }
    
    fun getApi(baseUrl: String): OpenCodeApi {
        if (api == null || retrofit == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
            
            val contentType = "application/json".toMediaType()
            retrofit = Retrofit.Builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
            
            api = retrofit!!.create(OpenCodeApi::class.java)
        }
        return api!!
    }
    
    fun getOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for SSE
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }
    
    fun reset() {
        retrofit = null
        api = null
    }
    
    private fun normalizeBaseUrl(url: String): String {
        var base = url.trimEnd('/')
        if (!base.endsWith("/")) base += "/"
        // Try to detect if it's API v2 path
        if (!base.contains("/api/") && !base.endsWith(":4096/") && !base.endsWith(":8080/")) {
            // Keep as is
        }
        return base
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ApiClient? = null
        
        fun getInstance(): ApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient().also { INSTANCE = it }
            }
        }
    }
}
