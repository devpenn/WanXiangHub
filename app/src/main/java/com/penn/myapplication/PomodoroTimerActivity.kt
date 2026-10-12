package com.penn.myapplication

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Vibrator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class PomodoroTimerActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStartPause: Button
    private lateinit var btnReset: Button
    
    private var timer: CountDownTimer? = null
    private var isRunning = false
    private val startTimeInMillis: Long = 25 * 60 * 1000 // 25 minutes
    private var timeLeftInMillis: Long = startTimeInMillis
    
    // 用于记录退出时的时间戳
    private var endTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pomodoro_timer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvTimer = findViewById(R.id.tv_timer)
        progressBar = findViewById(R.id.progress_bar)
        btnStartPause = findViewById(R.id.btn_start_pause)
        btnReset = findViewById(R.id.btn_reset)

        btnStartPause.setOnClickListener {
            if (isRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        btnReset.setOnClickListener {
            resetTimer()
        }
    }

    override fun onStop() {
        super.onStop()
        // 保存当前状态
        val prefs = getSharedPreferences("PomodoroPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putLong("timeLeftInMillis", timeLeftInMillis)
        editor.putBoolean("isRunning", isRunning)
        
        // 如果正在运行，保存预计结束时间点
        if (isRunning) {
            editor.putLong("endTime", System.currentTimeMillis() + timeLeftInMillis)
        } else {
            editor.putLong("endTime", 0)
        }
        
        editor.apply()

        // 停止 Timer，避免后台内存泄漏，我们回来时重新计算
        timer?.cancel()
    }

    override fun onStart() {
        super.onStart()

        val prefs = getSharedPreferences("PomodoroPrefs", Context.MODE_PRIVATE)

        timeLeftInMillis = prefs.getLong("timeLeftInMillis", startTimeInMillis)
        isRunning = prefs.getBoolean("isRunning", false)
        
        updateCountDownText()
        updateProgressBar()

        if (isRunning) {
            endTime = prefs.getLong("endTime", 0)
            val currentTime = System.currentTimeMillis()
            
            // 计算过去的时间
            timeLeftInMillis = endTime - currentTime

            if (timeLeftInMillis < 0) {
                // 已经结束了
                timeLeftInMillis = 0
                isRunning = false
                updateCountDownText()
                updateProgressBar()
                // 恢复按钮状态为结束
                btnStartPause.text = "开始专注"
                btnStartPause.visibility = android.view.View.INVISIBLE
                btnReset.visibility = android.view.View.VISIBLE
            } else {
                // 还在进行中，继续计时
                startTimer()
            }
        } else {
            // 暂停状态，恢复按钮显示
            if (timeLeftInMillis < startTimeInMillis) {
                btnStartPause.text = "继续"
                btnReset.visibility = android.view.View.VISIBLE
            } else {
                btnStartPause.text = "开始专注"
                btnReset.visibility = android.view.View.INVISIBLE
            }
        }
    }

    private fun startTimer() {
        // 先取消之前的避免重复
        timer?.cancel()
        
        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountDownText()
                updateProgressBar()
            }

            override fun onFinish() {
                isRunning = false
                btnStartPause.text = "开始专注"
                btnStartPause.visibility = android.view.View.INVISIBLE
                btnReset.visibility = android.view.View.VISIBLE
                
                timeLeftInMillis = 0
                updateCountDownText()
                updateProgressBar()
                
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(1000)
                }
                Toast.makeText(this@PomodoroTimerActivity, "专注结束！休息一下吧。", Toast.LENGTH_LONG).show()
            }
        }.start()

        isRunning = true
        btnStartPause.text = "暂停"
        btnReset.visibility = android.view.View.INVISIBLE
    }

    private fun pauseTimer() {
        timer?.cancel()
        isRunning = false
        btnStartPause.text = "继续"
        btnReset.visibility = android.view.View.VISIBLE
    }

    private fun resetTimer() {
        timer?.cancel()
        timeLeftInMillis = startTimeInMillis
        updateCountDownText()
        updateProgressBar()
        btnReset.visibility = android.view.View.INVISIBLE
        btnStartPause.visibility = android.view.View.VISIBLE
        btnStartPause.text = "开始专注"
    }

    private fun updateCountDownText() {
        // 防止负数
        if (timeLeftInMillis < 0) timeLeftInMillis = 0
        
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        tvTimer.text = timeFormatted
    }
    
    private fun updateProgressBar() {
        if (timeLeftInMillis < 0) timeLeftInMillis = 0
        val secondsLeft = (timeLeftInMillis / 1000).toInt()
        progressBar.progress = secondsLeft
    }
}
