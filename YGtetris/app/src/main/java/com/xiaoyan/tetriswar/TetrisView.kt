package com.xiaoyan.tetriswar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.media.SoundPool
import androidx.appcompat.app.AlertDialog
import android.graphics.RectF
import android.view.MotionEvent

@SuppressLint("ViewConstructor")
class TetrisView(context: Context, soundPool: SoundPool, private val onLevelComplete: (Boolean) -> Unit) : View(context) {

    private val gameAreaRect = Rect()
    val gameState = TetrisGameState(this, soundPool)
    private val gameLogic = TetrisGameLogic(context, gameState, this)
    private val gameLoopHandler = Handler(Looper.getMainLooper())
    private val horizontalMargin = 0f
    private val topMargin = 0.09f
    private val bottomMargin = 0.25f
    private val paint = Paint()
    private val tetrisRendering = TetrisRendering(context, paint, paddingLeft)
    private var isGameOverDialogShown = false
    private lateinit var dialog: AlertDialog // 添加此行

    var isGameLoopRunning = false
    private val dirtyRectangles = mutableListOf<Rect>()

    fun levelComplete() {
        if (gameState.checkLevelCompleted()) {
            if (isGameLoopRunning) {
                isGameLoopRunning = false
                gameLoopHandler.removeCallbacksAndMessages(null)
            }
            showLevelCompletedDialog()
        }
    }

    fun addDirtyRectangle(x: Int, y: Int) {
        val dirtyRect = Rect(
            gameAreaRect.left + x * blockSize,
            gameAreaRect.top + y * blockSize,
            gameAreaRect.left + (x + 1) * blockSize,
            gameAreaRect.top + (y + 1) * blockSize
        )
        dirtyRectangles.add(dirtyRect)
    }

    private val blockSize: Int
        get() = (gameAreaRect.width()) / gameState.boardWidth

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        gameState.onBoardUpdated = ::addDirtyRectangle
        startGameLoop()
    }

    private fun showLevelCompletedDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("恭喜你顺利过关")
        builder.setMessage("目前总分：${gameState.score}")
        builder.setPositiveButton("挑战下一关") { _, _ ->
            gameState.saveGameState(context)
            onLevelComplete(true)
        }
        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    fun showGameOverDialog() {
        if (isGameOverDialogShown) {
            return
        }
        isGameOverDialogShown = true
        gameState.stopGameLoop()
        val builder = AlertDialog.Builder(context)
        builder.setTitle("哈哈，你死了")
        builder.setMessage("目前总分：${gameState.score}")
        builder.setPositiveButton("再来一局") { _, _ ->
            isGameOverDialogShown = false
            dialog.dismiss()
            gameState.reset()
            gameState.saveGameState(context)
            startGameLoop()
            invalidate()
        }
        builder.setNegativeButton("买点儿道具") { dialog, _ ->
            dialog.dismiss()
            gameState.saveGameState(context)
            isGameOverDialogShown = false
            onLevelComplete(false)
        }
        dialog = builder.create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun startGameLoop() {
        isGameLoopRunning = true
        gameLoopHandler.post(object : Runnable {
            override fun run() {
                if (!gameState.isGameOver) {
                    gameLogic.update()
                    invalidate()
                    gameLoopHandler.postDelayed(this, 1000)
                } else {
                    isGameLoopRunning = false
                    showGameOverDialog()
                }
                if (gameState.checkLevelCompleted()) {
                    levelComplete()
                }
            }
        })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        gameAreaRect.set(
            paddingLeft.toFloat().toInt(),
            paddingTop.toFloat().toInt(),
            paddingLeft + blockSize * gameState.boardWidth,
            paddingTop + blockSize * gameState.boardHeight
        )
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Calculate x and y margins
        val horizontalMarginPx = (w * horizontalMargin).toInt()
        val bottomMarginPx = (h * bottomMargin).toInt()

        // Calculate the width and height of the game area
        val gameAreaWidth = w - 2 * horizontalMarginPx
        val gameAreaHeight = h - (h * topMargin).toInt() - bottomMarginPx

        // Calculate the block size
        val blockSizeX = gameAreaWidth / gameState.boardWidth
        val blockSizeY = gameAreaHeight / gameState.boardHeight

        // Use the smaller block size to keep the aspect ratio consistent
        val blockSize = minOf(blockSizeX, blockSizeY)

        // Update the gameAreaRect to reflect the new size and position
        val offsetX = (w - blockSize * gameState.boardWidth) / 2

        gameAreaRect.set(
            offsetX,
            (h * topMargin).toInt(),
            offsetX + blockSize * gameState.boardWidth,
            (h * topMargin).toInt() + blockSize * gameState.boardHeight
        )
    }

    override fun onDraw(canvas: Canvas) {
        tetrisRendering.screenBackground?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
        tetrisRendering.gameAreaBackground?.let { canvas.drawBitmap(it, null, gameAreaRect, paint) }
//        tetrisUI.drawButtons(canvas)
        dirtyRectangles.clear()
        tetrisRendering.renderTetrisBoard(canvas, gameState, gameAreaRect, blockSize)
        tetrisRendering.drawScoreAndConsecutiveClears(canvas, gameState, gameAreaRect)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            // 手动设置 ImageView 的坐标和尺寸
            val leftButtonX = 5f
            val leftButtonY = 955f
            val leftButtonWidth = 160f
            val leftButtonHeight = 160f

            val rightButtonX = 930f
            val rightButtonY = 955f
            val rightButtonWidth = 160f
            val rightButtonHeight = 160f

            val downButtonX = 5f
            val downButtonY = 1235f
            val downButtonWidth = 160f
            val downButtonHeight = 160f

            val shiftButtonX = 930f
            val shiftButtonY = 1235f
            val shiftButtonWidth = 160f
            val shiftButtonHeight = 160f

            val leftButtonRect = RectF(leftButtonX, leftButtonY, leftButtonX + leftButtonWidth, leftButtonY + leftButtonHeight)
            val rightButtonRect = RectF(rightButtonX, rightButtonY, rightButtonX + rightButtonWidth, rightButtonY + rightButtonHeight)
            val downButtonRect = RectF(downButtonX, downButtonY, downButtonX + downButtonWidth, downButtonY + downButtonHeight)
            val shiftButtonRect = RectF(shiftButtonX, shiftButtonY, shiftButtonX + shiftButtonWidth, shiftButtonY + shiftButtonHeight)

            when {
                leftButtonRect.contains(x, y) -> gameLogic.moveLeft()
                rightButtonRect.contains(x, y) -> gameLogic.moveRight()
                downButtonRect.contains(x, y) -> gameLogic.drop()
                shiftButtonRect.contains(x, y) -> gameLogic.rotate()
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_A -> gameLogic.moveLeft()
            KeyEvent.KEYCODE_D -> gameLogic.moveRight()
            KeyEvent.KEYCODE_J -> gameLogic.rotate()
            KeyEvent.KEYCODE_S -> gameLogic.tryMoveDown()
            KeyEvent.KEYCODE_SPACE -> gameLogic.drop()
        }
        invalidate()
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gameLoopHandler.removeCallbacksAndMessages(null)
    }
}