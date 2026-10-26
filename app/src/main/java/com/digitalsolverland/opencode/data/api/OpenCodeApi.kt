package com.digitalsolverland.opencode.data.api

import com.digitalsolverland.opencode.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * OpenCode v2 HTTP API. Every route lives under /api and is secured with
 * HTTP Basic auth (user `opencode`).
 */
interface OpenCodeApi {

    // ---- Sessions ----------------------------------------------------
    @GET("api/session")
    suspend fun listSessions(
        @Query("limit") limit: Int = 200,
        @Query("order") order: String = "desc",
    ): Response<SessionListResponse>

    @POST("api/session")
    suspend fun createSession(@Body body: CreateSessionRequest): Response<SessionResponse>

    @GET("api/session/{id}")
    suspend fun getSession(@Path("id") id: String): Response<SessionResponse>

    @DELETE("api/session/{id}")
    suspend fun deleteSession(@Path("id") id: String): Response<Unit>

    @GET("api/session/active")
    suspend fun activeSessions(): Response<ActiveSessionsResponse>

    @GET("api/session/{id}/message")
    suspend fun listMessages(
        @Path("id") id: String,
        @Query("limit") limit: Int = 200,
        @Query("order") order: String = "asc",
    ): Response<MessagesResponse>

    @POST("api/session/{id}/prompt")
    suspend fun prompt(
        @Path("id") id: String,
        @Body body: PromptRequest,
    ): Response<PromptAdmitted>

    @POST("api/session/{id}/interrupt")
    suspend fun interrupt(@Path("id") id: String): Response<Unit>

    @POST("api/session/{id}/model")
    suspend fun switchModel(
        @Path("id") id: String,
        @Body body: SwitchModelRequest,
    ): Response<Unit>

    @POST("api/session/{id}/agent")
    suspend fun switchAgent(
        @Path("id") id: String,
        @Body body: SwitchAgentRequest,
    ): Response<Unit>

    @POST("api/session/{id}/compact")
    suspend fun compact(
        @Path("id") id: String,
        @Body body: EmptyRequest = EmptyRequest(),
    ): Response<Unit>

    // ---- Catalog -----------------------------------------------------
    @GET("api/model")
    suspend fun listModels(): Response<ModelListResponse>

    @GET("api/agent")
    suspend fun listAgents(): Response<AgentListResponse>

    // ---- Health ------------------------------------------------------
    @GET("api/health")
    suspend fun health(): Response<okhttp3.ResponseBody>
}
