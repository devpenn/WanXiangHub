package com.penn.myapplication.lottery

import java.util.Calendar

/** 倒计时展示工具 */
object LotterySchedule {

    /** 把两个时刻的间隔格式化成 "X天XX时XX分XX秒"（非负） */
    fun countdownText(from: Calendar, until: Calendar): String {
        var seconds = ((until.timeInMillis - from.timeInMillis) / 1000).coerceAtLeast(0)
        val days = seconds / 86400
        seconds %= 86400
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60
        return "%d天%02d时%02d分%02d秒".format(days, hours, minutes, seconds)
    }

    /** "10月13日（周二）21:15" 风格的下期开奖时间描述 */
    fun nextDrawText(game: LotteryGame, from: Calendar): String {
        val next = game.nextDrawTime(from)
        val weekdays = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        val date = "${next.get(Calendar.MONTH) + 1}月${next.get(Calendar.DAY_OF_MONTH)}日"
        val week = weekdays[next.get(Calendar.DAY_OF_WEEK) - 1]
        val time = "%02d:%02d".format(next.get(Calendar.HOUR_OF_DAY), next.get(Calendar.MINUTE))
        return "$date（$week）$time"
    }
}
