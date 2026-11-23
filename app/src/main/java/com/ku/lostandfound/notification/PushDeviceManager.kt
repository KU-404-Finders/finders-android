package com.ku.lostandfound.notification

import android.content.Context
import android.util.Log
import com.ku.lostandfound.network.PushDeviceRequest
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.core.content.edit

object PushDeviceManager {

    private const val PREFS_NAME = "push_registration"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val KEY_REGISTERED_ID = "registered_installation_id"

    @Volatile private var appContext: Context? = null

    private val installationCallbackId = java.util.concurrent.atomic.AtomicLong()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val registration by lazy {
        PushRegistration(scope, RetrofitClient.pushDeviceApi,
            getRegisteredId = { appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.getString(KEY_REGISTERED_ID, null) },
            saveRegisteredId = { id -> appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit { putString(KEY_REGISTERED_ID, id) } },
            getInstallationId = ::getInstallationId)
    }

    // 앱 실행 시 초기화
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // FCM 등록 완료 시 SDK가 제공하는 FID만 서버에 동기화한다.
    fun onRegistered(
        context: Context,
        installationId: String,
    ) {
        if (installationId.isBlank()) return
        val callbackId = installationCallbackId.incrementAndGet()

        init(context)
        TokenManager.init(context.applicationContext)

        val preferences = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        synchronized(TokenManager) {
            if (callbackId != installationCallbackId.get()) return
            preferences.edit { putString(KEY_INSTALLATION_ID, installationId) }
            syncIfLoggedIn()
        }
    }

    private fun getInstallationId(): String? {
        val context = appContext ?: return null

        return context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(KEY_INSTALLATION_ID, null)
            ?.takeIf { it.isNotBlank() }
    }

    // 로그인 성공 후 호출
    fun onLoginSucceeded() = syncIfLoggedIn()

    fun syncIfLoggedIn() = registration.syncIfLoggedIn(TokenManager.sessionGeneration())

    fun resumeAfterFailedWithdrawal(generation: Long) = registration.reopen(generation)

    suspend fun unregisterBeforeLogout(authorization: String, expectedGeneration: Long) =
        registration.unregisterBeforeLogout(authorization, expectedGeneration)
}

internal class PushRegistration(
    private val scope: CoroutineScope,
    private val api: com.ku.lostandfound.network.PushDeviceApiService,
    private val getRegisteredId: () -> String? = { null },
    private val saveRegisteredId: (String?) -> Unit = {},
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val getInstallationId: () -> String?,
) {
    private companion object {
        const val TAG = "FindersFCM"
        const val MAX_REGISTER_ATTEMPTS = 3
    }

    private val requestMutex = Mutex()
    // Ownership changes share the session monitor. Never suspend or acquire requestMutex under it.
    private data class Owner(val generation: Long, val installationId: String)
    private var registrationJob: Job? = null
    private var active: Owner? = null
    private var registered: Owner? = null
    private var closedGeneration: Long? = null

    fun syncIfLoggedIn(generation: Long) {
        synchronized(TokenManager) {
            if (!TokenManager.isCurrentSession(generation)) return
            if (closedGeneration == generation || !TokenManager.isLoggedIn()) return
            val owner = Owner(generation, getInstallationId() ?: return)
            if (registered == owner || (active == owner && registrationJob?.isActive == true)) return
            registrationJob?.cancel()
            active = owner
            registered = null
            registrationJob = scope.launch { register(owner) }
        }
    }

    private fun owns(owner: Owner) = synchronized(TokenManager) {
        active === owner && closedGeneration != owner.generation && TokenManager.isCurrentSession(owner.generation)
    }

    private suspend fun register(owner: Owner) {
        requestMutex.withLock {
            repeat(MAX_REGISTER_ATTEMPTS) { index ->
                if (!owns(owner)) return@withLock
                val attempt = index + 1
                val retryable = try {
                    val response = api.registerDevice(
                        PushDeviceRequest(installationId = owner.installationId),
                        com.ku.lostandfound.network.RequestSession(owner.generation)
                    )

                    if (response.isSuccessful) {
                        synchronized(TokenManager) {
                            if (owns(owner)) {
                                registered = owner
                                saveRegisteredId(owner.installationId)
                            }
                        }
                        Log.d(TAG, "서버 기기 등록 성공")
                        return@withLock
                    }

                    Log.w(TAG, "서버 기기 등록 실패: ${response.code()} (attempt=$attempt)")
                    response.code() == 429 || response.code() in 500..599
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "서버 기기 등록 오류 (attempt=$attempt): ${e.javaClass.simpleName}")
                    true
                }

                if (!retryable || attempt == MAX_REGISTER_ATTEMPTS) return@withLock
                delay(1_000L shl index)
            }
        }
    }

    fun reopen(generation: Long) {
        synchronized(TokenManager) {
            if (closedGeneration != generation || !TokenManager.isCurrentSession(generation)) return
            closedGeneration = null
            syncIfLoggedIn(generation)
        }
    }

    // 로그아웃 전에 서버 등록 해제
    suspend fun unregisterBeforeLogout(authorization: String, expectedGeneration: Long) {
        val installationIds = synchronized(TokenManager) {
            if (!TokenManager.isCurrentSession(expectedGeneration)) return
            // Snapshot both owners before cancellation can discard the in-flight registration.
            val targetIds = listOfNotNull(registered?.installationId, getRegisteredId(), active?.installationId)
                .ifEmpty { listOfNotNull(getInstallationId()) }.distinct()
            closedGeneration = expectedGeneration
            registrationJob?.cancel()
            registrationJob = null
            active = null
            registered = null
            targetIds
        }

        val attempted = java.util.concurrent.atomic.AtomicInteger()
        val succeeded = java.util.concurrent.atomic.AtomicInteger()
        try {
            withContext(dispatcher) {
                requestMutex.withLock {
                    // Keep the registration barrier until ALL cleanup children finish or cancel.
                    // Independent retries let every ID start within the caller's shared deadline.
                    coroutineScope {
                        installationIds.map { installationId ->
                            async {
                                if (!TokenManager.isCurrentSession(expectedGeneration)) return@async
                                attempted.incrementAndGet()
                                if (unregisterId(authorization, expectedGeneration, installationId)) {
                                    succeeded.incrementAndGet()
                                }
                            }
                        }.awaitAll()
                    }
                }
            }
        } finally {
            val remaining = installationIds.size - succeeded.get()
            if (remaining > 0) {
                // Do not persist credentials or retry later using another account's credentials.
                Log.w(TAG, "기기 해제 미확인: remaining=$remaining, unattempted=${installationIds.size - attempted.get()}")
            }
        }
    }

    // Called under requestMutex, so a new session cannot register between cleanup requests.
    private suspend fun unregisterId(authorization: String, expectedGeneration: Long, installationId: String): Boolean {
        repeat(MAX_REGISTER_ATTEMPTS) { index ->
            if (!TokenManager.isCurrentSession(expectedGeneration)) return false
            val attempt = index + 1
            val retryable = try {
                val response = api.unregisterDevice(
                    authorization = authorization,
                    installationId = installationId
                )

                if (response.isSuccessful) {
                    synchronized(TokenManager) {
                        if (TokenManager.isCurrentSession(expectedGeneration) && getRegisteredId() == installationId) saveRegisteredId(null)
                    }
                    Log.d(TAG, "서버 기기 등록 해제 성공")
                    return true
                }

                Log.w(TAG, "서버 기기 등록 해제 실패: ${response.code()} (attempt=$attempt)")
                response.code() == 429 || response.code() in 500..599
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "서버 기기 등록 해제 오류 (attempt=$attempt): ${e.javaClass.simpleName}")
                true
            }

            if (!retryable || attempt == MAX_REGISTER_ATTEMPTS) return false
            delay(1_000L shl index)
        }
        return false
    }
}
