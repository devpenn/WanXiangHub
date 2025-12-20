package com.penn.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.random.Random

class ClassicGameActivity : AppCompatActivity() {
    private var targetNumber = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_classic_game)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize game
        resetGame()

        val etGuess = findViewById<EditText>(R.id.et_guess)
        val btnGuess = findViewById<Button>(R.id.btn_guess)
        val tvResult = findViewById<TextView>(R.id.tv_result)

        btnGuess.setOnClickListener {
            val guessText = etGuess.text.toString()
            if (guessText.isEmpty()) {
                Toast.makeText(this, "请输入一个数字", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val guessNumber = guessText.toIntOrNull()
            if (guessNumber == null) {
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when {
                guessNumber > targetNumber -> tvResult.text = "太大了！再试一次"
                guessNumber < targetNumber -> tvResult.text = "太小了！再试一次"
                else -> {
                    tvResult.text = "恭喜你！猜对了！\n数字已重置，开始新一轮游戏吧。"
                    resetGame()
                    etGuess.text.clear()
                }
            }
        }
    }

    private fun resetGame() {
        targetNumber = Random.nextInt(1, 101)
    }
}
