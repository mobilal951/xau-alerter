package com.xau.alerter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class NtfyService : Service() {

    companion object {
        private const val TAG = "NtfyService"
        private const val CHANNEL_ID = "ntfy_service_channel"
        private const val NOTIFICATION_ID = 2001
        private const val RECONNECT_BASE_MS = 2000L
        private const val RECONNECT_CAP_MS = 30000L
        private const val READ_TIMEOUT_MS = 90000
        private const val CONNECT_TIMEOUT_MS = 15000

        const val ACTION_STATUS = "com.xau.alerter.NTFY_STATUS"
        const val EXTRA_STATUS = "status"
        const val STATUS_CONNECTED = "connected"
        const val STATUS_CONNECTING = "connecting"
        const val STATUS_DISCONNECTED = "disconnected"
    }

    private var sseThread: Thread? = null
    @Volatile private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val server = PrefsManager.getNtfyServer(this)
        val topic = PrefsManager.getNtfyTopic(this)

        startForeground(NOTIFICATION_ID, buildNotification("Connecting to $topic..."))
        broadcastStatus(STATUS_CONNECTING)

        // Stop previous thread if restarted
        running = false
        sseThread?.interrupt()

        running = true
        sseThread = Thread { sseLoop(server, topic) }.apply {
            name = "ntfy-sse"
            isDaemon = true
            start()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        sseThread?.interrupt()
        sseThread = null
        releaseWakeLock()
        broadcastStatus(STATUS_DISCONNECTED)
        super.onDestroy()
    }

    private fun sseLoop(server: String, topic: String) {
        var backoff = RECONNECT_BASE_MS

        while (running) {
            var connection: HttpURLConnection? = null
            try {
                broadcastStatus(STATUS_CONNECTING)
                updateNotification("Connecting to $topic...")

                val url = URL("${server.trimEnd('/')}/$topic/sse")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "text/event-stream")
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doInput = true
                }

                connection.connect()

                if (connection.responseCode != 200) {
                    Log.w(TAG, "HTTP ${connection.responseCode}")
                    throw Exception("HTTP ${connection.responseCode}")
                }

                broadcastStatus(STATUS_CONNECTED)
                updateNotification("Connected to $topic")
                backoff = RECONNECT_BASE_MS // reset on successful connect

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String? = null

                while (running && reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (l.startsWith("data: ")) {
                        handleSseData(l.removePrefix("data: "))
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "SSE thread interrupted")
                break
            } catch (e: Exception) {
                Log.w(TAG, "SSE error: ${e.message}")
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
            }

            if (!running) break

            broadcastStatus(STATUS_DISCONNECTED)
            updateNotification("Reconnecting in ${backoff / 1000}s...")

            try {
                Thread.sleep(backoff)
            } catch (e: InterruptedException) {
                break
            }

            backoff = (backoff * 2).coerceAtMost(RECONNECT_CAP_MS)
        }
    }

    private fun handleSseData(json: String) {
        try {
            val obj = JSONObject(json)
            val event = obj.optString("event", "")
            if (event != "message") return

            val message = obj.optString("message", "")
            val title = obj.optString("title", "")
            val topic = obj.optString("topic", "")

            val fullText = buildString {
                if (title.isNotEmpty()) { append(title); append(" ") }
                if (topic.isNotEmpty()) { append(topic); append(" ") }
                append(message)
            }

            if (!PrefsManager.isEnabled(this)) return
            if (AlarmPlayer.isPlaying) return

            val result = AlertMatcher.match(this, fullText) ?: return
            AlarmPlayer.start(this, result.channel, result.text)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SSE data: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ntfy Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for ntfy SSE connection"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("XAU ntfy Monitor")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun broadcastStatus(status: String) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
        })
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "XauAlerter:NtfyWakeLock"
        ).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
