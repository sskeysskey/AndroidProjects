package com.xiaoyan.tetriswar

import android.content.Context
import android.media.SoundPool

class TetrisGameLogic(context: Context, private val gameState: TetrisGameState, private val tetrisView: TetrisView) {
    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(5).build()
    private val moveSound: Int
    private val rotateSound: Int
    private val dropSound: Int
    private val lineClearSound: Int

    init {
        gameState.loadGameState(context) //初始化读取分数
        gameState.spawnNewTetromino()
        gameState.tetrisBoard = TetrisBoard(context, soundPool, gameState)
        moveSound = soundPool.load(context, R.raw.move, 1)
        rotateSound = soundPool.load(context, R.raw.rotate, 1)
        dropSound = soundPool.load(context, R.raw.drop, 1)
        lineClearSound = soundPool.load(context, R.raw.clear, 1)
    }

    fun update() {
        if (!tryMoveDown()) {
            gameState.tetrisBoard.addToBoard(gameState.currentTetromino, gameState.currentX, gameState.currentY)
            gameState.tetrisBoard.clearFullLines { linesCleared ->
                if (linesCleared > 0) {
                    soundPool.play(lineClearSound, 1f, 1f, 1, 0, 1f)
                    gameState.updateScore(linesCleared)
                }
            }
            if (gameState.checkGameOver()) {
                tetrisView.post { tetrisView.showGameOverDialog() }
                tetrisView.isGameLoopRunning = false // 设置游戏循环停止
            } else {
                gameState.spawnNewTetromino()
            }
        }
    }

    fun tryMoveDown(): Boolean {
        if (!tetrisView.isGameLoopRunning) return false // 检查游戏循环是否停止
        val newY = gameState.currentY + 1
        if (!gameState.isValidPosition(gameState.currentTetromino, gameState.currentX, newY)) {
            return false
        }
        gameState.currentY = newY
        return true
    }

    fun moveLeft() {
        val oldX = gameState.currentX
        val oldY = gameState.currentY
        if (gameState.isValidPosition(gameState.currentTetromino, gameState.currentX - 1, gameState.currentY)) {
            gameState.currentX--
            gameState.addDirtyRectanglesForPiece(gameState.currentTetromino, oldX, oldY)
            gameState.addDirtyRectanglesForPiece(gameState.currentTetromino, gameState.currentX, gameState.currentY)

            soundPool.play(moveSound, 1f, 1f, 1, 0, 1f)
        }
    }

    fun moveRight() {
        val oldX = gameState.currentX
        val oldY = gameState.currentY
        if (gameState.isValidPosition(gameState.currentTetromino, gameState.currentX + 1, gameState.currentY)) {
            gameState.currentX++
            gameState.addDirtyRectanglesForPiece(gameState.currentTetromino, oldX, oldY)
            gameState.addDirtyRectanglesForPiece(gameState.currentTetromino, gameState.currentX, gameState.currentY)
            soundPool.play(moveSound, 1f, 1f, 1, 0, 1f)
        }
    }

    fun rotate() {
        val oldX = gameState.currentX
        val oldY = gameState.currentY
        val rotated = gameState.currentTetromino.rotated()
        soundPool.play(rotateSound, 1f, 1f, 1, 0, 1f)
        if (gameState.isValidPosition(rotated, gameState.currentX, gameState.currentY)) {
            gameState.currentTetromino = rotated
        } else {
            // 如果无法旋转，尝试调整方块位置
            var shiftX = 1
            var canShift = false
            // 尝试向右移动
            while (shiftX <= 1) {
                if (gameState.isValidPosition(rotated, gameState.currentX + shiftX, gameState.currentY)) {
                    canShift = true
                    break
                }
                shiftX++
            }
            // 尝试向左移动
            if (!canShift) {
                shiftX = -1
                while (shiftX >= -1) {
                    if (gameState.isValidPosition(rotated, gameState.currentX + shiftX, gameState.currentY)) {
                        canShift = true
                        break
                    }
                    shiftX--
                }

            }
            // 针对 I 型方块的特殊情况
            if (!canShift && gameState.currentTetromino.name == "I") {
                if (gameState.currentX + rotated.size >= gameState.boardWidth) {
                    shiftX = -2
                } else if (gameState.currentX - rotated.size < 0) {
                    shiftX = 2
                }
                if (gameState.isValidPosition(rotated, gameState.currentX + shiftX, gameState.currentY)) {
                    canShift = true
                }
            }
            // 针对 I 型方块的特殊情况结束

            if (canShift) {
                gameState.currentX += shiftX
                gameState.currentTetromino = rotated
                gameState.addDirtyRectanglesForPiece(gameState.currentTetromino, oldX, oldY)
                gameState.addDirtyRectanglesForPiece(gameState.currentTetromino, gameState.currentX, gameState.currentY)

            }
        }
    }

    fun drop() {
        while (tryMoveDown()) {
            // Do nothing
        }
        gameState.tetrisBoard.addToBoard(gameState.currentTetromino, gameState.currentX, gameState.currentY)
        gameState.tetrisBoard.clearFullLines { linesCleared ->
            if (linesCleared > 0) {
                soundPool.play(lineClearSound, 1f, 1f, 1, 0, 1f)
                gameState.updateScore(linesCleared)
                for (y in gameState.boardHeight - 1 downTo 0) {
                    for (x in 0 until gameState.boardWidth) {
                        gameState.addDirtyRectangle(x, y)
                    }
                }
            } else {
                soundPool.play(dropSound, 1f, 1f, 1, 0, 1f)
            }
        }
        if (gameState.checkGameOver()) {
            tetrisView.isGameLoopRunning = false
            tetrisView.post { tetrisView.showGameOverDialog() }

        } else {
            gameState.spawnNewTetromino()

        }
    }
}