package com.penn.myapplication.lottery

import java.util.Calendar

/**
 * 彩种定义：号码规则 + 开奖日程。
 * 开奖日程为公开赛程，倒计时离线即可计算，不依赖网络。
 */
enum class LotteryGame(
    val title: String,
    val mainCount: Int,
    val mainMax: Int,
    val specialCount: Int,
    val specialMax: Int,
    val mainLabel: String,
    val specialLabel: String,
    internal val drawWeekdays: Set<Int>,
    private val drawHour: Int,
    private val drawMinute: Int,
) {
    SSQ(
        title = "双色球",
        mainCount = 6, mainMax = 33,
        specialCount = 1, specialMax = 16,
        mainLabel = "红球", specialLabel = "蓝球",
        // 每周二、四、日 21:15 开奖
        drawWeekdays = setOf(Calendar.TUESDAY, Calendar.THURSDAY, Calendar.SUNDAY),
        drawHour = 21, drawMinute = 15,
    ),
    DLT(
        title = "大乐透",
        mainCount = 5, mainMax = 35,
        specialCount = 2, specialMax = 12,
        mainLabel = "前区", specialLabel = "后区",
        // 每周一、三、六 21:25 开奖
        drawWeekdays = setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.SATURDAY),
        drawHour = 21, drawMinute = 25,
    );

    /** 距 [from] 最近的一个未开奖时刻（含当天：当天未到开奖点则算当天） */
    fun nextDrawTime(from: Calendar): Calendar {
        val next = (from.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, drawHour)
            set(Calendar.MINUTE, drawMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var guard = 0
        while (next.get(Calendar.DAY_OF_WEEK) !in drawWeekdays || next <= from) {
            next.add(Calendar.DAY_OF_MONTH, 1)
            if (++guard > 8) break // 一周内必有开奖，防御性兜底
        }
        return next
    }

    /** 开奖时刻文案，如 "21:15" */
    fun drawTimeText(): String = "%02d:%02d".format(drawHour, drawMinute)

    companion object {
        private val WEEKDAY_NAMES = mapOf(
            Calendar.SUNDAY to "周日",
            Calendar.MONDAY to "周一",
            Calendar.TUESDAY to "周二",
            Calendar.WEDNESDAY to "周三",
            Calendar.THURSDAY to "周四",
            Calendar.FRIDAY to "周五",
            Calendar.SATURDAY to "周六",
        )

        fun weekdayName(dayOfWeek: Int): String = WEEKDAY_NAMES[dayOfWeek] ?: ""
    }
}

/** 机选的一注号码 */
data class LuckyNumber(
    val game: LotteryGame,
    val mains: List<Int>,
    val specials: List<Int>,
) {
    fun mainText(): String = mains.joinToString(" ") { two(it) }
    fun specialText(): String = specials.joinToString(" ") { two(it) }

    companion object {
        fun two(n: Int): String = if (n < 10) "0$n" else "$n"
    }
}

/** 一期开奖结果 */
data class DrawResult(
    val game: LotteryGame,
    val issue: String,      // 期号，如 26116
    val date: String,       // 开奖日期 yyyy-MM-dd
    val mains: List<Int>,
    val specials: List<Int>,
)
