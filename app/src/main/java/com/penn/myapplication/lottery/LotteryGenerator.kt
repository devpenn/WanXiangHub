package com.penn.myapplication.lottery

import java.security.SecureRandom

/** 机选号码生成器 */
object LotteryGenerator {

    private val random = SecureRandom()

    fun generate(game: LotteryGame): LuckyNumber = LuckyNumber(
        game = game,
        mains = pick(game.mainCount, game.mainMax),
        specials = pick(game.specialCount, game.specialMax),
    )

    /** 从 1..max 中不重复地抽 count 个，升序返回 */
    private fun pick(count: Int, max: Int): List<Int> {
        val pool = (1..max).toMutableList()
        val picked = mutableListOf<Int>()
        repeat(count) {
            picked.add(pool.removeAt(random.nextInt(pool.size)))
        }
        return picked.sorted()
    }
}
