package com.digitalsolverland.opencode.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val slug: String? = null,
    val title: String? = null,
    val projectID: String? = null,
    val directory: String? = null,
    val time: SessionTime? = null,
    val summary: SessionSummary? = null,
    val share: SessionShare? = null,
    val revert: SessionRevert? = null,
)

@Serializable
data class SessionTime(
    val created: Long,
    val updated: Long,
)

@Serializable
data class SessionSummary(
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: Int = 0,
)

@Serializable
data class SessionShare(
    val url: String,
)

@Serializable
data class SessionRevert(
    val messageID: String,
    val diff: String? = null,
)

@Serializable
data class Message(
    val id: String,
    val sessionID: String,
    val role: String,
    val time: MessageTime? = null,
    val system: String? = null,
    val model: MessageModel? = null,
    val agent: String? = null,
    val summary: MessageSummary? = null,
    val finish: String? = null,
    val error: MessageError? = null,
    val metadata: MessageMetadata? = null,
)

@Serializable
data class MessageTime(
    val created: Long,
    val completed: Long? = null,
)

@Serializable
data class MessageModel(
    val providerID: String,
    val modelID: String,
)

@Serializable
data class MessageSummary(
    val title: String? = null,
    val diffs: List<Diff>? = null,
)

@Serializable
data class Diff(
    val file: String,
    val before: String? = null,
    val after: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
)

@Serializable
data class MessageError(
    val name: String,
    val data: ErrorData? = null,
)

@Serializable
data class ErrorData(
    val message: String? = null,
)

@Serializable
data class MessageMetadata(
    val assistant: AssistantMetadata? = null,
)

@Serializable
data class AssistantMetadata(
    val cost: Double = 0.0,
    val tokens: TokenUsage? = null,
)

@Serializable
data class TokenUsage(
    val input: Int = 0,
    val output: Int = 0,
    val reasoning: Int = 0,
    val cache: CacheUsage? = null,
)

@Serializable
data class CacheUsage(
    val read: Int = 0,
    val write: Int = 0,
)

@Serializable
data class Part(
    val id: String,
    val sessionID: String,
    val messageID: String,
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    val callID: String? = null,
    val state: PartState? = null,
    val filename: String? = null,
    val mime: String? = null,
    val url: String? = null,
)

@Serializable
data class PartState(
    val status: String? = null,
    val input: kotlinx.serialization.json.JsonElement? = null,
    val output: kotlinx.serialization.json.JsonElement? = null,
    val error: String? = null,
    val time: PartTime? = null,
)

@Serializable
data class PartTime(
    val start: Long,
    val end: Long? = null,
)

@Serializable
data class Agent(
    val name: String,
    val description: String? = null,
    val mode: String? = null,
    val builtIn: Boolean = false,
    val model: AgentModel? = null,
)

@Serializable
data class AgentModel(
    val modelID: String,
    val providerID: String,
)

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val source: String? = null,
    val env: List<String>? = null,
    val models: Map<String, Model>? = null,
)

@Serializable
data class Model(
    val id: String,
    val name: String,
    val release_date: String? = null,
    val attachment: Boolean = false,
    val reasoning: Boolean = false,
    val temperature: Boolean = false,
    val tool_call: Boolean = false,
    val cost: ModelCost? = null,
    val limit: ModelLimit? = null,
)

@Serializable
data class ModelCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cache_read: Double = 0.0,
    val cache_write: Double = 0.0,
)

@Serializable
data class ModelLimit(
    val context: Long = 0,
    val output: Long = 0,
)

@Serializable
data class Config(
    val model: String? = null,
    val agent: String? = null,
    val small_model: String? = null,
)

@Serializable
data class Event(
    val type: String,
    val properties: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class CreateSessionRequest(
    val title: String? = null,
    val parentID: String? = null,
)

@Serializable
data class PromptRequest(
    val parts: List<PromptPart>,
    val model: PromptModel? = null,
    val agent: String? = null,
    val system: String? = null,
)

@Serializable
data class PromptPart(
    val type: String,
    val text: String? = null,
    val file: FilePart? = null,
)

@Serializable
data class FilePart(
    val mime: String,
    val url: String,
    val filename: String? = null,
)

@Serializable
data class PromptModel(
    val providerID: String,
    val modelID: String,
)

@Serializable
data class Todo(
    val id: String,
    val content: String,
    val status: String,
    val priority: String? = null,
)

@Serializable
data class PathInfo(
    val home: String? = null,
    val state: String? = null,
    val config: String? = null,
    val worktree: String? = null,
    val directory: String? = null,
)
