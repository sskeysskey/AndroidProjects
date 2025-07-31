package com.xiaoyan.tetriswar

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException


class LevelConditionLoader(private val context: Context) {
    fun loadLevelConditions(): List<LevelCondition> {
        val json: String

        try {
            val inputStream = context.assets.open("level_conditions.json")
            json = inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            e.printStackTrace()
            return emptyList()
        }

        val levelConditionsType = object : TypeToken<List<LevelCondition>>() {}.type
        return Gson().fromJson(json, levelConditionsType)
    }
}
