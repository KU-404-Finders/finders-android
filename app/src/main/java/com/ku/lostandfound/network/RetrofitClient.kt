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
import java.util.concurrent.CompletableFuture

sealed interface RefreshSessionResult {
    data object Success : RefreshSessionResult
    data object InvalidSession : RefreshSessionResult
    data object RetryableFailure : RefreshSessionResult
}

object RetrofitClient {
    private const val BASE_URL = "https://finders.hyungyu.dev/"

    private data class RefreshAttempt(
        val sessionGeneration: Long,
        val accessToken: String?,
        val result: CompletableFuture<String?>,
    )

    private val refreshLock = Any()
    private var activeRefresh: RefreshAttempt? = null

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("KUFindersOkHttp", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactHeader("Authorization")
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val originalRequest = chain.request()
        val token = TokenManager.accessToken
        val sessionGeneration = TokenManager.sessionGeneration()
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
            val newAccessToken = if (!TokenManager.isCurrentSession(sessionGeneration, token)) {
                null
            } else {
                // Share both success and failure with requests that encountered the same
                // expired credential while a reissue is already in progress.
                TokenManager.accessToken
                    ?.takeIf { it != token && TokenManager.isCurrentSession(sessionGeneration) }
                    ?: refreshTokensCoalesced(sessionGeneration, token)
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
        val sessionGeneration = TokenManager.sessionGeneration()
        val refreshToken = TokenManager.refreshToken ?: run {
            if (TokenManager.isCurrentSession(sessionGeneration)) TokenManager.expireSession()
            return RefreshSessionResult.InvalidSession
        }
        return try {
            val response = authApi.refreshToken(RefreshTokenRequest(refreshToken))
            if (!response.isSuccessful) {
                if (RefreshSessionPolicy.isConfirmedInvalid(response.code())) {
                    if (TokenManager.isCurrentSession(sessionGeneration)) TokenManager.expireSession()
                    RefreshSessionResult.InvalidSession
                } else {
                    RefreshSessionResult.RetryableFailure
                }
            } else {
                val body = response.body()
                val tokens = body?.data
                if (body?.success == true && tokens != null) {
                    if (TokenManager.saveRefreshedTokens(sessionGeneration, tokens.accessToken, tokens.refreshToken)) {
                        RefreshSessionResult.Success
                    } else {
                        RefreshSessionResult.InvalidSession
                    }
                } else {
                    // A syntactically incomplete successful response is not proof that the
                    // saved refresh token is invalid. Keep it so a later retry is possible.
                    RefreshSessionResult.RetryableFailure
                }
            }
        } catch (e: Exception) {
            NetworkLog.exception("refreshSession", e)
            RefreshSessionResult.RetryableFailure
        }
    }

    private fun refreshTokensBlocking(sessionGeneration: Long, accessToken: String?): String? {
        val refreshToken = TokenManager.refreshToken ?: run {
            // An access token without its refresh credential cannot recover from expiry.
            // Only clear when this is still the session that initiated the refresh.
            if (TokenManager.isCurrentSession(sessionGeneration, accessToken)) {
                TokenManager.expireSession()
            }
            return null
        }
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
                    if (RefreshSessionPolicy.isConfirmedInvalid(it.code)) {
                        if (TokenManager.isCurrentSession(sessionGeneration, accessToken)) TokenManager.expireSession()
                    }
                    return null
                }
                val body = it.body?.string().orEmpty()
                val parsed = Gson().fromJson(body, RefreshTokenResponse::class.java)
                val tokens = parsed.data
                if (parsed.success && tokens != null) {
                    tokens.accessToken.takeIf {
                        TokenManager.saveRefreshedTokens(sessionGeneration, it, tokens.refreshToken)
                    }
                } else {
                    // A malformed or incomplete success response is not a confirmed
                    // credential rejection. Preserve the session for a later retry.
                    null
                }
            }
        } catch (e: Exception) {
            NetworkLog.exception("refreshTokensBlocking", e)
            null
        }
    }

    private fun refreshTokensCoalesced(sessionGeneration: Long, accessToken: String?): String? {
        val attempt: RefreshAttempt
        val isOwner: Boolean

        synchronized(refreshLock) {
            val existing = activeRefresh
            if (
                existing != null &&
                existing.sessionGeneration == sessionGeneration &&
                existing.accessToken == accessToken
            ) {
                attempt = existing
                isOwner = false
            } else {
                attempt = RefreshAttempt(
                    sessionGeneration = sessionGeneration,
                    accessToken = accessToken,
                    result = CompletableFuture<String?>(),
                )
                activeRefresh = attempt
                isOwner = true
            }
        }

        if (!isOwner) {
            return try {
                attempt.result.get()
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            } catch (exception: Exception) {
                NetworkLog.exception("sharedRefresh", exception)
                null
            }
        }

        val refreshedToken = try {
            refreshTokensBlocking(sessionGeneration, accessToken)
        } catch (exception: Exception) {
            NetworkLog.exception("refreshTokensCoalesced", exception)
            null
        }
        attempt.result.complete(refreshedToken)
        synchronized(refreshLock) {
            if (activeRefresh === attempt) activeRefresh = null
        }
        return refreshedToken
    }

    private fun isAuthenticationFailure(response: okhttp3.Response): Boolean {
        // Spring Security uses this shape for an invalid/expired credential. Other 403s are
        // authorization failures and must be returned to the caller without ending the session.
        return response.code == 403 &&
                response.peekBody(2048).string().contains("\"timestamp\"")
    }
}
