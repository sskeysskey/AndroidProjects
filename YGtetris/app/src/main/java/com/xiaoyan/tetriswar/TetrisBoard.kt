package com.xiaoyan.tetriswar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.media.SoundPool

class TetrisBoard(context: Context, private val soundPool: SoundPool, private val gameState: TetrisGameState) {
    val boardWidth = 10
    val boardHeight = 20
    val board = Array(boardWidth) { arrayOfNulls<Int>(boardHeight) }
    private val lineClearSound: Int = soundPool.load(context, R.raw.clear, 1)
    private val animationDuration = 100L // 动画持续时间，单位毫秒
    val flashColor = R.drawable.golden_block // 闪烁颜色

    private fun getFullLines(): List<Int> {
        return (0 until boardHeight).filter { y ->
            (0 until boardWidth).all { x -> board[x][y] != null }
        }
    }

    fun getGhostTetrominoPosition(tetromino: Tetromino, x: Int, y: Int): Pair<Int, Int> {
        var ghostY = y
        while (canPlaceTetromino(tetromino, x, ghostY + 1)) {
            ghostY++
        }
        return Pair(x, ghostY)
    }

    fun canPlaceTetromino(tetromino: Tetromino, x: Int, y: Int): Boolean {
        for (i in 0 until tetromino.size) {
            for (j in 0 until tetromino.size) {
                if (tetromino[j, i]) {
                    val newX = x + j
                    val newY = y + i
                    // Check if the position is out of the board bounds
                    if (newX !in 0 until boardWidth || newY !in 0 until boardHeight) {
                        return false
                    }
                    // Check if the position is occupied by other pieces
                    if (board[newX][newY] != null) {
                        return false
                    }
                }
            }
        }
        return true
    }

    fun clearFullLines(onAnimationEnd: ((Int) -> Unit)? = null): Int {
        var linesCleared = 0
        val fullLines = getFullLines()

        CoroutineScope(Dispatchers.Main).launch {
            gameState.flashingLines.clear()
            gameState.flashingLines.addAll(fullLines)

            if (fullLines.isNotEmpty()) {
                soundPool.play(lineClearSound, 1f, 1f, 1, 0, 1f)
                flashLines(fullLines)
            }

            val newBoard = Array(boardWidth) { arrayOfNulls<Int>(boardHeight) }
            var newY = boardHeight - 1

            for (y in boardHeight - 1 downTo 0) {
                if (!fullLines.contains(y)) {
                    for (x in 0 until boardWidth) {
                        newBoard[x][newY] = board[x][y]
                    }
                    newY--
                } else {
                    linesCleared++
                }
            }

            for (x in 0 until boardWidth) {
                board[x] = newBoard[x].clone()
            }


            onAnimationEnd?.invoke(linesCleared)
        }

        return linesCleared
    }

    private suspend fun flashLines(lines: List<Int>, onAnimationEnd: ((Int) -> Unit)? = null) {
        lines.forEach { y ->
            for (x in 0 until boardWidth) {
                board[x][y] = flashColor
                gameState.onBoardUpdated?.invoke(x, y)
            }
        }
        delay(animationDuration)
        lines.forEach { y ->
            for (x in 0 until boardWidth) {
                board[x][y] = null
                gameState.onBoardUpdated?.invoke(x, y)
            }
        }
        onAnimationEnd?.invoke(lines.size)
    }

    fun addToBoard(tetromino: Tetromino, x: Int, y: Int) {
        if (gameState.isGameOver) {
            return
        }
        for (i in 0 until tetromino.size) {
            for (j in 0 until tetromino.size) {
                if (tetromino[j, i]) {
                    val newX = x + j
                    val newY = y + i

                    // 检查 newY 是否在边界内ESC
                    if (newY in 0 until boardHeight) {
                        board[newX][newY] = tetromino.colorResId
                        gameState.onBoardUpdated?.invoke(newX, newY)
                    } else {
                        gameState.onBoardUpdated?.invoke(newX, newY - 1)
                    }

                    if (newY - 1 >= 0) {
                        gameState.onBoardUpdated?.invoke(newX, newY - 1)
                    }
                }
            }
        }
    }

    fun clearBoard() {
        for (x in 0 until boardWidth) {
            board[x].fill(null)
            board[x].forEachIndexed { y, _ -> gameState.onBoardUpdated?.invoke(x, y) }
        }
    }

}
