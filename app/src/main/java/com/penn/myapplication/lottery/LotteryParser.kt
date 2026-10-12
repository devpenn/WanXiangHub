package com.penn.myapplication.lottery

/**
 * 500.com 历史开奖页解析器（纯函数，便于单测）。
 *
 * 页面结构：若干 `<tr class="t_tr1">` 数据行，单元格依次为
 *  [杂项] [期号] [主号码×N] [特殊号码×M] [奖池/奖金等统计...] [yyyy-MM-dd]
 * 其中双色球 6+1，大乐透 5+2 —— 两种玩法号码区都是 7 个两位数单元格。
 * 解析按"期号锚点 + 两位数号码 + 日期锚点"容错提取，不依赖绝对列下标。
 */
object LotteryParser {

    private val ROW_REGEX = Regex("<tr\\s+class=\"t_tr1\">(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    private val TD_REGEX = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
    private val TAG_REGEX = Regex("<[^>]+>")
    private val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun parse(game: LotteryGame, html: String): List<DrawResult> {
        val results = mutableListOf<DrawResult>()
        val seenIssues = mutableSetOf<String>()
        for (row in ROW_REGEX.findAll(html)) {
            val cells = TD_REGEX.findAll(row.groupValues[1])
                .map { it.groupValues[1].replace(TAG_REGEX, "").replace("&nbsp;", "").trim() }
                .toList()
            parseRow(game, cells)?.let { draw ->
                if (seenIssues.add(draw.issue)) results.add(draw)
            }
        }
        return results
    }

    private fun parseRow(game: LotteryGame, cells: List<String>): DrawResult? {
        val date = cells.lastOrNull { DATE_REGEX.matches(it) } ?: return null
        val issueIndex = cells.indexOfFirst { it.length in 4..7 && it.all(Char::isDigit) }
        if (issueIndex < 0) return null
        val issue = cells[issueIndex]

        // 期号之后紧邻的固定个数两位数单元格即号码区（双色球 6+1、大乐透 5+2）。
        // 不能用 takeWhile：双色球"快乐星期天"列在蓝球之后也可能出现两位数，会多吸一个
        val balls = cells.drop(issueIndex + 1)
            .filter { it.length == 2 && it.all(Char::isDigit) }
            .take(game.mainCount + game.specialCount)
            .map { it.toInt() }
        if (balls.size != game.mainCount + game.specialCount) return null

        val mains = balls.subList(0, game.mainCount)
        val specials = balls.subList(game.mainCount, balls.size)

        val mainsValid = mains.all { it in 1..game.mainMax } &&
            mains.sorted() == mains && mains.distinct().size == game.mainCount
        val specialsValid = specials.all { it in 1..game.specialMax } &&
            specials.sorted() == specials && specials.distinct().size == game.specialCount
        if (!mainsValid || !specialsValid) return null

        return DrawResult(
            game = game,
            issue = issue,
            date = date,
            mains = mains,
            specials = specials,
        )
    }
}
