package com.penn.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.penn.myapplication.asr.AsrModelDownloader
import com.penn.myapplication.asr.AsrModelInfo
import com.penn.myapplication.asr.OptionSplitter
import com.penn.myapplication.asr.SherpaAsrEngine
import java.io.IOException

class RandomPickerActivity : AppCompatActivity() {

    private var isSpinning = false
    private lateinit var wheelView: WheelView
    private lateinit var etOptions: EditText
    private lateinit var sharedPref: android.content.SharedPreferences

    private var asrEngine: SherpaAsrEngine? = null
    private var listenDialog: AlertDialog? = null
    private var micPulse: AlphaAnimation? = null

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startVoiceInput()
            } else {
                Toast.makeText(this, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_random_picker)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupHeader()

        wheelView = findViewById(R.id.wheel_view)
        etOptions = findViewById(R.id.et_options)
        val btnPick = findViewById<MaterialButton>(R.id.btn_pick)
        val tvResult = findViewById<TextView>(R.id.tv_result)
        val tilOptions = findViewById<TextInputLayout>(R.id.til_options)

        sharedPref = getSharedPreferences("RandomPickerPrefs", Context.MODE_PRIVATE)
        val savedOptions = sharedPref.getString("options", "火锅,烧烤,日料,川菜,粤菜,西餐")
        etOptions.setText(savedOptions)

        // 初始化转盘
        updateWheel(wheelView, savedOptions ?: "")

        // 输入框右侧麦克风图标 → 语音输入
        tilOptions.setEndIconOnClickListener { startVoiceInput() }

        btnPick.setOnClickListener {
            if (isSpinning) return@setOnClickListener

            val input = etOptions.text.toString()
            if (input.isBlank()) {
                Toast.makeText(this, "请输入选项", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 保存选项
            sharedPref.edit().putString("options", input).apply()

            // 支持中文逗号和英文逗号
            val options = input.split(",", "，")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (options.isEmpty()) {
                Toast.makeText(this, "请输入有效的选项", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (options.size < 2) {
                Toast.makeText(this, "至少需要2个选项", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 更新转盘并开始旋转
            wheelView.setOptions(options)
            tvResult.visibility = View.INVISIBLE
            isSpinning = true
            btnPick.isEnabled = false

            wheelView.spin { result ->
                tvResult.text = "🎉 $result"
                tvResult.visibility = View.VISIBLE
                isSpinning = false
                btnPick.isEnabled = true
            }
        }

        // 监听输入变化，实时更新转盘
        etOptions.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !isSpinning) {
                updateWheel(wheelView, etOptions.text.toString())
            }
        }
    }

    private fun setupHeader() {
        findViewById<TextView>(R.id.tv_header_title)?.text = "随机决定"
        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        asrEngine?.cancel()
        listenDialog?.dismiss()
    }

    private fun updateWheel(wheelView: WheelView, input: String) {
        val options = input.split(",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (options.isNotEmpty()) {
            wheelView.setOptions(options)
        }
    }

    // region 语音输入

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!AsrModelInfo.isModelReady(this)) {
            downloadModelThen { showListenDialog() }
            return
        }
        showListenDialog()
    }

    private fun downloadModelThen(onReady: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_model_download, null)
        val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.pb_model_download)
        val tvPercent = view.findViewById<TextView>(R.id.tv_download_percent)

        val dialog = AlertDialog.Builder(this)
            .setTitle("下载语音模型")
            .setMessage("首次使用语音输入需下载约 26MB 中文识别模型（仅下载一次）")
            .setView(view)
            .setNegativeButton("取消", null)
            .create()
        dialog.show()

        val downloader = AsrModelDownloader(AsrModelInfo.modelDir(this)) { percent ->
            runOnUiThread {
                if (!dialog.isShowing) return@runOnUiThread
                progressBar.progress = percent
                tvPercent.text = "$percent%"
            }
        }
        dialog.setOnCancelListener { downloader.cancel() }
        dialog.setOnDismissListener { downloader.cancel() }

        Thread {
            try {
                downloader.downloadBlocking()
                runOnUiThread {
                    if (dialog.isShowing) dialog.dismiss()
                    onReady()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    if (dialog.isShowing) dialog.dismiss()
                    Toast.makeText(this, "模型下载失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showListenDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_asr_listen, null)
        val ivMic = view.findViewById<ImageView>(R.id.iv_asr_mic)
        val tvStatus = view.findViewById<TextView>(R.id.tv_asr_status)
        val tvPartial = view.findViewById<TextView>(R.id.tv_asr_partial)
        val btnDone = view.findViewById<MaterialButton>(R.id.btn_asr_done)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_asr_cancel)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        listenDialog = dialog

        btnCancel.setOnClickListener {
            asrEngine?.cancel()
            dialog.dismiss()
        }
        btnDone.setOnClickListener {
            tvStatus.text = "正在识别…"
            btnDone.isEnabled = false
            asrEngine?.finish()
        }
        dialog.show()

        val engine = SherpaAsrEngine(AsrModelInfo.modelDir(this))
        asrEngine = engine
        engine.start(object : SherpaAsrEngine.Listener {
            override fun onReady() {
                runOnUiThread {
                    tvStatus.text = "请说出选项，说完停顿一下会自动结束"
                    startMicPulse(ivMic)
                }
            }

            override fun onPartial(text: String) {
                runOnUiThread { tvPartial.text = text }
            }

            override fun onFinal(text: String) {
                runOnUiThread {
                    dialog.dismiss()
                    if (text.isBlank()) {
                        Toast.makeText(this@RandomPickerActivity, "没听清，再试一次吧", Toast.LENGTH_SHORT).show()
                    } else {
                        showSplitOptionsDialog(text)
                    }
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this@RandomPickerActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun startMicPulse(ivMic: ImageView) {
        AlphaAnimation(1.0f, 0.3f).apply {
            duration = 700
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
            ivMic.startAnimation(this)
            micPulse = this
        }
    }

    private fun showSplitOptionsDialog(rawText: String) {
        val options = OptionSplitter.split(rawText).toMutableList()
        val view = layoutInflater.inflate(R.layout.dialog_option_split, null)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_options)
        val etNew = view.findViewById<TextInputEditText>(R.id.et_new_option)
        val tilNew = view.findViewById<TextInputLayout>(R.id.til_new_option)

        fun renderChips() {
            chipGroup.removeAllViews()
            options.forEach { option ->
                val chip = Chip(this)
                chip.text = option
                chip.isCloseIconVisible = true
                chip.setOnCloseIconClickListener {
                    options.remove(option)
                    renderChips()
                }
                chipGroup.addView(chip)
            }
        }
        renderChips()

        fun addCurrent() {
            val text = etNew.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && !options.contains(text)) {
                options.add(text)
                renderChips()
            }
            etNew.setText("")
        }
        tilNew.setEndIconOnClickListener { addCurrent() }
        etNew.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                addCurrent()
                true
            } else {
                false
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("确认选项")
            .setView(view)
            .setCancelable(false)
            .setNegativeButton("重说") { _, _ -> showListenDialog() }
            .setNeutralButton("取消", null)
            .setPositiveButton("确定", null)
            .create()
        dialog.setOnShowListener {
            // 手动校验后再关闭，避免选项不足时弹窗直接消失
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (options.size < 2) {
                    Toast.makeText(this, "至少需要2个选项", Toast.LENGTH_SHORT).show()
                } else {
                    applyOptions(options.joinToString("，"))
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun applyOptions(joined: String) {
        etOptions.setText(joined)
        sharedPref.edit().putString("options", joined).apply()
        updateWheel(wheelView, joined)
    }

    // endregion
}
