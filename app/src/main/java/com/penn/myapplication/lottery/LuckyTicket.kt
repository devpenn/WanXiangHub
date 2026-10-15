package com.penn.myapplication.lottery

import com.google.gson.annotations.SerializedName

/** 机选/自选彩票注单数据模型 (含 SQLite 字段与自动中奖算效) */
data class LuckyTicket(
    @SerializedName("id") val id: Long,
    @SerializedName("gameName") val gameName: String = "SSQ",
    @SerializedName("mains") val mains: List<Int>,
    @SerializedName("specials") val specials: List<Int>,
    @SerializedName("bought") val bought: Boolean = false,
    @SerializedName("purchaseDate") val purchaseDate: String = "",
    @SerializedName("purchaseTimestamp") val purchaseTimestamp: Long = System.currentTimeMillis(),
    @SerializedName("issue") val issue: String? = null,
    @SerializedName("drawMains") val drawMains: List<Int>? = null,
    @SerializedName("drawSpecials") val drawSpecials: List<Int>? = null,
    @SerializedName("prizeLevel") val prizeLevel: String = "待开奖",
    @SerializedName("prizeAmount") val prizeAmount: Double = 0.0
) {
    fun mainText(): String = mains.joinToString(" ") { LuckyNumber.two(it) }
    fun specialText(): String = specials.joinToString(" ") { LuckyNumber.two(it) }

    fun isWon(): Boolean = prizeAmount > 0.0 || (prizeLevel != "未中奖" && prizeLevel != "待开奖")
}
