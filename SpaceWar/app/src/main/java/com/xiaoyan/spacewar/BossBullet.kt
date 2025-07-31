package com.xiaoyan.spacewar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF

class BossLaser(private val bitmap: Bitmap, var x: Float, var y: Float, private val screenHeight: Int) {
    val width = bitmap.width
    var isActive = false  // 新增的属性

    val boundingBox: RectF
        get() = RectF(x, y, x + width, y + screenHeight)

    fun draw(canvas: Canvas) {
        val bottomOfScreen = canvas.height.toFloat()
        canvas.drawBitmap(bitmap, null, RectF(x, y, x + width, bottomOfScreen), null)
    }
}

class BossBullet(private val bitmap: Bitmap, var x: Float, var y: Float, private var dx: Float, private var dy: Float) {
    private val width = bitmap.width
    private val height = bitmap.height

    val boundingBox: RectF
        get() = RectF(x, y, x + width, y + height)

    fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, x, y, null)
    }

    fun update() {
        x += dx
        y += dy
    }
}



