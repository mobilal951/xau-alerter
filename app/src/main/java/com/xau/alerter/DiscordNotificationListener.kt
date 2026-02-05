package com.xau.alerter

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DiscordNotificationListener : NotificationListenerService() {

    companion object {
        private const val DISCORD_PACKAGE = "com.discord"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Only react to Discord notifications
        if (sbn.packageName != DISCORD_PACKAGE) return

        // Check if monitoring is enabled
        if (!PrefsManager.isEnabled(this)) return

        // Skip group summaries (bundled notification headers)
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Don't trigger if alarm is already active
        if (AlarmPlayer.isPlaying) return

        // Extract all text fields from the notification
        val extras = sbn.notification.extras
        val allText = buildString {
            appendField(extras.getCharSequence(Notification.EXTRA_TITLE))
            appendField(extras.getCharSequence(Notification.EXTRA_TEXT))
            appendField(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            appendField(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            appendField(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
            appendField(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
            appendField(sbn.notification.tickerText)
        }

        if (allText.isBlank()) return

        val allTextLower = allText.lowercase()

        // Check if any target channel name is present
        val channels = PrefsManager.getChannels(this)
        val matchedChannel = channels.firstOrNull { channel ->
            allTextLower.contains(channel.lowercase())
        } ?: return

        // If keywords are configured, at least one must also be present
        val keywords = PrefsManager.getKeywords(this)
        if (keywords.isNotEmpty()) {
            val hasKeyword = keywords.any { keyword ->
                allTextLower.contains(keyword.lowercase())
            }
            if (!hasKeyword) return
        }

        // TRIGGER ALARM
        AlarmPlayer.start(this, matchedChannel, allText.trim().take(300))
    }

    private fun StringBuilder.appendField(cs: CharSequence?) {
        cs?.let {
            append(it)
            append(" ")
        }
    }
}
