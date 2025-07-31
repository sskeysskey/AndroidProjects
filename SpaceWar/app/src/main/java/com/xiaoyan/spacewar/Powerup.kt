package com.xiaoyan.spacewar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF

class PowerUp(private val bitmap: Bitmap, private var x: Float, private var y: Float, private val screenWidth: Int, private val screenHeight: Int) {
    private var dx = (1..5).random().toFloat() * if ((0..1).random() == 0) 1 else -1
    private var dy = (1..5).random().toFloat() * if ((0..1).random() == 0) 1 else -1
    private val width = bitmap.width
    private val height = bitmap.height

    val boundingBox: RectF
        get() = RectF(x, y, x + width, y + height)

    fun update() {
        x += dx
        y += dy

        // 检查是否触碰到屏幕边缘，如果是则反转方向
        if (x < 0 || x + width > screenWidth) dx = -dx
        if (y < 0 || y + height > screenHeight) dy = -dy
    }

    fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, x, y, null)
    }
}
