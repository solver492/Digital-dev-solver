package com.digitalsolverland.opencode.data.repo

import com.digitalsolverland.opencode.data.api.ApiClient
import com.digitalsolverland.opencode.data.api.OpenCodeApi
import com.digitalsolverland.opencode.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Single source of truth for the OpenCode v2 server state.
 *
 * Everything is polled (no SSE): it survives doze mode, flaky Wi-Fi and
 * server restarts, which is what a phone actually needs.
 */
class OpenCodeRepository {

    private val apiClient = ApiClient.getInstance()

    @Volatile private var api: OpenCodeApi? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models

    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val agents: StateFlow<List<AgentInfo>> = _agents

    private val _currentSession = MutableStateFlow<SessionInfo?>(null)
    val currentSession: StateFlow<SessionInfo?> = _currentSession

    private val _messages = MutableStateFlow<List<SessionMessage>>(emptyList())
    val messages: StateFlow<List<SessionMessage>> = _messages

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking

    /** Human readable activity reported by the server while a turn runs. */
    private val _activity = MutableStateFlow<String?>(null)
    val activity: StateFlow<String?> = _activity

    private val _sessionsLoading = MutableStateFlow(false)
    val sessionsLoading: StateFlow<Boolean> = _sessionsLoading

    fun init(baseUrl: String, password: String) {
        api = apiClient.api(baseUrl, password)
    }

    fun currentBaseUrl(): String = apiClient.baseUrl()

    private fun requireApi(): OpenCodeApi =
        api ?: throw IllegalStateException("Client non initialisé")

    /* --------------------------- Connection -------------------------- */

    suspend fun connect(baseUrl: String, password: String): Boolean = withContext(Dispatchers.IO) {
        _isConnecting.value = true
        _error.value = null
        try {
            init(baseUrl, password)
            val response = requireApi().listSessions()
            if (!response.isSuccessful) {
                _isConnected.value = false
                _error.value = httpMessage(response, "Connexion refusée")
                return@withContext false
            }
            _isConnected.value = true
            _sessions.value = response.body()?.data.orEmpty()
                .sortedByDescending { it.time.updated }
            // Catalog is optional: a slow provider refresh must never block login.
            loadModelsSilently()
            loadAgentsSilently()
            true
        } catch (e: Exception) {
            _isConnected.value = false
            _error.value = "Serveur injoignable : ${e.message ?: "erreur réseau"}"
            false
        } finally {
            _isConnecting.value = false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        _isConnected.value = false
        _currentSession.value = null
        _messages.value = emptyList()
        _sessions.value = emptyList()
        _models.value = emptyList()
        _agents.value = emptyList()
        _activity.value = null
        _isWorking.value = false
    }

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext
        loadSessionsSilently()
        loadModelsSilently()
        loadAgentsSilently()
    }

    suspend fun loadSessionsSilently() = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext
        _sessionsLoading.value = _sessions.value.isEmpty()
        try {
            val response = requireApi().listSessions()
            if (response.isSuccessful) {
                val known = _sessions.value.associate { it.id to it.title }
                _sessions.value = response.body()?.data.orEmpty()
                    .map { s ->
                        val previous = known[s.id]
                        if (s.title.isBlank() && !previous.isNullOrBlank()) s.copy(title = previous) else s
                    }
                    .sortedByDescending { it.time.updated }
            } else if (response.code() == 401) {
                _isConnected.value = false
                _error.value = httpMessage(response, "Session expirée")
            }
        } catch (_: Exception) {
        } finally {
            _sessionsLoading.value = false
        }
    }

    suspend fun loadModelsSilently() = withContext(Dispatchers.IO) {
        try {
            val response = requireApi().listModels()
            if (response.isSuccessful) {
                _models.value = response.body()?.data.orEmpty()
                    .filter { it.refId.isNotBlank() && it.enabled }
                    .distinctBy { it.providerID + "/" + it.refId }
                    .sortedWith(compareBy({ !it.isFree }, { it.providerID }, { it.label }))
            }
        } catch (_: Exception) {
        }
    }

    suspend fun loadAgentsSilently() = withContext(Dispatchers.IO) {
        try {
            val response = requireApi().listAgents()
            if (response.isSuccessful) {
                _agents.value = response.body()?.data.orEmpty()
                    .filter { !it.hidden && it.id.isNotBlank() }
            }
        } catch (_: Exception) {
        }
    }

    /* ---------------------------- Sessions --------------------------- */

    suspend fun createSession(model: ModelRef?, agent: String?): SessionInfo? =
        withContext(Dispatchers.IO) {
            _error.value = null
            try {
                val response = requireApi().createSession(
                    CreateSessionRequest(
                        model = model,
                        agent = agent?.takeIf { it.isNotBlank() },
                    )
                )
                if (response.isSuccessful) {
                    val session = response.body()?.data
                    if (session != null) {
                        _currentSession.value = session
                        _messages.value = emptyList()
                        _sessions.value = (listOf(session) + _sessions.value)
                            .distinctBy { it.id }
                            .sortedByDescending { it.time.updated }
                    }
                    session
                } else {
                    _error.value = httpMessage(response, "Impossible de créer la session")
                    null
                }
            } catch (e: Exception) {
                _error.value = "Création impossible : ${e.message}"
                null
            }
        }

    suspend fun selectSession(session: SessionInfo) = withContext(Dispatchers.IO) {
        _currentSession.value = session
        _messages.value = emptyList()
        loadMessagesSilently(session.id)
        deriveTitleIfNeeded()
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val response = requireApi().deleteSession(sessionId)
            if (response.isSuccessful || response.code() == 404) {
                if (_currentSession.value?.id == sessionId) {
                    _currentSession.value = null
                    _messages.value = emptyList()
                }
                loadSessionsSilently()
            } else {
                _error.value = httpMessage(response, "Suppression impossible")
            }
        } catch (e: Exception) {
            _error.value = "Suppression impossible : ${e.message}"
        }
    }

    suspend fun loadMessagesSilently(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            // `desc` gives us the newest window of a long conversation;
            // the list is then sorted by timestamp so display order is stable
            // even if a server ignores the ordering parameter.
            val response = requireApi().listMessages(sessionId, limit = 200, order = "desc")
            if (response.isSuccessful) {
                _messages.value = response.body()?.data.orEmpty()
                    .sortedBy { it.time?.created ?: 0L }
                deriveTitleIfNeeded()
            }
        } catch (_: Exception) {
        }
    }

    /** Uses the first user message as a display title when the server has none. */
    private fun deriveTitleIfNeeded() {
        val session = _currentSession.value ?: return
        if (session.title.isNotBlank()) return
        val firstUser = _messages.value.firstOrNull { it.type == "user" && !it.text.isNullOrBlank() }
            ?: return
        val derived = firstUser.text!!.trim().lineSequence().first()
            .take(70).ifBlank { "Nouvelle session" }
        _currentSession.value = session.copy(title = derived)
        _sessions.value = _sessions.value.map {
            if (it.id == session.id) it.copy(title = derived) else it
        }
    }

    /* ----------------------------- Prompt ---------------------------- */

    suspend fun sendPrompt(
        text: String,
        model: ModelRef?,
        agent: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val session = _currentSession.value ?: return@withContext false
        _error.value = null
        _isWorking.value = true
        _activity.value = "Envoi du message…"
        try {
            // Apply the selected model/agent to the session before prompting.
            if (model != null && (session.model?.id != model.id ||
                        session.model?.providerID != model.providerID)
            ) {
                val r = requireApi().switchModel(session.id, SwitchModelRequest(model))
                if (!r.isSuccessful) {
                    _error.value = httpMessage(r, "Modèle non disponible")
                    _activity.value = null
                    _isWorking.value = false
                    return@withContext false
                }
                _currentSession.value = session.copy(model = model)
            }
            if (!agent.isNullOrBlank() && session.agent != agent) {
                val r = requireApi().switchAgent(session.id, SwitchAgentRequest(agent))
                if (!r.isSuccessful) {
                    _error.value = httpMessage(r, "Agent non disponible")
                    _activity.value = null
                    _isWorking.value = false
                    return@withContext false
                }
                _currentSession.value = (_currentSession.value ?: session).copy(agent = agent)
            }

            val known = _messages.value.map { it.id }.toHashSet()
            val response = requireApi().prompt(
                session.id,
                PromptRequest(prompt = PromptInput(text), delivery = "queue"),
            )
            if (!response.isSuccessful) {
                _error.value = httpMessage(response, "Envoi refusé")
                _activity.value = null
                _isWorking.value = false
                return@withContext false
            }

            awaitCompletion(session.id, known)
            loadSessionsSilently()
            true
        } catch (e: Exception) {
            _error.value = "Envoi impossible : ${e.message}"
            false
        } finally {
            _isWorking.value = false
            _activity.value = null
        }
    }

    /**
     * Polls the timeline (and the server's session status) until the turn is
     * over. Only messages that did not exist before the prompt are considered,
     * so a previous finished turn can never end the wait early.
     */
    private suspend fun awaitCompletion(sessionId: String, known: Set<String>) {
        val started = System.currentTimeMillis()
        var sawActivity = false
        var idleTicks = 0

        while (_isWorking.value && System.currentTimeMillis() - started < MAX_TURN_MS) {
            delay(POLL_MS)
            if (!_isWorking.value) return
            loadMessagesSilently(sessionId)

            val fresh = _messages.value.filter { it.id !in known }

            // Older servers append a terminal `idle` marker to the timeline.
            if (fresh.any { it.type == "idle" }) return

            // Nothing at all after 90s means the prompt never reached the agent.
            if (fresh.isEmpty() && System.currentTimeMillis() - started > 90_000) return

            val status = activeStatus(sessionId)
            val lastAssistant = fresh.lastOrNull { it.type == "assistant" }

            when (status) {
                "running", "busy", "retry" -> {
                    sawActivity = true
                    idleTicks = 0
                    _activity.value = if (status == "retry") "Nouvelle tentative…" else "OpenCode travaille…"
                }
                else -> {
                    val finished = lastAssistant?.time?.completed != null
                    if (sawActivity || finished) {
                        idleTicks++
                        if (idleTicks >= 2) return
                    }
                }
            }

            if (lastAssistant?.error != null && lastAssistant.time?.completed != null) return
        }
    }

    private suspend fun activeStatus(sessionId: String): String? = try {
        val response = requireApi().activeSessions()
        if (response.isSuccessful) response.body()?.data?.get(sessionId)?.type else null
    } catch (_: Exception) {
        null
    }

    suspend fun interrupt() = withContext(Dispatchers.IO) {
        val session = _currentSession.value ?: return@withContext
        // Stop polling immediately for a snappy UI, then tell the server.
        _isWorking.value = false
        _activity.value = null
        try {
            requireApi().interrupt(session.id)
        } catch (_: Exception) {
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun httpMessage(response: Response<*>, fallback: String): String {
        return when (response.code()) {
            401 -> "Mot de passe du serveur invalide"
            403 -> "Accès refusé"
            404 -> "Endpoint introuvable : vérifiez que le serveur est bien OpenCode v2"
            409 -> "Une requête est déjà en cours sur cette session"
            else -> {
                val body = try {
                    response.errorBody()?.string()?.take(300)
                } catch (_: Exception) {
                    null
                }
                if (!body.isNullOrBlank()) "$fallback (${response.code()}) : $body"
                else "$fallback (${response.code()})"
            }
        }
    }

    companion object {
        private const val POLL_MS = 1_000L
        private const val MAX_TURN_MS = 20 * 60 * 1000L
    }
}
