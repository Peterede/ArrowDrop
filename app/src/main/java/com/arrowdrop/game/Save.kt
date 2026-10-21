package com.arrowdrop.game

import android.content.Context

/** Tiny persistent store: the level the player is on. */
class Save(context: Context) {

    private val prefs = context.getSharedPreferences("arrowdrop", Context.MODE_PRIVATE)

    var level: Int
        get() = prefs.getInt(KEY_LEVEL, 1)
        set(value) = prefs.edit().putInt(KEY_LEVEL, value).apply()

    private companion object {
        const val KEY_LEVEL = "level"
    }
}
