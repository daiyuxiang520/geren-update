package com.open.wuling

import android.app.Application
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WulingApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Set crash handler for non-recovery process
        if (!isRecoveryProcess()) {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e("WulingApp", "Uncaught exception in thread ${thread.name}", throwable)
            }
        }
    }

    private fun isRecoveryProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= 28) {
            android.os.Process.myProcessName()
        } else {
            packageName
        }
        return processName?.endsWith(":recovery") == true
    }
}
