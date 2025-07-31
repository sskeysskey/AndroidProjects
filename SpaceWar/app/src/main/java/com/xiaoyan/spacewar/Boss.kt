package com.xiaoyan.spacewar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.content.res.AppCompatResources
import kotlin.math.cos
import kotlin.math.sin

class Boss(private val context: Context, private val bitmap: Bitmap, var x: Float, var y: Float, private val screenWidth: Int, var health: Int, private val laserResId: Int, private val isBoss1: Boolean, private val bulletSpeed: Float, private val bulletTimerMax: Int) {
    private val width = bitmap.width
    private val height = bitmap.height
    private val speed = 3f
    private var movingRight = true
    val bosslaser = createBossLaser()
    private var isFiring = false
    private var fireTimer = 0
    private var bulletTimer = 0
    private val bullets = mutableListOf<BossBullet>()
    private val screenHeight = context.resources.displayMetrics.heightPixels
    private val bulletBitmap = (AppCompatResources.getDrawable(context, R.drawable.bossbullet) as BitmapDrawable).bitmap
    private var bulletAngle = 90.0 // 初始角度设置为90度
    private var isIncreasing = false // 初始方向设置为向左转

    // 添加 healthPaint 对象
    private val healthPaint = Paint().apply {
        textSize = 50f  // 根据你的需要调整文本大小
        color = Color.WHITE  // 你可以选择其他颜色来表示血量
    }

    val boundingBox: RectF
        get() = RectF(x, y, x + width, y + height)

    fun drawHealth(canvas: Canvas) {
        val healthText = health.toString()
        val textWidth = healthPaint.measureText(healthText)
        val textX = x + width - textWidth - 10f // 将血量放在Boss右下角，离Boss边缘10个像素
        val textY = y + height - 10f // 同上
        canvas.drawText(healthText, textX, textY, healthPaint)
    }

    fun update() {
        if (movingRight) {
            if (x + width + speed <= screenWidth) {
                x += speed
            } else {
                movingRight = false
            }
        } else {
            if (x - speed >= 0) {
                x -= speed
            } else {
                movingRight = true
            }
        }
        fireTimer++

        if (fireTimer > 2 * 60) {
            isFiring = !isFiring
            bosslaser.isActive = isFiring
            fireTimer = 0
        }

        if (isFiring) {
            bosslaser.x = this.x + this.width / 2 - bosslaser.width / 2
        }

        // 更新子弹
        bullets.forEach { it.update() }
        bullets.removeIf { bullet -> bullet.y > screenHeight }

        // 发射新的子弹
        bulletTimer++
        if (bulletTimer >= bulletTimerMax) {
            bulletTimer = 0

            if (isBoss1) {
                for (i in 0 until 10) {
                    val angle = Math.PI * i / 9.0 // 调整角度的范围在0到π弧度
                    val dx = -bulletSpeed * cos(angle).toFloat() // 注意这里交换了dx和dy的计算公式，并且取了负值
                    val dy = bulletSpeed * sin(angle).toFloat() // dy取绝对值
                    bullets.add(BossBullet(bulletBitmap, x + width / 2, y + height, dx, dy))
                }
            } else {
                val angle = Math.toRadians(bulletAngle)
                val dx = bulletSpeed * cos(angle).toFloat()
                val dy = bulletSpeed * sin(angle).toFloat()
                bullets.add(BossBullet(bulletBitmap, x + width / 2, y + height, dx, dy))

                if (isIncreasing) {
                    bulletAngle += 3
                    if (bulletAngle >= 180) {
                        isIncreasing = false
                    }
                } else {
                    bulletAngle -= 3
                    if (bulletAngle <= 0) {
                        isIncreasing = true
                    }
                }
            }
        }
    }
    private fun createBossLaser(): BossLaser {
        val bosslaserBitmap = (AppCompatResources.getDrawable(context, laserResId) as BitmapDrawable).bitmap
        val bosslaserX = x + bitmap.width / 2f - bosslaserBitmap.width / 2f
        val bosslaserY = y + bitmap.height
        val screenHeight = context.resources.displayMetrics.heightPixels
        return BossLaser(bosslaserBitmap, bosslaserX, bosslaserY, screenHeight)
    }

    fun draw(canvas: Canvas) {
        if (isFiring) {
            bosslaser.draw(canvas)
        }
        canvas.drawBitmap(bitmap, x, y, null)
        drawHealth(canvas)
        val bulletsCopy = ArrayList(bullets)
        bulletsCopy.forEach { it?.draw(canvas) }
    }
}

