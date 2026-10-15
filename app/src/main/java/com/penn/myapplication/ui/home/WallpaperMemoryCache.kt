package com.penn.myapplication.ui.home

import android.graphics.Bitmap
import android.util.LruCache

/** 首页壁纸全局 LruCache 内存缓存类，防止重复解码磁盘文件，实现 0 延时瞬时挂载 */
object WallpaperMemoryCache {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 8

    private val lruCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? {
        return lruCache.get(key)
    }

    fun put(key: String, bitmap: Bitmap) {
        if (get(key) == null) {
            lruCache.put(key, bitmap)
        }
    }

    fun clear() {
        lruCache.evictAll()
    }
}
