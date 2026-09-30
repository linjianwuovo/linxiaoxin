package com.linxin

import android.app.Application
import com.linxin.core.network.NetTrace
import com.linxin.navigation.ShortcutRegistrar
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class LinXinApp : Application() {

    // 提前实例化，让"启用调试功能"的开关从启动就跟 DataStore 对齐
    @Inject lateinit var netTrace: NetTrace

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        ShortcutRegistrar.register(this)
    }

    /**
     * 全局崩溃落盘：把堆栈写到 app 私有目录，下次启动由 MainActivity
     * 读取、复制到剪贴板并弹窗，方便用户粘贴给开发端定位。
     */
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(thread, throwable) }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val content = buildString {
            appendLine("time=$time")
            appendLine("thread=${thread.name}")
            appendLine(sw.toString())
        }
        File(filesDir, CRASH_FILE_NAME).writeText(content)
    }

    companion object {
        const val CRASH_FILE_NAME = "linxin_crash.txt"
    }
}
