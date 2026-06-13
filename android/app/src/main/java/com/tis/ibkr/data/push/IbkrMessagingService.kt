package com.tis.ibkr.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tis.ibkr.IbkrApp
import kotlinx.coroutines.launch

/** Receives FCM messages + token rotations. The backend pushes a "gateway needs
 *  2FA" reminder; we surface it as a local notification. */
class IbkrMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Token rotated — re-register with the backend (best effort; needs a
        // paired device, otherwise the authed call 401s and is swallowed).
        IbkrApp.instance.appScope.launch {
            runCatching { IbkrApp.instance.api.registerFcmToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notif = message.notification
        val title = notif?.title ?: message.data["title"] ?: "BullTap"
        val body = notif?.body ?: message.data["body"] ?: ""
        PushNotifications.show(this, title, body)
    }
}
