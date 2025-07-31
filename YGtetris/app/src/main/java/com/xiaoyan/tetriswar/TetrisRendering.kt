package com.xiaoyan.tetriswar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.res.ResourcesCompat

class TetrisRendering(private val context: Context, private val paint: Paint, private val paddingLeft: Int) {
    private val blockBitmaps = mutableMapOf<Int, Bitmap>()
    var screenBackground: Bitmap? = null
    var gameAreaBackground: Bitmap? = null

    init {
        loadBackgroundImages()
        loadBlockBitmaps()
    }

    private fun loadBlockBitmaps() {
        val blockSize = 40
        val colorResIds = intArrayOf(
            R.drawable.orange,
            R.drawable.blue,
            R.drawable.red,
            R.drawable.yellow,
            R.drawable.green,
            R.drawable.purple,
            R.drawable.golden_block
        )
        for (colorResId in colorResIds) {
            val drawable = ResourcesCompat.getDrawable(context.resources, colorResId, null)
                ?: throw IllegalStateException("Drawable resource not found for colorResId: $colorResId")
            val bitmap = Bitmap.createBitmap(
                blockSize,
                blockSize,
                Bitmap.Config.ARGB_8888
            )
            val bitmapCanvas = Canvas(bitmap)
            drawable.setBounds(0, 0, blockSize, blockSize)
            drawable.draw(bitmapCanvas)
            blockBitmaps[colorResId] = bitmap
        }
    }

    companion object {
        private const val SCORE_TEXT_SIZE = 50f
        private const val CLEAR_COUNT_TEXT_SIZE = 30f
        private const val TEXT_COLOR = Color.BLACK
    }

    fun drawScoreAndConsecutiveClears(canvas: Canvas, gameState: TetrisGameState, gameAreaRect: Rect) {
        paint.textSize = SCORE_TEXT_SIZE
        paint.color = TEXT_COLOR
        val scoreText = "分数:  ${gameState.score}"
        canvas.drawText(scoreText, gameAreaRect.left.toFloat(), gameAreaRect.top.toFloat() - 20f, paint)

        val clearCountText = "多消计数: ${gameState.consecutiveThreeOrMoreLinesCleared}"
        val clearCountTextX = gameAreaRect.right.toFloat() - paint.measureText(clearCountText) - 30f
        canvas.drawText(clearCountText, clearCountTextX, gameAreaRect.top.toFloat() - 20f, paint)

        paint.textSize = CLEAR_COUNT_TEXT_SIZE
        paint.color = TEXT_COLOR
        val levelCondition = gameState.levelConditions.find { it.level == gameState.currentLevel }
        if (levelCondition != null) {
            val offsetX = 430f //以下是显示当前关卡和过关条件
            canvas.drawText("Level: ${gameState.currentLevel}", paddingLeft.toFloat() + 240, (gameAreaRect.bottom + 185).toFloat(), paint)
            canvas.drawText("${levelCondition.score}", paddingLeft.toFloat() + offsetX, (gameAreaRect.bottom + 260).toFloat(), paint)
            canvas.drawText("${levelCondition.consecutiveThreeOrMoreLinesCleared}",
                (gameAreaRect.right - paddingLeft - 122).toFloat(), (gameAreaRect.bottom + 255).toFloat(), paint)
        }
    }


    private fun loadBackgroundImages() {
        val inputStreamScreen = context.assets.open("screen_background.jpg")
        val inputStreamGameArea = context.assets.open("game_area_background.jpg")
        screenBackground = BitmapFactory.decodeStream(inputStreamScreen)
        gameAreaBackground = BitmapFactory.decodeStream(inputStreamGameArea)
    }

    fun renderTetrisBoard(
        canvas: Canvas,
        gameState: TetrisGameState,
        gameAreaRect: Rect,
        blockSize: Int
    ) {
        for (y in 0 until gameState.boardHeight) {
            for (x in 0 until gameState.boardWidth) {
                val blockColor = gameState.getBlock(x, y, showGhost = false)
                if (blockColor != null) {
                    if (gameState.flashingLines.contains(y) && gameState.tetrisBoard.flashColor == blockColor) {
                        drawCell(canvas, x, y, gameState.tetrisBoard.flashColor, 255, gameAreaRect, blockSize)
                    } else {
                        drawCell(canvas, x, y, blockColor, 255, gameAreaRect, blockSize)
                    }
                }
            }
        }

        val (ghostX, ghostY) = gameState.tetrisBoard.getGhostTetrominoPosition(
            gameState.currentTetromino,
            gameState.currentX,
            gameState.currentY
        )
        // Draw ghost tetromino
        drawTetromino(
            canvas,
            gameState.currentTetromino,
            ghostX,
            ghostY,
            blockSize,
            gameAreaRect,
            alpha = 50 // Set the alpha here
        )

        // Draw actual tetromino
        drawTetromino(
            canvas,
            gameState.currentTetromino,
            gameState.currentX,
            gameState.currentY,
            blockSize,
            gameAreaRect,
            alpha = 255 // Set the alpha here
        )

    }
    private fun drawCell(
        canvas: Canvas,
        x: Int,
        y: Int,
        colorResId: Int,
        alpha: Int,
        gameAreaRect: Rect,
        blockSize: Int
    ) {
        val offsetX = gameAreaRect.left
        val offsetY = gameAreaRect.top
        val blockRect = Rect(
            offsetX + x * blockSize,
            offsetY + y * blockSize,
            offsetX + (x + 1) * blockSize,
            offsetY + (y + 1) * blockSize
        )

        val bitmap = blockBitmaps[colorResId] ?: return
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)

        paint.alpha = alpha
        canvas.drawBitmap(bitmap, srcRect, blockRect, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.BLACK
        canvas.drawRect(blockRect, paint)
    }

    private fun drawTetromino(
        canvas: Canvas,
        tetromino: Tetromino,
        x: Int,
        y: Int,
        cellSize: Int,
        gameAreaRect: Rect,
        alpha: Int = 255 // Replace isGhost with alpha
    ) {
        for (i in 0 until tetromino.size) {
            for (j in 0 until tetromino.size) {
                if (tetromino[j, i]) {
                    drawCell(canvas, x + j, y + i, tetromino.colorResId, alpha, gameAreaRect, cellSize)
                }
            }
        }
    }
}