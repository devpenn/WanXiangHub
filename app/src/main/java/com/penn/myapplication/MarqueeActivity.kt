package com.penn.myapplication

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MarqueeActivity : AppCompatActivity() {

    private lateinit var tvMarquee: TextView
    private lateinit var layoutSettings: LinearLayout
    private lateinit var etText: EditText
    private var animator: ObjectAnimator? = null
    private var isScrolling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 强制横屏或者允许旋转，这里暂时默认竖屏，通过按钮旋转
        // requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_marquee)
        
        // 沉浸式全屏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 不设置 padding，让内容全屏
            insets
        }

        tvMarquee = findViewById(R.id.tv_marquee)
        layoutSettings = findViewById(R.id.layout_settings)
        etText = findViewById(R.id.et_text)

        findViewById<View>(R.id.main).setOnClickListener {
            toggleSettings()
        }

        findViewById<Button>(R.id.btn_update).setOnClickListener {
            val text = etText.text.toString()
            if (text.isNotEmpty()) {
                tvMarquee.text = text
                startScrolling()
            }
        }

        findViewById<Button>(R.id.btn_toggle_scroll).setOnClickListener {
            if (isScrolling) {
                animator?.pause()
                isScrolling = false
            } else {
                animator?.resume()
                isScrolling = true
            }
        }
        
        findViewById<Button>(R.id.btn_rotate).setOnClickListener {
             requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                 ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
             } else {
                 ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
             }
        }

        // 初始开始滚动
        tvMarquee.post {
            startScrolling()
        }
    }

    private fun startScrolling() {
        animator?.cancel()
        
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val textWidth = tvMarquee.paint.measureText(tvMarquee.text.toString())
        
        // 从屏幕右边完全移入，到屏幕左边完全移出
        tvMarquee.translationX = screenWidth
        
        val endX = -textWidth
        val distance = screenWidth - endX
        val duration = (distance * 5).toLong() // 速度系数

        animator = ObjectAnimator.ofFloat(tvMarquee, "translationX", screenWidth, endX).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
        isScrolling = true
    }

    private fun toggleSettings() {
        if (layoutSettings.visibility == View.VISIBLE) {
            layoutSettings.visibility = View.GONE
            // 隐藏状态栏和导航栏
             window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        } else {
            layoutSettings.visibility = View.VISIBLE
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}
