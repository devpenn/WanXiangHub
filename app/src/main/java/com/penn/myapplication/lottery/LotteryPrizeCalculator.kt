package com.penn.myapplication.lottery

/** 中奖计算结果 */
data class PrizeResult(
    val level: String,      // 奖级 (如 "六等奖", "未中奖")
    val amount: Double,     // 奖金金额 (如 5.0, 3000.0)
    val redMatchCount: Int,  // 红球中奖个数
    val blueMatchCount: Int  // 蓝球中奖个数
)

/** 自动化中奖计算器 (支持双色球与大乐透官方规则) */
object LotteryPrizeCalculator {

    fun calculate(
        game: LotteryGame,
        userMains: List<Int>,
        userSpecials: List<Int>,
        drawMains: List<Int>,
        drawSpecials: List<Int>
    ): PrizeResult {
        val redMatch = userMains.count { drawMains.contains(it) }
        val blueMatch = userSpecials.count { drawSpecials.contains(it) }

        return when (game) {
            LotteryGame.SSQ -> calculateSsq(redMatch, blueMatch)
            LotteryGame.DLT -> calculateDlt(redMatch, blueMatch)
        }
    }

    private fun calculateSsq(r: Int, b: Int): PrizeResult {
        return when {
            r == 6 && b == 1 -> PrizeResult("一等奖", 10000000.0, r, b)
            r == 6 && b == 0 -> PrizeResult("二等奖", 200000.0, r, b)
            r == 5 && b == 1 -> PrizeResult("三等奖", 3000.0, r, b)
            (r == 5 && b == 0) || (r == 4 && b == 1) -> PrizeResult("四等奖", 200.0, r, b)
            (r == 4 && b == 0) || (r == 3 && b == 1) -> PrizeResult("五等奖", 10.0, r, b)
            (r == 2 && b == 1) || (r == 1 && b == 1) || (r == 0 && b == 1) -> PrizeResult("六等奖", 5.0, r, b)
            else -> PrizeResult("未中奖", 0.0, r, b)
        }
    }

    private fun calculateDlt(f: Int, b: Int): PrizeResult {
        return when {
            f == 5 && b == 2 -> PrizeResult("一等奖", 10000000.0, f, b)
            f == 5 && b == 1 -> PrizeResult("二等奖", 150000.0, f, b)
            f == 5 && b == 0 -> PrizeResult("三等奖", 10000.0, f, b)
            f == 4 && b == 2 -> PrizeResult("四等奖", 3000.0, f, b)
            f == 4 && b == 1 -> PrizeResult("五等奖", 300.0, f, b)
            f == 3 && b == 2 -> PrizeResult("六等奖", 200.0, f, b)
            f == 4 && b == 0 -> PrizeResult("七等奖", 100.0, f, b)
            (f == 3 && b == 1) || (f == 2 && b == 2) -> PrizeResult("八等奖", 15.0, f, b)
            (f == 3 && b == 0) || (f == 1 && b == 2) || (f == 2 && b == 1) || (f == 0 && b == 2) -> PrizeResult("九等奖", 5.0, f, b)
            else -> PrizeResult("未中奖", 0.0, f, b)
        }
    }
}
