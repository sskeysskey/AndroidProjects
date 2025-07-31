package com.xiaoyan.tetriswar

data class LevelCondition(
    val level: Int,
    val score: Int,
    val consecutiveThreeOrMoreLinesCleared: Int
)
