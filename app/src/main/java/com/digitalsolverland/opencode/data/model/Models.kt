package com.digitalsolverland.opencode.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/* ------------------------------------------------------------------ *
 *  OpenCode v2 REST models  (GET/POST /api/...)
 *  The server wraps every payload in { "data": ... }.
 * ------------------------------------------------------------------ */

@Serializable
data class ModelRef(
    val id: String,
    val providerID: String,
    val variant: String? = null,
)

@Serializable
data class SessionTime(
    val created: Long = 0L,
    val updated: Long = 0L,
    val archived: Long? = null,
)

@Serializable
data class LocationRef(
    val directory: String? = null,
    val workspaceID: String? = null,
)

@Serializable
data class TokenInfo(
    val input: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val cache: CacheInfo? = null,
)

@Serializable
data class CacheInfo(
    val read: Long = 0L,
    val write: Long = 0L,
)

@Serializable
data class SessionSummary(
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: Int = 0,
)

@Serializable
data class SessionInfo(
    val id: String,
    val title: String = "",
    val parentID: String? = null,
    val projectID: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val cost: Double = 0.0,
    val tokens: TokenInfo? = null,
    val time: SessionTime = SessionTime(),
    val location: LocationRef? = null,
    val summary: SessionSummary? = null,
    val share: ShareInfo? = null,
)

@Serializable
data class ShareInfo(val url: String = "")

/* ----------------------------- Messages ---------------------------- */

@Serializable
data class MessageTime(
    val created: Long = 0L,
    val completed: Long? = null,
    val streamed: Long? = null,
)

@Serializable
data class ToolState(
    val status: String = "pending",
    val input: JsonElement? = null,
    val content: List<ContentPiece> = emptyList(),
    val output: String? = null,
    val error: JsonElement? = null,
    val title: String? = null,
)

@Serializable
data class ContentPiece(
    val type: String = "",
    val text: String? = null,
)

@Serializable
data class MessageContent(
    val type: String = "",
    val id: String? = null,
    val text: String? = null,
    val name: String? = null,
    val state: ToolState? = null,
)

@Serializable
data class SessionMessage(
    val id: String,
    val type: String = "",
    val time: MessageTime? = null,
    /* user */
    val text: String? = null,
    /* assistant */
    val agent: String? = null,
    val model: ModelRef? = null,
    val content: List<MessageContent> = emptyList(),
    val error: JsonElement? = null,
    val finish: String? = null,
    val cost: Double = 0.0,
    val tokens: TokenInfo? = null,
    /* idle */
    val outcome: String? = null,
    /* compaction / synthetic / system */
    val summary: String? = null,
    val description: String? = null,
    /* shell */
    val command: String? = null,
    val output: String? = null,
)

/* ------------------------- Models / Agents ------------------------- */

@Serializable
data class ModelVariant(val id: String = "")

@Serializable
data class ModelLimit(
    val context: Long = 0L,
    val input: Long? = null,
    val output: Long = 0L,
)

@Serializable
data class ModelCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
)

@Serializable
data class ModelCapabilities(
    val tools: Boolean = false,
    val reasoning: Boolean = false,
)

@Serializable
data class ModelInfo(
    val id: String = "",
    val modelID: String? = null,
    val providerID: String = "",
    val name: String = "",
    val family: String? = null,
    val variants: List<ModelVariant> = emptyList(),
    val capabilities: ModelCapabilities? = null,
    val limit: ModelLimit? = null,
    val cost: ModelCost? = null,
    val status: String = "active",
    val enabled: Boolean = true,
) {
    /** Stable id used by ModelRef. */
    val refId: String get() = if (id.isNotBlank()) id else (modelID ?: "")
    val label: String get() = name.ifBlank { refId }
    val isFree: Boolean get() = (cost?.input ?: 0.0) <= 0.0 && (cost?.output ?: 0.0) <= 0.0
}

@Serializable
data class AgentInfo(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val mode: String? = null,
    val hidden: Boolean = false,
    val model: ModelRef? = null,
) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: id
}

/* --------------------------- API envelopes ------------------------- */

@Serializable
data class SessionListResponse(val data: List<SessionInfo> = emptyList())

@Serializable
data class SessionResponse(val data: SessionInfo)

@Serializable
data class Cursor(val previous: String? = null, val next: String? = null)

@Serializable
data class MessagesResponse(
    val data: List<SessionMessage> = emptyList(),
    val cursor: Cursor? = null,
)

@Serializable
data class ModelListResponse(val data: List<ModelInfo> = emptyList())

@Serializable
data class AgentListResponse(val data: List<AgentInfo> = emptyList())

@Serializable
data class ActiveSessionInfo(
    val type: String = "idle",
    val attempt: Int = 0,
    val message: String? = null,
)

@Serializable
data class ActiveSessionsResponse(val data: Map<String, ActiveSessionInfo> = emptyMap())

/* ----------------------------- Requests ---------------------------- */

@Serializable
data class CreateSessionRequest(
    val id: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
)

@Serializable
data class PromptInput(val text: String)

@Serializable
data class PromptRequest(
    val prompt: PromptInput,
    val delivery: String = "queue",
    val resume: Boolean? = null,
)

@Serializable
data class SwitchModelRequest(val model: ModelRef)

@Serializable
data class SwitchAgentRequest(val agent: String)

@Serializable
data class PromptAdmitted(
    val id: String = "",
    val sessionID: String = "",
    val admittedSeq: Long = 0L,
)
