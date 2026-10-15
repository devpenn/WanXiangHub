package com.penn.myapplication.ui.todo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.penn.myapplication.db.WanXiangDatabaseHelper
import com.penn.myapplication.lottery.LuckyTicket

/** 待办日程 SQLite 数据仓库 (支持自动数据库迁移与彩票开奖日历联动) */
object TodoRepository {

    private const val OLD_PREF = "TodoPrefs"
    private const val OLD_KEY = "todos"

    /** 读取全量 SQLite 待办日程 */
    fun getAllTodos(context: Context): MutableList<TodoItem> {
        migrateOldPrefsIfNeeded(context)
        val list = mutableListOf<TodoItem>()
        val dbHelper = WanXiangDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            WanXiangDatabaseHelper.TABLE_TODOS,
            null, null, null, null, null,
            "${WanXiangDatabaseHelper.COLUMN_TODO_CREATED_AT} DESC"
        )

        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TODO_ID))
                val content = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TODO_CONTENT))
                val category = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TODO_CATEGORY))
                val isDone = c.getInt(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TODO_IS_DONE)) == 1
                val createdAt = c.getLong(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TODO_CREATED_AT))
                val date = c.getString(c.getColumnIndexOrThrow(WanXiangDatabaseHelper.COLUMN_TODO_DATE))

                list.add(TodoItem(id, content, category, isDone, createdAt, date))
            }
        }
        return list
    }

    /** 保存/插入待办事项到 SQLite */
    fun saveTodo(context: Context, todo: TodoItem) {
        val dbHelper = WanXiangDatabaseHelper(context)
        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(WanXiangDatabaseHelper.COLUMN_TODO_CONTENT, todo.content)
            put(WanXiangDatabaseHelper.COLUMN_TODO_CATEGORY, todo.safeCategory)
            put(WanXiangDatabaseHelper.COLUMN_TODO_IS_DONE, if (todo.isDone) 1 else 0)
            put(WanXiangDatabaseHelper.COLUMN_TODO_CREATED_AT, todo.createdAt)
            put(WanXiangDatabaseHelper.COLUMN_TODO_DATE, todo.date)
        }

        val rows = db.update(
            WanXiangDatabaseHelper.TABLE_TODOS,
            values,
            "${WanXiangDatabaseHelper.COLUMN_TODO_ID} = ?",
            arrayOf(todo.id.toString())
        )

        if (rows == 0) {
            values.put(WanXiangDatabaseHelper.COLUMN_TODO_ID, todo.id)
            db.insert(WanXiangDatabaseHelper.TABLE_TODOS, null, values)
        }
    }

    /** 从 SQLite 删除指定待办 */
    fun deleteTodo(context: Context, id: Long) {
        val dbHelper = WanXiangDatabaseHelper(context)
        val db = dbHelper.writableDatabase
        db.delete(
            WanXiangDatabaseHelper.TABLE_TODOS,
            "${WanXiangDatabaseHelper.COLUMN_TODO_ID} = ?",
            arrayOf(id.toString())
        )
    }

    /** 联动逻辑：用户买彩票后，自动向日历插入/更新开奖日提醒日程 */
    fun linkLotteryReminderToCalendar(context: Context, ticket: LuckyTicket, drawDate: String) {
        val titleName = if (ticket.gameName == "SSQ") "双色球" else "大乐透"
        val reminderContent = "🎯【${titleName}开奖提醒】已购号码: ${ticket.mainText()} + ${ticket.specialText()}，点击核对中奖"

        val existingTodos = getAllTodos(context)
        val matchedTodo = existingTodos.find { it.date == drawDate && it.content.contains(titleName) }

        if (matchedTodo != null) {
            matchedTodo.content = reminderContent
            saveTodo(context, matchedTodo)
        } else {
            val newTodo = TodoItem(
                id = System.currentTimeMillis(),
                content = reminderContent,
                category = "彩票",
                isDone = false,
                createdAt = System.currentTimeMillis(),
                date = drawDate
            )
            saveTodo(context, newTodo)
        }
    }

    /** 从旧版 SharedPreferences 自动无缝迁移至 SQLite */
    private fun migrateOldPrefsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(OLD_PREF, Context.MODE_PRIVATE)
        val oldJson = prefs.getString(OLD_KEY, null) ?: return

        try {
            val type = object : TypeToken<List<TodoItem>>() {}.type
            val oldTodos: List<TodoItem>? = Gson().fromJson(oldJson, type)

            if (!oldTodos.isNullOrEmpty()) {
                val dbHelper = WanXiangDatabaseHelper(context)
                val db = dbHelper.writableDatabase

                db.beginTransaction()
                try {
                    oldTodos.forEach { todo ->
                        val values = ContentValues().apply {
                            put(WanXiangDatabaseHelper.COLUMN_TODO_ID, todo.id)
                            put(WanXiangDatabaseHelper.COLUMN_TODO_CONTENT, todo.content)
                            put(WanXiangDatabaseHelper.COLUMN_TODO_CATEGORY, todo.safeCategory)
                            put(WanXiangDatabaseHelper.COLUMN_TODO_IS_DONE, if (todo.isDone) 1 else 0)
                            put(WanXiangDatabaseHelper.COLUMN_TODO_CREATED_AT, todo.createdAt)
                            put(WanXiangDatabaseHelper.COLUMN_TODO_DATE, todo.date)
                        }
                        db.insertWithOnConflict(
                            WanXiangDatabaseHelper.TABLE_TODOS,
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_IGNORE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            // 清理旧 Prefs，避免重复迁移
            prefs.edit().remove(OLD_KEY).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
