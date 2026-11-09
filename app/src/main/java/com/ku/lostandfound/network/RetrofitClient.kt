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

sealed interface RefreshSessionResult {
    data object Success : RefreshSessionResult
    data object InvalidSession : RefreshSessionResult
    data object RetryableFailure : RefreshSessionResult
}

object RetrofitClient {
    private const val BASE_URL = "https://finders.hyungyu.dev/"

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("KUFindersOkHttp", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactHeader("Authorization")
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val originalRequest = chain.request()
        val token = TokenManager.accessToken
        val path = originalRequest.url.encodedPath
        val method = originalRequest.method
        val isAuthRequest = path.endsWith("/api/v1/auth/login") ||
                path.endsWith("/api/v1/auth/join") ||
                path.endsWith("/api/v1/auth/reissue") ||
                path.endsWith("/api/v1/auth/email/send") ||
                path.startsWith("/api/v1/auth/password-reset/")
        val request = if (
            token != null &&
            originalRequest.header("Authorization") == null &&
            !isAuthRequest
        ) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        val response = chain.proceed(request)
        if (isAuthenticationFailure(response)) {
            val newAccessToken = synchronized(this) {
                // Another request may already have refreshed this exact token while this
                // request waited for the lock.
                TokenManager.accessToken
                    ?.takeIf { it != token }
                    ?: refreshTokensBlocking()
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
    val passwordResetApi: PasswordResetApiService = retrofit.create(PasswordResetApiService::class.java)
    val userApi: UserApiService = retrofit.create(UserApiService::class.java)
    val lostItemApi: LostItemApiService = retrofit.create(LostItemApiService::class.java)
    val foundItemApi: FoundItemApiService = retrofit.create(FoundItemApiService::class.java)
    val chatApi: ChatApiService = retrofit.create(ChatApiService::class.java)
    val moderationApi: ModerationApiService = retrofit.create(ModerationApiService::class.java)
    val adminApi: AdminApiService = retrofit.create(AdminApiService::class.java)

    val pushDeviceApi: PushDeviceApiService =
        retrofit.create(PushDeviceApiService::class.java)

    /** Used during app start only after an expired access token was detected. */
    suspend fun refreshSession(): RefreshSessionResult {
        val refreshToken = TokenManager.refreshToken ?: return RefreshSessionResult.InvalidSession
        return try {
            val response = authApi.refreshToken(RefreshTokenRequest(refreshToken))
            if (!response.isSuccessful) {
                if (response.code() == 401 || response.code() == 403) {
                    TokenManager.expireSession()
                    RefreshSessionResult.InvalidSession
                } else {
                    RefreshSessionResult.RetryableFailure
                }
            } else {
                val body = response.body()
                val tokens = body?.data
                if (body?.success == true && tokens != null) {
                    TokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
                    RefreshSessionResult.Success
                } else {
                    TokenManager.expireSession()
                    RefreshSessionResult.InvalidSession
                }
            }
        } catch (e: Exception) {
            NetworkLog.exception("refreshSession", e)
            RefreshSessionResult.RetryableFailure
        }
    }

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
                    if (it.code == 401 || it.code == 403) {
                        TokenManager.expireSession()
                    }
                    return null
                }
                val body = it.body?.string().orEmpty()
                val parsed = Gson().fromJson(body, RefreshTokenResponse::class.java)
                val tokens = parsed.data
                if (parsed.success && tokens != null) {
                    TokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)
                    tokens.accessToken
                } else {
                    TokenManager.expireSession()
                    null
                }
            }
        } catch (e: Exception) {
            NetworkLog.exception("refreshTokensBlocking", e)
            null
        }
    }

    private fun isAuthenticationFailure(response: okhttp3.Response): Boolean {
        // Spring Security uses this shape for an invalid/expired credential. Other 403s are
        // authorization failures and must be returned to the caller without ending the session.
        return response.code == 403 &&
                response.peekBody(2048).string().contains("\"timestamp\"")
    }
}
