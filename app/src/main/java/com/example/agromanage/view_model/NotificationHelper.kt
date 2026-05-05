package com.example.agromanage.view_model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Static helper για ειδοποιήσεις χωρίς να φτιάχνεις νέο NotificationViewModel.
 * Γράφει απευθείας στα SharedPreferences με κλειδί ΑΝΑ ΧΡΗΣΤΗ ώστε ο κάθε
 * λογαριασμός να βλέπει μόνο τις δικές του ειδοποιήσεις.
 */
object NotificationHelper {

    /** Επιστρέφει το κλειδί ειδοποιήσεων για τον τρέχοντα χρήστη (ή κενό αν κανείς δεν είναι συνδεδεμένος). */
    fun savedNotificationsKey(uid: String? = null): String {
        val u = uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: ""
        return if (u.isEmpty()) "saved_notifications_anonymous" else "saved_notifications_$u"
    }

    fun showSystemNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "market_channel",
                "Ειδοποιήσεις Αγοράς & Καιρού",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "market_channel")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun addNotificationToHistory(context: Context, title: String, message: String) {
        val prefs = context.getSharedPreferences("AgroPrefs", Context.MODE_PRIVATE)
        val key = savedNotificationsKey()
        val jsonString = prefs.getString(key, "[]")

        try {
            val oldArray = JSONArray(jsonString)
            val newNotif = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("title", title)
                put("message", message)
                put("isRead", false)
            }

            val newArray = JSONArray()
            newArray.put(newNotif)
            for (i in 0 until oldArray.length()) {
                newArray.put(oldArray.getJSONObject(i))
            }

            prefs.edit().putString(key, newArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}