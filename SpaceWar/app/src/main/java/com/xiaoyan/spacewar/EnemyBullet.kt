package com.xiaoyan.spacewar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.sqrt

class EnemyBullet(val bitmap: Bitmap, var x: Float, var y: Float, targetX: Float, targetY: Float) {
    private val speed = 6f
    private var deltaX = 0f
    private var deltaY = 0f

    val boundingBox: RectF
        get() = RectF(x, y, x + bitmap.width, y + bitmap.height)

    init {
        val diffX = targetX - x
        val diffY = targetY - y
        val length = sqrt(diffX * diffX + diffY * diffY)

        deltaX = diffX / length * speed
        deltaY = diffY / length * speed
    }

    fun update() {
        x += deltaX
        y += deltaY
    }

    fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, x, y, null)
    }
}