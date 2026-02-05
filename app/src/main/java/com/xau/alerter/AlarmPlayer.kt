package com.xau.alerter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationCompat

object AlarmPlayer {

    private const val CHANNEL_ID = "xau_alarm_channel"
    private const val NOTIFICATION_ID = 1001

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    var isPlaying = false
        private set

    var triggerChannel: String = ""
        private set

    var triggerText: String = ""
        private set

    fun start(context: Context, channelName: String, notifText: String) {
        if (isPlaying) return

        triggerChannel = channelName
        triggerText = notifText
        isPlaying = true

        val soundUri = PrefsManager.getAlarmUri(context)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Force alarm volume to max
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        // Start alarm sound
        try {
            startMediaPlayer(context, soundUri)
        } catch (e: Exception) {
            // Fallback to default alarm sound
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                startMediaPlayer(context, fallbackUri)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }

        // Acquire partial wake lock to keep CPU alive
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "XauAlerter:AlarmWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 min safety timeout
        }

        // Post notification with full-screen intent to wake screen and show AlarmActivity
        showAlarmNotification(context, channelName, notifText)
    }

    fun stop(context: Context) {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {}
            it.release()
        }
        mediaPlayer = null

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)

        isPlaying = false
    }

    private fun startMediaPlayer(context: Context, uri: Uri) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(context, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun showAlarmNotification(context: Context, channelName: String, notifText: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create high-importance notification channel
        val channel = NotificationChannel(
            CHANNEL_ID,
            "XAU Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm for Discord channel alerts"
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)

        // Intent for AlarmActivity
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("channel", channelName)
            putExtra("text", notifText)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("XAU ALERT!")
            .setContentText("Channel: #$channelName")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }
}
