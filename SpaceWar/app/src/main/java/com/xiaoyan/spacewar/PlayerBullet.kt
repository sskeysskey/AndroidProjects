package com.xiaoyan.spacewar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.content.res.AppCompatResources
import java.util.Timer
import java.util.TimerTask

class PlayerBullet(val bitmap: Bitmap, private var x: Float, var y: Float, private val speedX: Float = 0f) {
    private val speedY = 20f // 子弹发射的速度
    private val width = bitmap.width
    private val height = bitmap.height

    val boundingBox: RectF
        get() = RectF(x, y, x + width, y + height)

    fun update() {
        y -= speedY
        x += speedX // 更新x坐标
    }

    fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, x, y, null)
    }
}

class BulletManager(context: Context, private val gameView: GameView) {
    val playerbullets = mutableListOf<PlayerBullet>()
    private val playerbulletTimer = Timer()

    private val playerbulletBitmap: Bitmap = run {
        val originalplayerBulletBitmap = (AppCompatResources.getDrawable(context,
            R.drawable.bulletplayer
        ) as BitmapDrawable).bitmap
        val scaleFactor = 0.5f // 子弹的尺寸和大小
        val newWidth = (originalplayerBulletBitmap.width * scaleFactor).toInt()
        val newHeight = (originalplayerBulletBitmap.height * scaleFactor).toInt()
        Bitmap.createScaledBitmap(originalplayerBulletBitmap, newWidth, newHeight, false)
    }

    init {
        playerbulletTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                gameView.player?.let {
                    val bulletY = it.y - playerbulletBitmap.height
                    synchronized(playerbullets) {
                        when (it.powerUpLevel) {
                            0 -> {
                                // 如果火力等级为 0，只发射一颗子弹
                                val bulletX = it.x + it.normalBitmap.width / 2f - playerbulletBitmap.width / 2f
                                val bullet = PlayerBullet(playerbulletBitmap, bulletX, bulletY)
                                playerbullets.add(bullet)
                            }
                            1 -> {
                                // 如果火力等级为 1，发射两颗子弹
                                val bulletX1 = it.x + it.normalBitmap.width * 0.25f - playerbulletBitmap.width / 2f
                                val bulletX2 = it.x + it.normalBitmap.width * 0.75f - playerbulletBitmap.width / 2f
                                val bullet1 = PlayerBullet(playerbulletBitmap, bulletX1, bulletY)
                                val bullet2 = PlayerBullet(playerbulletBitmap, bulletX2, bulletY)
                                playerbullets.add(bullet1)
                                playerbullets.add(bullet2)
                            }
                            2 -> {
                                // 创建中间的子弹
                                val middleBulletX = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width / 2f
                                playerbullets.add(PlayerBullet(playerbulletBitmap, middleBulletX, bulletY))

                                // 创建向左和向右的子弹，它们的x轴速度为负值和正值
                                val leftBulletX = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width
                                val rightBulletX = it.x + it.normalBitmap.width * 0.5f
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX, bulletY, speedX = 5f))
                            }
                            3 -> {
                                val leftBulletX = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width
                                val rightBulletX = it.x + it.normalBitmap.width * 0.5f
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX, bulletY, speedX = 5f))

                                // 在中间位置创建两颗子弹，间隔更大
                                val middleBulletX1 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width - playerbulletBitmap.width / 2f
                                val middleBulletX2 = it.x + it.normalBitmap.width * 0.5f + playerbulletBitmap.width / 2f
                                playerbullets.add(PlayerBullet(playerbulletBitmap, middleBulletX1, bulletY))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, middleBulletX2, bulletY))
                            }
                            4 -> {
                                // 创建向左和向右的子弹，它们的x轴速度为负值和正值，间隔更大
                                val leftBulletX1 = it.x + it.normalBitmap.width * 0.5f - 4 * playerbulletBitmap.width
                                val rightBulletX1 = it.x + it.normalBitmap.width * 0.5f + 3 * playerbulletBitmap.width
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX1, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX1, bulletY, speedX = 5f))

                                // 在左右两侧各再增加一颗子弹，间隔更大
                                val leftBulletX2 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width - playerbulletBitmap.width / 2f
                                val rightBulletX2 = it.x + it.normalBitmap.width * 0.5f + playerbulletBitmap.width + 4 * playerbulletBitmap.width
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX2, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX2, bulletY, speedX = 5f))

                                // 在中间位置创建两颗子弹，间隔更大
                                val middleBulletX1 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width - playerbulletBitmap.width / 2f
                                val middleBulletX2 = it.x + it.normalBitmap.width * 0.5f + playerbulletBitmap.width / 2f
                                playerbullets.add(PlayerBullet(playerbulletBitmap, middleBulletX1, bulletY))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, middleBulletX2, bulletY))
                            }

                            5 -> {
                                // 创建向左和向右的子弹，它们的x轴速度为负值和正值，间隔更大
                                val leftBulletX1 = it.x + it.normalBitmap.width * 0.5f - 4 * playerbulletBitmap.width
                                val rightBulletX1 = it.x + it.normalBitmap.width * 0.5f + 3 * playerbulletBitmap.width
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX1, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX1, bulletY, speedX = 5f))

                                // 在左右两侧各再增加一颗子弹，间隔更大
                                val leftBulletX2 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width - playerbulletBitmap.width / 2f
                                val rightBulletX2 = it.x + it.normalBitmap.width * 0.5f + playerbulletBitmap.width + 4 * playerbulletBitmap.width
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX2, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX2, bulletY, speedX = 5f))

                                // 在中间位置创建两颗子弹，间隔更大
                                val middleBulletX1 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width - playerbulletBitmap.width / 2f
                                val middleBulletX2 = it.x + it.normalBitmap.width * 0.5f + playerbulletBitmap.width / 2f
                                playerbullets.add(PlayerBullet(playerbulletBitmap, middleBulletX1, bulletY))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, middleBulletX2, bulletY))

                                // Creating additional left and right bullets
                                val leftBulletX3 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width - 9 * playerbulletBitmap.width
                                val rightBulletX3 = it.x + it.normalBitmap.width * 0.5f + 11 * playerbulletBitmap.width
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX3, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX3, bulletY, speedX = 5f))
                            }

                            6 -> {
                                // 创建向左和向右的子弹，它们的x轴速度为负值和正值，间隔更大
                                val leftBulletX1 = it.x + it.normalBitmap.width * 0.5f - 4 * playerbulletBitmap.width
                                val rightBulletX1 = it.x + it.normalBitmap.width * 0.5f + 3 * playerbulletBitmap.width
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX1, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX1, bulletY, speedX = 5f))

                                // 在左右两侧各再增加一颗子弹，间隔更大
                                val leftBulletX2 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width - playerbulletBitmap.width / 2f
                                val rightBulletX2 = it.x + it.normalBitmap.width * 0.5f + playerbulletBitmap.width + 4 * playerbulletBitmap.width
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX2, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX2, bulletY, speedX = 5f))

                                // 在中间位置创建两颗子弹，间隔更大
                                val middleBulletX1 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width - playerbulletBitmap.width / 2f
                                val middleBulletX2 = it.x + it.normalBitmap.width * 0.5f + playerbulletBitmap.width / 2f
                                playerbullets.add(PlayerBullet(playerbulletBitmap, middleBulletX1, bulletY))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, middleBulletX2, bulletY))

                                // Creating additional left and right bullets
                                val leftBulletX3 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width - 9 * playerbulletBitmap.width
                                val rightBulletX3 = it.x + it.normalBitmap.width * 0.5f + 11 * playerbulletBitmap.width
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX3, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX3, bulletY, speedX = 5f))

                                // Creating additional left and right bullets
                                val leftBulletX4 = it.x + it.normalBitmap.width * 0.5f - playerbulletBitmap.width -  11 * playerbulletBitmap.width
                                val rightBulletX4 = it.x + it.normalBitmap.width * 0.5f + 13 * playerbulletBitmap.width
                                playerbullets.add(PlayerBullet(playerbulletBitmap, leftBulletX4, bulletY, speedX = -5f))
                                playerbullets.add(PlayerBullet(playerbulletBitmap, rightBulletX4, bulletY, speedX = 5f))
                            }

                            else -> {
                                // 如果火力等级大于 6，打印一个错误消息
                                println("Invalid powerUpLevel: ${it.powerUpLevel}")
                            }
                        }
                    }
                }
            }
        }, 0, 200) // 每 多少 毫秒创建一颗子弹
    }

    fun updateBullets() {
        synchronized(playerbullets) {
            val iterator = playerbullets.iterator()
            while (iterator.hasNext()) {
                val bullet = iterator.next()
                bullet.update()
                // 如果子弹飞出屏幕，从列表中移除
                if (bullet.y + bullet.bitmap.height < 0) {
                    iterator.remove()
                }
            }
        }
    }

    fun drawBullets(canvas: Canvas) {
        synchronized(playerbullets) {
            playerbullets.forEach { it.draw(canvas) }
        }
    }

    fun stop() {
        playerbulletTimer.cancel()
        synchronized(playerbullets) {
            playerbullets.clear()
        }
    }

}
