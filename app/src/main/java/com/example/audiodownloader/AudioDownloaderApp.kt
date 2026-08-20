package com.example.audiodownloader

import android.os.Environment
import android.util.Log
import com.chaquo.python.android.PyApplication
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class AudioDownloaderApp : PyApplication() {
    override fun onCreate() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val errorLog = "=== GLOBAL CRASH ON THREAD [${thread.name}] ===\n$sw"
            Log.e("CRASH_LOGGER", errorLog)
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(downloadDir, "app_crash.txt").writeText(errorLog)
                File(getExternalFilesDir(null), "app_crash.txt").writeText(errorLog)
            } catch (_: Exception) {}
        }

        try {
            super.onCreate()
        } catch (t: Throwable) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(downloadDir, "app_crash.txt").writeText("=== PyApplication onCreate Failed ===\n$sw")
            } catch (_: Exception) {}
            throw t
        }
    }
}
