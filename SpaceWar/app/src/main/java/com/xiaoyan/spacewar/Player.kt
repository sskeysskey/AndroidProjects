package com.xiaoyan.spacewar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.content.res.AppCompatResources
import kotlin.math.sin

class Player(val normalBitmap: Bitmap, private val hitBitmap: Bitmap, private val screenWidth: Int, private val screenHeight: Int, context: Context) {
    internal var x: Float = (screenWidth - normalBitmap.width) / 2f
    internal var y: Float = screenHeight * 5 / 6f - normalBitmap.height / 2f

    var speed = 100f
    var powerUpLevel = 0

    private val width = normalBitmap.width
    private val height = normalBitmap.height

    private val healthUnit: Bitmap = (AppCompatResources.getDrawable(context, R.drawable.healthbar) as BitmapDrawable).bitmap
    private val lifeLogo: Bitmap = (AppCompatResources.getDrawable(context, R.drawable.lifelogo) as BitmapDrawable).bitmap

    private val initialHealth = 10
    var health: Int = initialHealth
    private val healthBarSpacing: Int = 10
    private val healthBarX: Int = healthBarSpacing + lifeLogo.width + healthBarSpacing
    private val healthBarY: Int = 50
    var isInvincible = false

    private var isHit = false
    private var hitStartTime: Long = 0
    private val hitDuration: Long = 2000 //  被击中后颤抖/无敌/模糊的毫秒数

    fun revive() {
        // 在这里复活玩家
        health = initialHealth
        hit()
        powerUpLevel = 4
    }

    val boundingBox: RectF
        get() = RectF(x, y, x + width, y + height)

    fun hit() {
        if (!isInvincible) {
            isHit = true
            isInvincible = true
            hitStartTime = System.currentTimeMillis()
        }
    }

    fun updateInvincibleState() {
        // Reset hit and invincible state after duration
        if (isHit && System.currentTimeMillis() - hitStartTime >= hitDuration) {
            isHit = false
            isInvincible = false
        }
    }

    fun update(dx: Float, dy: Float) {
        x += dx * speed
        y += dy * speed

        // 防止飞机飞出屏幕
        if (x < 0) x = 0f
        if (y < 0) y = 0f
        if (x + width > screenWidth) x = screenWidth - width.toFloat()
        if (y + height > screenHeight) y = screenHeight - height.toFloat()

        // Reset hit state after duration
        if (isHit && System.currentTimeMillis() - hitStartTime >= hitDuration) {
            isHit = false
            isInvincible = false
        }
    }

    fun draw(canvas: Canvas) {
        // Draw the player
        if (isHit) {
            // Draw with hit effect
            val dx = (sin(System.currentTimeMillis().toDouble() / 15) * 10).toFloat() // Increase shake frequency
            canvas.drawBitmap(hitBitmap, x + dx, y, null)
        } else {
            canvas.drawBitmap(normalBitmap, x, y, null)
        }

        // Draw Life logo to the left of the health bar
        val lifeLogoX = healthBarX - lifeLogo.width - healthBarSpacing
        val lifeLogoY = healthBarY + (healthUnit.height - lifeLogo.height) / 2  // centered vertically with the health bar
        canvas.drawBitmap(lifeLogo, lifeLogoX.toFloat(), lifeLogoY.toFloat(), null)

        // Draw HealthBar with spacing between units
        for (i in 0 until health ) {
            val dst = Rect(healthBarX + i * (healthUnit.width + healthBarSpacing), healthBarY, healthBarX + i * (healthUnit.width + healthBarSpacing) + healthUnit.width, healthBarY + healthUnit.height)
            canvas.drawBitmap(healthUnit, null, dst, null)
        }
    }
}