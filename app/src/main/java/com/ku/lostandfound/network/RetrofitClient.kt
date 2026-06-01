package com.ku.lostandfound.network

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "http://finders-alb-2066093184.ap-northeast-2.elb.amazonaws.com/"

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("KUFindersOkHttp", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val originalRequest = chain.request()
        val token = TokenManager.accessToken
        val path = originalRequest.url.encodedPath
        val method = originalRequest.method
        val isAuthRequest = path.endsWith("/api/v1/auth/login") ||
            path.endsWith("/api/v1/auth/join") ||
            path.endsWith("/api/v1/auth/reissue") ||
            path.endsWith("/api/v1/auth/email/send")
        val isPublicLostItemRequest = method == "GET" && path.startsWith("/api/v1/lost-items")
        val isPublicFoundItemRequest = method == "GET" && path.startsWith("/api/v1/found-items")

        val request = if (
            token != null &&
            originalRequest.header("Authorization") == null &&
            !isAuthRequest &&
            !isPublicLostItemRequest &&
            !isPublicFoundItemRequest
        ) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        val response = chain.proceed(request)
        if (response.code == 403 && response.peekBody(2048).string().contains("\"timestamp\"")) {
            val newAccessToken = synchronized(this) {
                refreshTokensBlocking()
            }
            if (newAccessToken != null) {
                response.close()
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
                return@Interceptor chain.proceed(retryRequest)
            }
        }
        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val signupApi: SignupApiService = retrofit.create(SignupApiService::class.java)
    val authApi: AuthApiService = retrofit.create(AuthApiService::class.java)
    val userApi: UserApiService = retrofit.create(UserApiService::class.java)
    val lostItemApi: LostItemApiService = retrofit.create(LostItemApiService::class.java)
    val foundItemApi: FoundItemApiService = retrofit.create(FoundItemApiService::class.java)

    private fun refreshTokensBlocking(): String? {
        val refreshToken = TokenManager.refreshToken ?: return null
        return try {
            val refreshRequestJson = Gson().toJson(RefreshTokenRequest(refreshToken))
            val request = Request.Builder()
                .url("${BASE_URL}api/v1/auth/reissue")
                .post(refreshRequestJson.toRequestBody("application/json".toMediaType()))
                .build()
            val response = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()
                .newCall(request)
                .execute()
            response.use {
                if (!it.isSuccessful) {
                    TokenManager.clear()
                    return null
                }
                val body = it.body?.string().orEmpty()
                val parsed = Gson().fromJson(body, RefreshTokenResponse::class.java)
                val tokens = parsed.data
                if (parsed.success && tokens != null) {
                    TokenManager.accessToken = tokens.accessToken
                    TokenManager.refreshToken = tokens.refreshToken
                    tokens.accessToken
                } else {
                    TokenManager.clear()
                    null
                }
            }
        } catch (e: Exception) {
            NetworkLog.exception("refreshTokensBlocking", e)
            TokenManager.clear()
            null
        }
    }
}
