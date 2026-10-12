package com.penn.myapplication.lottery

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 开奖数据本地缓存（保底层）。
 * 每次查询成功后写入；所有网络源失败时读出，App 永不空屏。
 */
object LotteryCache {

    private const val PREF = "lottery_cache"
    private val gson = Gson()

    private data class CachePayload(
        @SerializedName("fetchedAt") val fetchedAt: String,
        @SerializedName("results") val results: List<DrawResult>,
    )

    fun save(context: Context, game: LotteryGame, results: List<DrawResult>) {
        if (results.isEmpty()) return
        val payload = CachePayload(
            fetchedAt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                .format(java.util.Date()),
            results = results,
        )
        prefs(context).edit().putString(key(game), gson.toJson(payload)).apply()
    }

    fun load(context: Context, game: LotteryGame): List<DrawResult>? = try {
        val json = prefs(context).getString(key(game), null) ?: return null
        gson.fromJson(json, CachePayload::class.java)?.results?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    /** 缓存写入时间（用于"离线缓存·更新于 xx"展示），无缓存返回 null */
    fun savedAt(context: Context, game: LotteryGame): String? = try {
        val json = prefs(context).getString(key(game), null) ?: return null
        gson.fromJson(json, CachePayload::class.java)?.fetchedAt
    } catch (_: Exception) {
        null
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun key(game: LotteryGame) = "cache_${game.name}"
}
