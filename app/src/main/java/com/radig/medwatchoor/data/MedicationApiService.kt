package com.radig.medwatchoor.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit API service for fetching and uploading medication data
 */
interface MedicationApiService {
    @GET("medwatchoor/stevie.json")
    suspend fun getMedications(): MedicationResponse

    @POST("medwatchoor/upload.php")
    suspend fun uploadMedications(
        @Header("Authorization") authToken: String,
        @Body data: MedicationResponse
    ): Response<UploadResponse>

    companion object {
        private const val BASE_URL = "https://www.radig.com/"
        private const val UPLOAD_TOKEN = "UeBSqVYo3I1huHzk6ABaoyozimTaASyBXiCLLe6Y"

        fun create(): MedicationApiService {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                android.util.Log.d("OkHttp", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(MedicationApiService::class.java)
        }

        fun getAuthToken(): String {
            val token = "Bearer $UPLOAD_TOKEN"
            android.util.Log.d("MedicationApiService", "Auth token: $token")
            return token
        }
    }
}

/**
 * Response from the upload endpoint
 */
data class UploadResponse(
    val success: Boolean,
    val message: String,
    val timestamp: String? = null
)
