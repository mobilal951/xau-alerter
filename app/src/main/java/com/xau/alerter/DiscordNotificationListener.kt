package com.xau.alerter

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DiscordNotificationListener : NotificationListenerService() {

    companion object {
        private const val DISCORD_PACKAGE = "com.discord"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != DISCORD_PACKAGE) return
        if (!PrefsManager.isEnabled(this)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (AlarmPlayer.isPlaying) return

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

        val result = AlertMatcher.match(this, allText) ?: return
        AlarmPlayer.start(this, result.channel, result.text)
    }

    private fun StringBuilder.appendField(cs: CharSequence?) {
        cs?.let {
            append(it)
            append(" ")
        }
    }
}
