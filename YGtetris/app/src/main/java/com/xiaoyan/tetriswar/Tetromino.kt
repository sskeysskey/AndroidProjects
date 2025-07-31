package com.xiaoyan.tetriswar

import java.util.Random

enum class Tetromino(var colorResId: Int, private val shapes: Array<Array<BooleanArray>>) {

    I(1, I_SHAPE),
    J(2, J_SHAPE),
    L(3, L_SHAPE),
    O(4, O_SHAPE),
    S(5, S_SHAPE),
    T(6, T_SHAPE);


    val size: Int
        get() = shapes[currentRotation].size

    operator fun get(x: Int, y: Int): Boolean {
        if (x < 0 || x >= size || y < 0 || y >= size) {
            return false
        }

        val shape = shapes[currentRotation]

        if (y >= shape.size || x >= shape[y].size) {
            return false
        }
        return shape[y][x]
    }

    private var currentRotation = 0

    fun rotated(): Tetromino {
        val newTetromino = Tetromino.valueOf(this.name)
        newTetromino.currentRotation = (currentRotation + 1) % shapes.size
        return newTetromino
    }

    companion object {
        private val colorResIds = intArrayOf(
            R.drawable.orange,
            R.drawable.blue,
            R.drawable.red,
            R.drawable.yellow,
            R.drawable.green,
            R.drawable.purple
        )

        fun getRandomTetromino(): Tetromino {
            val random = Random()
            val tetromino = values()[random.nextInt(values().size)]
            tetromino.colorResId = colorResIds[random.nextInt(colorResIds.size)]
            return tetromino
        }
    }
}