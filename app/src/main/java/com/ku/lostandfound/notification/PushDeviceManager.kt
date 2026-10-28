package com.ku.lostandfound.notification

import android.content.Context
import android.util.Log
import com.ku.lostandfound.network.PushDeviceRequest
import com.ku.lostandfound.network.RetrofitClient
import com.ku.lostandfound.network.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object PushDeviceManager {

    private const val TAG = "FindersFCM"

    // 서버 API가 준비되고 계약이 일치하면 true로 변경
    private const val SERVER_SYNC_ENABLED = false

    private const val PREFS_NAME = "fcm_registration"
    private const val KEY_INSTALLATION_ID = "installation_id"

    private var appContext: Context? = null

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val requestMutex = Mutex()

    @Volatile
    private var sessionClosed = false

    // 앱 실행 시 초기화
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // Firebase 등록 완료 또는 식별자 변경 시 호출
    fun onFirebaseRegistered(
        context: Context,
        installationId: String,
    ) {
        if (installationId.isBlank()) return

        init(context)
        TokenManager.init(context.applicationContext)

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_INSTALLATION_ID, installationId)
            .apply()

        Log.d(TAG, "Firebase 기기 식별자 저장 완료")

        // 로그인된 상태라면 서버에 동기화
        syncIfLoggedIn()
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
    fun onLoginSucceeded() {
        sessionClosed = false
        syncIfLoggedIn()
    }

    // 서버에 현재 기기 등록
    fun syncIfLoggedIn() {
        if (!SERVER_SYNC_ENABLED || sessionClosed) return

        val installationId = getInstallationId() ?: return

        if (TokenManager.accessToken.isNullOrBlank()) return

        scope.launch {
            requestMutex.withLock {
                if (
                    sessionClosed ||
                    TokenManager.accessToken.isNullOrBlank()
                ) {
                    return@withLock
                }

                try {
                    val response =
                        RetrofitClient.pushDeviceApi.registerDevice(
                            PushDeviceRequest(
                                installationId = installationId
                            )
                        )

                    if (response.isSuccessful) {
                        Log.d(TAG, "서버 기기 등록 성공")
                    } else {
                        Log.w(
                            TAG,
                            "서버 기기 등록 실패: ${response.code()}"
                        )
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "서버 기기 등록 오류", e)
                }
            }
        }
    }

    // 로그아웃 전에 서버 등록 해제
    suspend fun unregisterBeforeLogout() {
        sessionClosed = true

        if (!SERVER_SYNC_ENABLED) return

        val installationId = getInstallationId() ?: return

        if (TokenManager.accessToken.isNullOrBlank()) return

        withContext(Dispatchers.IO) {
            requestMutex.withLock {
                try {
                    val response =
                        RetrofitClient.pushDeviceApi.unregisterDevice(
                            installationId = installationId
                        )

                    if (response.isSuccessful) {
                        Log.d(TAG, "서버 기기 등록 해제 성공")
                    } else {
                        Log.w(
                            TAG,
                            "서버 기기 등록 해제 실패: ${response.code()}"
                        )
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "서버 기기 등록 해제 오류", e)
                }
            }
        }
    }
}