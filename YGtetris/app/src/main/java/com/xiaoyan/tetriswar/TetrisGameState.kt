package com.xiaoyan.tetriswar

import android.content.Context
import android.media.SoundPool

class TetrisGameState(private val tetrisView: TetrisView, soundPool: SoundPool) {
    val boardWidth = 10
    val boardHeight = 20
    private val context = tetrisView.context
    val levelConditions = LevelConditionLoader(context).loadLevelConditions()
    var currentLevel = 1
    var tetrisBoard = TetrisBoard(context, soundPool, this)
    var onBoardUpdated: ((x: Int, y: Int) -> Unit)? = null

    val isGameOver: Boolean
        get() = checkGameOver()

    // 加金光闪闪效果
    val flashingLines = mutableSetOf<Int>()

    var currentTetromino = Tetromino.getRandomTetromino()
    var currentX = boardWidth / 2 - 2
    var currentY = 0

    var score = 0       //记分功能
    var consecutiveThreeOrMoreLinesCleared = 0  //结束

    fun saveGameState(context: Context) {
        val sharedPreferences = context.getSharedPreferences("TetrisGameState", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("score", score)
        editor.putInt("consecutiveThreeOrMoreLinesCleared", consecutiveThreeOrMoreLinesCleared)
        editor.apply()
    }

    fun loadGameState(context: Context) {
        val sharedPreferences = context.getSharedPreferences("TetrisGameState", Context.MODE_PRIVATE)
        score = sharedPreferences.getInt("score", 0)
        consecutiveThreeOrMoreLinesCleared = sharedPreferences.getInt("consecutiveThreeOrMoreLinesCleared", 0)
    }

    fun checkLevelCompleted(): Boolean {
        val levelCondition = levelConditions.find { it.level == currentLevel }
        return levelCondition != null &&
                score >= levelCondition.score &&
                consecutiveThreeOrMoreLinesCleared >= levelCondition.consecutiveThreeOrMoreLinesCleared
    }

    fun stopGameLoop() {
        tetrisView.isGameLoopRunning = false
    }

    fun addDirtyRectangle(x: Int, y: Int) {
        tetrisView.addDirtyRectangle(x, y)
    }

    fun addDirtyRectanglesForPiece(tetromino: Tetromino, x: Int, y: Int) {
        for (i in 0 until tetromino.size) {
            for (j in 0 until tetromino.size) {
                if (tetromino[j, i]) {
                    addDirtyRectangle(x + j, y + i)
                }
            }
        }
    }

    fun updateScore(linesCleared: Int) {
        when (linesCleared) {
            1 -> {
                score += 10
            }
            2 -> {
                score += 30
            }
            3 -> {
                score += 100
                consecutiveThreeOrMoreLinesCleared += 1
            }
            4 -> {
                score += 150
                consecutiveThreeOrMoreLinesCleared += 2
            }
        }
        saveGameState(context)
    }

    fun checkGameOver(): Boolean {
        for (x in 0 until boardWidth) {
            if (tetrisBoard.board[x][0] != null) {
                tetrisView.isGameLoopRunning = false
                return true
            }
        }
        return false
    }

    fun reset() {
        currentTetromino = Tetromino.getRandomTetromino()
        currentX = (boardWidth - currentTetromino.size) / 2
        currentY = 0
//        score = 0
//        consecutiveThreeOrMoreLinesCleared = 0
        tetrisBoard.clearBoard()
        tetrisView.isGameLoopRunning = false // 设置游戏循环停止
    }

    fun spawnNewTetromino() {
        tetrisBoard.addToBoard(currentTetromino, currentX, currentY)
        currentTetromino = Tetromino.getRandomTetromino()
        currentX = boardWidth / 2 - 2
        currentY = 0

        if (!isValidPosition(currentTetromino, currentX, currentY)) {
            if (!isGameOver) {
                tetrisView.isGameLoopRunning = false
                tetrisView.showGameOverDialog()
            }
        }
    }

    fun isValidPosition(tetromino: Tetromino, x: Int, y: Int): Boolean {
        for (i in 0 until tetromino.size) {
            for (j in 0 until tetromino.size) {
                if (tetromino[j, i]) {
                    val newX = x + j
                    val newY = y + i
                    // Check if the position is out of the board bounds
                    if (newX !in 0 until tetrisBoard.boardWidth || newY !in 0 until tetrisBoard.boardHeight) {
                        return false
                    }
                    // Check if the position is occupied by other pieces
                    if (tetrisBoard.board[newX][newY] != null) {
                        return false
                    }
                }
            }
        }
        return true
    }

    fun getBlock(x: Int, y: Int, showGhost: Boolean = true): Int? {
        if (x !in 0 until boardWidth || y !in 0 until boardHeight) {
            return null
        }
        if (showGhost) {
            val (ghostX, ghostY) = tetrisBoard.getGhostTetrominoPosition(
                currentTetromino,
                currentX,
                currentY
            )
            if (tetrisBoard.canPlaceTetromino(
                    currentTetromino,
                    ghostX,
                    ghostY
                ) && currentTetromino[x - ghostX, y - ghostY]
            ) {
                return currentTetromino.colorResId
            }
        }
        return tetrisBoard.board[x][y] ?: 0
    }
}