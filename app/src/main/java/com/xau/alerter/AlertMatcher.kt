package com.xau.alerter

import android.content.Context

object AlertMatcher {

    data class MatchResult(val channel: String, val text: String)

    /**
     * Returns a MatchResult if the text matches a configured channel (and keyword, if any).
     * Returns null if no match.
     */
    fun match(context: Context, text: String): MatchResult? {
        if (text.isBlank()) return null

        val textLower = text.lowercase()

        val channels = PrefsManager.getChannels(context)
        val matchedChannel = channels.firstOrNull { channel ->
            textLower.contains(channel.lowercase())
        } ?: return null

        val keywords = PrefsManager.getKeywords(context)
        if (keywords.isNotEmpty()) {
            val hasKeyword = keywords.any { keyword ->
                textLower.contains(keyword.lowercase())
            }
            if (!hasKeyword) return null
        }

        return MatchResult(matchedChannel, text.trim().take(300))
    }
}
