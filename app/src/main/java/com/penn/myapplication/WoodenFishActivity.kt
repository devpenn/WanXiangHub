package com.penn.myapplication

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class WoodenFishActivity : AppCompatActivity() {

    private var meritCount = 0
    private lateinit var tvMeritCount: TextView
    private lateinit var ivWoodenFish: ImageView
    private lateinit var containerFloatingText: FrameLayout
    private lateinit var soundPool: SoundPool
    private var soundId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_wooden_fish)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvMeritCount = findViewById(R.id.tv_merit_count)
        ivWoodenFish = findViewById(R.id.iv_wooden_fish)
        containerFloatingText = findViewById(R.id.container_floating_text)

        // Load saved merit count
        val sharedPref = getSharedPreferences("WoodenFishPrefs", Context.MODE_PRIVATE)
        meritCount = sharedPref.getInt("merit_count", 0)
        tvMeritCount.text = "功德: $meritCount"

        initSoundPool()

        findViewById<android.view.View>(R.id.main).setOnClickListener {
            knockWoodenFish()
        }
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
            
        // 这里需要一个木鱼的声音文件，假设放在 res/raw/wooden_fish.mp3
        // 由于还没有音频文件，先用系统提示音代替或者暂不播放
        // soundId = soundPool.load(this, R.raw.wooden_fish, 1)
    }

    private fun knockWoodenFish() {
        // 1. 播放声音 (如果有)
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }

        // 2. 木鱼缩放动画
        ivWoodenFish.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(50)
            .withEndAction {
                ivWoodenFish.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(50)
                    .start()
            }
            .start()

        // 3. 功德+1
        meritCount++
        tvMeritCount.text = "功德: $meritCount"

        // Save merit count
        val sharedPref = getSharedPreferences("WoodenFishPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putInt("merit_count", meritCount).apply()

        // 4. 飘出“功德+1”文字
        showFloatingText()
    }

    private fun showFloatingText() {
        val textView = TextView(this)
        textView.text = "功德 +1"
        textView.setTextColor(Color.WHITE)
        textView.textSize = 24f
        textView.alpha = 0f
        
        // 初始位置在木鱼上方
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        // 稍微随机一点的水平位置
        val randomX = (Math.random() * 100 - 50).toInt()
        
        // 计算木鱼中心位置
        val location = IntArray(2)
        ivWoodenFish.getLocationOnScreen(location)
        val fishCenterX = location[0] + ivWoodenFish.width / 2
        val fishCenterY = location[1] + ivWoodenFish.height / 2
        
        textView.x = (fishCenterX + randomX).toFloat()
        textView.y = (fishCenterY - 200).toFloat()

        containerFloatingText.addView(textView)

        // 飘动动画
        textView.animate()
            .alpha(1f)
            .translationYBy(-300f)
            .setDuration(800)
            .withEndAction {
                textView.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            containerFloatingText.removeView(textView)
                        }
                    })
                    .start()
            }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}
