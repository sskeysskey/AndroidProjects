package com.xiaoyan.spacewar

import android.graphics.Bitmap
import android.graphics.Canvas

class Explosion(private val bitmap: Bitmap, private var x: Float, private var y: Float) {
    private var visibleDuration = 0
    val isVisible: Boolean
        get() = visibleDuration < 5

    fun draw(canvas: Canvas) {
        if (isVisible) {
            canvas.drawBitmap(bitmap, x, y, null)
            visibleDuration++
        }
    }
}