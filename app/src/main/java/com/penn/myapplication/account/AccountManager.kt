package com.penn.myapplication.account

import android.content.Context

/** 账号个人资料数据模型 */
data class UserProfile(
    val nickname: String,
    val userId: String,
    val signature: String,
    val avatarUri: String?,
    val memberTag: String,
    val loginDays: Int
)

/** 账号系统管理器 */
object AccountManager {

    fun getUserProfile(context: Context): UserProfile {
        val prefs = context.getSharedPreferences("AccountPrefs", Context.MODE_PRIVATE)
        val nickname = prefs.getString("nickname", "万象用户") ?: "万象用户"
        val userId = prefs.getString("user_id", "WX-88482026") ?: "WX-88482026"
        val signature = prefs.getString("signature", "万物皆有灵，万象尽更新") ?: "万物皆有灵，万象尽更新"
        val avatarUri = prefs.getString("avatar_uri", null)
        val memberTag = prefs.getString("member_tag", "PRO 极客") ?: "PRO 极客"
        val loginDays = prefs.getInt("login_days", 12)

        return UserProfile(nickname, userId, signature, avatarUri, memberTag, loginDays)
    }

    fun saveUserProfile(
        context: Context,
        nickname: String,
        signature: String,
        avatarUri: String? = null
    ) {
        val prefs = context.getSharedPreferences("AccountPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("nickname", nickname)
            putString("signature", signature)
            if (avatarUri != null) {
                putString("avatar_uri", avatarUri)
            }
            apply()
        }
    }
}
