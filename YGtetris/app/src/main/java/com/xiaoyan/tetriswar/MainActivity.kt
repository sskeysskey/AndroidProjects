package com.xiaoyan.tetriswar

import android.media.SoundPool
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    //测试专用11.30
    companion object {
        lateinit var tetrisView: TetrisView
    }

    private var currentLevelIndex: Int = 0

    private lateinit var itemclear4lineImageView: ImageView //道具相关
    private lateinit var itemclear4lineCountTextView: TextView //道具相关
    private lateinit var resetscoreImageView: ImageView //重置功能
    private lateinit var leftImageView: ImageView
    private lateinit var rightImageView: ImageView
    private lateinit var downImageView: ImageView
    private lateinit var shiftImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val soundPool = SoundPool.Builder().setMaxStreams(4).build()
        currentLevelIndex = intent.getIntExtra("currentLevelIndex", 0)
        tetrisView = TetrisView(this, soundPool, ::levelCompletionHandler)
        val frameLayout = findViewById<FrameLayout>(R.id.frameLayout)
        frameLayout.addView(tetrisView)

        itemclear4lineImageView = findViewById(R.id.itemclear4line_imageview) // 设置道具图片
        itemclear4lineCountTextView = findViewById(R.id.itemclear4line_count_text_view) // 设置道具数量
        resetscoreImageView = findViewById(R.id.resetscore) // 设置清除分数功能
        leftImageView = findViewById(R.id.left_button)
        rightImageView = findViewById(R.id.right_button)
        downImageView = findViewById(R.id.down_button)
        shiftImageView = findViewById(R.id.shift_button)


        resetscoreImageView.setOnClickListener { //清除分数功能监听
            val gameState = TetrisGameState(tetrisView, soundPool)
            gameState.loadGameState(this)
            gameState.score = 0
            gameState.consecutiveThreeOrMoreLinesCleared = 0
            gameState.saveGameState(this)
        }

        itemclear4lineImageView.setOnClickListener { //道具监听
            // 假设当前选择的道具是第一个道具
//            val powerUpId = 1 //道具相关
//            tetrisView.gameState.powerUpStore.usePowerUp(powerUpId) //道具相关
            // 更新道具数量
            updatePowerUpInfo() //道具相关
        }

        updateGameStateLevel()
    }

    private fun levelCompletionHandler(levelCompleted: Boolean) {
        if (levelCompleted) {
            currentLevelIndex++
            GameProgressUtils.saveLevelIndex(this, currentLevelIndex)
        }
        val intent = Intent(this, LevelActivity::class.java)
        intent.putExtra("currentLevelIndex", currentLevelIndex)
        startActivity(intent)
        finish()
    }

    private fun updateGameStateLevel() {
        tetrisView.gameState.currentLevel = currentLevelIndex + 1
    }

    private fun updatePowerUpInfo() {
        // 假设当前选择的道具是第一个道具
//        val powerUpId = 1
//        val powerUp = tetrisView.gameState.powerUpStore.powerUps.find { it.id == powerUpId }
//        if (powerUp != null) {
//            powerUpImageView.setImageResource(powerUp.iconResId)
//            powerUpCountTextView.text = tetrisView.gameState.powerUpCounts.getValue(powerUpId).toString()
//        }
    }
}
