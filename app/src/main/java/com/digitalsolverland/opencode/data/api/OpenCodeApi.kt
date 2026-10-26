package com.digitalsolverland.opencode.data.api

import com.digitalsolverland.opencode.data.model.*
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface OpenCodeApi {
    // Config & Info
    @GET("config")
    suspend fun getConfig(): Response<Config>
    
    @GET("path")
    suspend fun getPath(): Response<PathInfo>
    
    // Sessions
    @GET("session")
    suspend fun listSessions(): Response<List<Session>>
    
    @GET("session/status")
    suspend fun getSessionStatus(): Response<Map<String, String>>
    
    @POST("session")
    suspend fun createSession(@Body request: CreateSessionRequest): Response<Session>
    
    @GET("session/{id}")
    suspend fun getSession(@Path("id") id: String): Response<Session>
    
    @DELETE("session/{id}")
    suspend fun deleteSession(@Path("id") id: String): Response<Boolean>
    
    @PATCH("session/{id}")
    suspend fun updateSession(@Path("id") id: String, @Body body: Map<String, String>): Response<Session>
    
    @POST("session/{id}/share")
    suspend fun shareSession(@Path("id") id: String): Response<Session>
    
    @DELETE("session/{id}/share")
    suspend fun unshareSession(@Path("id") id: String): Response<Session>
    
    @POST("session/{id}/abort")
    suspend fun abortSession(@Path("id") id: String): Response<Boolean>
    
    // Messages
    @GET("session/{id}/message")
    suspend fun listMessages(@Path("id") id: String): Response<List<Message>>
    
    @GET("session/{id}/message/{messageID}")
    suspend fun getMessage(@Path("id") id: String, @Path("messageID") messageID: String): Response<Message>
    
    @POST("session/{id}/message")
    suspend fun createMessage(@Path("id") id: String, @Body request: PromptRequest): Response<Message>
    
    @POST("session/{id}/prompt")
    suspend fun prompt(@Path("id") id: String, @Body request: PromptRequest): Response<Message>
    
    @POST("session/{id}/prompt_async")
    suspend fun promptAsync(@Path("id") id: String, @Body request: PromptRequest): Response<Unit>
    
    @POST("session/{id}/command")
    suspend fun command(@Path("id") id: String, @Body body: Map<String, Any>): Response<Message>
    
    @POST("session/{id}/shell")
    suspend fun shell(@Path("id") id: String, @Body body: Map<String, String>): Response<Message>
    
    // Parts
    @GET("session/{id}/message/{messageID}/part")
    suspend fun listParts(@Path("id") id: String, @Path("messageID") messageID: String): Response<List<Part>>
    
    // Agents & Providers
    @GET("agent")
    suspend fun listAgents(): Response<List<Agent>>
    
    @GET("config/providers")
    suspend fun listProviders(): Response<ProvidersResponse>
    
    @GET("provider")
    suspend fun getProviders(): Response<ProvidersResponse>
    
    // Events (SSE) - handled separately with OkHttp
    @GET("event")
    suspend fun getEventStream(): Response<ResponseBody>
    
    // TUI
    @POST("tui/append-prompt")
    suspend fun appendPrompt(@Body body: Map<String, String>): Response<Boolean>
    
    @POST("tui/open-sessions")
    suspend fun openSessions(): Response<Boolean>
    
    @POST("tui/select-session")
    suspend fun selectSession(@Body body: Map<String, String>): Response<Boolean>
    
    // Health
    @GET("doc")
    suspend fun getDoc(): Response<JsonElement>
}

@Serializable
data class ProvidersResponse(
    val providers: Map<String, Provider>,
    val default: Map<String, String>? = null,
)
