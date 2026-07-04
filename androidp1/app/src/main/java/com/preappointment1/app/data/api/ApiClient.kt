package com.preappointment1.app.data.api

import com.preappointment1.app.data.AuthHelper
import com.preappointment1.app.data.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://living-patient-memory-api-772480669824.us-central1.run.app/"

    private val refreshLock = Any()

    private val authHeaderInterceptor = Interceptor { chain ->
        val token = SessionManager.getToken()
        val requestBuilder = chain.request().newBuilder()
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
        if (response.code != 401) return@Authenticator null
        if (response.request.url.encodedPath.contains("/auth/")) return@Authenticator null

        synchronized(refreshLock) {
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val currentToken = SessionManager.getToken()

            if (failedToken != null && failedToken != currentToken && currentToken != null) {
                return@Authenticator response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshed = runBlocking { AuthHelper.refreshToken() }
            if (!refreshed) return@Authenticator null

            val newToken = SessionManager.getToken() ?: return@Authenticator null
            response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }
    }

    private fun buildClient(withAuthenticator: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
        if (withAuthenticator) {
            builder.authenticator(tokenAuthenticator)
        }
        return builder.build()
    }

    private val authRetrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(buildClient(withAuthenticator = false))
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiRetrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(buildClient(withAuthenticator = true))
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** Register/login only — no 401 auto-retry (avoids refresh loops). */
    val authApiService: ApiService = authRetrofit.create(ApiService::class.java)

    val apiService: ApiService = apiRetrofit.create(ApiService::class.java)
}
