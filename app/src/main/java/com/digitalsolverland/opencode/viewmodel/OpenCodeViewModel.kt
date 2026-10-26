package com.digitalsolverland.opencode.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.digitalsolverland.opencode.SecureSettings
import com.digitalsolverland.opencode.data.api.ApiClient
import com.digitalsolverland.opencode.data.model.*
import com.digitalsolverland.opencode.data.repo.OpenCodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader

class OpenCodeViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SecureSettings(application)
    private val repository = OpenCodeRepository()
    
    val isConnected = repository.isConnected
    val sessions = repository.sessions
    val agents = repository.agents
    val providers = repository.providers
    val currentSession = repository.currentSession
    val messages = repository.messages
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _serverUrl = MutableStateFlow(settings.serverUrl)
    val serverUrl: StateFlow<String> = _serverUrl
    
    private val _selectedAgent = MutableStateFlow<String?>(null)
    val selectedAgent: StateFlow<String?> = _selectedAgent
    
    private val _selectedModel = MutableStateFlow<PromptModel?>(null)
    val selectedModel: StateFlow<PromptModel?> = _selectedModel
    
    private var eventStreamJob: Job? = null
    
    init {
        initConnection()
    }
    
    fun initConnection() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.init(settings.serverUrl)
                val connected = repository.checkConnection()
                if (connected) {
                    repository.loadSessions()
                    repository.loadAgents()
                    repository.loadProviders()
                    startEventStream()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        settings.serverUrl = url
        repository.init(url)
    }
    
    fun connect() {
        initConnection()
    }
    
    fun createSession(title: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.createSession(title)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun selectSession(session: Session) {
        viewModelScope.launch {
            repository.selectSession(session)
        }
    }
    
    fun deleteSession(session: Session) {
        viewModelScope.launch {
            repository.deleteSession(session.id)
        }
    }
    
    fun sendMessage(text: String) {
        val session = currentSession.value ?: return
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val model = _selectedModel.value
                val agent = _selectedAgent.value
                repository.sendPrompt(session.id, text, model, agent)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun abortCurrentSession() {
        val session = currentSession.value ?: return
        viewModelScope.launch {
            repository.abortSession(session.id)
        }
    }
    
    fun setAgent(agent: String?) {
        _selectedAgent.value = agent
    }
    
    fun setModel(providerID: String, modelID: String) {
        _selectedModel.value = PromptModel(providerID = providerID, modelID = modelID)
        settings.defaultProvider = providerID
        settings.defaultModel = modelID
    }
    
    private fun startEventStream() {
        eventStreamJob?.cancel()
        eventStreamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = ApiClient.getInstance().getOkHttpClient()
                val request = Request.Builder()
                    .url(repository.getEventStreamUrl(settings.serverUrl))
                    .addHeader("Accept", "text/event-stream")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val source = response.body?.source() ?: return@use
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.substring(6)
                            if (data.isNotEmpty() && data != "[DONE]") {
                                // Handle events
                                withContext(Dispatchers.Main) {
                                    handleEvent(data)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Retry after delay
                delay(5000)
                if (isConnected.value) {
                    startEventStream()
                }
            }
        }
    }
    
    private suspend fun handleEvent(data: String) {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val event = json.decodeFromString<com.digitalsolverland.opencode.data.model.Event>(data)
            
            when (event.type) {
                "session.updated", "session.created", "session.deleted" -> {
                    repository.loadSessions()
                }
                "message.updated" -> {
                    currentSession.value?.let { repository.loadMessages(it.id) }
                }
                "message.part.updated" -> {
                    currentSession.value?.let { repository.loadMessages(it.id) }
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        eventStreamJob?.cancel()
    }
}
