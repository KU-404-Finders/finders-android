package com.ku.lostandfound.network

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    private val refreshClient = OkHttpClient()
    private val refreshLock = Any()
    private var activeRefresh: RefreshAttempt? = null

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val originalRequest = chain.request()
        val credentials = TokenManager.credentials()
        val sessionGeneration = credentials.generation
        val token = credentials.access
        val required = originalRequest.tag(RequestSession::class.java)
        if (required != null && required.generation != sessionGeneration && originalRequest.header("Authorization") == null) {
            throw java.io.IOException("Session changed")
        }
        val path = originalRequest.url.encodedPath

        val isAuthRequest =
            path.endsWith("/api/v1/auth/login") ||
                    path.endsWith("/api/v1/auth/join") ||
                    path.endsWith("/api/v1/auth/reissue") ||
                    path.endsWith("/api/v1/auth/email/send") ||
                    path.startsWith("/api/v1/auth/password-reset/")

        // 이 인터셉터가 인증 헤더를 추가한 요청만 자동 갱신 대상
        val shouldAttachToken =
            !token.isNullOrBlank() &&
                    originalRequest.header("Authorization") == null &&
                    !isAuthRequest

        val request = if (shouldAttachToken) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        if (
            shouldAttachToken &&
            isAuthenticationFailure(response) &&
            TokenManager.isCurrentSession(sessionGeneration)
        ) {
            val currentToken = TokenManager.accessToken

            val newAccessToken = when {
                currentToken.isNullOrBlank() -> null

                // 다른 요청이 이미 토큰을 갱신한 경우 재사용
                currentToken != token -> currentToken

                // 아직 같은 토큰이라면 갱신 요청 공유
                else -> refreshTokensCoalesced(
                    sessionGeneration,
                    token
                )
            }

            if (
                !newAccessToken.isNullOrBlank() &&
                TokenManager.isCurrentSession(
                    sessionGeneration,
                    newAccessToken
                )
            ) {
                response.close()

                val retryRequest = request.newBuilder()
                    .header(
                        "Authorization",
                        "Bearer $newAccessToken"
                    )
                    .build()

                return@Interceptor chain.proceed(retryRequest)
            }
        }

        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .callFactory { original ->
            // Capture ownership before OkHttp queues the request on a worker thread.
            val owner = original.tag(RequestSession::class.java) ?: RequestSession(TokenManager.sessionGeneration())
            okHttpClient.newCall(original.newBuilder().tag(RequestSession::class.java, owner).build())
        }
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
    val notificationApi: NotificationApiService = retrofit.create(NotificationApiService::class.java)

    val pushDeviceApi: PushDeviceApiService =
        retrofit.create(PushDeviceApiService::class.java)

    /** Used during app start only after an expired access token was detected. */
    suspend fun refreshSession(): RefreshSessionResult {
        val credentials = TokenManager.credentials()
        val refreshed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            refreshTokensCoalesced(credentials.generation, credentials.access)
        }
        val current = TokenManager.credentials()
        return when {
            current.access.isNullOrBlank() || current.refresh.isNullOrBlank() -> RefreshSessionResult.InvalidSession
            refreshed != null && current.access == refreshed -> RefreshSessionResult.Success
            (current.generation != credentials.generation || current.access != credentials.access) &&
                !TokenManager.isAccessTokenExpired() -> RefreshSessionResult.Success
            else -> RefreshSessionResult.RetryableFailure
        }
    }

    private fun refreshTokensBlocking(sessionGeneration: Long, accessToken: String?): String? {
        val credentials = TokenManager.credentials()
        if (credentials.generation != sessionGeneration || credentials.access != accessToken) return null
        val refreshToken = credentials.refresh ?: run {
            // An access token without its refresh credential cannot recover from expiry.
            // Only clear when this is still the session that initiated the refresh.
            TokenManager.expireSession(credentials)
            return null
        }
        return try {
            val refreshRequestJson = Gson().toJson(RefreshTokenRequest(refreshToken))
            val request = Request.Builder()
                .url("${BASE_URL}api/v1/auth/reissue")
                .post(refreshRequestJson.toRequestBody("application/json".toMediaType()))
                .build()
            val response = refreshClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    if (RefreshSessionPolicy.isConfirmedInvalid(it.code, if (it.code == 400) it.body?.string() else null)) {
                        TokenManager.expireSession(credentials)
                    }
                    return null
                }
                val body = it.body?.string().orEmpty()
                val parsed = Gson().fromJson(body, RefreshTokenResponse::class.java)
                val tokens = parsed.data
                if (parsed.success && tokens != null) {
                    tokens.accessToken.takeIf {
                        TokenManager.saveRefreshedTokens(credentials, it, tokens.refreshToken)
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
            val current = TokenManager.credentials()
            if (current.generation != sessionGeneration) return null
            if (current.access != accessToken) return current.access
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
