package com.penn.myapplication

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

class MarqueeActivity : AppCompatActivity() {

    private lateinit var tvMarquee: TextView
    private lateinit var layoutSettings: MaterialCardView
    private lateinit var etText: TextInputEditText
    private lateinit var sbSpeed: SeekBar
    private lateinit var btnToggleScroll: MaterialButton
    private lateinit var tvHint: TextView
    private var animator: ObjectAnimator? = null
    private var isScrolling = false
    private var scrollSpeed = 50 // 默认速度

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoHidePanel = Runnable { hidePanel() }
    private var hintAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_marquee)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            insets
        }

        tvMarquee = findViewById(R.id.tv_marquee)
        layoutSettings = findViewById(R.id.layout_settings)
        etText = findViewById(R.id.et_text)
        sbSpeed = findViewById(R.id.sb_speed)
        btnToggleScroll = findViewById(R.id.btn_toggle_scroll)
        tvHint = findViewById(R.id.tv_hint)

        // 加载保存的文字和速度
        val sharedPref = getSharedPreferences("MarqueePrefs", Context.MODE_PRIVATE)
        val savedText = sharedPref.getString("marquee_text", "请输入文字")
        scrollSpeed = sharedPref.getInt("marquee_speed", 50)

        tvMarquee.text = savedText
        etText.setText(savedText)
        sbSpeed.progress = scrollSpeed

        // 弹幕滚动区：轻点（UP 且未滚动）切换面板；拖动翻看弹幕不触发。
        // 不用 root 的 click 监听：DOWN 会被滚动区吞掉，root 的 click 永远不触发。
        val hsv = findViewById<View>(R.id.hsv_marquee)
        var downX = 0f
        var downY = 0f
        hsv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                }
                MotionEvent.ACTION_UP -> {
                    val slop = ViewConfiguration.get(v.context).scaledTouchSlop
                    if (Math.abs(e.x - downX) < slop && Math.abs(e.y - downY) < slop) {
                        togglePanel()
                    }
                }
            }
            false // 不消费事件，HorizontalScrollView 的滚动照常工作
        }
        layoutSettings.setOnClickListener { resetAutoHide() }

        findViewById<MaterialButton>(R.id.btn_update).setOnClickListener {
            val text = etText.text.toString()
            if (text.isNotEmpty()) {
                tvMarquee.text = text
                sharedPref.edit().putString("marquee_text", text).apply()
                startScrolling()
            }
            etText.clearFocus()
            hideKeyboard()
            hidePanel()
        }

        btnToggleScroll.setOnClickListener {
            if (isScrolling) {
                animator?.pause()
                isScrolling = false
                btnToggleScroll.text = "继续"
            } else {
                animator?.resume()
                isScrolling = true
                btnToggleScroll.text = "暂停"
            }
            resetAutoHide()
        }

        findViewById<MaterialButton>(R.id.btn_rotate).setOnClickListener {
            requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            resetAutoHide()
        }

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                scrollSpeed = progress
                if (isScrolling) {
                    updateAnimationSpeed()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                resetAutoHide()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sharedPref.edit().putInt("marquee_speed", scrollSpeed).apply()
                resetAutoHide()
            }
        })

        // 进入即沉浸：面板收起、系统栏隐藏，只留纯弹幕
        hidePanel(instant = true)
        showEntryHint()

        tvMarquee.post {
            startScrolling()
        }
    }

    // region 面板显隐

    private fun togglePanel() {
        if (layoutSettings.isVisible) hidePanel() else showPanel()
    }

    private fun showPanel() {
        mainHandler.removeCallbacks(autoHidePanel)
        layoutSettings.isVisible = true
        layoutSettings.alpha = 0f
        layoutSettings.translationY = layoutSettings.height.coerceAtLeast(1).toFloat()
        layoutSettings.animate()
            .alpha(1f).translationY(0f).setDuration(220L)
            .start()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            show(WindowInsetsCompat.Type.systemBars())
        }
        scheduleAutoHide()
    }

    private fun hidePanel(instant: Boolean = false) {
        mainHandler.removeCallbacks(autoHidePanel)
        hideKeyboard()
        val end = {
            layoutSettings.isVisible = false
            layoutSettings.alpha = 1f
            layoutSettings.translationY = 0f
        }
        if (instant || !layoutSettings.isVisible) {
            end()
        } else {
            layoutSettings.animate()
                .alpha(0f)
                .translationY(layoutSettings.height.coerceAtLeast(1).toFloat())
                .setDuration(200L)
                .withEndAction { end() }
                .start()
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** 面板打开 5 秒无操作自动收起：举着手机给别人看字时不必手动收 */
    private fun scheduleAutoHide() {
        mainHandler.removeCallbacks(autoHidePanel)
        mainHandler.postDelayed(autoHidePanel, AUTO_HIDE_MS)
    }

    private fun resetAutoHide() {
        if (layoutSettings.isVisible) scheduleAutoHide()
    }

    private fun showEntryHint() {
        tvHint.isVisible = true
        tvHint.alpha = 1f
        hintAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = 2500L
            duration = 600L
            addUpdateListener {
                tvHint.alpha = it.animatedValue as Float
                if (it.animatedFraction >= 1f) tvHint.isVisible = false
            }
            start()
        }
    }

    private fun hideKeyboard() {
        currentFocus?.let { focus ->
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(focus.windowToken, 0)
            focus.clearFocus()
        }
    }

    // endregion

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        hintAnimator?.cancel()
    }

    private fun updateAnimationSpeed() {
        animator?.let {
            val currentX = tvMarquee.translationX
            val textWidth = tvMarquee.width.toFloat()
            // 终点是文字完全移出屏幕左侧
            val endX = -textWidth

            it.cancel()

            // 计算剩余距离
            val distanceLeft = currentX - endX
            val factor = calculateSpeedFactor(scrollSpeed)
            val duration = (distanceLeft * factor).toLong()

            animator = ObjectAnimator.ofFloat(tvMarquee, "translationX", currentX, endX).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (isScrolling && !cancelled) {
                            startScrolling()
                        }
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        cancelled = true
                    }
                })
                start()
            }
        }
    }

    private fun startScrolling() {
        animator?.cancel()

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val textWidth = tvMarquee.width.toFloat()

        tvMarquee.translationX = screenWidth

        val endX = -textWidth
        val distance = screenWidth - endX
        val factor = calculateSpeedFactor(scrollSpeed)
        val duration = (distance * factor).toLong()

        animator = ObjectAnimator.ofFloat(tvMarquee, "translationX", screenWidth, endX).apply {
            this.duration = duration
            repeatCount = 0
            interpolator = LinearInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isScrolling && !cancelled) {
                        startScrolling()
                    }
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }
            })
            start()
        }
        isScrolling = true
    }

    private fun calculateSpeedFactor(progress: Int): Float {
        val safeProgress = progress.coerceIn(1, 100)
        val mappedSpeed = 1f + (safeProgress - 1) * (500f / 99f)
        val baseConstant = 100f
        return baseConstant / mappedSpeed
    }

    private companion object {
        const val AUTO_HIDE_MS = 5000L
    }
}
