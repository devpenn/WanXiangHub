package com.penn.myapplication.ui.home

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nlf.calendar.Solar
import com.penn.myapplication.R
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
    private lateinit var layoutFestival: LinearLayout
    private lateinit var tvFestival: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_home, container, false)

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
        // 「我的」页可能改过壁纸/自定义文字，回来时重新应用
        setupWallpaper()
        setupCustomText()
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

    private fun setupWallpaper() {
        if (!isAdded) return
        val prefs = requireContext().getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        val type = prefs.getString("wallpaper_type", "none")
        val uriStr = prefs.getString("wallpaper_uri", null)

        ivWallpaper.visibility = View.GONE
        vvWallpaper.visibility = View.GONE
        viewGradientOverlay.visibility = View.GONE

        when (type) {
            "image" -> if (uriStr != null) {
                ivWallpaper.setImageURI(Uri.parse(uriStr))
                ivWallpaper.visibility = View.VISIBLE
                viewGradientOverlay.visibility = View.VISIBLE
            }
            "video" -> if (uriStr != null) {
                vvWallpaper.visibility = View.VISIBLE
                viewGradientOverlay.visibility = View.VISIBLE
                vvWallpaper.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    vvWallpaper.start()
                }
                vvWallpaper.setVideoURI(Uri.parse(uriStr))
            }
        }
    }

    private fun setupCustomText() {
        if (!isAdded) return
        val prefs = requireContext().getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        val text = prefs.getString("custom_text", "") ?: ""
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
