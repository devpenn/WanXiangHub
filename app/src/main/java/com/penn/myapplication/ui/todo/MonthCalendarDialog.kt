package com.penn.myapplication.ui.todo

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nlf.calendar.Solar
import com.penn.myapplication.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MonthCalendarDialog(
    context: Context,
    private val initialDate: Date,
    private val onDateSelected: (Date) -> Unit
) : Dialog(context) {

    private lateinit var tvMonthYear: TextView
    private lateinit var rvCalendar: RecyclerView
    private lateinit var btnPrevMonth: ImageView
    private lateinit var btnNextMonth: ImageView
    private lateinit var btnToday: Button
    private lateinit var btnCancel: Button
    private lateinit var btnConfirm: Button

    private val calendar = Calendar.getInstance().apply { time = initialDate }
    private var selectedDate: Date = initialDate
    private lateinit var adapter: MonthCalendarAdapter

    data class CalendarDay(
        val date: Date,
        val dayOfMonth: Int,
        val isCurrentMonth: Boolean,
        val lunarInfo: String,
        var isSelected: Boolean = false,
        val isToday: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_month_calendar)

        // 设置对话框宽度
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        initViews()
        setupListeners()
        updateCalendar()
    }

    private fun initViews() {
        tvMonthYear = findViewById(R.id.tv_month_year)
        rvCalendar = findViewById(R.id.rv_calendar)
        btnPrevMonth = findViewById(R.id.btn_prev_month)
        btnNextMonth = findViewById(R.id.btn_next_month)
        btnToday = findViewById(R.id.btn_today)
        btnCancel = findViewById(R.id.btn_cancel)
        btnConfirm = findViewById(R.id.btn_confirm)

        rvCalendar.layoutManager = GridLayoutManager(context, 7)
        adapter = MonthCalendarAdapter { day ->
            if (day.isCurrentMonth) {
                selectedDate = day.date
                updateCalendar()
            }
        }
        rvCalendar.adapter = adapter
    }

    private fun setupListeners() {
        btnPrevMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        btnNextMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }

        tvMonthYear.setOnClickListener {
            showYearMonthPicker()
        }

        btnToday.setOnClickListener {
            selectedDate = Date()
            calendar.time = selectedDate
            updateCalendar()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnConfirm.setOnClickListener {
            onDateSelected(selectedDate)
            dismiss()
        }
    }

    private fun showYearMonthPicker() {
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        val years = (1900..2100).toList()
        val months = listOf("1月", "2月", "3月", "4月", "5月", "6月",
                           "7月", "8月", "9月", "10月", "11月", "12月")

        val yearIndex = years.indexOf(currentYear)
        val monthIndex = currentMonth

        val dialogView = layoutInflater.inflate(R.layout.dialog_year_month_picker, null)
        val yearPicker = dialogView.findViewById<android.widget.NumberPicker>(R.id.year_picker)
        val monthPicker = dialogView.findViewById<android.widget.NumberPicker>(R.id.month_picker)

        yearPicker.minValue = 0
        yearPicker.maxValue = years.size - 1
        yearPicker.displayedValues = years.map { it.toString() + "年" }.toTypedArray()
        yearPicker.value = yearIndex
        yearPicker.wrapSelectorWheel = false
        setNumberPickerDividerColor(yearPicker)

        monthPicker.minValue = 0
        monthPicker.maxValue = months.size - 1
        monthPicker.displayedValues = months.toTypedArray()
        monthPicker.value = monthIndex
        monthPicker.wrapSelectorWheel = true
        setNumberPickerDividerColor(monthPicker)

        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle("选择年月")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                calendar.set(Calendar.YEAR, years[yearPicker.value])
                calendar.set(Calendar.MONTH, monthPicker.value)
                updateCalendar()
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        // 设置对话框宽度和圆角
        dialog.window?.apply {
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.75).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(R.drawable.dialog_background)
        }
    }

    private fun updateCalendar() {
        tvMonthYear.text = SimpleDateFormat("yyyy年M月", Locale.getDefault()).format(calendar.time)

        val days = mutableListOf<CalendarDay>()
        val tempCal = calendar.clone() as Calendar

        // 设置到月初
        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) // 1=周日, 2=周一...

        // 调整为周一开始 (周日=7, 周一=1)
        val offset = if (firstDayOfWeek == 1) 6 else firstDayOfWeek - 2

        // 添加空白占位
        for (i in 0 until offset) {
            days.add(CalendarDay(
                date = Date(),
                dayOfMonth = 0,
                isCurrentMonth = false,
                lunarInfo = "",
                isSelected = false,
                isToday = false
            ))
        }

        val today = Calendar.getInstance()
        val selectedCal = Calendar.getInstance().apply { time = selectedDate }

        // 获取当月天数
        val maxDay = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 只生成当月的日期
        for (day in 1..maxDay) {
            tempCal.set(Calendar.DAY_OF_MONTH, day)
            val date = tempCal.time
            val isToday = isSameDay(tempCal, today)
            val isSelected = isSameDay(tempCal, selectedCal)

            // 获取农历信息
            val lunarInfo = getLunarInfo(date)

            days.add(CalendarDay(
                date = date,
                dayOfMonth = day,
                isCurrentMonth = true,
                lunarInfo = lunarInfo,
                isSelected = isSelected,
                isToday = isToday
            ))
        }

        adapter.submitList(days)
    }



    private fun getLunarInfo(date: Date): String {
        val solar = Solar.fromDate(date)
        val lunar = solar.lunar

        // 优先显示节日
        val festivals = lunar.festivals
        if (festivals.isNotEmpty()) {
            return festivals[0]
        }

        // 显示节气
        val jieQi = lunar.jieQi
        if (jieQi.isNotEmpty()) {
            return jieQi
        }

        // 显示农历日期
        return if (lunar.day == 1) {
            lunar.monthInChinese + "月"
        } else {
            lunar.dayInChinese
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // 月历适配器
    private class MonthCalendarAdapter(
        private val onDayClick: (CalendarDay) -> Unit
    ) : RecyclerView.Adapter<MonthCalendarAdapter.DayViewHolder>() {

        private var days = listOf<CalendarDay>()

        fun submitList(newDays: List<CalendarDay>) {
            days = newDays
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_month_calendar_day, parent, false)
            return DayViewHolder(view, onDayClick)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            holder.bind(days[position])
        }

        override fun getItemCount() = days.size

        class DayViewHolder(
            itemView: View,
            private val onDayClick: (CalendarDay) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            private val layoutDay: View = itemView.findViewById(R.id.layout_day)
            private val tvDay: TextView = itemView.findViewById(R.id.tv_day)
            private val tvLunar: TextView = itemView.findViewById(R.id.tv_lunar)

            fun bind(day: CalendarDay) {
                // 如果是空白占位，隐藏内容
                if (day.dayOfMonth == 0) {
                    layoutDay.visibility = View.INVISIBLE
                    return
                }

                layoutDay.visibility = View.VISIBLE
                tvDay.text = day.dayOfMonth.toString()
                tvLunar.text = day.lunarInfo

                // 设置文字颜色
                tvDay.setTextColor(Color.parseColor("#333333"))
                tvLunar.setTextColor(Color.parseColor("#888888"))

                // 设置背景
                val background = GradientDrawable()
                background.shape = GradientDrawable.OVAL

                when {
                    day.isSelected -> {
                        background.setColor(Color.parseColor("#6366F1"))
                        tvDay.setTextColor(Color.WHITE)
                        tvLunar.setTextColor(Color.WHITE)
                    }
                    day.isToday -> {
                        background.setStroke(3, Color.parseColor("#6366F1"))
                    }
                }

                tvDay.background = background

                // 点击事件
                layoutDay.setOnClickListener {
                    onDayClick(day)
                }
            }
        }
    }

    private fun setNumberPickerDividerColor(picker: android.widget.NumberPicker) {
        try {
            // 修改分割线高度为2px
            val dividerField = android.widget.NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight")
            dividerField.isAccessible = true
            dividerField.set(picker, 4)

            // 修改分割线颜色为主题色
            val colorField = android.widget.NumberPicker::class.java.getDeclaredField("mSelectionDivider")
            colorField.isAccessible = true
            val drawable = android.graphics.drawable.ColorDrawable(0xFF6366F1.toInt())
            colorField.set(picker, drawable)

            // 延迟执行，确保 NumberPicker 已经渲染
            picker.post {
                val count = picker.childCount
                for (i in 0 until count) {
                    val child = picker.getChildAt(i)
                    if (child is android.widget.EditText) {
                        child.textSize = 16f
                        child.setPadding(0, 2, 0, 2)
                        child.gravity = android.view.Gravity.CENTER
                        child.setTextColor(0xFF1F2937.toInt())
                        // 隐藏输入框的光标和选中效果
                        child.isCursorVisible = false
                        child.isFocusable = false
                        child.isFocusableInTouchMode = false
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
