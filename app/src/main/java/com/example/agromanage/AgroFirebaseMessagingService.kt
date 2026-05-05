package com.example.agromanage

import com.example.agromanage.view_model.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AgroFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data["title"]
            ?: message.notification?.title
            ?: return
        val body = message.data["body"]
            ?: message.notification?.body
            ?: ""

        NotificationHelper.showSystemNotification(applicationContext, title, body)
        NotificationHelper.addNotificationToHistory(applicationContext, title, body)
    }
}