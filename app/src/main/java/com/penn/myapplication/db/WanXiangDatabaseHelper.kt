package com.penn.myapplication.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 全应用通用 SQLite 数据库 Helper */
class WanXiangDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "wanxiang_hub.db"
        const val DATABASE_VERSION = 1

        // 待办日程表
        const val TABLE_TODOS = "todos"
        const val COLUMN_TODO_ID = "id"
        const val COLUMN_TODO_CONTENT = "content"
        const val COLUMN_TODO_CATEGORY = "category"
        const val COLUMN_TODO_IS_DONE = "is_done"
        const val COLUMN_TODO_CREATED_AT = "created_at"
        const val COLUMN_TODO_DATE = "date"
        const val COLUMN_TODO_LINKED_TICKET_ID = "linked_ticket_id"

        // 彩票好运表
        const val TABLE_LUCKY_TICKETS = "lucky_tickets"
        const val COLUMN_TICKET_ID = "id"
        const val COLUMN_TICKET_GAME = "game"
        const val COLUMN_TICKET_RED_BALLS = "red_balls"
        const val COLUMN_TICKET_BLUE_BALLS = "blue_balls"
        const val COLUMN_TICKET_PURCHASE_DATE = "purchase_date"
        const val COLUMN_TICKET_PURCHASE_TIME = "purchase_time"
        const val COLUMN_TICKET_ISSUE = "issue"
        const val COLUMN_TICKET_IS_BOUGHT = "is_bought"
        const val COLUMN_TICKET_DRAW_RED = "draw_red"
        const val COLUMN_TICKET_DRAW_BLUE = "draw_blue"
        const val COLUMN_TICKET_PRIZE_LEVEL = "prize_level"
        const val COLUMN_TICKET_PRIZE_AMOUNT = "prize_amount"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTodosTable = """
            CREATE TABLE $TABLE_TODOS (
                $COLUMN_TODO_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TODO_CONTENT TEXT NOT NULL,
                $COLUMN_TODO_CATEGORY TEXT,
                $COLUMN_TODO_IS_DONE INTEGER DEFAULT 0,
                $COLUMN_TODO_CREATED_AT INTEGER,
                $COLUMN_TODO_DATE TEXT NOT NULL,
                $COLUMN_TODO_LINKED_TICKET_ID INTEGER DEFAULT -1
            );
        """.trimIndent()

        val createLuckyTable = """
            CREATE TABLE $TABLE_LUCKY_TICKETS (
                $COLUMN_TICKET_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TICKET_GAME TEXT NOT NULL,
                $COLUMN_TICKET_RED_BALLS TEXT NOT NULL,
                $COLUMN_TICKET_BLUE_BALLS TEXT NOT NULL,
                $COLUMN_TICKET_PURCHASE_DATE TEXT NOT NULL,
                $COLUMN_TICKET_PURCHASE_TIME INTEGER,
                $COLUMN_TICKET_ISSUE TEXT,
                $COLUMN_TICKET_IS_BOUGHT INTEGER DEFAULT 0,
                $COLUMN_TICKET_DRAW_RED TEXT,
                $COLUMN_TICKET_DRAW_BLUE TEXT,
                $COLUMN_TICKET_PRIZE_LEVEL TEXT DEFAULT '待开奖',
                $COLUMN_TICKET_PRIZE_AMOUNT REAL DEFAULT 0.0
            );
        """.trimIndent()

        db.execSQL(createTodosTable)
        db.execSQL(createLuckyTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TODOS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LUCKY_TICKETS")
        onCreate(db)
    }
}
