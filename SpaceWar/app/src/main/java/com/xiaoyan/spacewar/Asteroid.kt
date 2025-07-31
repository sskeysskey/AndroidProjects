package com.xiaoyan.spacewar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Color

class Asteroid(private val bitmap: Bitmap, var x: Float, var y: Float, var health: Int) {
    private val speed = 5f
    private val width = bitmap.width
    private val height = bitmap.height
    private var rotation = 0f
    private val matrix = Matrix()
    //以下为绘制陨石上血量显示功能
    private val healthPaint = Paint().apply {
        textSize = 40f  // 根据你的需要调整文本大小啊
        color = Color.WHITE  // 你可以选择其他颜色来表示血量
    }
    //绘制结束


    val boundingBox: RectF
        get() = RectF(x, y, x + width, y + height)

    fun update() {
        y += speed
        rotation = (rotation + 1) % 360 // 在每次 update 时更新旋转角度
    }

    fun draw(canvas: Canvas) {
        matrix.reset()
        matrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f) // move the bitmap to fix its pivot to center
        matrix.postRotate(rotation) // rotate the bitmap
        matrix.postTranslate(x + bitmap.width / 2f, y + bitmap.height / 2f) // lastly move the bitmap to its desired location
        canvas.drawBitmap(bitmap, matrix, null)
        // 以下为陨石上绘制血量
        val healthText = health.toString()
        val textWidth = healthPaint.measureText(healthText)
        val textX = x + bitmap.width - textWidth - 10 // 将血量放在陨石右下角，离陨石边缘10个像素
        val textY = y + bitmap.height - 10 // 同上
        canvas.drawText(healthText, textX, textY, healthPaint)
        //绘制结束
    }
}

