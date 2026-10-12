package com.penn.myapplication

import android.app.Application
import com.google.android.material.color.DynamicColors

class GuessApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Android 12+ (Material You)：跟随壁纸动态取色，Android 17 上即系统级个性化外观
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
