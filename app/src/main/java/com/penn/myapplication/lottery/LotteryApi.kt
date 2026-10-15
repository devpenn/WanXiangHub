package com.penn.myapplication.lottery

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 开奖数据拉取，多源回退：
 * 1. lottery-data 仓库（GitHub Actions + Playwright 每日抓官方，经 gh-proxy 访问）
 * 2. 500.com 历史开奖页（直连）
 * 调用方在两者都失败时可用 LotteryCache 读本地缓存兜底。
 */
object LotteryApi {

    private const val TAG = "LotteryApi"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    // lottery-data 仓库（github.com/devpenn/lottery-data）
    private const val DATA_REPO_RAW =
        "https://raw.githubusercontent.com/devpenn/lottery-data/main/data"
    // GitHub 加速代理，空串为 raw 直连兜底（海外网络）
    private val GH_PROXIES = listOf("https://gh-proxy.com", "https://gh.llkk.cc", "")

    private val gson = Gson()
    private val DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    @Throws(IOException::class)
    fun fetchHistory(game: LotteryGame, limit: Int = 30): List<DrawResult> {
        tryRepo(game)?.let {
            Log.i(TAG, "${game.name} 命中 lottery-data 仓库源")
            return it
        }
        Log.i(TAG, "${game.name} 仓库源不可用，回退 500.com")
        return fetchFrom500com(game, limit)
    }

    // ---------- 源一：lottery-data 仓库 JSON ----------

    private data class RepoPayload(
        @SerializedName("game") val game: String?,
        @SerializedName("results") val results: List<RepoDraw>?,
    )

    private data class RepoDraw(
        @SerializedName("issue") val issue: String?,
        @SerializedName("date") val date: String?,
        @SerializedName("mains") val mains: List<Int>?,
        @SerializedName("specials") val specials: List<Int>?,
    )

    /** 供单测：解析仓库 JSON 文本，畸形输入返回空列表 */
    internal fun parseRepoPayload(game: LotteryGame, json: String): List<DrawResult> {
        val payload = try {
            gson.fromJson(json, RepoPayload::class.java)
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()
        return payload.results.orEmpty().mapNotNull { d ->
            val issue = d.issue ?: return@mapNotNull null
            val date = d.date ?: return@mapNotNull null
            val mains = d.mains ?: return@mapNotNull null
            val specials = d.specials ?: return@mapNotNull null
            if (!DATE_REGEX.matches(date)) return@mapNotNull null
            val valid = mains.size == game.mainCount && mains.all { it in 1..game.mainMax } &&
                specials.size == game.specialCount && specials.all { it in 1..game.specialMax }
            if (!valid) return@mapNotNull null
            DrawResult(game, issue, date, mains, specials)
        }
    }

    private fun tryRepo(game: LotteryGame): List<DrawResult>? {
        for (proxy in GH_PROXIES) {
            val url = if (proxy.isEmpty()) DATA_REPO_RAW else "$proxy/$DATA_REPO_RAW"
            try {
                val body = httpGetString("${url}/${fileName(game)}.json")
                val results = parseRepoPayload(game, body)
                if (results.isNotEmpty()) return results
            } catch (e: IOException) {
                Log.w(TAG, "仓库源失败 ($proxy): ${e.message}")
            }
        }
        return null
    }

    private fun fileName(game: LotteryGame) = if (game == LotteryGame.SSQ) "ssq" else "dlt"

    // ---------- 源二：500.com 历史开奖页 ----------

    @Throws(IOException::class)
    private fun fetchFrom500com(game: LotteryGame, limit: Int): List<DrawResult> {
        val dir = if (game == LotteryGame.SSQ) "ssq" else "dlt"
        val url = "https://datachart.500.com/$dir/history/newinc/history.php?limit=$limit&sort=0"
        val html = httpGetString(url)
        val results = LotteryParser.parse(game, html)
        if (results.isEmpty()) throw IOException("开奖数据解析失败，请稍后重试")
        return results
    }

    // ---------- HTTP ----------

    private fun httpGetString(urlStr: String): String {
        var currentUrl = urlStr
        repeat(4) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                when (conn.responseCode) {
                    in 300..399 -> {
                        val location = conn.getHeaderField("Location")
                            ?: throw IOException("重定向缺少目标地址")
                        // Location 可能是相对路径，基于当前 URL 解析
                        currentUrl = if (location.startsWith("http")) {
                            location
                        } else {
                            URL(URL(currentUrl), location).toString()
                        }
                        return@repeat
                    }
                    HttpURLConnection.HTTP_OK -> {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        return decodeBody(bytes)
                    }
                    else -> throw IOException("服务器返回 HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("重定向次数过多")
    }

    /** 页面编码兜底：优先 UTF-8，出现替代符则按 GBK 重解 */
    private fun decodeBody(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) String(bytes, charset("GBK")) else utf8
    }
}
