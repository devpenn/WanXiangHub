package com.penn.myapplication.ui.tools

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.penn.myapplication.ClassicGameActivity
import com.penn.myapplication.LuckyActivity
import com.penn.myapplication.MarqueeActivity
import com.penn.myapplication.PomodoroTimerActivity
import com.penn.myapplication.QrGeneratorActivity
import com.penn.myapplication.R
import com.penn.myapplication.RandomPickerActivity
import com.penn.myapplication.WarmPopupActivity
import com.penn.myapplication.WoodenFishActivity

class ToolsFragment : Fragment() {

    // 定义工具数据模型
    data class ToolItem(
        val name: String,
        val iconRes: Int,
        val targetActivity: Class<*>?
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_tools, container, false)
        
        val rvTools = root.findViewById<RecyclerView>(R.id.rv_tools)
        
        // 准备数据
        val toolsList = listOf(
            ToolItem("经典猜数字", R.drawable.ic_guess, ClassicGameActivity::class.java),
            ToolItem("温馨弹窗", R.drawable.ic_popper, WarmPopupActivity::class.java),
            ToolItem("手持弹幕", R.drawable.ic_marquee_banner, MarqueeActivity::class.java),
            ToolItem("电子木鱼", R.drawable.ic_woodenfish, WoodenFishActivity::class.java),
            ToolItem("随机决定", R.drawable.ic_wheel, RandomPickerActivity::class.java),
            ToolItem("专注番茄钟", R.drawable.ic_pomodoro, PomodoroTimerActivity::class.java),
            ToolItem("二维码生成", R.drawable.ic_qrcode, QrGeneratorActivity::class.java),
            ToolItem("今日好运", R.drawable.ic_clover, LuckyActivity::class.java),
            // 未来可以在这里轻松添加更多工具
            ToolItem("待开发功能", R.drawable.ic_dev_plus, null)
        )

        // 设置 RecyclerView
        rvTools.layoutManager = GridLayoutManager(context, 4) // 4列网格
        rvTools.adapter = ToolsAdapter(requireContext(), toolsList)
        
        return root
    }

    // RecyclerView Adapter
    inner class ToolsAdapter(
        private val context: Context,
        private val items: List<ToolItem>
    ) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

        inner class ToolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivIcon: ImageView = itemView.findViewById(R.id.iv_tool_icon)
            val tvName: TextView = itemView.findViewById(R.id.tv_tool_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_tool_grid, parent, false)
            return ToolViewHolder(view)
        }

        override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.ivIcon.setImageResource(item.iconRes)
            
            holder.itemView.setOnClickListener {
                if (item.targetActivity != null) {
                    val intent = Intent(context, item.targetActivity)
                    context.startActivity(intent)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
