package com.penn.myapplication

import com.penn.myapplication.lottery.LotteryApi
import com.penn.myapplication.lottery.LotteryGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryApiRepoParseTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.readBytes().decodeToString()

    @Test
    fun `解析真实双色球仓库JSON`() {
        val results = LotteryApi.parseRepoPayload(LotteryGame.SSQ, fixture("ssq.json"))
        assertTrue("至少20期, 实际 ${results.size}", results.size >= 20)
        val latest = results.first()
        assertEquals("26116", latest.issue)
        assertEquals("2026-10-11", latest.date)
        assertEquals(listOf(10, 19, 24, 27, 28, 33), latest.mains)
        assertEquals(listOf(12), latest.specials)
    }

    @Test
    fun `解析真实大乐透仓库JSON`() {
        val results = LotteryApi.parseRepoPayload(LotteryGame.DLT, fixture("dlt.json"))
        assertTrue(results.size >= 20)
        val latest = results.first()
        assertEquals("26115", latest.issue)
        assertEquals(listOf(12, 20, 32, 33, 35), latest.mains)
        assertEquals(listOf(6, 7), latest.specials)
    }

    @Test
    fun `非法行被过滤`() {
        val json = """
            {"game":"ssq","results":[
              {"issue":"26116","date":"2026-10-11","mains":[99,2,3,4,5,6],"specials":[7]},
              {"issue":"26115","date":"2026-10-09","mains":[1,2,3,4,5,6],"specials":[7]},
              {"issue":"26114","date":"bad-date","mains":[1,2,3,4,5,6],"specials":[7]}
            ]}
        """.trimIndent()
        val results = LotteryApi.parseRepoPayload(LotteryGame.SSQ, json)
        assertEquals(1, results.size)
        assertEquals("26115", results[0].issue)
    }

    @Test
    fun `畸形JSON返回空列表不崩溃`() {
        assertTrue(LotteryApi.parseRepoPayload(LotteryGame.SSQ, "not-json").isEmpty())
        assertTrue(LotteryApi.parseRepoPayload(LotteryGame.SSQ, "{}").isEmpty())
    }
}
