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
import com.penn.myapplication.R
import com.penn.myapplication.WarmPopupActivity

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
            ToolItem("经典猜数字", R.drawable.ic_dashboard, ClassicGameActivity::class.java),
            ToolItem("温馨弹窗", R.drawable.ic_notifications, WarmPopupActivity::class.java),
            // 未来可以在这里轻松添加更多工具
             ToolItem("待开发功能", R.drawable.ic_home, null)
        )

        // 设置 RecyclerView
        rvTools.layoutManager = GridLayoutManager(context, 2) // 2列网格
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
