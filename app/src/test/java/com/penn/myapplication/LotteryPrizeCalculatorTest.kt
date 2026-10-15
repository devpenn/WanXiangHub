package com.penn.myapplication

import com.penn.myapplication.lottery.LotteryGame
import com.penn.myapplication.lottery.LotteryPrizeCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class LotteryPrizeCalculatorTest {

    @Test
    fun testSsqFirstPrize() {
        val userMains = listOf(1, 2, 3, 4, 5, 6)
        val userSpecials = listOf(7)
        val drawMains = listOf(1, 2, 3, 4, 5, 6)
        val drawSpecials = listOf(7)

        val result = LotteryPrizeCalculator.calculate(
            LotteryGame.SSQ, userMains, userSpecials, drawMains, drawSpecials
        )
        assertEquals("一等奖", result.level)
        assertEquals(10000000.0, result.amount, 0.01)
    }

    @Test
    fun testSsqSixthPrizeBlueOnly() {
        val userMains = listOf(10, 11, 12, 13, 14, 15)
        val userSpecials = listOf(7)
        val drawMains = listOf(1, 2, 3, 4, 5, 6)
        val drawSpecials = listOf(7)

        val result = LotteryPrizeCalculator.calculate(
            LotteryGame.SSQ, userMains, userSpecials, drawMains, drawSpecials
        )
        assertEquals("六等奖", result.level)
        assertEquals(5.0, result.amount, 0.01)
    }

    @Test
    fun testDltNinthPrize() {
        val userMains = listOf(1, 2, 3, 10, 11)
        val userSpecials = listOf(1, 2)
        val drawMains = listOf(1, 2, 3, 20, 21)
        val drawSpecials = listOf(8, 9)

        val result = LotteryPrizeCalculator.calculate(
            LotteryGame.DLT, userMains, userSpecials, drawMains, drawSpecials
        )
        assertEquals("九等奖", result.level)
        assertEquals(5.0, result.amount, 0.01)
    }
}
