package com.digitalsolverland.opencode.data.repo

import com.digitalsolverland.opencode.data.api.ApiClient
import com.digitalsolverland.opencode.data.api.OpenCodeApi
import com.digitalsolverland.opencode.data.api.ProvidersResponse
import com.digitalsolverland.opencode.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class OpenCodeRepository {
    private val apiClient = ApiClient.getInstance()
    private var api: OpenCodeApi? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions
    
    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents: StateFlow<List<Agent>> = _agents
    
    private val _providers = MutableStateFlow<ProvidersResponse?>(null)
    val providers: StateFlow<ProvidersResponse?> = _providers
    
    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    private val _parts = MutableStateFlow<Map<String, List<Part>>>(emptyMap())
    val parts: StateFlow<Map<String, List<Part>>> = _parts
    
    fun init(baseUrl: String) {
        apiClient.reset()
        api = apiClient.getApi(baseUrl)
    }
    
    private fun getApi(): OpenCodeApi {
        return api ?: throw IllegalStateException("Repository not initialized")
    }
    
    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = getApi().getConfig()
            _isConnected.value = response.isSuccessful
            response.isSuccessful
        } catch (e: Exception) {
            _isConnected.value = false
            false
        }
    }
    
    suspend fun loadSessions() = withContext(Dispatchers.IO) {
        try {
            val response = getApi().listSessions()
            if (response.isSuccessful) {
                _sessions.value = response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
    
    suspend fun createSession(title: String? = null): Session? = withContext(Dispatchers.IO) {
        try {
            val response = getApi().createSession(CreateSessionRequest(title = title))
            if (response.isSuccessful) {
                val session = response.body()
                session?.let {
                    _currentSession.value = it
                    loadSessions()
                }
                session
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun selectSession(session: Session) {
        _currentSession.value = session
        loadMessages(session.id)
    }
    
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val response = getApi().deleteSession(sessionId)
            if (response.isSuccessful) {
                if (_currentSession.value?.id == sessionId) {
                    _currentSession.value = null
                    _messages.value = emptyList()
                    _parts.value = emptyMap()
                }
                loadSessions()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
    
    suspend fun loadMessages(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val response = getApi().listMessages(sessionId)
            if (response.isSuccessful) {
                _messages.value = response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            _messages.value = emptyList()
        }
    }
    
    suspend fun sendPrompt(sessionId: String, text: String, model: PromptModel? = null, agent: String? = null): Message? = withContext(Dispatchers.IO) {
        try {
            val request = PromptRequest(
                parts = listOf(PromptPart(type = "text", text = text)),
                model = model,
                agent = agent
            )
            val response = getApi().prompt(sessionId, request)
            if (response.isSuccessful) {
                val message = response.body()
                // Reload messages to get full conversation
                loadMessages(sessionId)
                message
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun sendPromptAsync(sessionId: String, text: String, model: PromptModel? = null, agent: String? = null) = withContext(Dispatchers.IO) {
        try {
            val request = PromptRequest(
                parts = listOf(PromptPart(type = "text", text = text)),
                model = model,
                agent = agent
            )
            getApi().promptAsync(sessionId, request)
        } catch (e: Exception) {
            // ignore
        }
    }
    
    suspend fun loadAgents() = withContext(Dispatchers.IO) {
        try {
            val response = getApi().listAgents()
            if (response.isSuccessful) {
                _agents.value = response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
    
    suspend fun loadProviders() = withContext(Dispatchers.IO) {
        try {
            val response = getApi().getProviders()
            if (response.isSuccessful) {
                _providers.value = response.body()
            }
        } catch (e: Exception) {
            try {
                val response2 = getApi().listProviders()
                if (response2.isSuccessful) {
                    _providers.value = response2.body()
                }
            } catch (e2: Exception) {
                // ignore
            }
        }
    }
    
    suspend fun abortSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            getApi().abortSession(sessionId)
        } catch (e: Exception) {
            // ignore
        }
    }
    
    fun getEventStreamUrl(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return "$base/event"
    }
}
