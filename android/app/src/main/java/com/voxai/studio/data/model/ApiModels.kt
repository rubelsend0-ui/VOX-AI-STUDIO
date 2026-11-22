package com.voxai.studio.data.model

data class HealthResponse(
    val success: Boolean,
    val status: String?,
    val database: String?,
    val aiEngine: AiHealthInfo?,
    val error: String?
)

data class AiHealthInfo(
    val configured: Boolean,
    val reachable: Boolean,
    val providerName: String,
    val supportedLanguages: List<String>,
    val message: String
)

data class VoiceDto(
    val id: String,
    val name: String,
    val provider: String,
    val status: String,
    val durationSeconds: Double,
    val createdAt: String
)

data class VoicesListResponse(
    val success: Boolean,
    val voices: List<VoiceDto>?,
    val error: String?
)

data class SingleVoiceResponse(
    val success: Boolean,
    val voice: VoiceDto?,
    val error: String?
)

data class PreviewResponse(
    val success: Boolean,
    val previewUrl: String?,
    val error: String?
)

data class DubbingRequest(
    val voiceId: String,
    val scriptText: String,
    val language: String,
    val projectTitle: String,
    val saveProject: Boolean = true
)

data class GenerationDto(
    val id: String,
    val voiceId: String?,
    val voiceName: String?,
    val projectTitle: String?,
    val scriptText: String?,
    val language: String?,
    val provider: String?,
    val status: String,
    val progress: Int,
    val durationSeconds: Double?,
    val errorMessage: String?,
    val mp3Url: String?,
    val wavUrl: String?,
    val createdAt: String?
)

data class GenerationStartResponse(
    val success: Boolean,
    val generation: GenerationDto?,
    val error: String?
)

data class HistoryResponse(
    val success: Boolean,
    val history: List<GenerationDto>?,
    val error: String?
)

data class SettingsDto(
    val activeProvider: String,
    val xttsInferenceUrl: String,
    val hasExternalApiKey: Boolean,
    val defaultLanguage: String,
    val supportedLanguages: List<String>,
    val providerHealth: AiHealthInfo
)

data class SettingsResponse(
    val success: Boolean,
    val settings: SettingsDto?,
    val error: String?
)

data class UpdateSettingsRequest(
    val activeProvider: String? = null,
    val xttsInferenceUrl: String? = null,
    val defaultLanguage: String? = null
)
