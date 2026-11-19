package com.clippost.app

import android.content.Context
import android.os.Build

/**
 * Saves the error when the app crashes, so the next launch can show it on screen.
 * That lets you copy the cause without Android Studio or a computer.
 */
object CrashReporter {

    private const val PREFS = "clippost_crash"
    private const val KEY = "last"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val chain = generateSequence(error) { it.cause }
                    .joinToString("\n") { "${it.javaClass.name}: ${it.message}" }
                val text = buildString {
                    appendLine(chain)
                    appendLine()
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(error.stackTraceToString())
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, text.take(8000)).commit()
            } catch (_: Throwable) {
                // Never let the reporter itself crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
