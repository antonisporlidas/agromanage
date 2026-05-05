package com.example.agromanage.view_model

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agromanage.data.local.FarmField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    // ==========================================
    // --- ΛΕΙΤΟΥΡΓΙΑ ΚΑΙΡΟΥ ΜΕ ΣΥΝΤΕΤΑΓΜΕΝΕΣ ---
    // ==========================================
    suspend fun getWeatherForCoordinates(lat: Double, lon: Double, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Ζητάμε τον καιρό ΒΑΣΕΙ ΣΥΝΤΕΤΑΓΜΕΝΩΝ (lat & lon)
                val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$apiKey&units=metric&lang=el"
                val request = Request.Builder().url(url).build()
                val client = OkHttpClient()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonObject = JSONObject(responseBody ?: "")

                    val temp = jsonObject.getJSONObject("main").getDouble("temp").toInt()
                    val description = jsonObject.getJSONArray("weather").getJSONObject(0).getString("description")

                    return@withContext "$temp°C, $description"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext "Μη διαθέσιμος (ελέγξτε τη σύνδεση)"
        }
    }

    // ==========================================
    // --- ΕΞΥΠΝΗ ΠΡΟΕΙΔΟΠΟΙΗΣΗ ΚΑΙΡΟΥ ---
    // ==========================================
    suspend fun checkWeatherAlerts(fields: List<FarmField>, apiKey: String) {
        withContext(Dispatchers.IO) {
            val prefs = getApplication<Application>().getSharedPreferences("AgroPrefs", Context.MODE_PRIVATE)
            val currentTime = System.currentTimeMillis()

            for (field in fields) {
                // Ελέγχουμε αν τον έχουμε ήδη ειδοποιήσει για ΑΥΤΟ το χωράφι τις τελευταίες 12 ώρες
                val lastAlertTime = prefs.getLong("weather_alert_${field.id}", 0L)
                if (currentTime - lastAlertTime < 12 * 60 * 60 * 1000) {
                    continue // Το προσπερνάμε για να μην τον πρήξουμε
                }

                try {
                    // Ζητάμε πρόβλεψη (forecast) για τα επόμενα δύο 3ωρα (cnt=2)
                    val url = "https://api.openweathermap.org/data/2.5/forecast?lat=${field.latitude}&lon=${field.longitude}&appid=$apiKey&units=metric&lang=el&cnt=2"
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

                                // Διαβάζουμε πότε χτυπήσαμε τελευταία φορά για αυτό το χωράφι
                                val lastAlertTime = prefs.getLong("weather_alert_${field.id}", 0L)
                                val currentTime = System.currentTimeMillis()

                                // Αν πέρασαν 24 ώρες (86.400.000 ms), κάνουμε ντιν ΚΑΙ το βάζουμε στο ιστορικό
                                if (currentTime - lastAlertTime > 24 * 60 * 60 * 1000) {

                                    val ctx = getApplication<Application>()
                                    NotificationHelper.showSystemNotification(ctx, titleStr, msgStr)
                                    NotificationHelper.addNotificationToHistory(ctx, titleStr, msgStr)

                                    // Κλειδώνουμε το χωράφι για τις επόμενες 24 ώρες
                                    prefs.edit().putLong("weather_alert_${field.id}", currentTime).apply()
                                }

                                break // Σταματάμε τον έλεγχο για τις υπόλοιπες ώρες αυτού του χωραφιού
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}