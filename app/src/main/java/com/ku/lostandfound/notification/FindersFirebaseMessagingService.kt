package com.ku.lostandfound.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ku.lostandfound.network.TokenManager

class FindersFirebaseMessagingService : FirebaseMessagingService() {

    override fun onRegistered(installationId: String) {
        super.onRegistered(installationId)

        PushDeviceManager.onFirebaseRegistered(
            context = applicationContext,
            installationId = installationId,
        )

        // 테스트용: 확인 후 삭제
        Log.d("FindersFCM", "TEST_FID=$installationId")
    }

    override fun onMessageReceived(
        remoteMessage: RemoteMessage
    ) {
        super.onMessageReceived(remoteMessage)

        TokenManager.init(applicationContext)

        // 로그아웃 상태에서는 직접 알림을 표시하지 않음
        if (!TokenManager.isLoggedIn()) return

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "찾을건대"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: return

        if (body.isBlank()) return

        Log.d(
            "FindersFCM",
            "푸시 알림 수신 완료"
        )

        val target = NotificationTarget.fromData(
            remoteMessage.data
        )

        FindersNotificationHelper.showNotification(
            context = applicationContext,
            title = title,
            body = body,
            target = target,
        )
    }
}