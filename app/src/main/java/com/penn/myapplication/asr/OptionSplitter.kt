package com.penn.myapplication.asr

/**
 * 把语音识别出的整句话拆分成选项列表。
 *
 * 识别结果通常没有标点，因此主要靠口语分隔词切分；
 * 拆分只求"尽力而为"，拆错的由确认弹窗里用户手动调整兜底。
 */
object OptionSplitter {

    // 口语中常见的分隔连接词，注意长词放前面避免被短词截断
    private val SPOKEN_SEPARATORS =
        Regex("然后|还有|或者|还是|以及|另外|其次|接着|再来一个|再来|来一个|加一个|一个")

    private val PUNCTUATION = Regex("[，,、。．；;！!？?：:\\s]+")

    // 句首句尾常见的语气词
    private val FILLER_HEAD = Regex("^[嗯呃哦啊哈]+")
    private val FILLER_TAIL = Regex("[嗯呃哦啊哈]+$")

    fun split(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw
            .replace(SPOKEN_SEPARATORS, "，")
            .split(PUNCTUATION)
            .map { it.trim().replace(FILLER_HEAD, "").replace(FILLER_TAIL, "").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
