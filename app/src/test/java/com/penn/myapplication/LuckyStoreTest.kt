package com.penn.myapplication

import com.penn.myapplication.lottery.LotteryGame
import com.penn.myapplication.lottery.LuckyStore
import com.penn.myapplication.lottery.LuckyTicket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuckyStoreTest {

    @Test
    fun `注单JSON序列化往返`() {
        val tickets = listOf(
            LuckyTicket(1, listOf(1, 5, 12, 17, 23, 31), listOf(9), bought = false),
            LuckyTicket(2, listOf(3, 8, 14, 22, 28, 33), listOf(15), bought = true),
        )
        val restored = LuckyStore.fromJson(LuckyStore.toJson(tickets))
        assertEquals(2, restored.size)
        assertEquals(tickets[0], restored[0])
        assertEquals(tickets[1], restored[1])
        assertTrue(restored[1].bought)
    }

    @Test
    fun `畸形JSON返回空列表`() {
        assertTrue(LuckyStore.fromJson("not-json").isEmpty())
        assertTrue(LuckyStore.fromJson("null").isEmpty())
    }

    @Test
    fun `空列表往返`() {
        assertEquals(0, LuckyStore.fromJson(LuckyStore.toJson(emptyList())).size)
    }

    @Test
    fun `序列化不携带game字段_玩法由存储key区分`() {
        val dlt = LuckyTicket(9, listOf(3, 11, 22, 28, 35), listOf(4, 11), bought = false)
        val json = LuckyStore.toJson(listOf(dlt))
        assertEquals(dlt, LuckyStore.fromJson(json)[0])
        assertTrue(!json.contains("game"))
    }
}
