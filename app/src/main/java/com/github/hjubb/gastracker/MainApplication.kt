package com.github.hjubb.gastracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.*
import com.github.hjubb.gastracker.MainApplication.Companion.GAS_PRICE
import com.github.hjubb.gastracker.MainApplication.Companion.gasPrice
import com.github.hjubb.gastracker.MainApplication.Companion.prefs
import com.github.hjubb.gastracker.MainApplication.Companion.setLastGas
import com.github.hjubb.gastracker.MainApplication.Companion.shouldNotify
import com.github.kittinunf.fuel.core.await
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.serialization.kotlinxDeserializerOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration


class MainApplication : Application() {
    companion object {
        val Context.prefs: DataStore<Preferences> by preferencesDataStore(name = "settings")

        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("App_NOTIFICATIONS_ENABLED")
        internal val GAS_PRICE = intPreferencesKey("App_GAS_PRICE")
        private val LAST_GAS = intPreferencesKey("App_LAST_GAS")
        private val LAST_UPDATE = longPreferencesKey("App_LAST_UPDATE")
        private val HAS_NOTIFIED = booleanPreferencesKey("App_HAS_NOTIFIED")
        private val RECENT_GAS_VALUES_STRING = stringPreferencesKey("App_RECENT_GAS_VALUES_STRING")

        const val DEFAULT_GAS = 70
        const val MAX_GAS = 500f

        fun Preferences.notificationsEnabled() = get(NOTIFICATIONS_ENABLED) ?: false
        private fun Preferences.hasNotified() = get(HAS_NOTIFIED) ?: false
        fun Preferences.shouldNotify() = notificationsEnabled() && !hasNotified()
        fun Preferences.gasPrice() = get(GAS_PRICE) ?: DEFAULT_GAS
        fun Preferences.lastGas() = get(LAST_GAS) ?: -1
        fun Preferences.lastUpdate() = get(LAST_UPDATE) ?: System.currentTimeMillis()
        fun Preferences.recentGasValues(): List<Int> =
            get(RECENT_GAS_VALUES_STRING)?.split(',')?.map { it.toInt() } ?: List(50) { 0 }

        fun MutablePreferences.setGasPrice(value: Int) {
            set(GAS_PRICE, value)
            set(HAS_NOTIFIED, false)
        }

        fun MutablePreferences.setIsNotifEnabled(value: Boolean) {
            set(NOTIFICATIONS_ENABLED, value)
            set(HAS_NOTIFIED, false)
        }

        fun MutablePreferences.setLastGas(value: Int, recent: List<Int>) {
            set(RECENT_GAS_VALUES_STRING, recent.reversed().joinToString(",") { it.toString() })
            set(LAST_GAS, value)
            set(LAST_UPDATE, System.currentTimeMillis())
            // intentionally don't call set(HAS_NOTIFIED, true) here so you continue getting notified every 15 minutes while gas is cheap
        }

    }

    override fun onCreate() {
        super.onCreate()
        val requests = PeriodicWorkRequestBuilder<PeriodicUpdate>(Duration.ofMinutes(15))
            .setInitialDelay(Duration.ZERO)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

        val request = OneTimeWorkRequestBuilder<PeriodicUpdate>()
            .setInitialDelay(Duration.ZERO)
            .build()

        val manager = WorkManager.getInstance(this)

        manager.enqueueUniqueWork("foreground", ExistingWorkPolicy.KEEP, request)

        manager.enqueueUniquePeriodicWork("background", ExistingPeriodicWorkPolicy.KEEP, requests)
    }
}

const val GET_URL =
    "https://gas.ibolpap.finance/gas_price"

const val CHANNEL_ID = "App_CHANNEL_ID"

class PeriodicUpdate(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {

    @Serializable
    data class Response(val average: Int, val recent: List<Int>)

    private fun notifyGasLower(latest: Int) {
        val name = "Notifications"
        val descriptionText = "Gas Alert Notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            channel
        )
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dispenser)
            .setContentTitle("Gas Tracker Alert")
            .setContentText("Right now gas is $latest, which is below your alert amount!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        with(NotificationManagerCompat.from(applicationContext)) {
            notify(0, builder.build())
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val json = Json {
                this.ignoreUnknownKeys = true
            }
            val response = GET_URL.httpGet().await(kotlinxDeserializerOf<Response>(json = json))
            val latest = (response.average / 10) // responses are 10x as eth gas station returns
            applicationContext.prefs.edit { prefs ->
                prefs.setLastGas(latest, response.recent.map { it / 10 })
                if (prefs.contains(GAS_PRICE) && prefs.shouldNotify() && prefs.gasPrice() > latest) {
                    notifyGasLower(latest)
                }
            }

            Result.success()
        } catch (exception: Exception) {
            Log.e("GasTracker", "Failed to get live gas values", exception)
            Result.failure()
        }
        Result.success()
    }

}
