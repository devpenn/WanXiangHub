package com.penn.myapplication.lottery

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.penn.myapplication.db.WanXiangDatabaseHelper
import com.penn.myapplication.ui.todo.TodoRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 彩票注单 SQLite 本地持久化与开奖中奖计算仓库。
 * 支持按日期精准筛选、日历双向联动、离线恢复与自动中奖算效！
 */
object LuckyStore {

    private const val OLD_PREF = "lucky_tickets"
    private val gson = Gson()
    private val idSeq = AtomicLong(System.currentTimeMillis())

    fun newId(): Long = idSeq.incrementAndGet()

    /** 从 SQLite 读取指定玩法的所有彩票注单，并自动与最新开奖结果比对中奖 */
    fun load(context: Context, game: LotteryGame, latestDraw: DrawResult? = null): MutableList<LuckyTicket> {
        migrateOldPrefsIfNeeded(context, game)
        val list = mutableListOf<LuckyTicket>()
        val dbHelper = WanXiangDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            WanXiangDatabaseHelper.TABLE_LUCKY_TICKETS,
            null,
            "${WanXiangDatabaseHelper.COLUMN_TICKET_GAME} = ?",
            arrayOf(game.name),
            null, null,
            "${WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_TIME} DESC"
        )

        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_ID))
                val gameName = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_GAME))
                val redStr = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_RED_BALLS))
                val blueStr = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_BLUE_BALLS))
                val purchaseDate = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_DATE))
                val purchaseTime = c.getLong(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_TIME))
                val issue = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_ISSUE))
                val bought = c.getInt(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_IS_BOUGHT)) == 1
                val drawRedStr = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_DRAW_RED))
                val drawBlueStr = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_DRAW_BLUE))
                val prizeLevel = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_PRIZE_LEVEL)) ?: "待开奖"
                val prizeAmount = c.getDouble(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_PRIZE_AMOUNT))

                val mains = redStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                val specials = blueStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                val drawMains = drawRedStr?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                val drawSpecials = drawBlueStr?.split(",")?.mapNotNull { it.trim().toIntOrNull() }

                var ticket = LuckyTicket(
                    id = id,
                    gameName = gameName,
                    mains = mains,
                    specials = specials,
                    bought = bought,
                    purchaseDate = purchaseDate,
                    purchaseTimestamp = purchaseTime,
                    issue = issue,
                    drawMains = drawMains,
                    drawSpecials = drawSpecials,
                    prizeLevel = prizeLevel,
                    prizeAmount = prizeAmount
                )

                // 仅当注单期号与最新开奖期号匹配，或购买日期严格小于最新已开奖日期时进行核对；未开奖注单维持"待开奖"
                val isDrawFinished = latestDraw != null && (
                    (ticket.issue != null && ticket.issue == latestDraw.issue) ||
                    (ticket.purchaseDate.isNotEmpty() && ticket.purchaseDate < latestDraw.date)
                )

                if (isDrawFinished && latestDraw != null) {
                    val prizeResult = LotteryPrizeCalculator.calculate(
                        game,
                        ticket.mains,
                        ticket.specials,
                        latestDraw.mains,
                        latestDraw.specials
                    )
                    if (ticket.prizeLevel != prizeResult.level || ticket.prizeAmount != prizeResult.amount) {
                        ticket = ticket.copy(
                            issue = latestDraw.issue,
                            drawMains = latestDraw.mains,
                            drawSpecials = latestDraw.specials,
                            prizeLevel = prizeResult.level,
                            prizeAmount = prizeResult.amount
                        )
                        saveSingleTicket(context, ticket)
                    }
                } else {
                    // 今日或未来尚未开奖注单，重置为待开奖
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
                    if (ticket.purchaseDate >= todayStr && (ticket.prizeLevel != "待开奖" || ticket.prizeAmount != 0.0)) {
                        ticket = ticket.copy(prizeLevel = "待开奖", prizeAmount = 0.0)
                        saveSingleTicket(context, ticket)
                    }
                }

                list.add(ticket)
            }
        }
        return list
    }

    /** 从 SQLite 按日期筛选读取指定玩法的彩票注单 */
    fun loadByDate(context: Context, game: LotteryGame, targetDate: String, latestDraw: DrawResult? = null): MutableList<LuckyTicket> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val allTickets = load(context, game, latestDraw)
        return allTickets.filter {
            it.purchaseDate == targetDate || (it.purchaseDate.isEmpty() && targetDate == todayStr)
        }.toMutableList()
    }

    /** 查询包含标记已购注单的所有历史日期集合 (仅统计 bought == true) */
    fun getDatesWithBoughtTickets(context: Context): Set<String> {
        val dates = mutableSetOf<String>()
        val dbHelper = WanXiangDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            true,
            WanXiangDatabaseHelper.TABLE_LUCKY_TICKETS,
            arrayOf(WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_DATE),
            "${WanXiangDatabaseHelper.COLUMN_TICKET_IS_BOUGHT} = 1",
            null, null, null, null, null
        )

        cursor.use { c ->
            while (c.moveToNext()) {
                val date = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_DATE))
                if (!date.isNullOrBlank()) {
                    dates.add(date)
                }
            }
        }
        return dates
    }

    /** 保存全量注单列表到 SQLite 并同步开奖日历提醒 */
    fun save(context: Context, game: LotteryGame, tickets: List<LuckyTicket>) {
        tickets.forEach { ticket ->
            saveSingleTicket(context, ticket.copy(gameName = game.name))
        }
    }

    /** 保存/更新单条注单 */
    fun saveSingleTicket(context: Context, ticket: LuckyTicket) {
        val dbHelper = WanXiangDatabaseHelper(context)
        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(WanXiangDatabaseHelper.COLUMN_TICKET_GAME, ticket.gameName)
            put(WanXiangDatabaseHelper.COLUMN_TICKET_RED_BALLS, ticket.mains.joinToString(","))
            put(WanXiangDatabaseHelper.COLUMN_TICKET_BLUE_BALLS, ticket.specials.joinToString(","))
            put(WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_DATE, ticket.purchaseDate)
            put(WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_TIME, ticket.purchaseTimestamp)
            put(WanXiangDatabaseHelper.COLUMN_TICKET_ISSUE, ticket.issue)
            put(WanXiangDatabaseHelper.COLUMN_TICKET_IS_BOUGHT, if (ticket.bought) 1 else 0)
            put(WanXiangDatabaseHelper.COLUMN_TICKET_DRAW_RED, ticket.drawMains?.joinToString(","))
            put(WanXiangDatabaseHelper.COLUMN_TICKET_DRAW_BLUE, ticket.drawSpecials?.joinToString(","))
            put(WanXiangDatabaseHelper.COLUMN_TICKET_PRIZE_LEVEL, ticket.prizeLevel)
            put(WanXiangDatabaseHelper.COLUMN_TICKET_PRIZE_AMOUNT, ticket.prizeAmount)
        }

        val rows = db.update(
            WanXiangDatabaseHelper.TABLE_LUCKY_TICKETS,
            values,
            "${WanXiangDatabaseHelper.COLUMN_TICKET_ID} = ?",
            arrayOf(ticket.id.toString())
        )

        if (rows == 0) {
            values.put(WanXiangDatabaseHelper.COLUMN_TICKET_ID, ticket.id)
            db.insert(WanXiangDatabaseHelper.TABLE_LUCKY_TICKETS, null, values)
        }

        // 如果标记已购买，自动与日历联动
        if (ticket.bought && ticket.purchaseDate.isNotEmpty()) {
            TodoRepository.linkLotteryReminderToCalendar(context, ticket, ticket.purchaseDate)
        }
    }

    /** 从 SQLite 删除单条注单 */
    fun deleteSingleTicket(context: Context, ticketId: Long) {
        val dbHelper = WanXiangDatabaseHelper(context)
        val db = dbHelper.writableDatabase
        db.delete(
            WanXiangDatabaseHelper.TABLE_LUCKY_TICKETS,
            "${WanXiangDatabaseHelper.COLUMN_TICKET_ID} = ?",
            arrayOf(ticketId.toString())
        )
    }

    /** 清空指定玩法注单 */
    fun clear(context: Context, game: LotteryGame) {
        val dbHelper = WanXiangDatabaseHelper(context)
        val db = dbHelper.writableDatabase
        db.delete(
            WanXiangDatabaseHelper.TABLE_LUCKY_TICKETS,
            "${WanXiangDatabaseHelper.COLUMN_TICKET_GAME} = ?",
            arrayOf(game.name)
        )
    }

    // 纯函数（供单测）
    internal fun toJson(tickets: List<LuckyTicket>): String = gson.toJson(tickets)

    internal fun fromJson(json: String): MutableList<LuckyTicket> = try {
        gson.fromJson(json, TypeToken.getParameterized(MutableList::class.java, LuckyTicket::class.java).type)
            ?: mutableListOf()
    } catch (_: Exception) {
        mutableListOf()
    }

    /** 从旧版 SharedPreferences 自动无缝迁移至 SQLite */
    private fun migrateOldPrefsIfNeeded(context: Context, game: LotteryGame) {
        val prefs = context.getSharedPreferences(OLD_PREF, Context.MODE_PRIVATE)
        val key = "tickets_${game.name}"
        val oldJson = prefs.getString(key, null) ?: return

        try {
            val oldList = fromJson(oldJson)
            if (oldList.isNotEmpty()) {
                val dbHelper = WanXiangDatabaseHelper(context)
                val db = dbHelper.writableDatabase

                db.beginTransaction()
                try {
                    oldList.forEach { ticket ->
                        val values = ContentValues().apply {
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_ID, ticket.id)
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_GAME, game.name)
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_RED_BALLS, ticket.mains.joinToString(","))
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_BLUE_BALLS, ticket.specials.joinToString(","))
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_DATE, ticket.purchaseDate)
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_PURCHASE_TIME, ticket.purchaseTimestamp)
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_ISSUE, ticket.issue)
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_IS_BOUGHT, if (ticket.bought) 1 else 0)
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_PRIZE_LEVEL, ticket.prizeLevel)
                            put(WanXiangDatabaseHelper.COLUMN_TICKET_PRIZE_AMOUNT, ticket.prizeAmount)
                        }
                        db.insertWithOnConflict(
                            WanXiangDatabaseHelper.TABLE_LUCKY_TICKETS,
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_IGNORE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            prefs.edit().remove(key).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
