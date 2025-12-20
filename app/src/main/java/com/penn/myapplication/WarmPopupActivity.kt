package com.penn.myapplication

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.random.Random

class WarmPopupActivity : AppCompatActivity() {

    private lateinit var popupContainer: FrameLayout
    private lateinit var controlPanel: View
    private lateinit var btnToggle: View
    private lateinit var tvInfo: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnCloseAll: Button

    private var windowCount = 0
    private val maxWindows = 300
    private var isPopping = false
    private val handler = Handler(Looper.getMainLooper())
    private var popupRunnable: Runnable? = null
    private val interval = 300L // 300ms

    // 温馨提示文字数组
    private val tips = listOf(
        "多喝水哦~", "保持微笑呀", "每天都要元气满满",
        "记得吃水果", "保持好心情", "好好爱自己", "暴富",
        "梦想成真", "期待下一次见面", "金榜题名",
        "顺顺利利", "早点休息", "愿所有烦恼都消失",
        "别熬夜", "今天过得开心嘛", "天冷了，多穿衣服"
    )

    // 背景颜色数组
    private val bgColors = listOf(
        "#FFB6C1", // lightpink
        "#87CEEB", // skyblue
        "#90EE90", // lightgreen
        "#E6E6FA", // lavender
        "#FFFFE0", // lightyellow
        "#DDA0DD", // plum
        "#FF7F50", // coral
        "#FFE4C4", // bisque
        "#7FFFD4", // aquamarine
        "#FFE4E1", // mistyrose
        "#F0FFF0", // honeydew
        "#FFF0F5", // lavenderblush
        "#FDF5E6"  // oldlace
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_warm_popup)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupListeners()
        updateCount()
        
        // 自动开始
        startPopping()
    }

    private fun initViews() {
        popupContainer = findViewById(R.id.popup_container)
        controlPanel = findViewById(R.id.control_panel)
        btnToggle = findViewById(R.id.btn_toggle_panel)
        tvInfo = findViewById(R.id.tv_info)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)
        btnCloseAll = findViewById(R.id.btn_close_all)
    }

    private fun setupListeners() {
        btnToggle.setOnClickListener {
            togglePanel()
        }

        btnStart.setOnClickListener {
            startPopping()
        }

        btnStop.setOnClickListener {
            stopPopping()
        }

        btnCloseAll.setOnClickListener {
            closeAllWindows()
        }
    }

    private fun togglePanel() {
        if (controlPanel.visibility == View.VISIBLE) {
            controlPanel.animate()
                .alpha(0f)
                .translationX(-100f)
                .setDuration(300)
                .withEndAction { controlPanel.visibility = View.GONE }
                .start()
            
            // 移动 toggle button 回去
             btnToggle.animate().translationX(0f).setDuration(300).start()
        } else {
            controlPanel.visibility = View.VISIBLE
            controlPanel.alpha = 0f
            controlPanel.translationX = -100f
            controlPanel.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .start()
                
            // 移动 toggle button 避开面板
            val panelWidth = controlPanel.width.toFloat()
            btnToggle.animate().translationX(panelWidth + 20f).setDuration(300).start()
        }
    }

    private fun startPopping() {
        if (!isPopping && windowCount < maxWindows) {
            isPopping = true
            btnStart.isEnabled = false
            
            popupRunnable = object : Runnable {
                override fun run() {
                    if (windowCount < maxWindows) {
                        createWarmTip()
                        handler.postDelayed(this, interval)
                    } else {
                        stopPopping()
                    }
                }
            }
            handler.post(popupRunnable!!)
        }
    }

    private fun stopPopping() {
        isPopping = false
        btnStart.isEnabled = true
        popupRunnable?.let { handler.removeCallbacks(it) }
        popupRunnable = null
    }

    private fun closeAllWindows() {
        popupContainer.removeAllViews()
        windowCount = 0
        updateCount()
        stopPopping()
    }

    private fun updateCount() {
        tvInfo.text = "当前弹窗数量: $windowCount / $maxWindows"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createWarmTip() {
        if (isFinishing || isDestroyed) return

        val view = LayoutInflater.from(this).inflate(R.layout.item_warm_tip, popupContainer, false)
        val cardView = view.findViewById<CardView>(R.id.card_tip)
        val tvContent = view.findViewById<TextView>(R.id.tv_tip_content)
        val flClose = view.findViewById<FrameLayout>(R.id.fl_close_area)

        // 随机内容
        tvContent.text = tips.random()
        cardView.setCardBackgroundColor(Color.parseColor(bgColors.random()))

        // 随机位置 (减去卡片大概的宽高，防止出界)
        val parentWidth = popupContainer.width
        val parentHeight = popupContainer.height
        // 如果容器还没测量好，先不添加
        if (parentWidth == 0 || parentHeight == 0) return

        val cardWidth = 180.dpToPx() // 估算值
        val cardHeight = 45.dpToPx() // 估算值

        val x = Random.nextInt(0, (parentWidth - cardWidth).coerceAtLeast(1))
        val y = Random.nextInt(0, (parentHeight - cardHeight).coerceAtLeast(1))

        val layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.leftMargin = x
        layoutParams.topMargin = y
        view.layoutParams = layoutParams

        // 进场动画
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.alpha = 0f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(300)
            .start()

        // 关闭逻辑
        flClose.setOnClickListener {
            // 避免重复点击
            if (flClose.tag == "closing") return@setOnClickListener
            flClose.tag = "closing"
            
            view.animate()
                .scaleX(0.5f)
                .scaleY(0.5f)
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    popupContainer.removeView(view)
                    windowCount--
                    updateCount()
                }
                .start()
        }
        
        // 显示关闭按钮的 hover 效果模拟 (点击切换显示状态，或者长按)
        // 在手机上没有 hover，这里改成点击卡片本身或者长按显示关闭按钮，
        // 或者简单点：始终让关闭按钮可点击，但只显示一个小叉
        flClose.visibility = View.VISIBLE

        // 拖拽逻辑
        var dX = 0f
        var dY = 0f
        
        view.setOnTouchListener { v, event ->
            // 把事件带到最上层
            v.parent.requestDisallowInterceptTouchEvent(true)
            view.bringToFront()
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                }
                else -> {
                    v.performClick()
                }
            }
            true
        }

        popupContainer.addView(view)
        windowCount++
        updateCount()
    }
    
    // 扩展函数：dp 转 px
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }
    
    override fun onDestroy() {
        stopPopping()
        super.onDestroy()
    }
}
