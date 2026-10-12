package com.penn.myapplication

import com.penn.myapplication.lottery.LotteryGame
import com.penn.myapplication.lottery.LotteryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryParserTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.readBytes().decodeToString()

    @Test
    fun `解析真实双色球历史页快照`() {
        val results = LotteryParser.parse(LotteryGame.SSQ, fixture("ssq_history_sample.html"))
        assertTrue("至少解析出25期, 实际 ${results.size}", results.size >= 25)

        val latest = results.first()
        assertEquals("26116", latest.issue)
        assertEquals("2026-10-11", latest.date)
        assertEquals(listOf(10, 19, 24, 27, 28, 33), latest.mains)
        assertEquals(listOf(12), latest.specials)

        // 期号去重且按页面顺序（新→旧）
        assertEquals(results.size, results.map { it.issue }.distinct().size)
        assertTrue(results[0].issue > results.last().issue)
    }

    @Test
    fun `解析真实大乐透历史页快照`() {
        val results = LotteryParser.parse(LotteryGame.DLT, fixture("dlt_history_sample.html"))
        assertTrue("至少解析出25期, 实际 ${results.size}", results.size >= 25)

        val latest = results.first()
        assertEquals("26115", latest.issue)
        assertEquals("2026-10-10", latest.date)
        assertEquals(listOf(12, 20, 32, 33, 35), latest.mains)
        assertEquals(listOf(6, 7), latest.specials)
    }

    @Test
    fun `双色球快乐星期天列不会污染号码区`() {
        // 周日开奖时蓝球后有"快乐星期天"两位数列，不应被吸进号码
        val html = """
            <table>
            <tr class="t_tr1"><td>1</td><td>26117</td><td>01</td><td>05</td><td>12</td><td>17</td><td>23</td><td>31</td><td>09</td><td>06</td><td>936,031,217</td><td>5</td><td>8,754,806</td><td>64</td><td>366,680</td><td>363,128,812</td><td>2026-10-15</td></tr>
            </table>
        """.trimIndent()
        val results = LotteryParser.parse(LotteryGame.SSQ, html)
        assertEquals(1, results.size)
        assertEquals(listOf(1, 5, 12, 17, 23, 31), results[0].mains)
        assertEquals(listOf(9), results[0].specials)
    }

    @Test
    fun `脏数据行被跳过`() {
        val html = """
            <table>
            <tr class="t_tr1"><td>2</td><td>26116</td><td>10</td><td>19</td><td>24</td><td>27</td><td>28</td><td>33</td><td>99</td><td>x</td><td>2026-10-11</td></tr>
            <tr class="t_tr1"><td>1</td><td>26115</td><td>01</td><td>02</td><td>03</td><td>04</td><td>05</td><td>06</td><td>07</td><td>y</td><td>2026-10-09</td></tr>
            </table>
        """.trimIndent()
        val results = LotteryParser.parse(LotteryGame.SSQ, html)
        // 蓝球99越界 → 该行跳过；合法行正常解析
        assertEquals(1, results.size)
        assertEquals("26115", results[0].issue)
    }
}
