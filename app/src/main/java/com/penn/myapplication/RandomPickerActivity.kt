package com.penn.myapplication

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.random.Random

class RandomPickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_random_picker)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etOptions = findViewById<EditText>(R.id.et_options)
        val btnPick = findViewById<Button>(R.id.btn_pick)
        val tvResult = findViewById<TextView>(R.id.tv_result)

        btnPick.setOnClickListener {
            val input = etOptions.text.toString()
            if (input.isBlank()) {
                Toast.makeText(this, "请输入选项", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 支持中文逗号和英文逗号
            val options = input.split(",", "，")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (options.isEmpty()) {
                Toast.makeText(this, "请输入有效的选项", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 简单的随机滚动效果
            startRandomAnimation(tvResult, options)
        }
    }

    private fun startRandomAnimation(textView: TextView, options: List<String>) {
        val totalDuration = 2000L // 动画持续2秒
        val interval = 100L // 每100ms变换一次文字
        val steps = (totalDuration / interval).toInt()
        var currentStep = 0

        textView.animate().cancel()
        textView.scaleX = 1f
        textView.scaleY = 1f
        
        // 使用 Handler 或者递归 postDelayed 来实现快速切换
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        val runnable = object : Runnable {
            override fun run() {
                if (currentStep < steps) {
                    textView.text = options.random()
                    textView.setTextColor(android.graphics.Color.GRAY)
                    currentStep++
                    handler.postDelayed(this, interval)
                } else {
                    // 最终结果
                    val finalResult = options.random()
                    textView.text = finalResult
                    textView.setTextColor(getColor(R.color.warm_primary))
                    
                    // 放大动画展示结果
                    textView.animate()
                        .scaleX(1.5f)
                        .scaleY(1.5f)
                        .setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                // 稍微缩放回来一点
                                textView.animate()
                                    .scaleX(1.2f)
                                    .scaleY(1.2f)
                                    .setDuration(200)
                                    .start()
                            }
                        })
                        .start()
                }
            }
        }
        
        handler.post(runnable)
    }
}
