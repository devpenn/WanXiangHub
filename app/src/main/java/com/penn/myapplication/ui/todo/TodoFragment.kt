package com.penn.myapplication.ui.todo

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nlf.calendar.Solar
import com.nlf.calendar.util.HolidayUtil
import com.penn.myapplication.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 待办/日程数据模型 */
data class TodoItem(
    val id: Long = System.currentTimeMillis(),
    var content: String,
    var category: String? = "工作", // "工作" / "个人" / "重要"
    var isDone: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    val date: String // YYYY-MM-DD
) {
    val safeCategory: String
        get() = if (category.isNullOrBlank()) "工作" else category!!
}

/** 当日待办列表的 ViewHolder */
class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val tvContent: TextView = itemView.findViewById(R.id.tv_todo_content)
    private val cbDone: CheckBox = itemView.findViewById(R.id.cb_done)
    private val tvCategoryTag: TextView = itemView.findViewById(R.id.tv_category_tag)

    fun bind(
        todo: TodoItem,
        onDoneChanged: ((TodoItem, Boolean) -> Unit)?,
        onDelete: ((TodoItem) -> Unit)?
    ) {
        tvContent.text = todo.content

        val cat = todo.safeCategory
        tvCategoryTag.text = cat

        val tintColor = when (cat) {
            "重要" -> Color.parseColor("#EF4444") // 红色
            "个人", "生活" -> Color.parseColor("#10B981") // 绿色
            else -> Color.parseColor("#3B82F6") // 蓝色
        }
        tvCategoryTag.backgroundTintList = android.content.res.ColorStateList.valueOf(tintColor)

        if (todo.isDone) {
            tvContent.paintFlags = tvContent.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvContent.setTextColor(Color.GRAY)
            tvCategoryTag.alpha = 0.5f
        } else {
            tvContent.paintFlags = tvContent.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            tvContent.setTextColor(Color.BLACK)
            tvCategoryTag.alpha = 1.0f
        }

        cbDone.setOnCheckedChangeListener(null)
        cbDone.isChecked = todo.isDone
        cbDone.setOnCheckedChangeListener { _, isChecked ->
            onDoneChanged?.invoke(todo, isChecked)
        }

        itemView.setOnClickListener {
            cbDone.isChecked = !cbDone.isChecked
        }

        itemView.setOnLongClickListener {
            AlertDialog.Builder(itemView.context)
                .setTitle("删除日程")
                .setMessage("确定要删除这项日程待办吗？")
                .setPositiveButton("删除") { _, _ ->
                    onDelete?.invoke(todo)
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }
}

/** 当日待办列表适配器（ListAdapter + DiffUtil，勾选只刷新变化的行，不跳动） */
class TodoAdapter :
    androidx.recyclerview.widget.ListAdapter<TodoItem, TodoViewHolder>(DIFF) {

    var onDoneChanged: ((TodoItem, Boolean) -> Unit)? = null
    var onDelete: ((TodoItem) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        holder.bind(getItem(position), onDoneChanged, onDelete)
    }

    companion object {
        private val DIFF = object : androidx.recyclerview.widget.DiffUtil.ItemCallback<TodoItem>() {
            override fun areItemsTheSame(oldItem: TodoItem, newItem: TodoItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: TodoItem, newItem: TodoItem) = oldItem == newItem
        }
    }
}

/**
 * 日历 · 钉钉式日程日历
 * - 月/周视图无缝切换（导航行右侧）
 * - 点击顶部年月 (如 2026年10月 ▼) 弹出年月选择器，快速跳转至任意年份和月份
 * - 支持左右拖拽/滑动手势无缝翻周/翻月
 * - 日程分类管理：支持“工作”、“个人”、“重要”分类标签与独立颜色
 * - 当日概览栏：实时显示选中日期的完整农历/公历以及完成进度统计
 * - 钉钉 UI：公历 + 农历（节日/节气优先），选中日期实底圆 + 白色文字，未选中的今天描边圆
 * - 日期格：法定节假日 休/班 徽章 + 待办数角标
 */
class TodoFragment : Fragment() {

    private enum class ViewMode { MONTH, WEEK }

    private lateinit var rvTodos: RecyclerView
    private lateinit var llEmptyTodo: View
    private lateinit var tvScheduleDateTitle: TextView
    private lateinit var tvScheduleCountSummary: TextView
    private lateinit var fabAddTodo: FloatingActionButton
    private lateinit var tvCurrentMonth: TextView
    private lateinit var rowWeekday: LinearLayout
    private lateinit var llCalendar: LinearLayout
    private lateinit var btnToday: MaterialButton
    private lateinit var btnPrevMonth: View
    private lateinit var btnNextMonth: View
    private lateinit var btnViewMonth: MaterialButton
    private lateinit var btnViewWeek: MaterialButton

    private lateinit var todoAdapter: TodoAdapter

    private var viewMode = ViewMode.MONTH
    private val gson = Gson()
    private var allTodos = mutableListOf<TodoItem>()

    // displayCal 代表当前日历翻到/展示的月份或周；selectedDate 代表用户当前选中的日期
    private var displayCal: Calendar = Calendar.getInstance()
    private var selectedDate: Date = Date()

    // 拖动手势检测器，支持左右滑动日历翻周/翻月
    private val calendarGestureDetector by lazy {
        GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 60
            private val SWIPE_VELOCITY_THRESHOLD = 60

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // 向右拖动 -> 上一周 / 上一个月
                            shiftCalendar(-1)
                        } else {
                            // 向左拖动 -> 下一周 / 下一个月
                            shiftCalendar(1)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_todo, container, false)

        rvTodos = root.findViewById(R.id.rv_todos)
        llEmptyTodo = root.findViewById(R.id.ll_empty_todo)
        tvScheduleDateTitle = root.findViewById(R.id.tv_schedule_date_title)
        tvScheduleCountSummary = root.findViewById(R.id.tv_schedule_count_summary)
        fabAddTodo = root.findViewById(R.id.fab_add_todo)
        tvCurrentMonth = root.findViewById(R.id.tv_current_month)
        rowWeekday = root.findViewById(R.id.row_weekday)
        llCalendar = root.findViewById(R.id.ll_calendar)
        btnToday = root.findViewById(R.id.btn_today)
        btnPrevMonth = root.findViewById(R.id.btn_prev_month)
        btnNextMonth = root.findViewById(R.id.btn_next_month)
        btnViewMonth = root.findViewById(R.id.btn_view_month)
        btnViewWeek = root.findViewById(R.id.btn_view_week)

        loadTodos()

        todoAdapter = TodoAdapter().apply {
            onDoneChanged = { todo, isChecked ->
                todo.isDone = isChecked
                saveTodos()
                updateTodoList()
            }
            onDelete = { todo ->
                allTodos.remove(todo)
                saveTodos()
                buildCalendar()
                updateTodoList()
            }
        }
        rvTodos.layoutManager = LinearLayoutManager(requireContext())
        rvTodos.adapter = todoAdapter

        fabAddTodo.setOnClickListener { showAddTodoDialog() }
        btnToday.setOnClickListener { jumpToToday() }
        btnPrevMonth.setOnClickListener { shiftCalendar(-1) }
        btnNextMonth.setOnClickListener { shiftCalendar(1) }
        btnViewMonth.setOnClickListener { setViewMode(ViewMode.MONTH) }
        btnViewWeek.setOnClickListener { setViewMode(ViewMode.WEEK) }

        // 点击年月标题弹出年月选择对话框
        tvCurrentMonth.setOnClickListener { showYearMonthPickerDialog() }

        // 绑定拖动手势监听
        llCalendar.setOnTouchListener { _, event ->
            calendarGestureDetector.onTouchEvent(event)
            true
        }

        buildWeekdayHeader()
        buildCalendar()
        updateTodoList()
        applyViewToggleStyles()

        return root
    }

    // region 视图与月历

    private fun setViewMode(mode: ViewMode) {
        if (viewMode == mode) return
        viewMode = mode
        // 切换视图模式时，将展示基准对齐当前选中的日期
        displayCal = Calendar.getInstance().apply { time = selectedDate }
        applyViewToggleStyles()
        buildCalendar()
        updateTodoList()
    }

    private fun applyViewToggleStyles() {
        val content = view ?: return
        val primary = MaterialColors.getColor(content, androidx.appcompat.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(content, com.google.android.material.R.attr.colorOnPrimary)
        val bg = ContextCompat.getColor(requireContext(), R.color.background)
        fun style(btn: MaterialButton, selected: Boolean) {
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (selected) primary else bg
            )
            btn.setTextColor(if (selected) onPrimary else primary)
        }
        style(btnViewMonth, viewMode == ViewMode.MONTH)
        style(btnViewWeek, viewMode == ViewMode.WEEK)
    }

    private fun selectedCal(): Calendar = Calendar.getInstance().apply { time = selectedDate }

    private fun buildWeekdayHeader() {
        rowWeekday.removeAllViews()
        // 与钉钉一致：周日开头
        for (i in 0 until 7) {
            val tv = TextView(requireContext()).apply {
                text = weekdayName(Calendar.SUNDAY + i)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rowWeekday.addView(tv)
        }
    }

    private fun buildCalendar() {
        if (!isAdded) return
        when (viewMode) {
            ViewMode.MONTH -> buildMonthGrid()
            ViewMode.WEEK -> buildWeekStrip()
        }
        tvCurrentMonth.text = monthTitleText()
    }

    private fun monthTitleText(): String = when (viewMode) {
        ViewMode.MONTH -> {
            "${displayCal.get(Calendar.YEAR)}年${displayCal.get(Calendar.MONTH) + 1}月"
        }
        ViewMode.WEEK -> {
            val start = weekStartOf(displayCal)
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
            val startYear = start.get(Calendar.YEAR)
            val startMonth = start.get(Calendar.MONTH) + 1
            val endYear = end.get(Calendar.YEAR)
            val endMonth = end.get(Calendar.MONTH) + 1

            if (startYear != endYear) {
                "${startYear}年${startMonth}月 - ${endYear}年${endMonth}月"
            } else if (startMonth != endMonth) {
                "${startYear}年${startMonth}月 - ${endMonth}月"
            } else {
                "${startYear}年${startMonth}月"
            }
        }
    }

    /** 强制 firstDayOfWeek 为 周日，避免不同 Locale 导致周首偏差 */
    private fun weekStartOf(cal: Calendar): Calendar {
        val result = (cal.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.SUNDAY
        }
        val dayOfWeek = result.get(Calendar.DAY_OF_WEEK)
        val diffToSunday = dayOfWeek - Calendar.SUNDAY
        result.add(Calendar.DAY_OF_MONTH, -diffToSunday)
        return result
    }

    private fun buildMonthGrid() {
        llCalendar.removeAllViews()
        val dispYear = displayCal.get(Calendar.YEAR)
        val dispMonth0 = displayCal.get(Calendar.MONTH)
        val today = Calendar.getInstance()

        val first = Calendar.getInstance().apply { set(dispYear, dispMonth0, 1) }
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val leading = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val totalCells = leading + daysInMonth
        val rows = Math.ceil(totalCells / 7.0).toInt()

        for (row in 0 until rows) {
            val rowView = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (col in 0 until 7) {
                val dayOffset = row * 7 + col - leading + 1
                val cellDate = Calendar.getInstance().apply {
                    set(dispYear, dispMonth0, 1)
                    add(Calendar.DAY_OF_MONTH, dayOffset - 1)
                }
                val isCurrentMonth = cellDate.get(Calendar.MONTH) == dispMonth0
                val cellView = makeDayCell(cellDate.get(Calendar.DAY_OF_MONTH), cellDate, today, isCurrentMonth)
                rowView.addView(
                    cellView,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
            llCalendar.addView(rowView, fixedRowParams())
        }
    }

    private fun buildWeekStrip() {
        llCalendar.removeAllViews()
        val today = Calendar.getInstance()
        val weekStart = weekStartOf(displayCal)

        val rowView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (i in 0 until 7) {
            val cellDate = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            val cellView = makeDayCell(cellDate.get(Calendar.DAY_OF_MONTH), cellDate, today, true)
            rowView.addView(
                cellView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        llCalendar.addView(rowView, fixedRowParams())
    }

    private fun fixedRowParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))

    private fun makeDayCell(day: Int, cellDate: Calendar, today: Calendar, isCurrentMonth: Boolean): View {
        val isToday = sameDay(cellDate, today)
        val isSelected = sameDay(cellDate, selectedCal())
        val dateKey = "%04d-%02d-%02d".format(
            cellDate.get(Calendar.YEAR),
            cellDate.get(Calendar.MONTH) + 1,
            cellDate.get(Calendar.DAY_OF_MONTH)
        )
        val count = allTodos.count { it.date == dateKey }

        val cell = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(2), dp(2), dp(3))
        }

        val numTv = TextView(requireContext()).apply {
            text = day.toString()
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            when {
                isSelected -> {
                    // 钉钉设计：选中日用实底圆 + 白色字
                    background = ContextCompat.getDrawable(context, R.drawable.bg_day_today)
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                }
                isToday -> {
                    // 钉钉设计：未选中的今天用描边圆 + 主题色字
                    background = ContextCompat.getDrawable(context, R.drawable.bg_day_selected)
                    setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
                }
                else -> {
                    background = null
                    val colorRes = if (isCurrentMonth || viewMode == ViewMode.WEEK) R.color.text_primary else R.color.text_tertiary
                    setTextColor(ContextCompat.getColor(context, colorRes))
                }
            }
        }
        cell.addView(numTv)

        // 农历 + 法定节假日 休/班 徽章
        val lunarRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lunarTv = TextView(requireContext()).apply {
            text = lunarText(cellDate)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            when {
                isSelected -> setTextColor(ContextCompat.getColor(context, R.color.primary))
                isToday -> setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
                else -> setTextColor(ContextCompat.getColor(context, if (isCurrentMonth || viewMode == ViewMode.WEEK) R.color.text_tertiary else R.color.outline_variant))
            }
        }
        lunarRow.addView(lunarTv)

        val holiday = try {
            HolidayUtil.getHoliday(
                cellDate.get(Calendar.YEAR),
                cellDate.get(Calendar.MONTH) + 1,
                cellDate.get(Calendar.DAY_OF_MONTH)
            )
        } catch (_: Exception) {
            null
        }
        if (holiday != null) {
            val isWork = holiday.isWork()
            val tag = TextView(requireContext()).apply {
                text = if (isWork) "班" else "休"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                background = ContextCompat.getDrawable(
                    context, if (isWork) R.drawable.bg_tag_work else R.drawable.bg_tag_holiday
                )
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(2)
                    setPadding(dp(2), 0, dp(2), 0)
                    topMargin = dp(1)
                    bottomMargin = dp(1)
                }
            }
            lunarRow.addView(tag)
        }
        cell.addView(lunarRow)

        // 待办数角标
        if (count > 0) {
            val badge = TextView(requireContext()).apply {
                text = count.toString()
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(ContextCompat.getColor(context, R.color.on_secondary_container))
                background = ContextCompat.getDrawable(context, R.drawable.bg_badge_pill)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.secondary_container)
                )
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(11)).apply { topMargin = dp(1) }
            }
            cell.addView(badge)
        }

        // 传递手势给 GestureDetector，拖动时触发翻周/翻月，点击时选择日期
        cell.setOnTouchListener { _, event ->
            calendarGestureDetector.onTouchEvent(event)
            false
        }

        cell.setOnClickListener {
            selectedDate = cellDate.time
            buildCalendar()
            updateTodoList()
        }
        return cell
    }

    private fun jumpToToday() {
        selectedDate = Date()
        displayCal = Calendar.getInstance()
        buildCalendar()
        updateTodoList()
    }

    private fun shiftCalendar(delta: Int) {
        if (viewMode == ViewMode.WEEK) {
            displayCal.add(Calendar.DAY_OF_MONTH, 7 * delta)
        } else {
            displayCal.add(Calendar.MONTH, delta)
        }
        buildCalendar()
    }

    /** 弹出钉钉风格的年月选择器，快速跳转至任意年份和月份 */
    private fun showYearMonthPickerDialog() {
        val currentYear = displayCal.get(Calendar.YEAR)
        val currentMonth = displayCal.get(Calendar.MONTH)

        val years = (1950..2050).toList()
        val months = listOf("1月", "2月", "3月", "4月", "5月", "6月",
                            "7月", "8月", "9月", "10月", "11月", "12月")

        val yearIndex = years.indexOf(currentYear).coerceAtLeast(0)
        val monthIndex = currentMonth.coerceIn(0, 11)

        val dialogView = layoutInflater.inflate(R.layout.dialog_year_month_picker, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.year_picker)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.month_picker)

        yearPicker.minValue = 0
        yearPicker.maxValue = years.size - 1
        yearPicker.displayedValues = years.map { "${it}年" }.toTypedArray()
        yearPicker.value = yearIndex
        yearPicker.wrapSelectorWheel = false

        monthPicker.minValue = 0
        monthPicker.maxValue = months.size - 1
        monthPicker.displayedValues = months.toTypedArray()
        monthPicker.value = monthIndex
        monthPicker.wrapSelectorWheel = true

        AlertDialog.Builder(requireContext())
            .setTitle("选择年月")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val selectedYear = years[yearPicker.value]
                val selectedMonth0 = monthPicker.value

                displayCal.set(Calendar.YEAR, selectedYear)
                displayCal.set(Calendar.MONTH, selectedMonth0)

                val targetDay = selectedCal().get(Calendar.DAY_OF_MONTH)
                    .coerceAtMost(displayCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val newSelCal = (displayCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, targetDay)
                }
                selectedDate = newSelCal.time

                buildCalendar()
                updateTodoList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun lunarText(cal: Calendar): String {
        val solar = Solar.fromDate(cal.time)
        val lunar = solar.lunar
        return when {
            lunar.festivals.isNotEmpty() -> lunar.festivals[0]
            lunar.jieQi.isNotEmpty() -> lunar.jieQi
            lunar.day == 1 -> lunar.monthInChinese + "月"
            else -> lunar.dayInChinese
        }
    }

    // endregion

    private fun updateTodoList() {
        if (!isAdded) return
        val fmtDateTitle = SimpleDateFormat("M月d日 EEEE", Locale.getDefault()).format(selectedDate)
        tvScheduleDateTitle.text = fmtDateTitle

        val selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
        val sortedTodos = allTodos
            .filter { it.date == selectedDateStr }
            .sortedBy { it.createdAt }
            .map { it.copy() }

        val total = sortedTodos.size
        val doneCount = sortedTodos.count { it.isDone }
        tvScheduleCountSummary.text = if (total == 0) "暂无日程" else "共 ${total} 项 · 已完成 ${doneCount} 项"

        todoAdapter.submitList(sortedTodos)
        llEmptyTodo.visibility = if (sortedTodos.isEmpty()) View.VISIBLE else View.GONE
    }

    // region Data Persistence

    private fun loadTodos() {
        val prefs = requireContext().getSharedPreferences("TodoPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("todos", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<TodoItem>>() {}.type
            val loaded: MutableList<TodoItem>? = gson.fromJson(json, type)
            if (loaded != null) {
                allTodos = loaded.map {
                    val cat = if (it.category.isNullOrBlank()) "工作" else it.category!!
                    it.copy(category = cat)
                }.toMutableList()
            }
        }
    }

    private fun saveTodos() {
        val prefs = requireContext().getSharedPreferences("TodoPrefs", Context.MODE_PRIVATE)
        val json = gson.toJson(allTodos)
        prefs.edit().putString("todos", json).apply()
    }

    // endregion

    // region Dialogs

    private fun showAddTodoDialog() {
        val context = requireContext()
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        val etInput = EditText(context).apply {
            hint = "输入日程事项内容"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        dialogView.addView(etInput)

        val tvCatTitle = TextView(context).apply {
            text = "选择分类："
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        dialogView.addView(tvCatTitle)

        var selectedCategory = "工作"
        val rgCategories = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }

        val categories = listOf("工作", "个人", "重要")
        categories.forEachIndexed { index, cat ->
            val rb = RadioButton(context).apply {
                id = View.generateViewId()
                text = when (cat) {
                    "重要" -> "📌 重要"
                    "个人" -> "🏠 个人"
                    else -> "💼 工作"
                }
                isChecked = index == 0
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            rb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedCategory = cat
            }
            rgCategories.addView(rb)
        }
        dialogView.addView(rgCategories)

        AlertDialog.Builder(context)
            .setTitle("新增日程待办")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val content = etInput.text.toString().trim()
                if (content.isNotBlank()) {
                    val newTodo = TodoItem(
                        content = content,
                        category = selectedCategory,
                        date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
                    )
                    allTodos.add(newTodo)
                    saveTodos()
                    buildCalendar()
                    updateTodoList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // endregion

    // region Helper

    private fun sameDay(date1: Calendar, date2: Calendar): Boolean =
        date1.get(Calendar.YEAR) == date2.get(Calendar.YEAR) &&
            date1.get(Calendar.MONTH) == date2.get(Calendar.MONTH) &&
            date1.get(Calendar.DAY_OF_MONTH) == date2.get(Calendar.DAY_OF_MONTH)

    private fun weekdayName(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.SUNDAY -> "日"
        Calendar.MONDAY -> "一"
        Calendar.TUESDAY -> "二"
        Calendar.WEDNESDAY -> "三"
        Calendar.THURSDAY -> "四"
        Calendar.FRIDAY -> "五"
        else -> "六"
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    // endregion
}
