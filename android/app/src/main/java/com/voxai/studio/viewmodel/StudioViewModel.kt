package com.voxai.studio.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.voxai.studio.data.local.*
import com.voxai.studio.data.model.*
import com.voxai.studio.data.remote.ApiClientProvider
import com.voxai.studio.util.VoiceRecorderHelper
import com.voxai.studio.util.copyUriToCacheFile
import com.voxai.studio.util.enqueueAudioDownload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

data class StudioUiState(
    val isConfigured: Boolean = false,
    val backendUrl: String = "",
    val apiKey: String = "",
    val apiConnected: Boolean = false,
    val aiEngineStatus: String = "Not Connected",
    val activeProvider: String = "xtts",
    val xttsUrl: String = "",
    val supportedLanguages: List<String> = listOf("en", "hi", "bn", "es", "fr", "de"),
    val isLoading: Boolean = false,
    val statusBanner: String? = null,
    val errorBanner: String? = null,
    // Cloning
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val currentAmplitude: Float = 0f,
    val stagedAudioFile: File? = null,
    val stagedAudioName: String = "",
    // Dubbing
    val selectedVoiceId: String = "",
    val scriptText: String = "",
    val selectedLanguage: String = "en",
    val projectTitle: String = "",
    val activeJob: GenerationDto? = null,
    // Player
    val isPlaying: Boolean = false,
    val currentPlayingTitle: String = "",
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 1L
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val configStore = SecureConfigStore(application)
    private val db = VoxDatabase.getInstance(application)
    private val dao = db.studioDao()
    private val recorder = VoiceRecorderHelper(application)

    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    private val _uiState = MutableStateFlow(
        StudioUiState(
            isConfigured = configStore.isConfigured(),
            backendUrl = configStore.backendUrl,
            apiKey = configStore.apiKey
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val voices: StateFlow<List<CachedVoiceEntity>> = dao.observeVoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<CachedGenerationEntity>> = dao.observeGenerations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var recordTimerJob: Job? = null
    private var playerProgressJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPlayerProgressLoop() else playerProgressJob?.cancel()
            }
        })
        if (configStore.isConfigured()) {
            refreshAll()
        }
    }

    private fun startPlayerProgressLoop() {
        playerProgressJob?.cancel()
        playerProgressJob = viewModelScope.launch {
            while (true) {
                _uiState.update {
                    it.copy(
                        playbackPositionMs = player.currentPosition.coerceAtLeast(0L),
                        playbackDurationMs = player.duration.coerceAtLeast(1L)
                    )
                }
                delay(250)
            }
        }
    }

    fun playAudioUrl(url: String, title: String) {
        try {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
            _uiState.update { it.copy(currentPlayingTitle = title, errorBanner = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorBanner = "Playback failed: ${e.message}") }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _uiState.update { it.copy(playbackPositionMs = positionMs) }
    }

    fun saveConnectionConfig(url: String, key: String) {
        configStore.backendUrl = url
        configStore.apiKey = key
        _uiState.update {
            it.copy(
                backendUrl = configStore.backendUrl,
                apiKey = configStore.apiKey,
                isConfigured = configStore.isConfigured(),
                errorBanner = null,
                statusBanner = "API credentials saved securely."
            )
        }
        refreshAll()
    }

    fun refreshAll() {
        val api = ApiClientProvider.create(configStore) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorBanner = null) }
            try {
                val health = api.getHealth()
                val settingsRes = api.getSettings()
                val voicesRes = api.getVoices()
                val historyRes = api.getDubbingHistory()

                voicesRes.voices?.let { list ->
                    dao.clearVoices()
                    dao.insertVoices(list.map {
                        CachedVoiceEntity(it.id, it.name, it.provider, it.status, it.durationSeconds, it.createdAt)
                    })
                    if (_uiState.value.selectedVoiceId.isBlank() && list.isNotEmpty()) {
                        _uiState.update { it.copy(selectedVoiceId = list.first().id) }
                    }
                }

                historyRes.history?.let { list ->
                    dao.clearGenerations()
                    dao.insertGenerations(list.map {
                        CachedGenerationEntity(
                            id = it.id,
                            voiceId = it.voiceId ?: "",
                            voiceName = it.voiceName ?: "Voice",
                            projectTitle = it.projectTitle ?: "Dubbing",
                            scriptText = it.scriptText ?: "",
                            language = it.language ?: "en",
                            status = it.status,
                            progress = it.progress,
                            durationSeconds = it.durationSeconds ?: 0.0,
                            mp3Url = it.mp3Url,
                            wavUrl = it.wavUrl,
                            createdAt = it.createdAt ?: ""
                        )
                    })
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        apiConnected = health.success,
                        aiEngineStatus = health.aiEngine?.message ?: "Connected",
                        activeProvider = settingsRes.settings?.activeProvider ?: "xtts",
                        xttsUrl = settingsRes.settings?.xttsInferenceUrl ?: "",
                        supportedLanguages = settingsRes.settings?.supportedLanguages ?: it.supportedLanguages
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        apiConnected = false,
                        aiEngineStatus = "Unreachable",
                        errorBanner = "Sync error: ${e.message}"
                    )
                }
            }
        }
    }

    fun startMicRecording() {
        try {
            val file = recorder.startRecording()
            _uiState.update {
                it.copy(
                    isRecording = true,
                    recordingSeconds = 0,
                    stagedAudioFile = file,
                    stagedAudioName = "Mic Recording",
                    errorBanner = null
                )
            }
            recordTimerJob?.cancel()
            recordTimerJob = viewModelScope.launch {
                var ticks = 0
                while (true) {
                    delay(200)
                    ticks++
                    val amp = (recorder.getMaxAmplitude() / 32767f).coerceIn(0.05f, 1f)
                    _uiState.update {
                        it.copy(
                            recordingSeconds = ticks / 5,
                            currentAmplitude = amp
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorBanner = "Microphone error: ${e.message}") }
        }
    }

    fun stopMicRecording() {
        recordTimerJob?.cancel()
        val file = recorder.stopRecording()
        _uiState.update {
            it.copy(
                isRecording = false,
                currentAmplitude = 0f,
                stagedAudioFile = file,
                stagedAudioName = "Mic Sample (${it.recordingSeconds}s)"
            )
        }
    }

    fun stageImportedUri(uri: Uri) {
        val file = copyUriToCacheFile(getApplication(), uri)
        if (file != null) {
            _uiState.update {
                it.copy(
                    stagedAudioFile = file,
                    stagedAudioName = file.name,
                    errorBanner = null
                )
            }
        } else {
            _uiState.update { it.copy(errorBanner = "Unable to read selected audio file.") }
        }
    }

    fun cloneStagedVoice(voiceName: String, consentConfirmed: Boolean) {
        val api = ApiClientProvider.create(configStore) ?: return
        val file = _uiState.value.stagedAudioFile
        if (file == null || !file.exists()) {
            _uiState.update { it.copy(errorBanner = "Please record or upload a voice sample first.") }
            return
        }
        if (voiceName.isBlank()) {
            _uiState.update { it.copy(errorBanner = "Please enter a custom voice profile name.") }
            return
        }
        if (!consentConfirmed) {
            _uiState.update { it.copy(errorBanner = "You must confirm ownership or permission to clone this voice.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorBanner = null, statusBanner = "Uploading & cloning voice...") }
            try {
                val reqFile = file.asRequestBody("audio/*".toMediaType())
                val part = MultipartBody.Part.createFormData("audio", file.name, reqFile)
                val nameBody = voiceName.trim().toRequestBody("text/plain".toMediaType())
                val consentBody = "true".toRequestBody("text/plain".toMediaType())

                val res = api.cloneVoice(part, nameBody, consentBody)
                if (res.success && res.voice != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            stagedAudioFile = null,
                            stagedAudioName = "",
                            selectedVoiceId = res.voice.id,
                            statusBanner = "Voice '${res.voice.name}' cloned and verified!"
                        )
                    }
                    refreshAll()
                } else {
                    _uiState.update { it.copy(isLoading = false, errorBanner = res.error ?: "Voice cloning failed.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorBanner = "Clone failed: ${e.message}") }
            }
        }
    }

    fun previewVoice(voiceId: String, voiceName: String) {
        val api = ApiClientProvider.create(configStore) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(statusBanner = "Generating preview for $voiceName...") }
            try {
                val res = api.previewVoice(voiceId)
                if (res.success && res.previewUrl != null) {
                    _uiState.update { it.copy(statusBanner = null) }
                    playAudioUrl(res.previewUrl, "Preview: $voiceName")
                } else {
                    _uiState.update { it.copy(errorBanner = res.error ?: "Preview unavailable.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "Preview error: ${e.message}") }
            }
        }
    }

    fun renameVoice(voiceId: String, newName: String) {
        val api = ApiClientProvider.create(configStore) ?: return
        viewModelScope.launch {
            try {
                api.renameVoice(voiceId, mapOf("name" to newName.trim()))
                refreshAll()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "Rename failed: ${e.message}") }
            }
        }
    }

    fun deleteVoice(voiceId: String) {
        val api = ApiClientProvider.create(configStore) ?: return
        viewModelScope.launch {
            try {
                api.deleteVoice(voiceId)
                refreshAll()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "Delete failed: ${e.message}") }
            }
        }
    }

    fun selectVoiceForDubbing(voiceId: String) {
        _uiState.update { it.copy(selectedVoiceId = voiceId) }
    }

    fun updateScriptText(text: String) {
        _uiState.update { it.copy(scriptText = text) }
    }

    fun updateLanguage(lang: String) {
        _uiState.update { it.copy(selectedLanguage = lang) }
    }

    fun updateProjectTitle(title: String) {
        _uiState.update { it.copy(projectTitle = title) }
    }

    fun generateDubbing() {
        val api = ApiClientProvider.create(configStore) ?: return
        val state = _uiState.value
        if (state.selectedVoiceId.isBlank()) {
            _uiState.update { it.copy(errorBanner = "Select a saved voice profile first.") }
            return
        }
        if (state.scriptText.isBlank()) {
            _uiState.update { it.copy(errorBanner = "Enter a script to synthesize.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorBanner = null) }
            try {
                val startRes = api.generateDubbing(
                    DubbingRequest(
                        voiceId = state.selectedVoiceId,
                        scriptText = state.scriptText.trim(),
                        language = state.selectedLanguage,
                        projectTitle = state.projectTitle.ifBlank { "Studio Dubbing" }
                    )
                )
                val job = startRes.generation
                if (!startRes.success || job == null) {
                    _uiState.update { it.copy(isLoading = false, errorBanner = startRes.error ?: "Failed to queue job.") }
                    return@launch
                }

                _uiState.update { it.copy(activeJob = job) }

                // Poll job progress
                while (true) {
                    delay(2000)
                    val poll = api.getGenerationStatus(job.id).generation ?: break
                    _uiState.update { it.copy(activeJob = poll) }
                    if (poll.status == "COMPLETED") {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusBanner = "Dubbing synthesis complete!"
                            )
                        }
                        poll.mp3Url?.let { url -> playAudioUrl(url, poll.projectTitle ?: "Generated Dub") }
                        refreshAll()
                        break
                    } else if (poll.status == "FAILED") {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorBanner = poll.errorMessage ?: "Dubbing generation failed."
                            )
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorBanner = "Generation error: ${e.message}") }
            }
        }
    }

    fun downloadAudio(url: String?, title: String, ext: String) {
        if (url.isNullOrBlank()) {
            _uiState.update { it.copy(errorBanner = "Audio download URL not ready.") }
            return
        }
        val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".$ext"
        enqueueAudioDownload(getApplication(), url, safeName)
        _uiState.update { it.copy(statusBanner = "Downloading $safeName to Downloads folder...") }
    }

    fun updateProviderSettings(provider: String, xttsUrl: String) {
        val api = ApiClientProvider.create(configStore) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorBanner = null) }
            try {
                val res = api.updateSettings(UpdateSettingsRequest(activeProvider = provider, xttsInferenceUrl = xttsUrl.trim()))
                if (res.success && res.settings != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeProvider = res.settings.activeProvider,
                            xttsUrl = res.settings.xttsInferenceUrl,
                            supportedLanguages = res.settings.supportedLanguages,
                            aiEngineStatus = res.settings.providerHealth.message,
                            statusBanner = "AI Provider settings updated."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorBanner = "Settings error: ${e.message}") }
            }
        }
    }

    fun clearBanners() {
        _uiState.update { it.copy(statusBanner = null, errorBanner = null) }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
