package com.xiaoyan.spacewar

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources

class GameView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val plane: Bitmap = (AppCompatResources.getDrawable(context, R.drawable.player) as BitmapDrawable).bitmap
    private val planeHit: Bitmap = (AppCompatResources.getDrawable(context, R.drawable.player_hit) as BitmapDrawable).bitmap
    private val background: Bitmap = (AppCompatResources.getDrawable(context, R.drawable.background) as BitmapDrawable).bitmap
    private var backgroundY = 0f
    private var backgroundSpeed = 3f //背景滚动速度
    var player: Player? = null

    private var updateThread: Thread? = null
    private var running = true
    private var manager: Manager? = null
    private var bulletManager: BulletManager
    private var deathImage: Bitmap = (AppCompatResources.getDrawable(context,
        R.drawable.death_image
    ) as BitmapDrawable).bitmap
    private var dead = false
    private val restartButton: Bitmap = (AppCompatResources.getDrawable(context, R.drawable.restart) as BitmapDrawable).bitmap
    private val reviveButton: Bitmap = (AppCompatResources.getDrawable(context, R.drawable.revive) as BitmapDrawable).bitmap
    private val restartButtonX = 400f - restartButton.width / 2
    private val restartButtonY = 1040f - restartButton.height / 2
    private val reviveButtonX = 720f - reviveButton.width / 2
    private val reviveButtonY = 1050f - reviveButton.height / 2


    private fun checkPlayerPowerUpCollision() {
        val powerUp = manager?.powerUp

        if (powerUp != null && player != null && RectF.intersects(player!!.boundingBox, powerUp.boundingBox) && player!!.powerUpLevel < 6) {
            player!!.powerUpLevel += 1
            manager?.powerUp = null
        }
    }

    private fun resetGame() {
        player = Player(plane, planeHit, width, height, context)
        manager = Manager(context, width, height, bulletManager.playerbullets)
        dead = false
        invalidate()
    }

    private fun revivePlayer() {
        player?.revive()
        dead = false
        invalidate()
    }

    init {
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                handleTouchEvent(event)
            }
            if (event.action == MotionEvent.ACTION_UP) {
                performClick()
            }
            true
        }
        isFocusable = true
        isFocusableInTouchMode = true

        bulletManager = BulletManager(context, this)

        updateThread = Thread {
            while (running) {
                update()
                try {
                    Thread.sleep(10)
                } catch (e: InterruptedException) {
                    // The thread was interrupted during sleep.
                    // Break the loop if the thread was stopped.
                    if (!running) {
                        break
                    }
                }
            }
        }
        updateThread!!.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (player == null) {
            player = Player(plane, planeHit, w, h, context)
        }
        if (manager == null) {
            manager = Manager(context, w, h, bulletManager.playerbullets)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        drawBackground(canvas)
        player?.draw(canvas)
        bulletManager.drawBullets(canvas)
        manager?.drawAsteroids(canvas)
        manager?.powerUp?.draw(canvas)
        manager?.drawEnemies(canvas)
        manager?.drawEnemyBullets(canvas)
        manager?.drawBoss(canvas)

        if (dead) {
            // 显示死亡图片
            canvas.drawBitmap(deathImage, (width - deathImage.width) / 2f, (height - deathImage.height) / 2f, null)

            canvas.drawBitmap(restartButton, restartButtonX, restartButtonY, null)
            canvas.drawBitmap(reviveButton, reviveButtonX, reviveButtonY, null)
        }
    }

    private fun update() {
        player?.updateInvincibleState() // 更新飞机的无敌状态
        (context as Activity).runOnUiThread {
            invalidate()
        }

        backgroundY += backgroundSpeed
        if (backgroundY >= height) {
            backgroundY = 0f
        }

        bulletManager.updateBullets()

        synchronized(bulletManager.playerbullets) {
            manager?.updateAsteroids()

            manager?.checkPlayerBulletAsteroidCollision()
        }
        manager?.powerUp?.update()
        checkPlayerPowerUpCollision()

        player?.let {
            manager?.updateEnemies(it)
            manager?.updateBoss(it)
            manager?.checkPlayerBulletEnemyCollision()
            manager?.checkPlayerAsteroidCollision(it)
            manager?.checkPlayerEnemyCollision(it)
            manager?.checkEnemyBulletPlayerCollision(it)
            manager?.checkPlayerBulletBossCollision()

            if (it.health <= 0) {
                dead = true
            }
        }
        manager?.updateEnemyBullets()
    }

    private fun handleTouchEvent(event: MotionEvent) {
        // 如果玩家已经死亡，那么我们需要检查点击事件是否发生在复活或者重新开始按钮上
        if (dead) {
            val x = event.x
            val y = event.y

            if (x >= restartButtonX && x < restartButtonX + restartButton.width &&
                y >= restartButtonY && y < restartButtonY + restartButton.height) {
                (context as Activity).runOnUiThread {
                    resetGame()
                }
            } else if (x >= reviveButtonX && x < reviveButtonX + reviveButton.width &&
                y >= reviveButtonY && y < reviveButtonY + reviveButton.height) {
                (context as Activity).runOnUiThread {
                    revivePlayer()
                }
            }
        } else {
            player?.let {
                val dx = event.x - it.x - it.normalBitmap.width / 2
                val dy = event.y - it.y - it.normalBitmap.height / 2
                it.update(dx / it.speed, dy / it.speed)
            }
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val aspectRatio = background.width.toFloat() / background.height.toFloat()
        val screenHeight = height
        val screenWidth = (aspectRatio * screenHeight).toInt()

        val src = Rect(0, 0, background.width, background.height)
        val dst = Rect(0, backgroundY.toInt(), screenWidth, screenHeight + backgroundY.toInt())
        val dst2 = Rect(0, (backgroundY - screenHeight).toInt(), screenWidth, backgroundY.toInt())

        canvas.drawBitmap(background, src, dst, null)
        canvas.drawBitmap(background, src, dst2, null)
    }

    fun stop() {
        running = false
        updateThread?.join() // Wait for the thread to finish
        bulletManager.stop() // 在这里调用 BulletManager 的 stop 方法
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        manager?.bossexplosionSound?.release()
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_W -> player?.update(0f, -1f)
            KeyEvent.KEYCODE_S -> player?.update(0f, 1f)
            KeyEvent.KEYCODE_A -> player?.update(-1f, 0f)
            KeyEvent.KEYCODE_D -> player?.update(1f, 0f)
        }
        return super.onKeyDown(keyCode, event)
    }
}
