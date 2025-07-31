package com.xiaoyan.spacewar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.content.res.AppCompatResources
import kotlin.random.Random

class Enemy(private val context: Context, val bitmap: Bitmap, var x: Float, var y: Float, var health: Int, private val type: Int) {
    private val speed = 4
    private val horizontalSpeed = 2
    val enemybullets = mutableListOf<EnemyBullet>()

    fun getEnemyBullets(): List<EnemyBullet> {
        return enemybullets.toList()
    }

    val boundingBox: RectF
        get() = RectF(x, y, x + bitmap.width, y + bitmap.height)

    fun update(playerX: Float, playerY: Float) {
        when (type) {
            1 -> y += speed
            2 -> {
                x += horizontalSpeed
                y += speed / 2f
            }
            3 -> {
                x += speed / 2f
                y += speed
            }
            4 -> {
                x -= horizontalSpeed
                y += speed / 2f
            }
            5 -> {
                x -= speed / 2f
                y += speed
            }
        }
        enemybullets.forEach { it.update() }

        // 敌机有一定的几率发射新的子弹11.30
        if (Random.nextInt(1000) < 2) {
            enemybullets.add(createEnemyBullet(playerX, playerY))
        }
    }

    // 创建新的子弹
    private fun createEnemyBullet(playerX: Float, playerY: Float): EnemyBullet {
        var enemybulletBitmap = (AppCompatResources.getDrawable(context, R.drawable.bulletenemy) as BitmapDrawable).bitmap
        enemybulletBitmap = Bitmap.createScaledBitmap(enemybulletBitmap, enemybulletBitmap.width / 2, enemybulletBitmap.height / 2, true)

        val enemybulletX = x + bitmap.width / 2f - enemybulletBitmap.width / 2f
        val enemybulletY = y + bitmap.height
        return EnemyBullet(enemybulletBitmap, enemybulletX, enemybulletY, playerX, playerY)
    }

    fun drawEnemyBullet(canvas: Canvas) {
        canvas.drawBitmap(bitmap, x, y, null)
        enemybullets.forEach { it.draw(canvas) }
    }
}