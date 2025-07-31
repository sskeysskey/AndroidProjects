package com.xiaoyan.tetriswar

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Rect
import android.widget.Button
import androidx.core.graphics.component1
import androidx.core.graphics.component2

class LevelActivity : AppCompatActivity() {
    private lateinit var currentLevel: ImageView
    private val levels = listOf(
        Rect(580, 1620, 854, 1894),
        Rect(180, 1520, 454, 1794),
        Rect(600, 1300, 874, 1574),
        Rect(700, 1000, 974, 1274),
        Rect(350, 800, 624, 1074),
        Rect(750, 600, 1024, 874),
        Rect(250, 400, 524, 674),
        Rect(250, 400, 524, 674)
        // 添加更多关卡坐标
    )
    private var currentLevelIndex = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.level_activity)

        currentLevelIndex = GameProgressUtils.getSavedLevelIndex(this)

        currentLevel = findViewById(R.id.current_level)
        updateCurrentLevelPosition()

        val resetButton: Button = findViewById(R.id.reset_button)
        resetButton.setOnClickListener {
            currentLevelIndex = 0
            GameProgressUtils.saveLevelIndex(this, currentLevelIndex)
            updateCurrentLevelPosition()
        }

        currentLevel.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.rawX.toInt() - v.left
                val y = event.rawY.toInt() - v.top

                val clickedLevelIndex = findClickedLevel(x, y)

                if (clickedLevelIndex != -1) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("currentLevelIndex", clickedLevelIndex)
                    startActivity(intent)
                }
            }
            true
        }
    }

    private fun findClickedLevel(x: Int, y: Int): Int {
        levels.forEachIndexed { index: Int, rect: Rect ->
            if (rect.contains(x, y)) {
                return index
            }
        }
        return -1
    }

    private fun updateCurrentLevelPosition() {
        val imageSize = 274
        currentLevel.layoutParams.width = imageSize
        currentLevel.layoutParams.height = imageSize
        currentLevel.requestLayout()

        val (x, y) = levels[currentLevelIndex]
        currentLevel.x = x.toFloat()
        currentLevel.y = y.toFloat()
    }
}
