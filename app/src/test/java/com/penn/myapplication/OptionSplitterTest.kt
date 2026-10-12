package com.penn.myapplication

import com.penn.myapplication.asr.OptionSplitter
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionSplitterTest {

    @Test
    fun `口语连接词拆分`() {
        // 语音识别结果通常没有标点，靠"然后/还有/或者"等连接词切分
        assertEquals(listOf("火锅", "烧烤", "日料"), OptionSplitter.split("火锅然后烧烤还有日料"))
        assertEquals(listOf("看电影", "逛街"), OptionSplitter.split("看电影或者逛街"))
        assertEquals(listOf("苹果", "香蕉", "橘子"), OptionSplitter.split("苹果还是香蕉以及橘子"))
    }

    @Test
    fun `带标点的文本正常拆分`() {
        assertEquals(
            listOf("火锅", "烧烤", "日料"),
            OptionSplitter.split("火锅，烧烤、日料")
        )
        assertEquals(
            listOf("火锅", "烧烤"),
            OptionSplitter.split("火锅,烧烤")
        )
    }

    @Test
    fun `一个字头的列举也能拆`() {
        assertEquals(listOf("火锅", "烧烤"), OptionSplitter.split("一个火锅一个烧烤"))
        assertEquals(listOf("日料", "西餐"), OptionSplitter.split("再来一个日料加一个西餐"))
    }

    @Test
    fun `语气词被清理`() {
        assertEquals(listOf("火锅", "烧烤"), OptionSplitter.split("嗯火锅，烧烤啊"))
    }

    @Test
    fun `重复选项去重`() {
        assertEquals(listOf("火锅", "烧烤"), OptionSplitter.split("火锅然后火锅还有烧烤"))
    }

    @Test
    fun `空输入返回空列表`() {
        assertEquals(emptyList<String>(), OptionSplitter.split(""))
        assertEquals(emptyList<String>(), OptionSplitter.split("   "))
    }

    @Test
    fun `无分隔词时整体作为一个选项`() {
        // 拆不出来就整句给用户，由确认弹窗手动调整兜底
        assertEquals(listOf("麻酱拌面"), OptionSplitter.split("麻酱拌面"))
    }
}
