package com.mosquednd.whatsnew

import androidx.annotation.DrawableRes
import com.mosquednd.R

enum class ReleaseNoteCategory(val title: String, val icon: String) {
    WHATS_NEW("What's New", "🌟"),
    CHANGES("Changes Made", "🔄"),
    BUG_FIXES("Bug Fixes", "🐛"),
    IMPROVEMENTS("Improvements", "⚡")
}

data class WhatsNewItem(
    val category: ReleaseNoteCategory,
    val title: String,
    val description: String? = null
)

data class ReleaseNote(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val summary: String,
    val items: List<WhatsNewItem>
)
