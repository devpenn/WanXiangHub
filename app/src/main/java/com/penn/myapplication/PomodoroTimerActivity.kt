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

        updateCountDownText()
    }

    private fun startTimer() {
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
                
                // 震动提醒
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    // 震动 1秒
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
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        tvTimer.text = timeFormatted
    }
    
    private fun updateProgressBar() {
        // max = 1500 (25 * 60)
        // progress = current seconds left
        val secondsLeft = (timeLeftInMillis / 1000).toInt()
        progressBar.progress = secondsLeft
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
