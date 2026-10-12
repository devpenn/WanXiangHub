package com.penn.myapplication

import com.penn.myapplication.lottery.LotteryGame
import com.penn.myapplication.lottery.LotteryGenerator
import com.penn.myapplication.lottery.LuckyNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryGeneratorTest {

    @Test
    fun `双色球号码符合规则`() {
        repeat(1000) {
            val n = LotteryGenerator.generate(LotteryGame.SSQ)
            assertEquals(6, n.mains.size)
            assertEquals(1, n.specials.size)
            assertTrue(n.mains.all { it in 1..33 })
            assertTrue(n.specials.all { it in 1..16 })
            assertEquals(6, n.mains.distinct().size)
            assertEquals(n.mains, n.mains.sorted())
            // 红蓝区独立，允许数字重叠
        }
    }

    @Test
    fun `大乐透号码符合规则`() {
        repeat(1000) {
            val n = LotteryGenerator.generate(LotteryGame.DLT)
            assertEquals(5, n.mains.size)
            assertEquals(2, n.specials.size)
            assertTrue(n.mains.all { it in 1..35 })
            assertTrue(n.specials.all { it in 1..12 })
            assertEquals(5, n.mains.distinct().size)
            assertEquals(2, n.specials.distinct().size)
            assertEquals(n.mains, n.mains.sorted())
            assertEquals(n.specials, n.specials.sorted())
        }
    }

    @Test
    fun `补零格式化`() {
        assertEquals("01", LuckyNumber.two(1))
        assertEquals("16", LuckyNumber.two(16))
        assertEquals("33", LuckyNumber.two(33))
    }
}
