package com.penn.myapplication.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.nlf.calendar.Solar
import com.penn.myapplication.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var ivWallpaper: ImageView
    private lateinit var vvWallpaper: VideoView
    private lateinit var viewGradientOverlay: View
    private lateinit var tvGreeting: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvLunar: TextView
    private lateinit var divider: View
    private lateinit var tvCustomText: TextView
    private lateinit var layoutFestival: View
    private lateinit var tvFestival: TextView
    private lateinit var rootContainer: View

    // 状态防刷闪烁缓存
    private var currentWallpaperType: String? = null
    private var currentWallpaperUri: String? = null
    private var currentQuickToolsIds: String? = null
    private var currentCustomText: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        rootContainer = root

        ivWallpaper = root.findViewById(R.id.iv_wallpaper)
        vvWallpaper = root.findViewById(R.id.vv_wallpaper)
        viewGradientOverlay = root.findViewById(R.id.view_gradient_overlay)
        tvGreeting = root.findViewById(R.id.tv_greeting)
        tvDate = root.findViewById(R.id.tv_date)
        tvLunar = root.findViewById(R.id.tv_lunar)
        divider = root.findViewById(R.id.divider)
        tvCustomText = root.findViewById(R.id.tv_custom_text)
        layoutFestival = root.findViewById(R.id.layout_festival)
        tvFestival = root.findViewById(R.id.tv_festival)

        setupGreeting()
        setupLunarInfo()

        return root
    }

    override fun onResume() {
        super.onResume()
        // 恢复页面时智能增量刷新
        setupWallpaper()
        setupCustomText()
        setupQuickTools(rootContainer)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // 解决 hide/show 切换模式下切回主页时，实时挂载最新修改的自定义文字、壁纸与快捷组件
            setupWallpaper()
            setupCustomText()
            setupQuickTools(rootContainer)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 切 Tab 时旧 View 树被销毁，需清空 View 绑定缓存，确保新 View 树顺利渲染挂载
        currentWallpaperType = null
        currentWallpaperUri = null
        currentQuickToolsIds = null
        currentCustomText = null
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..10 -> "早上好"
            in 11..12 -> "中午好"
            in 13..17 -> "下午好"
            else -> "晚上好"
        }
        tvGreeting.text = greeting

        val dateText = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA)
            .format(System.currentTimeMillis())
        tvDate.text = dateText
    }

    private fun setupLunarInfo() {
        val solar = Solar.fromDate(Date())
        val lunar = solar.lunar

        // 农历：干支年 生肖年 月日，如 "丙午马年 九月初三"
        tvLunar.text = "${lunar.yearInGanZhi}${lunar.animal}年 ${lunar.monthInChinese}${lunar.dayInChinese}"

        // 节日/节气气泡：公历节日 > 农历节日 > 节气
        val festivalName = when {
            solar.festivals.isNotEmpty() -> solar.festivals[0]
            lunar.festivals.isNotEmpty() -> lunar.festivals[0]
            lunar.jieQi.isNotEmpty() -> lunar.jieQi
            else -> null
        }
        if (festivalName != null) {
            layoutFestival.visibility = View.VISIBLE
            tvFestival.text = if (lunar.jieQi == festivalName) "今日节气：$festivalName" else festivalName
        } else {
            layoutFestival.visibility = View.GONE
        }
    }

    private fun setupQuickTools(root: View) {
        val container = root.findViewById<LinearLayout>(R.id.layout_quick_tools) ?: return

        val selectedTools = HomeQuickTools.getSelectedTools(requireContext())
        val newToolsIds = selectedTools.joinToString(",") { it.id }

        // 防刷校验：快捷组件未发生变化且容器已有子元素时，跳过重复 inflate 避免气泡闪烁
        if (newToolsIds == currentQuickToolsIds && container.childCount > 0) {
            return
        }
        currentQuickToolsIds = newToolsIds

        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        // 1. 动态生成选中的快捷气泡
        selectedTools.forEach { tool ->
            val bubbleView = inflater.inflate(
                R.layout.item_quick_tool_bubble,
                container,
                false
            ) as LiquidGlassView

            val ivIcon = bubbleView.findViewById<ImageView>(R.id.iv_quick_icon)
            val tvName = bubbleView.findViewById<TextView>(R.id.tv_quick_name)

            ivIcon.setImageResource(tool.iconRes)
            tvName.text = tool.name

            bubbleView.setOnClickListener {
                startActivity(Intent(requireContext(), tool.targetActivityClass))
            }

            bubbleView.setOnLongClickListener {
                HomeQuickTools.showCustomDialog(requireContext()) {
                    currentQuickToolsIds = null // 清空缓存以强制重新渲染
                    setupQuickTools(root)
                }
                true
            }

            container.addView(bubbleView)
        }

        // 2. 末尾添加 "+ 自定义" 气泡
        val editBubbleView = inflater.inflate(
            R.layout.item_quick_tool_bubble,
            container,
            false
        ) as LiquidGlassView

        val ivEditIcon = editBubbleView.findViewById<ImageView>(R.id.iv_quick_icon)
        val tvEditName = editBubbleView.findViewById<TextView>(R.id.tv_quick_name)

        ivEditIcon.setImageResource(R.drawable.ic_add)
        tvEditName.text = "自定义"

        editBubbleView.setOnClickListener {
            HomeQuickTools.showCustomDialog(requireContext()) {
                currentQuickToolsIds = null // 清空缓存以强制重新渲染
                setupQuickTools(root)
            }
        }

        container.addView(editBubbleView)
    }

    private fun setupWallpaper() {
        if (!isAdded) return
        val prefs = requireContext().getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        val type = prefs.getString("wallpaper_type", "none") ?: "none"
        val uriStr = prefs.getString("wallpaper_uri", null)

        val isAlreadyShowing = when (type) {
            "image", "gif" -> ivWallpaper.visibility == View.VISIBLE && ivWallpaper.drawable != null
            "video" -> vvWallpaper.visibility == View.VISIBLE
            else -> false
        }

        if (type == currentWallpaperType && uriStr == currentWallpaperUri && isAlreadyShowing) {
            return
        }

        currentWallpaperType = type
        currentWallpaperUri = uriStr

        when (type) {
            "image" -> if (!uriStr.isNullOrEmpty()) {
                val bitmap = loadWallpaperBitmap(requireContext(), uriStr)
                if (bitmap != null) {
                    ivWallpaper.setImageBitmap(bitmap)
                    ivWallpaper.visibility = View.VISIBLE
                    viewGradientOverlay.visibility = View.VISIBLE
                    vvWallpaper.visibility = View.GONE
                } else {
                    hideAllWallpapers()
                }
            } else {
                hideAllWallpapers()
            }
            "gif" -> if (!uriStr.isNullOrEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val file = File(Uri.parse(uriStr).path ?: "")
                        if (file.exists()) {
                            val source = ImageDecoder.createSource(file)
                            val drawable = ImageDecoder.decodeDrawable(source)
                            ivWallpaper.setImageDrawable(drawable)
                            if (drawable is AnimatedImageDrawable) {
                                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                                drawable.start()
                            }
                            ivWallpaper.visibility = View.VISIBLE
                            viewGradientOverlay.visibility = View.VISIBLE
                            vvWallpaper.visibility = View.GONE
                        } else {
                            hideAllWallpapers()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        hideAllWallpapers()
                    }
                } else {
                    val bitmap = loadWallpaperBitmap(requireContext(), uriStr)
                    if (bitmap != null) {
                        ivWallpaper.setImageBitmap(bitmap)
                        ivWallpaper.visibility = View.VISIBLE
                        viewGradientOverlay.visibility = View.VISIBLE
                        vvWallpaper.visibility = View.GONE
                    } else {
                        hideAllWallpapers()
                    }
                }
            } else {
                hideAllWallpapers()
            }
            "video" -> if (!uriStr.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(uriStr)
                    vvWallpaper.visibility = View.VISIBLE
                    viewGradientOverlay.visibility = View.VISIBLE
                    ivWallpaper.visibility = View.GONE
                    vvWallpaper.setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        vvWallpaper.start()
                    }
                    vvWallpaper.setVideoURI(uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    hideAllWallpapers()
                }
            } else {
                hideAllWallpapers()
            }
            else -> {
                hideAllWallpapers()
            }
        }
    }

    private fun loadWallpaperBitmap(context: Context, uriStr: String): Bitmap? {
        val uri = Uri.parse(uriStr)

        // 1. 本地私有路径 / file:// 形式
        if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: uriStr
            val file = File(path)
            if (file.exists()) {
                return decodeSampledBitmapFromFile(file.absolutePath, 1080, 2400)
            }
        }

        // 2. content:// 形式相册 Uri
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                return BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. 兜底文件
        val fallbackFile = File(context.filesDir, "custom_wallpaper_image.jpg")
        if (fallbackFile.exists()) {
            return decodeSampledBitmapFromFile(fallbackFile.absolutePath, 1080, 2400)
        }

        return null
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun hideAllWallpapers() {
        ivWallpaper.visibility = View.GONE
        vvWallpaper.visibility = View.GONE
        viewGradientOverlay.visibility = View.GONE
    }

    private fun setupCustomText() {
        if (!isAdded) return
        val prefs = requireContext().getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        val text = prefs.getString("custom_text", "") ?: ""

        if (text == currentCustomText && tvCustomText.text.toString() == text && (text.isEmpty() || tvCustomText.visibility == View.VISIBLE)) {
            return
        }
        currentCustomText = text

        if (text.isNotEmpty()) {
            divider.visibility = View.VISIBLE
            tvCustomText.text = text
            tvCustomText.visibility = View.VISIBLE
        } else {
            divider.visibility = View.GONE
            tvCustomText.visibility = View.GONE
        }
    }
}
