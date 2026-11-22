package com.voxai.studio.data.remote

import com.voxai.studio.data.local.SecureConfigStore
import com.voxai.studio.data.model.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface VoxApiService {
    @GET("api/health")
    suspend fun getHealth(): HealthResponse

    @GET("api/voices")
    suspend fun getVoices(): VoicesListResponse

    @Multipart
    @POST("api/voices/clone")
    suspend fun cloneVoice(
        @Part audio: MultipartBody.Part,
        @Part("name") name: RequestBody,
        @Part("consentConfirmed") consentConfirmed: RequestBody
    ): SingleVoiceResponse

    @PATCH("api/voices/{id}")
    suspend fun renameVoice(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): SingleVoiceResponse

    @DELETE("api/voices/{id}")
    suspend fun deleteVoice(@Path("id") id: String): Map<String, Any>

    @POST("api/voices/{id}/preview")
    suspend fun previewVoice(@Path("id") id: String): PreviewResponse

    @POST("api/dubbing/generate")
    suspend fun generateDubbing(@Body request: DubbingRequest): GenerationStartResponse

    @GET("api/dubbing/history")
    suspend fun getDubbingHistory(): HistoryResponse

    @GET("api/dubbing/{id}")
    suspend fun getGenerationStatus(@Path("id") id: String): GenerationStartResponse

    @GET("api/settings")
    suspend fun getSettings(): SettingsResponse

    @PATCH("api/settings")
    suspend fun updateSettings(@Body request: UpdateSettingsRequest): SettingsResponse
}

object ApiClientProvider {
    fun create(configStore: SecureConfigStore): VoxApiService? {
        val rawUrl = configStore.backendUrl
        if (!rawUrl.startsWith("http")) return null
        val normalizedUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("X-API-Key", configStore.apiKey)
                    .build()
                chain.proceed(req)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VoxApiService::class.java)
    }
}
