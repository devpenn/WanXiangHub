package com.penn.myapplication

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.penn.myapplication.lottery.DrawResult
import com.penn.myapplication.lottery.LotteryApi
import com.penn.myapplication.lottery.LotteryCache
import com.penn.myapplication.lottery.LotteryGame
import com.penn.myapplication.lottery.LotteryGenerator
import com.penn.myapplication.lottery.LotterySchedule
import com.penn.myapplication.lottery.LuckyStore
import com.penn.myapplication.lottery.LuckyTicket
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LuckyActivity : AppCompatActivity() {

    private var currentGame = LotteryGame.SSQ
    private val tickets = mutableListOf<LuckyTicket>()
    private lateinit var ticketAdapter: TicketAdapter
    private var queryThread: Thread? = null

    // 当前选中的日期 (默认今天，格式 YYYY-MM-DD)
    private var selectedDateStr: String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    // 每个玩法各自缓存开奖数据，切 Tab 秒出
    private val drawCache = mutableMapOf<LotteryGame, List<DrawResult>>()

    private lateinit var rvTickets: RecyclerView
    private lateinit var tvEmptyTickets: TextView
    private lateinit var tvClearTickets: TextView
    private lateinit var tvTicketsTitle: TextView
    private lateinit var tvLatestIssue: TextView
    private lateinit var tvQueryError: TextView
    private lateinit var tvNextDraw: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnSsq: MaterialButton
    private lateinit var btnDlt: MaterialButton
    private lateinit var btnGenerate: MaterialButton
    private lateinit var tvSelectDate: TextView
    private lateinit var btnDateToday: MaterialButton

    private val mainHandler = Handler(Looper.getMainLooper())
    private val countdownTask = object : Runnable {
        override fun run() {
            val now = Calendar.getInstance()
            tvNextDraw.text = "下期开奖：${LotterySchedule.nextDrawText(currentGame, now)}"
            tvCountdown.text = LotterySchedule.countdownText(now, currentGame.nextDrawTime(now))
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_lucky)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lucky_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupHeader()

        // 接收 Intent 过滤参数
        val targetGameName = intent.getStringExtra("target_game")
        if (targetGameName == "DLT") {
            currentGame = LotteryGame.DLT
        } else if (targetGameName == "SSQ") {
            currentGame = LotteryGame.SSQ
        }

        val targetDate = intent.getStringExtra("target_date")
        if (!targetDate.isNullOrEmpty()) {
            selectedDateStr = targetDate
        }

        rvTickets = findViewById(R.id.rv_tickets)
        tvEmptyTickets = findViewById(R.id.tv_empty_tickets)
        tvClearTickets = findViewById(R.id.tv_clear_tickets)
        tvTicketsTitle = findViewById(R.id.tv_tickets_title)
        tvLatestIssue = findViewById(R.id.tv_latest_issue)
        tvQueryError = findViewById(R.id.tv_query_error)
        tvNextDraw = findViewById(R.id.tv_next_draw)
        tvCountdown = findViewById(R.id.tv_countdown)
        btnSsq = findViewById(R.id.btn_game_ssq)
        btnDlt = findViewById(R.id.btn_game_dlt)
        btnGenerate = findViewById(R.id.btn_generate)
        tvSelectDate = findViewById(R.id.tv_select_date)
        btnDateToday = findViewById(R.id.btn_date_today)
        val btnHistory = findViewById<MaterialButton>(R.id.btn_history)

        rvTickets.layoutManager = LinearLayoutManager(this)
        ticketAdapter = TicketAdapter(
            onToggle = { pos -> toggleBought(pos) },
            onDelete = { pos -> deleteTicket(pos) },
        )
        rvTickets.adapter = ticketAdapter

        btnSsq.setOnClickListener { switchGame(LotteryGame.SSQ) }
        btnDlt.setOnClickListener { switchGame(LotteryGame.DLT) }
        btnGenerate.setOnClickListener { generate() }
        findViewById<MaterialButton>(R.id.btn_manual).setOnClickListener { showManualSelectDialog() }
        btnHistory.setOnClickListener { showHistoryDialog() }
        tvQueryError.setOnClickListener { loadDraws(force = true) }
        tvClearTickets.setOnClickListener { confirmClearTickets() }

        tvSelectDate.setOnClickListener { showDatePickerDialog() }
        btnDateToday.setOnClickListener {
            selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            updateDateBarDisplay()
            loadTickets()
        }

        applyTabStyles()
        updateDateBarDisplay()
        loadTickets()
        loadDraws()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(countdownTask)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(countdownTask)
    }

    override fun onDestroy() {
        super.onDestroy()
        queryThread?.interrupt()
    }

    // region 日期选择控制

    private fun updateDateBarDisplay() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val isToday = selectedDateStr == todayStr
        tvSelectDate.text = if (isToday) "📅 $selectedDateStr (今天)" else "📅 $selectedDateStr"
    }

    private fun showDatePickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_lottery_date_picker, null)
        val tvMonthTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_month_title)
        val btnPrevMonth = dialogView.findViewById<ImageView>(R.id.btn_dialog_prev_month)
        val btnNextMonth = dialogView.findViewById<ImageView>(R.id.btn_dialog_next_month)
        val btnToday = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_today)
        val llGrid = dialogView.findViewById<LinearLayout>(R.id.ll_dialog_calendar_grid)

        var dialogDialog: AlertDialog? = null

        val displayCal = Calendar.getInstance()
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(selectedDateStr)
            if (date != null) displayCal.time = date
        } catch (_: Exception) {
        }

        // 查询包含已购注单的日期数量映射
        val boughtCountMap = mutableMapOf<String, Int>()
        try {
            val dbHelper = com.penn.myapplication.db.WanXiangDatabaseHelper(this)
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT ${com.penn.myapplication.db.WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_DATE}, COUNT(*) FROM ${com.penn.myapplication.db.WanXiangDatabaseHelper.TABLE_LUCKY_TICKETS} WHERE ${com.penn.myapplication.db.WanXiangDatabaseHelper.COLUMN_TICKET_IS_BOUGHT} = 1 AND ${com.penn.myapplication.db.WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_DATE} != '' GROUP BY ${com.penn.myapplication.db.WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_DATE}",
                null
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    val d = c.getString(0)
                    val cnt = c.getInt(1)
                    if (!d.isNullOrEmpty()) {
                        boughtCountMap[d] = cnt
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        fun renderDialogMonth() {
            llGrid.removeAllViews()
            val year = displayCal.get(Calendar.YEAR)
            val month0 = displayCal.get(Calendar.MONTH)
            tvMonthTitle.text = "${year}年${month0 + 1}月"

            val firstDay = Calendar.getInstance().apply { set(year, month0, 1) }
            val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
            val leading = firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            val totalCells = leading + daysInMonth
            val rows = Math.ceil(totalCells / 7.0).toInt()

            for (row in 0 until rows) {
                val rowView = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                for (col in 0 until 7) {
                    val dayOffset = row * 7 + col - leading + 1
                    val cellCal = Calendar.getInstance().apply {
                        set(year, month0, 1)
                        add(Calendar.DAY_OF_MONTH, dayOffset - 1)
                    }
                    val isCurrentMonth = cellCal.get(Calendar.MONTH) == month0
                    val dateKey = String.format(Locale.CHINA, "%04d-%02d-%02d", cellCal.get(Calendar.YEAR), cellCal.get(Calendar.MONTH) + 1, cellCal.get(Calendar.DAY_OF_MONTH))
                    val isToday = dateKey == SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
                    val isSelected = dateKey == selectedDateStr
                    val boughtCount = boughtCountMap[dateKey] ?: 0

                    val cell = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(2), dp(4), dp(2), dp(4))
                        setOnClickListener {
                            if (isCurrentMonth) {
                                selectedDateStr = dateKey
                                updateDateBarDisplay()
                                loadTickets()
                                dialogDialog?.dismiss()
                            }
                        }
                    }

                    val numTv = TextView(this).apply {
                        text = cellCal.get(Calendar.DAY_OF_MONTH).toString()
                        gravity = Gravity.CENTER
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                        when {
                            isSelected -> {
                                background = ContextCompat.getDrawable(context, R.drawable.bg_day_today)
                                setTextColor(Color.WHITE)
                            }
                            isToday -> {
                                background = ContextCompat.getDrawable(context, R.drawable.bg_day_selected)
                                setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
                            }
                            else -> {
                                setTextColor(if (isCurrentMonth) MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface) else Color.GRAY)
                            }
                        }
                    }
                    cell.addView(numTv)

                    // 在日历格上显示 🎯 N (仅标识已标记购买的注数)
                    if (boughtCount > 0 && isCurrentMonth) {
                        val badge = TextView(this).apply {
                            text = "🎯 $boughtCount"
                            gravity = Gravity.CENTER
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            includeFontPadding = false
                            setTextColor(Color.WHITE)
                            background = ContextCompat.getDrawable(context, R.drawable.bg_hint_pill)
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setPadding(dp(3), dp(1), dp(3), dp(1))
                                topMargin = dp(1)
                            }
                        }
                        cell.addView(badge)
                    }

                    rowView.addView(
                        cell,
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    )
                }
                llGrid.addView(rowView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
            }
        }

        btnPrevMonth.setOnClickListener {
            displayCal.add(Calendar.MONTH, -1)
            renderDialogMonth()
        }

        btnNextMonth.setOnClickListener {
            displayCal.add(Calendar.MONTH, 1)
            renderDialogMonth()
        }

        btnToday.setOnClickListener {
            selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            updateDateBarDisplay()
            loadTickets()
            dialogDialog?.dismiss()
        }

        renderDialogMonth()

        dialogDialog = AlertDialog.Builder(this)
            .setTitle("选择好运日期")
            .setView(dialogView)
            .setNegativeButton("关闭", null)
            .show()
    }

    // endregion

    // region 注单管理

    private fun loadTickets() {
        tickets.clear()
        val latestDraw = drawCache[currentGame]?.firstOrNull()
        val loaded = LuckyStore.loadByDate(this, currentGame, selectedDateStr, latestDraw)
        loaded.sortBy { it.purchaseTimestamp }
        tickets += loaded
        ticketAdapter.notifyDataSetChanged()
        refreshTicketHeader()
    }

    /** 机选一注：赋权给当前选定日期，并在列表末尾追加（从上往下排列） */
    private fun generate() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        if (selectedDateStr < todayStr) {
            Toast.makeText(this, "历史日期已归档，无法新增注单", Toast.LENGTH_SHORT).show()
            return
        }
        val n = LotteryGenerator.generate(currentGame)
        val ticket = LuckyTicket(
            id = LuckyStore.newId(),
            gameName = currentGame.name,
            mains = n.mains,
            specials = n.specials,
            bought = false,
            purchaseDate = selectedDateStr,
            purchaseTimestamp = System.currentTimeMillis()
        )
        tickets.add(ticket)
        LuckyStore.saveSingleTicket(this, ticket)
        val newPos = tickets.size - 1
        ticketAdapter.notifyItemInserted(newPos)
        rvTickets.scrollToPosition(newPos)
        refreshTicketHeader()
    }

    /** 弹窗手动自选彩票号码 */
    private fun showManualSelectDialog() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        if (selectedDateStr < todayStr) {
            Toast.makeText(this, "历史日期归档，无法加注", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_lottery, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_manual_title)
        val llPreview = dialogView.findViewById<LinearLayout>(R.id.ll_manual_preview_balls)
        val tvRedTitle = dialogView.findViewById<TextView>(R.id.tv_red_section_title)
        val tvBlueTitle = dialogView.findViewById<TextView>(R.id.tv_blue_section_title)
        val gridRed = dialogView.findViewById<android.widget.GridLayout>(R.id.grid_red_balls)
        val gridBlue = dialogView.findViewById<android.widget.GridLayout>(R.id.grid_blue_balls)

        val reqRedCount = if (currentGame == LotteryGame.SSQ) 6 else 5
        val reqBlueCount = if (currentGame == LotteryGame.SSQ) 1 else 2
        val maxRedNum = if (currentGame == LotteryGame.SSQ) 33 else 35
        val maxBlueNum = if (currentGame == LotteryGame.SSQ) 16 else 12

        tvTitle.text = "自选${currentGame.title} (红球${reqRedCount}个 + 蓝球${reqBlueCount}个)"

        val selectedReds = mutableSetOf<Int>()
        val selectedBlues = mutableSetOf<Int>()

        fun updatePreviewAndTitles() {
            tvRedTitle.text = "🔴 选择红球 (已选 ${selectedReds.size}/$reqRedCount)"
            tvBlueTitle.text = "🔵 选择蓝球 (已选 ${selectedBlues.size}/$reqBlueCount)"

            val sortedReds = selectedReds.sorted()
            val sortedBlues = selectedBlues.sorted()
            renderBalls(llPreview, sortedReds, sortedBlues, ballSizeDp = 22, textSizeSp = 10)
        }

        updatePreviewAndTitles()

        // 动态构建红球选择块
        for (i in 1..maxRedNum) {
            val num = i
            val btn = TextView(this).apply {
                text = com.penn.myapplication.lottery.LuckyNumber.two(num)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = ContextCompat.getDrawable(this@LuckyActivity, R.drawable.bg_ball_red)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E5E7EB"))
                setTextColor(Color.parseColor("#374151"))

                val param = android.widget.GridLayout.LayoutParams().apply {
                    width = dp(32)
                    height = dp(32)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                layoutParams = param

                setOnClickListener {
                    if (selectedReds.contains(num)) {
                        selectedReds.remove(num)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E5E7EB"))
                        setTextColor(Color.parseColor("#374151"))
                    } else {
                        if (selectedReds.size >= reqRedCount) {
                            Toast.makeText(this@LuckyActivity, "红球最多只能选择 ${reqRedCount} 个", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        selectedReds.add(num)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                        setTextColor(Color.WHITE)
                    }
                    updatePreviewAndTitles()
                }
            }
            gridRed.addView(btn)
        }

        // 动态构建蓝球选择块
        for (i in 1..maxBlueNum) {
            val num = i
            val btn = TextView(this).apply {
                text = com.penn.myapplication.lottery.LuckyNumber.two(num)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = ContextCompat.getDrawable(this@LuckyActivity, R.drawable.bg_ball_blue)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E5E7EB"))
                setTextColor(Color.parseColor("#374151"))

                val param = android.widget.GridLayout.LayoutParams().apply {
                    width = dp(32)
                    height = dp(32)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                layoutParams = param

                setOnClickListener {
                    if (selectedBlues.contains(num)) {
                        selectedBlues.remove(num)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E5E7EB"))
                        setTextColor(Color.parseColor("#374151"))
                    } else {
                        if (selectedBlues.size >= reqBlueCount) {
                            Toast.makeText(this@LuckyActivity, "蓝球最多只能选择 ${reqBlueCount} 个", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        selectedBlues.add(num)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3B82F6"))
                        setTextColor(Color.WHITE)
                    }
                    updatePreviewAndTitles()
                }
            }
            gridBlue.addView(btn)
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("确定添加") { _, _ ->
                if (selectedReds.size != reqRedCount || selectedBlues.size != reqBlueCount) {
                    Toast.makeText(this, "请选满红球 ${reqRedCount}个 与 蓝球 ${reqBlueCount}个", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val ticket = LuckyTicket(
                    id = LuckyStore.newId(),
                    gameName = currentGame.name,
                    mains = selectedReds.sorted(),
                    specials = selectedBlues.sorted(),
                    bought = false,
                    purchaseDate = selectedDateStr,
                    purchaseTimestamp = System.currentTimeMillis()
                )
                tickets.add(ticket)
                LuckyStore.saveSingleTicket(this, ticket)
                val newPos = tickets.size - 1
                ticketAdapter.notifyItemInserted(newPos)
                rvTickets.scrollToPosition(newPos)
                refreshTicketHeader()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleBought(pos: Int) {
        val t = tickets.getOrNull(pos) ?: return
        val updated = t.copy(
            bought = !t.bought,
            purchaseDate = if (t.purchaseDate.isEmpty()) selectedDateStr else t.purchaseDate
        )
        tickets[pos] = updated
        LuckyStore.saveSingleTicket(this, updated)
        ticketAdapter.notifyItemChanged(pos)
        refreshTicketHeader()
    }

    private fun deleteTicket(pos: Int) {
        val t = tickets.getOrNull(pos) ?: return
        if (t.bought) {
            AlertDialog.Builder(this)
                .setTitle("确认删除已购彩票")
                .setMessage("该注号码已标记为已购买，确定要删除吗？")
                .setPositiveButton("确定删除") { _, _ ->
                    performDeleteTicket(pos, t)
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            performDeleteTicket(pos, t)
        }
    }

    private fun performDeleteTicket(pos: Int, ticket: LuckyTicket) {
        if (pos in tickets.indices && tickets[pos].id == ticket.id) {
            tickets.removeAt(pos)
            LuckyStore.deleteSingleTicket(this, ticket.id)
            ticketAdapter.notifyItemRemoved(pos)
            refreshTicketHeader()
            Toast.makeText(this, "已删除该注", Toast.LENGTH_SHORT).show()
        } else {
            loadTickets()
        }
    }

    private fun confirmClearTickets() {
        if (tickets.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("清空${selectedDateStr}${currentGame.title}号码")
            .setMessage("将删除 $selectedDateStr 当天的全部 ${tickets.size} 注号码，确定吗？")
            .setPositiveButton("清空") { _, _ ->
                tickets.forEach { ticket ->
                    LuckyStore.deleteSingleTicket(this, ticket.id)
                }
                tickets.clear()
                ticketAdapter.notifyDataSetChanged()
                refreshTicketHeader()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshTicketHeader() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val isPastDate = selectedDateStr < todayStr
        val dateLabel = if (selectedDateStr == todayStr) "今日" else selectedDateStr

        // 历史日期无法加注，直接隐藏加注双按键栏
        val buttonContainer = findViewById<View>(R.id.ll_button_container)
        buttonContainer?.visibility = if (isPastDate) View.GONE else View.VISIBLE

        tvTicketsTitle.text = if (tickets.isEmpty()) {
            "🎯 $dateLabel 号码"
        } else {
            val boughtCount = tickets.count { it.bought }
            val totalCost = tickets.size * 2
            "🎯 $dateLabel 号码（${tickets.size} 注 · 共 ${totalCost}元，已买 $boughtCount）"
        }
        tvEmptyTickets.visibility = if (tickets.isEmpty()) View.VISIBLE else View.GONE
        tvClearTickets.visibility = if (tickets.isEmpty() || isPastDate) View.GONE else View.VISIBLE
    }

    // endregion

    // region 玩法切换

    private fun switchGame(game: LotteryGame) {
        if (game == currentGame) return
        currentGame = game
        applyTabStyles()
        refreshCountdownFrame()
        loadTickets()
        loadDraws()
    }

    private fun applyTabStyles() {
        styleTab(btnSsq, selected = currentGame == LotteryGame.SSQ)
        styleTab(btnDlt, selected = currentGame == LotteryGame.DLT)
    }

    private fun styleTab(button: MaterialButton, selected: Boolean) {
        val content = findViewById<View>(android.R.id.content)
        val primary = MaterialColors.getColor(content, androidx.appcompat.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(content, com.google.android.material.R.attr.colorOnPrimary)
        val outline = MaterialColors.getColor(content, com.google.android.material.R.attr.colorOutline)
        val bg = ContextCompat.getColor(this, R.color.background)

        button.backgroundTintList = ColorStateList.valueOf(if (selected) primary else bg)
        button.setTextColor(if (selected) onPrimary else primary)
        if (selected) {
            button.strokeWidth = 0
        } else {
            button.strokeWidth = dp(1)
            button.strokeColor = ColorStateList.valueOf(outline)
        }
    }

    // endregion

    // region 开奖查询

    private fun loadDraws(force: Boolean = false) {
        if (!force && queryThread?.isAlive == true) return
        tvQueryError.visibility = View.GONE
        tvLatestIssue.text = "最新开奖查询中…"
        val game = currentGame
        queryThread = Thread {
            try {
                val results = LotteryApi.fetchHistory(game, limit = 30)
                LotteryCache.save(applicationContext, game, results)
                drawCache[game] = results
                runOnUiThread {
                    if (currentGame != game || results.isEmpty()) return@runOnUiThread
                    showLatest(results.first())
                    loadTickets()
                }
            } catch (e: IOException) {
                val cached = LotteryCache.load(applicationContext, game)
                val savedAt = LotteryCache.savedAt(applicationContext, game)
                runOnUiThread {
                    if (currentGame != game) return@runOnUiThread
                    if (cached != null) {
                        drawCache[game] = cached
                        showLatest(cached.first())
                        tvLatestIssue.text = "（离线缓存·$savedAt）${tvLatestIssue.text}"
                        tvQueryError.visibility = View.GONE
                    } else {
                        tvLatestIssue.text = ""
                        findViewById<LinearLayout>(R.id.ll_latest_balls).removeAllViews()
                        tvQueryError.visibility = View.VISIBLE
                    }
                }
            }
        }.also { it.start() }
    }

    private fun showLatest(draw: DrawResult) {
        tvLatestIssue.text = "第${draw.issue}期："
        renderBalls(
            findViewById<LinearLayout>(R.id.ll_latest_balls),
            draw.mains, draw.specials, ballSizeDp = 22, textSizeSp = 10
        )
    }

    // endregion

    // region 历史开奖

    private fun showHistoryDialog() {
        val cached = drawCache[currentGame]
        if (cached.isNullOrEmpty()) {
            Toast.makeText(this, "开奖数据还没加载好，稍后再试", Toast.LENGTH_SHORT).show()
            return
        }
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@LuckyActivity)
            adapter = HistoryAdapter(cached)
            setPadding(0, dp(8), 0, dp(8))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        AlertDialog.Builder(this)
            .setTitle("${currentGame.title} · 历史开奖")
            .setView(recyclerView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private inner class HistoryAdapter(private val items: List<DrawResult>) :
        RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvIssue: TextView = view.findViewById(R.id.tv_history_issue)
            val tvDate: TextView = view.findViewById(R.id.tv_history_date)
            val llBalls: LinearLayout = view.findViewById(R.id.ll_history_balls)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lucky_history, parent, false)
            return HistoryViewHolder(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val draw = items[position]
            holder.tvIssue.text = "第${draw.issue}期"
            holder.tvDate.text = draw.date
            renderBalls(holder.llBalls, draw.mains, draw.specials, ballSizeDp = 22, textSizeSp = 10)
        }
    }

    // endregion

    // region 注单列表 Adapter

    private inner class TicketAdapter(
        private val onToggle: (Int) -> Unit,
        private val onDelete: (Int) -> Unit,
    ) : RecyclerView.Adapter<TicketAdapter.TicketViewHolder>() {

        inner class TicketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: com.google.android.material.card.MaterialCardView =
                view as com.google.android.material.card.MaterialCardView
            val llBalls: LinearLayout = view.findViewById(R.id.ll_ticket_balls)
            val llPrizeStatusRow: LinearLayout = view.findViewById(R.id.ll_prize_status_row)
            val tvPrizeBadge: TextView = view.findViewById(R.id.tv_prize_badge)
            val ivBought: ImageView = view.findViewById(R.id.iv_ticket_bought)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lucky_number, parent, false)
            return TicketViewHolder(view)
        }

        override fun getItemCount() = tickets.size

        override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
            val ticket = tickets[position]

            // 1. 渲染行1: 大号 28dp 醒目红蓝彩票球 (独占第一行，绝不压缩截断)
            renderBalls(holder.llBalls, ticket.mains, ticket.specials, ballSizeDp = 28, textSizeSp = 12)

            // 2. 渲染行2: 仅在已完结开奖(中奖或未中)时显示徽章；未开奖或今日注单不展示任何徽章
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            val isPendingDraw = ticket.prizeLevel == "待开奖" || ticket.purchaseDate >= todayStr

            if (ticket.bought && !isPendingDraw) {
                holder.llPrizeStatusRow.visibility = View.VISIBLE
                if (ticket.prizeAmount > 0) {
                    holder.tvPrizeBadge.text = "🎉 ${ticket.prizeLevel} ${ticket.prizeAmount.toInt()}元"
                    holder.tvPrizeBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                } else {
                    holder.tvPrizeBadge.text = "未中奖"
                    holder.tvPrizeBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9CA3AF"))
                }
            } else {
                holder.llPrizeStatusRow.visibility = View.GONE
            }

            val bgAttr = if (ticket.bought) {
                com.google.android.material.R.attr.colorPrimaryContainer
            } else {
                com.google.android.material.R.attr.colorSurfaceContainerLowest
            }
            holder.card.setCardBackgroundColor(
                MaterialColors.getColor(holder.card, bgAttr)
            )

            holder.card.setOnClickListener { onToggle(holder.bindingAdapterPosition) }
            holder.card.setOnLongClickListener {
                onDelete(holder.bindingAdapterPosition)
                true
            }
        }
    }

    // endregion

    private fun setupHeader() {
        findViewById<TextView>(R.id.tv_header_title)?.text = "今日好运"
        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }
    }

    private fun refreshCountdownFrame() {
        val now = Calendar.getInstance()
        tvNextDraw.text = "下期开奖：${LotterySchedule.nextDrawText(currentGame, now)}"
        tvCountdown.text = LotterySchedule.countdownText(now, currentGame.nextDrawTime(now))
    }

    private fun renderBalls(
        container: LinearLayout,
        mains: List<Int>,
        specials: List<Int>,
        ballSizeDp: Int,
        textSizeSp: Int,
    ) {
        container.removeAllViews()
        fun addBall(number: Int, red: Boolean) {
            val ball = TextView(this).apply {
                text = com.penn.myapplication.lottery.LuckyNumber.two(number)
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                background = ContextCompat.getDrawable(
                    context, if (red) R.drawable.bg_ball_red else R.drawable.bg_ball_blue
                )
                layoutParams = LinearLayout.LayoutParams(dp(ballSizeDp), dp(ballSizeDp)).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
            }
            container.addView(ball)
        }
        mains.forEach { addBall(it, red = true) }
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(6), 1)
        })
        specials.forEach { addBall(it, red = false) }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
