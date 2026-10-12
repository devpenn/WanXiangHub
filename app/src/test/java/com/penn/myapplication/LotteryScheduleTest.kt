package com.penn.myapplication

import com.penn.myapplication.lottery.LotteryGame
import com.penn.myapplication.lottery.LotterySchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LotteryScheduleTest {

    private fun calendar(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }

    @Test
    fun `双色球 周一中午 下一期是周二`() {
        // 2026-10-12 是周一
        val next = LotteryGame.SSQ.nextDrawTime(calendar(2026, 10, 12, 12, 0))
        assertEquals(2026, next.get(Calendar.YEAR))
        assertEquals(10, next.get(Calendar.MONTH) + 1)
        assertEquals(13, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, next.get(Calendar.MINUTE))
    }

    @Test
    fun `双色球 周日白天 当天算下一期`() {
        val next = LotteryGame.SSQ.nextDrawTime(calendar(2026, 10, 11, 10, 0))
        assertEquals(11, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `双色球 周日开奖后 下一期是周二`() {
        val next = LotteryGame.SSQ.nextDrawTime(calendar(2026, 10, 11, 21, 30))
        assertEquals(13, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.TUESDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `大乐透 周一中午 当天21点25算下一期`() {
        val next = LotteryGame.DLT.nextDrawTime(calendar(2026, 10, 12, 12, 0))
        assertEquals(12, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(25, next.get(Calendar.MINUTE))
    }

    @Test
    fun `大乐透 周一开奖后 下一期是周三`() {
        val next = LotteryGame.DLT.nextDrawTime(calendar(2026, 10, 12, 22, 0))
        assertEquals(14, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `大乐透 周六开奖后 下一期是下周一`() {
        val next = LotteryGame.DLT.nextDrawTime(calendar(2026, 10, 10, 21, 30))
        assertEquals(12, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `倒计时格式化`() {
        val text = LotterySchedule.countdownText(
            calendar(2026, 10, 12, 10, 0),
            calendar(2026, 10, 13, 21, 15),
        )
        assertEquals("1天11时15分00秒", text)
    }

    @Test
    fun `倒计时负值兜底为0`() {
        val text = LotterySchedule.countdownText(
            calendar(2026, 10, 13, 22, 0),
            calendar(2026, 10, 13, 21, 15),
        )
        assertEquals("0天00时00分00秒", text)
    }

    @Test
    fun `下期开奖文案`() {
        val text = LotterySchedule.nextDrawText(LotteryGame.SSQ, calendar(2026, 10, 12, 12, 0))
        assertEquals("10月13日（周二）21:15", text)
    }
}
