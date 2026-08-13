package com.knee.emgapp

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃日志: 记录未捕获异常与 UI 刷新异常到 filesDir/crash.log,
 * 下次启动时由 MainActivity 弹窗展示, 便于定位真机问题。
 */
object CrashLog {

    @Volatile
    lateinit var app: Context
        private set

    fun file(): File = File(app.filesDir, "crash.log")

    fun append(tag: String, t: Throwable) {
        try {
            val f = file()
            val line = buildString {
                append("[$tag] ")
                appendLine(SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date()))
                appendLine(Log.getStackTraceString(t))
                appendLine("----------")
                if (f.exists()) append(f.readText())
            }.take(50000)
            f.writeText(line)
        } catch (_: Throwable) {
        }
    }
}

class EmgApp : Application() {

    private var prev: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        CrashLog.app = this
        prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLog.append("CRASH", throwable)
            prev?.uncaughtException(thread, throwable)
        }
    }
}
