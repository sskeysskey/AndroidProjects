package com.xiaoyan.spacewar

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.content.res.AppCompatResources
import java.util.Timer
import java.util.TimerTask

class EnemyManager(private val context: Context, private val width: Int, private val height: Int) {
    val enemies = mutableListOf<Enemy>()
    var waveInProgress = false

    fun createEnemy(enemyType: Int): Enemy {
        val enemyResourceId = when (enemyType) {
            1 -> R.drawable.enemy1
            2 -> R.drawable.enemy2
            3 -> R.drawable.enemy3
            4 -> R.drawable.enemy4
            else -> R.drawable.enemy1
        }
        val enemyHealth = when (enemyType) {
            1 -> 8
            2 -> 9
            3 -> 10
            4 -> 10
            else -> 10
        }
        val enemyBitmap = (AppCompatResources.getDrawable(context, enemyResourceId) as BitmapDrawable).bitmap
        val x = when (enemyType) {
            2 -> -enemyBitmap.width.toFloat()
            4 -> width.toFloat()
            5 -> width.toFloat()
            else -> (0..width - enemyBitmap.width).random().toFloat()
        }
        val y = when (enemyType) {
            2, 4 -> height / 4f - enemyBitmap.height / 2f
            5 -> -enemyBitmap.height.toFloat()
            else -> -enemyBitmap.height.toFloat()
        }
        return Enemy(context, enemyBitmap, x, y, enemyHealth, enemyType)
    }

    fun launchEnemyWave() {
        val enemyTypes = listOf(1, 2, 3, 4, 5)
        val delay = 500L

        for (i in 0 until 2) {
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    synchronized(enemies) {
                        val enemyType = enemyTypes.random()
                        enemies.add(createEnemy(enemyType))
                    }
                }
            }, delay * i)
        }
        waveInProgress = false
    }
}