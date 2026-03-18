package com.example.enduro.api

import com.example.enduro.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class SegmentDto(
    val id: Int,
    val name: String,
    val start_lat: Double,
    val start_lon: Double,
    val end_lat: Double,
    val end_lon: Double
)

data class ActivityUploadRequest(
    val track: List<Map<String, Any>>
)

data class ActivityUploadResponse(
    val activity_id: Int,
    val attempts: Int
)

interface EnduroApi {
    @GET("segments")
    suspend fun getSegments(@Header("Authorization") token: String): List<SegmentDto>

    @POST("sync/activity")
    suspend fun uploadActivity(
        @Header("Authorization") token: String,
        @Body body: ActivityUploadRequest
    ): ActivityUploadResponse
}

object ApiClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val api: EnduroApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EnduroApi::class.java)
    }
}
