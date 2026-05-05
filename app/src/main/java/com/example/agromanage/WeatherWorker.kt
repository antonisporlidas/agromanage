package com.example.agromanage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.agromanage.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WeatherWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.success()

                val dbLocal = AppDatabase.getDatabase(applicationContext)
                val fields = dbLocal.farmDao().getAllFieldsForUser(uid)

                val myWeatherApiKey = BuildConfig.OPENWEATHER_API_KEY
                val prefs = applicationContext.getSharedPreferences("AgroPrefs", Context.MODE_PRIVATE)
                val currentTime = System.currentTimeMillis()

                for (field in fields) {
                    val lastAlertTime = prefs.getLong("weather_alert_${field.id}", 0L)

                    // Ελέγχουμε αν έχουν περάσει 24 ώρες (86.400.000 milliseconds)
                    if (currentTime - lastAlertTime <= 24 * 60 * 60 * 1000) {
                        println("Το ντιν για το '${field.name}' έχει ήδη χτυπήσει μέσα στο 24ωρο. Αγνόηση.")
                        continue // Προσπερνάμε το χωράφι
                    }

                    val url = "https://api.openweathermap.org/data/2.5/forecast?lat=${field.latitude}&lon=${field.longitude}&appid=$myWeatherApiKey&units=metric&lang=el&cnt=2"
                    val request = Request.Builder().url(url).build()
                    val client = OkHttpClient()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: continue
                        val jsonObject = JSONObject(responseBody)
                        val listArray = jsonObject.getJSONArray("list")

                        for (i in 0 until listArray.length()) {
                            val forecastObj = listArray.getJSONObject(i)
                            val weatherArray = forecastObj.getJSONArray("weather")
                            val weatherId = weatherArray.getJSONObject(0).getInt("id")
                            val desc = weatherArray.getJSONObject(0).getString("description")

                            // Κωδικοί OpenWeatherMap: 2xx (Καταιγίδα), 5xx (Βροχή), 6xx (Χιόνι)
                            if (weatherId in 200..699) {
                                val titleStr = "⚠️ Προσοχή: Επιδείνωση Καιρού!"
                                val msgStr = "Προβλέπεται $desc στο χωράφι '${field.name}' τις επόμενες ώρες."

                                // Καλούμε τις τοπικές συναρτήσεις αντί του ViewModel
                                showLocalNotification(titleStr, msgStr)
                                saveNotificationToHistory(applicationContext, titleStr, msgStr)

                                // Κλειδώνουμε το χωράφι για 24 ώρες
                                prefs.edit().putLong("weather_alert_${field.id}", currentTime).apply()

                                break // Βρήκαμε κακό καιρό, δεν χρειάζεται να ελέγξουμε τις επόμενες ώρες για ΑΥΤΟ το χωράφι
                            }
                        }
                    }
                }
                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.retry()
            }
        }
    }

    private fun showLocalNotification(title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("weather_channel", "Ειδοποιήσεις Καιρού", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, "weather_channel")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun saveNotificationToHistory(context: Context, title: String, message: String) {
        // Χρησιμοποιούμε το per-user κλειδί για να μην εμφανίζονται ειδοποιήσεις σε λάθος λογαριασμό
        com.example.agromanage.view_model.NotificationHelper.addNotificationToHistory(context, title, message)
    }
}