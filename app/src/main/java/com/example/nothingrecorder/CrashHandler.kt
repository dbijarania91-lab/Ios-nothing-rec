package com.example.nothingrecorder

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val stackTrace = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTrace))
        
        // Save to: /Android/data/com.example.nothingrecorder/files/crash_log.txt
        try {
            val file = File(context.getExternalFilesDir(null), "crash_log.txt")
            file.writeText(stackTrace.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Kill the app so it doesn't loop
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }
}
