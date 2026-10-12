package com.penn.myapplication.lottery

import com.google.gson.annotations.SerializedName

/** 机选的一注号码 + 是否已购买（实际投注时勾选标记） */
data class LuckyTicket(
    @SerializedName("id") val id: Long,
    @SerializedName("mains") val mains: List<Int>,
    @SerializedName("specials") val specials: List<Int>,
    @SerializedName("bought") val bought: Boolean,
) {
    fun mainText(): String = mains.joinToString(" ") { LuckyNumber.two(it) }
    fun specialText(): String = specials.joinToString(" ") { LuckyNumber.two(it) }
}
