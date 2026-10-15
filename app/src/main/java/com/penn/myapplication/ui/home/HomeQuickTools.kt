package com.penn.myapplication.ui.home

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.penn.myapplication.ClassicGameActivity
import com.penn.myapplication.LuckyActivity
import com.penn.myapplication.MarqueeActivity
import com.penn.myapplication.PomodoroTimerActivity
import com.penn.myapplication.QrGeneratorActivity
import com.penn.myapplication.R
import com.penn.myapplication.RandomPickerActivity
import com.penn.myapplication.WarmPopupActivity
import com.penn.myapplication.WoodenFishActivity

/** 首页快捷功能气泡数据模型 */
data class QuickToolItem(
    val id: String,
    val name: String,
    val iconRes: Int,
    val targetActivityClass: Class<*>
)

/** 首页快捷气泡中央注册与自定义配置管理类 */
object HomeQuickTools {

    /** 应用内所有可供选择的快捷工具列表（采用一致官方名称） */
    val ALL_TOOLS = listOf(
        QuickToolItem("woodenfish", "电子木鱼", R.drawable.ic_woodenfish, WoodenFishActivity::class.java),
        QuickToolItem("clover", "今日好运", R.drawable.ic_clover, LuckyActivity::class.java),
        QuickToolItem("pomodoro", "专注番茄钟", R.drawable.ic_pomodoro, PomodoroTimerActivity::class.java),
        QuickToolItem("random", "随机决定", R.drawable.ic_wheel, RandomPickerActivity::class.java),
        QuickToolItem("marquee", "手持弹幕", R.drawable.ic_marquee_banner, MarqueeActivity::class.java),
        QuickToolItem("qrcode", "二维码生成", R.drawable.ic_qrcode, QrGeneratorActivity::class.java),
        QuickToolItem("guess", "经典猜数字", R.drawable.ic_guess, ClassicGameActivity::class.java),
        QuickToolItem("popper", "温馨弹窗", R.drawable.ic_popper, WarmPopupActivity::class.java)
    )

    private val DEFAULT_TOOL_IDS = listOf("woodenfish", "clover", "pomodoro", "random")

    /** 读取用户自定义选择的快捷工具 */
    fun getSelectedTools(context: Context): List<QuickToolItem> {
        val prefs = context.getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        val idsStr = prefs.getString("quick_tools_ids", null) ?: DEFAULT_TOOL_IDS.joinToString(",")
        val ids = idsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val selected = ids.mapNotNull { id -> ALL_TOOLS.find { it.id == id } }
        return if (selected.isNotEmpty()) selected else ALL_TOOLS.take(4)
    }

    /** 保存用户自定义选择的快捷工具 */
    fun saveSelectedTools(context: Context, tools: List<QuickToolItem>) {
        val prefs = context.getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        val idsStr = tools.joinToString(",") { it.id }
        prefs.edit().putString("quick_tools_ids", idsStr).apply()
    }

    /** 弹出用户自定义配置对话框 */
    fun showCustomDialog(context: Context, onSaved: () -> Unit) {
        val currentSelected = getSelectedTools(context)
        val selectedIndices = BooleanArray(ALL_TOOLS.size) { index ->
            currentSelected.any { it.id == ALL_TOOLS[index].id }
        }

        val toolNames = ALL_TOOLS.map { it.name }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("自定义首页快捷气泡 (可选1~6项)")
            .setMultiChoiceItems(toolNames, selectedIndices) { _, which, isChecked ->
                selectedIndices[which] = isChecked
            }
            .setPositiveButton("保存确定") { _, _ ->
                val newSelectedTools = ALL_TOOLS.filterIndexed { index, _ -> selectedIndices[index] }
                if (newSelectedTools.isEmpty()) {
                    Toast.makeText(context, "请至少选择1个快捷气泡", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newSelectedTools.size > 6) {
                    Toast.makeText(context, "最多选择6个快捷气泡", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveSelectedTools(context, newSelectedTools)
                Toast.makeText(context, "首页快捷气泡已更新", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
