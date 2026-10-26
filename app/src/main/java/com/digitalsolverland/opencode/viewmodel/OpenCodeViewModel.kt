package com.digitalsolverland.opencode.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.digitalsolverland.opencode.SecureSettings
import com.digitalsolverland.opencode.data.model.AgentInfo
import com.digitalsolverland.opencode.data.model.ModelInfo
import com.digitalsolverland.opencode.data.model.ModelRef
import com.digitalsolverland.opencode.data.model.SessionInfo
import com.digitalsolverland.opencode.data.repo.OpenCodeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OpenCodeViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SecureSettings(application)
    private val repository = OpenCodeRepository()

    /* ------------------------------ Data ----------------------------- */

    val isConnected: StateFlow<Boolean> = repository.isConnected
    val isConnecting: StateFlow<Boolean> = repository.isConnecting
    val sessions: StateFlow<List<SessionInfo>> = repository.sessions
    val sessionsLoading: StateFlow<Boolean> = repository.sessionsLoading
    val models: StateFlow<List<ModelInfo>> = repository.models
    val agents: StateFlow<List<AgentInfo>> = repository.agents
    val currentSession: StateFlow<SessionInfo?> = repository.currentSession
    val messages = repository.messages
    val isWorking: StateFlow<Boolean> = repository.isWorking
    val activity: StateFlow<String?> = repository.activity
    val error: StateFlow<String?> = repository.error

    /* ---------------------------- Settings --------------------------- */

    private val _serverUrl = MutableStateFlow(settings.serverUrl)
    val serverUrl: StateFlow<String> = _serverUrl

    private val _serverPassword = MutableStateFlow(settings.serverPassword)
    val serverPassword: StateFlow<String> = _serverPassword

    private val _selectedModel = MutableStateFlow(restoreModel())
    val selectedModel: StateFlow<ModelRef?> = _selectedModel

    private val _selectedAgent = MutableStateFlow(settings.defaultAgent.takeIf { it.isNotBlank() })
    val selectedAgent: StateFlow<String?> = _selectedAgent

    val quickActions: List<Pair<String, String>> = settings.quickActions()

    /** Kept after the transient snackbar clears, for the settings screen. */
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    /* ---------------------------- Lifecycle -------------------------- */

    init {
        // Silent reconnect on cold start: users land straight in their sessions.
        if (settings.serverPassword.isNotBlank()) {
            connect()
        }
    }

    fun connect(onResult: ((Boolean) -> Unit)? = null) {
        val url = _serverUrl.value
        val password = _serverPassword.value
        viewModelScope.launch {
            _connectionError.value = null
            val ok = repository.connect(url, password)
            if (ok) {
                applyDefaultSelection()
                startRefreshLoop()
            } else {
                _connectionError.value = repository.error.value
            }
            onResult?.invoke(ok)
        }
    }

    fun disconnect() {
        viewModelScope.launch { repository.disconnect() }
    }

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        settings.serverUrl = url
    }

    fun updateServerPassword(password: String) {
        _serverPassword.value = password
        settings.serverPassword = password
    }

    fun saveConnection(url: String, password: String) {
        updateServerUrl(url)
        updateServerPassword(password)
    }

    fun refresh() {
        viewModelScope.launch { repository.refreshAll() }
    }

    /* ------------------------------ Model ---------------------------- */

    fun setModel(model: ModelInfo) {
        val ref = ModelRef(id = model.refId, providerID = model.providerID)
        _selectedModel.value = ref
        settings.rememberModel(model.providerID, model.refId)
    }

    fun setAgent(agent: AgentInfo?) {
        _selectedAgent.value = agent?.id
        settings.defaultAgent = agent?.id.orEmpty()
    }

    /** Picks up the model/agent of the opened session so the header is accurate. */
    fun syncSelectionFrom(session: SessionInfo) {
        session.model?.let { _selectedModel.value = it }
        session.agent?.let { agent ->
            if (agent.isNotBlank()) _selectedAgent.value = agent
        }
    }

    private fun restoreModel(): ModelRef? {
        val provider = settings.defaultProvider
        val model = settings.defaultModel
        return if (provider.isNotBlank() && model.isNotBlank()) {
            ModelRef(id = model, providerID = provider)
        } else null
    }

    private fun applyDefaultSelection() {
        if (_selectedModel.value == null) {
            val provider = settings.defaultProvider
            val model = settings.defaultModel
            if (provider.isNotBlank() && model.isNotBlank()) {
                _selectedModel.value = ModelRef(id = model, providerID = provider)
            }
        }
    }

    /* ---------------------------- Sessions --------------------------- */

    fun createSession(onCreated: (SessionInfo) -> Unit) {
        viewModelScope.launch {
            val session = repository.createSession(_selectedModel.value, _selectedAgent.value)
            if (session != null) {
                onCreated(session)
                startRefreshLoop()
            }
        }
    }

    fun openSession(session: SessionInfo) {
        syncSelectionFrom(session)
        viewModelScope.launch { repository.selectSession(session) }
    }

    fun deleteSession(session: SessionInfo) {
        viewModelScope.launch { repository.deleteSession(session.id) }
    }

    fun closeSession() {
        // Keep the repository's selection: back navigation only hides the chat.
    }

    /* ----------------------------- Chat ------------------------------ */

    fun sendMessage(text: String) {
        val body = text.trim()
        if (body.isEmpty() || isWorking.value) return
        if (currentSession.value == null) {
            // No session yet (e.g. deep link): create one, then send.
            viewModelScope.launch {
                val session = repository.createSession(_selectedModel.value, _selectedAgent.value)
                    ?: return@launch
                repository.sendPrompt(body, _selectedModel.value, _selectedAgent.value)
            }
            return
        }
        viewModelScope.launch {
            repository.sendPrompt(body, _selectedModel.value, _selectedAgent.value)
        }
    }

    fun compact() {
        if (isWorking.value) return
        viewModelScope.launch { repository.compact() }
    }

    fun stop() {
        viewModelScope.launch { repository.interrupt() }
    }

    fun clearError() {
        repository.clearError()
    }

    /* ---------------------------- Polling ---------------------------- */

    private var refreshJob: Job? = null

    private fun startRefreshLoop() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            while (isConnected.value) {
                kotlinx.coroutines.delay(if (isWorking.value) 4_000L else 15_000L)
                if (!isConnected.value) break
                if (!isWorking.value) repository.loadSessionsSilently()
            }
        }
    }
}
