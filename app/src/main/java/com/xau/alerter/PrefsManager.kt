package com.xau.alerter

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

object PrefsManager {

    private const val PREFS_NAME = "xau_alerter_prefs"
    private const val KEY_ENABLED = "monitoring_enabled"
    private const val KEY_CHANNELS = "channels"
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_ALARM_URI = "alarm_uri"

    private const val DEFAULT_CHANNELS = "xau,assassin-xau,umrah-challenge"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun getChannels(context: Context): List<String> =
        prefs(context).getString(KEY_CHANNELS, DEFAULT_CHANNELS)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: listOf("xau", "assassin-xau", "umrah-challenge")

    fun setChannels(context: Context, channels: List<String>) {
        prefs(context).edit()
            .putString(KEY_CHANNELS, channels.joinToString(","))
            .apply()
    }

    fun getChannelsRaw(context: Context): String =
        prefs(context).getString(KEY_CHANNELS, DEFAULT_CHANNELS) ?: DEFAULT_CHANNELS

    fun getKeywords(context: Context): List<String> =
        prefs(context).getString(KEY_KEYWORDS, "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun setKeywords(context: Context, keywords: String) {
        prefs(context).edit().putString(KEY_KEYWORDS, keywords).apply()
    }

    fun getKeywordsRaw(context: Context): String =
        prefs(context).getString(KEY_KEYWORDS, "") ?: ""

    fun getAlarmUri(context: Context): Uri? {
        val uriStr = prefs(context).getString(KEY_ALARM_URI, null)
        return uriStr?.let { Uri.parse(it) }
    }

    fun setAlarmUri(context: Context, uri: Uri?) {
        prefs(context).edit().putString(KEY_ALARM_URI, uri?.toString()).apply()
    }
}
