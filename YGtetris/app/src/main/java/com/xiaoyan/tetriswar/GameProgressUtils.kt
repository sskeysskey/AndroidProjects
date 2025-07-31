package com.xiaoyan.tetriswar

import android.content.Context

object GameProgressUtils {

    private const val GAME_PROGRESS_PREFS = "game_progress"
    private const val CURRENT_LEVEL_INDEX = "current_level_index"

    fun saveLevelIndex(context: Context, levelIndex: Int) {
        val sharedPreferences = context.getSharedPreferences(GAME_PROGRESS_PREFS, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putInt(CURRENT_LEVEL_INDEX, levelIndex)
            apply()
        }
    }

    fun getSavedLevelIndex(context: Context): Int {
        val sharedPreferences = context.getSharedPreferences(GAME_PROGRESS_PREFS, Context.MODE_PRIVATE)
        return sharedPreferences.getInt(CURRENT_LEVEL_INDEX, 0)
    }
}
