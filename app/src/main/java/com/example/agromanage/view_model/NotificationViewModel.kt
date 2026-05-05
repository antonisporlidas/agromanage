package com.example.agromanage.view_model

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// 1. Το "Μοντέλο" της ειδοποίησης (Μεταφέρθηκε εδώ)


class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    data class AppNotification(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val message: String,
        var isRead: Boolean = false
    )
    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ==========================================
    // --- STATE FLOWS & ΛΙΣΤΑ ΕΙΔΟΠΟΙΗΣΕΩΝ ---
    // ==========================================
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    // 1. Φτιάχνουμε τον "κατάσκοπο" που κοιτάει τη μνήμη
    private val prefs = application.getSharedPreferences("AgroPrefs", Context.MODE_PRIVATE)
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // Παρακολουθούμε ΜΟΝΟ το κλειδί του τρέχοντος χρήστη
        if (key == NotificationHelper.savedNotificationsKey()) {
            loadNotifications()
        }
    }

    // Όταν αλλάξει ο συνδεδεμένος χρήστης (logout/login άλλου), φόρτωσε εκ νέου ώστε
    // να φύγουν οι ειδοποιήσεις του προηγούμενου χρήστη και να φανούν μόνο του νέου.
    private val authListener = FirebaseAuth.AuthStateListener {
        loadNotifications()
        subscribeToMyNotifications()
    }

    init {
        createNotificationChannel()
        loadNotifications()
        subscribeToMyNotifications()

        // 2. Ενεργοποιούμε τον "κατάσκοπο"
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        FirebaseAuth.getInstance().removeAuthStateListener(authListener)
    }

    // ==========================================
    // --- ANDROID SYSTEM NOTIFICATIONS ---
    // ==========================================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "market_channel",
                "Ειδοποιήσεις Αγοράς & Καιρού",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showSystemNotification(title: String, message: String) {
        val manager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(getApplication(), "market_channel")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Προεπιλεγμένο εικονίδιο
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ==========================================
    // --- IN-APP NOTIFICATIONS (ΣΥΡΤΑΡΙ) ---
    // ==========================================
    fun addNotification(title: String, message: String) {
        val newNotif = AppNotification(title = title, message = message)
        _notifications.value = listOf(newNotif) + _notifications.value
        saveNotifications()
    }

    fun markNotificationsAsRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        saveNotifications()
    }

    fun deleteNotification(notificationId: String) {
        _notifications.value = _notifications.value.filter { it.id != notificationId }
        saveNotifications()
    }

    // ==========================================
    // --- LOCAL STORAGE (SharedPreferences) ---
    // ==========================================
    private fun saveNotifications() {
        val key = NotificationHelper.savedNotificationsKey()
        val jsonArray = JSONArray()

        _notifications.value.forEach { notif ->
            val jsonObj = JSONObject().apply {
                put("id", notif.id)
                put("title", notif.title)
                put("message", notif.message)
                put("isRead", notif.isRead)
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }

    private fun loadNotifications() {
        val key = NotificationHelper.savedNotificationsKey()
        val jsonString = prefs.getString(key, null)

        if (jsonString == null) {
            // Αν δεν υπάρχουν αποθηκευμένα για τον τρέχοντα χρήστη, καθάρισε το state
            _notifications.value = emptyList()
            return
        }

        try {
            val jsonArray = JSONArray(jsonString)
            val loadedList = mutableListOf<AppNotification>()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                loadedList.add(
                    AppNotification(
                        id = jsonObj.getString("id"),
                        title = jsonObj.getString("title"),
                        message = jsonObj.getString("message"),
                        isRead = jsonObj.getBoolean("isRead")
                    )
                )
            }
            _notifications.value = loadedList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==========================================
    // --- FIREBASE CLOUD MESSAGING (FCM) ---
    // ==========================================
    fun subscribeToMyNotifications() {
        val uid = currentUserId
        val lastSubscribedUid = prefs.getString("subscribed_notification_uid", null)

        // Αν είχαμε εγγραφεί παλιότερα σε άλλον χρήστη (logout/αλλαγή λογαριασμού),
        // ξεγραφόμαστε από το παλιό topic ώστε η συσκευή να μη λαμβάνει τις
        // ειδοποιήσεις του προηγούμενου χρήστη.
        if (!lastSubscribedUid.isNullOrEmpty() && lastSubscribedUid != uid) {
            val oldTopic = "user_$lastSubscribedUid"
            FirebaseMessaging.getInstance().unsubscribeFromTopic(oldTopic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Καθαρίζουμε το pref ΜΟΝΟ μετά από επιτυχή unsubscribe και
                        // μόνο αν δεν έχει ήδη γραφτεί νέος uid (προστασία από race
                        // με το subscribe block παρακάτω).
                        val current = prefs.getString("subscribed_notification_uid", null)
                        if (current == lastSubscribedUid) {
                            prefs.edit().remove("subscribed_notification_uid").apply()
                        }
                        println("Επιτυχής αποσυνδρομή από το topic: $oldTopic")
                    } else {
                        println("Αποτυχία αποσυνδρομής από το topic: $oldTopic")
                    }
                }
        }

        if (uid.isNotEmpty()) {
            val topicName = "user_$uid"

            FirebaseMessaging.getInstance().subscribeToTopic(topicName)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        prefs.edit().putString("subscribed_notification_uid", uid).apply()
                        println("Επιτυχής εγγραφή στις ειδοποιήσεις για το topic: $topicName")
                    } else {
                        println("Αποτυχία εγγραφής στις ειδοποιήσεις.")
                    }
                }
        }
    }
}