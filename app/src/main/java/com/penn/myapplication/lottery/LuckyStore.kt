package com.penn.myapplication.lottery

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.atomic.AtomicLong

/**
 * 注单本地持久化（每个玩法一份列表）。
 * 机选的号码要"拿着去买"，所以必须落盘：退出 App、重启手机都不丢。
 */
object LuckyStore {

    private const val PREF = "lucky_tickets"
    private val gson = Gson()

    private val idSeq = AtomicLong(System.currentTimeMillis())

    fun newId(): Long = idSeq.incrementAndGet()

    fun load(context: Context, game: LotteryGame): MutableList<LuckyTicket> = try {
        val json = prefs(context).getString(key(game), null) ?: return mutableListOf()
        fromJson(json)
    } catch (_: Exception) {
        mutableListOf()
    }

    fun save(context: Context, game: LotteryGame, tickets: List<LuckyTicket>) {
        prefs(context).edit().putString(key(game), toJson(tickets)).apply()
    }

    fun clear(context: Context, game: LotteryGame) {
        prefs(context).edit().remove(key(game)).apply()
    }

    // 纯函数（供单测）
    internal fun toJson(tickets: List<LuckyTicket>): String = gson.toJson(tickets)

    internal fun fromJson(json: String): MutableList<LuckyTicket> = try {
        gson.fromJson(json, TypeToken.getParameterized(MutableList::class.java, LuckyTicket::class.java).type)
            ?: mutableListOf()
    } catch (_: Exception) {
        mutableListOf()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun key(game: LotteryGame) = "tickets_${game.name}"
}
