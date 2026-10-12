package com.penn.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.penn.myapplication.lottery.DrawResult
import com.penn.myapplication.lottery.LotteryApi
import com.penn.myapplication.lottery.LotteryGame
import com.penn.myapplication.lottery.LotteryGenerator
import com.penn.myapplication.lottery.LotterySchedule
import com.penn.myapplication.lottery.LotteryCache
import com.penn.myapplication.lottery.LuckyStore
import com.penn.myapplication.lottery.LuckyTicket
import java.io.IOException
import java.util.Calendar

class LuckyActivity : AppCompatActivity() {

    private var currentGame = LotteryGame.SSQ
    private val tickets = mutableListOf<LuckyTicket>()
    private lateinit var ticketAdapter: TicketAdapter
    private var queryThread: Thread? = null

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

    private val mainHandler = Handler(Looper.getMainLooper())
    private val countdownTask = object : Runnable {
        override fun run() {
            val now = Calendar.getInstance()
            tvNextDraw.text = "下期开奖：${LotterySchedule.nextDrawText(currentGame, now)}"
            tvCountdown.text = LotterySchedule.countdownText(now, currentGame.nextDrawTime(now))
            mainHandler.postDelayed(this, 1000)
        }
    }

    // 摇一摇加一注
    private var sensorManager: SensorManager? = null
    private var lastShakeAt = 0L
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val gForce = Math.sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH
            val now = System.currentTimeMillis()
            // 阈值 2.8（约 1.8g 冲击）+ 1.5s 冷却：明显用力摇才触发，避免走路/轻晃误加注
            if (gForce > 2.8 && now - lastShakeAt > 1500) {
                lastShakeAt = now
                generate()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
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
        btnHistory.setOnClickListener { showHistoryDialog() }
        tvQueryError.setOnClickListener { loadDraws(force = true) }
        tvClearTickets.setOnClickListener { confirmClearTickets() }


        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        loadTickets()
        loadDraws()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(countdownTask)
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager?.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(countdownTask)
        sensorManager?.unregisterListener(shakeListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        queryThread?.interrupt()
    }

    // region 注单管理

    private fun loadTickets() {
        tickets.clear()
        tickets += LuckyStore.load(this, currentGame)
        ticketAdapter.notifyDataSetChanged()
        refreshTicketHeader()
    }

    /** 机选一注：加入列表顶部并持久化（摇一摇同样走这里） */
    private fun generate() {
        val n = LotteryGenerator.generate(currentGame)
        val ticket = LuckyTicket(LuckyStore.newId(), n.mains, n.specials, bought = false)
        tickets.add(ticket)
        LuckyStore.save(this, currentGame, tickets)
        val newPos = tickets.size - 1
        ticketAdapter.notifyItemInserted(newPos)
        rvTickets.scrollToPosition(newPos)
        refreshTicketHeader()
    }

    private fun toggleBought(pos: Int) {
        val t = tickets.getOrNull(pos) ?: return
        tickets[pos] = t.copy(bought = !t.bought)
        LuckyStore.save(this, currentGame, tickets)
        ticketAdapter.notifyItemChanged(pos)
    }

    private fun deleteTicket(pos: Int) {
        if (tickets.getOrNull(pos) == null) return
        tickets.removeAt(pos)
        LuckyStore.save(this, currentGame, tickets)
        ticketAdapter.notifyItemRemoved(pos)
        refreshTicketHeader()
        Toast.makeText(this, "已删除该注", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearTickets() {
        if (tickets.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("清空${currentGame.title}号码")
            .setMessage("将删除全部 ${tickets.size} 注号码，确定吗？")
            .setPositiveButton("清空") { _, _ ->
                tickets.clear()
                LuckyStore.clear(this, currentGame)
                ticketAdapter.notifyDataSetChanged()
                refreshTicketHeader()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshTicketHeader() {
        tvTicketsTitle.text = if (tickets.isEmpty()) {
            "🎯 我的号码"
        } else {
            val boughtCount = tickets.count { it.bought }
            "🎯 我的号码（${tickets.size} 注，已买 $boughtCount）"
        }
        tvEmptyTickets.visibility = if (tickets.isEmpty()) View.VISIBLE else View.GONE
        tvClearTickets.visibility = if (tickets.isEmpty()) View.GONE else View.VISIBLE
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

    /** 选中 = primary 实底 + 对比文字；未选 = 背景底 + primary 文字 + 细描边。显式三件套，来回切换不串样式 */
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
                }
            } catch (e: IOException) {
                // 双网络源都失败 → 读本地缓存兜底，App 永不空屏
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
        tvLatestIssue.text = "最新一期：第${draw.issue}期（${draw.date}）"
        renderBalls(
            findViewById<LinearLayout>(R.id.ll_latest_balls),
            draw.mains, draw.specials, ballSizeDp = 30, textSizeSp = 12
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
            renderBalls(holder.llBalls, draw.mains, draw.specials, ballSizeDp = 26, textSizeSp = 11)
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
            renderBalls(holder.llBalls, ticket.mains, ticket.specials, ballSizeDp = 26, textSizeSp = 11)

            // 已买 → primaryContainer 底色 + 勾标；未买 → 白底
            val bgAttr = if (ticket.bought) {
                com.google.android.material.R.attr.colorPrimaryContainer
            } else {
                com.google.android.material.R.attr.colorSurfaceContainerLowest
            }
            holder.card.setCardBackgroundColor(
                MaterialColors.getColor(holder.card, bgAttr)
            )
            holder.ivBought.isVisible = ticket.bought
            if (ticket.bought) {
                holder.ivBought.setColorFilter(
                    MaterialColors.getColor(holder.ivBought, com.google.android.material.R.attr.colorOnPrimaryContainer)
                )
            }

            holder.card.setOnClickListener { onToggle(holder.bindingAdapterPosition) }
            holder.card.setOnLongClickListener {
                onDelete(holder.bindingAdapterPosition)
                true
            }
        }
    }

    // endregion

    private fun refreshCountdownFrame() {
        val now = Calendar.getInstance()
        tvNextDraw.text = "下期开奖：${LotterySchedule.nextDrawText(currentGame, now)}"
        tvCountdown.text = LotterySchedule.countdownText(now, currentGame.nextDrawTime(now))
    }

    /** 把号码渲染成红/蓝圆形球追加到容器 */
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
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
            }
            container.addView(ball)
        }
        mains.forEach { addBall(it, red = true) }
        // 主区/特区之间留个间隔
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        })
        specials.forEach { addBall(it, red = false) }
    }

    private fun ImageView.isVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
