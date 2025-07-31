package com.xiaoyan.spacewar

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    private lateinit var soundPool: SoundPool
    private var backgroundMusic: Int = 0
    private var shootSoundId: Int = 0
    private var isLoaded = false
    private lateinit var launchPage: ImageView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        //测试专用11.30
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        launchPage = findViewById(R.id.launchPage)
        startButton = findViewById(R.id.startButton)

        startButton.setOnClickListener {
            gameView = GameView(this)
            setContentView(gameView)
            gameView.requestFocus()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                .setMaxStreams(2)
                .build()

            backgroundMusic = soundPool.load(this, R.raw.background, 1)
            shootSoundId = soundPool.load(this, R.raw.playershoot, 1)

            soundPool.setOnLoadCompleteListener { soundPool, sampleId, _ ->
                isLoaded = true
                if (sampleId == backgroundMusic) {
                    soundPool.play(backgroundMusic, 1f, 1f, 0, -1, 1f)
                } else if (sampleId == shootSoundId) {
                    soundPool.play(shootSoundId, 0.5f, 0.5f, 0, -1, 2f)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if(::gameView.isInitialized){
            gameView.requestFocus()
        }

        if (isLoaded) {
            soundPool.play(backgroundMusic, 1f, 1f, 0, -1, 1f)
            soundPool.play(shootSoundId, 0.5f, 0.5f, 0, -1, 2f)
        }
    }

    override fun onPause() {
        super.onPause()
        if(::soundPool.isInitialized){
            soundPool.autoPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(::gameView.isInitialized){
            gameView.stop()
        }
    }
}
