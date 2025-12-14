package com.murari.careerpolitics.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.murari.careerpolitics.BuildConfig
import com.murari.careerpolitics.config.AppConfig

/**
 * Production-safe logger.
 *
 * Design principles:
 * - Debug/verbose logs are disabled in release builds
 * - Warnings and errors remain for crash diagnostics
 * - Centralized tag formatting
 * - Crash reporting integration is isolated and fail-safe
 */
object Logger {

    private fun formatTag(tag: String): String =
        "${AppConfig.LOG_TAG}:$tag"

    /** Verbose logging (debug only) */
    fun v(tag: String, message: String) {
        if (BuildConfig.ENABLE_LOGGING) {
            Log.v(formatTag(tag), message)
        }
    }

    /** Debug logging (debug only) */
    fun d(tag: String, message: String) {
        if (BuildConfig.ENABLE_LOGGING) {
            Log.d(formatTag(tag), message)
        }
    }

    /** Info logging (debug only) */
    fun i(tag: String, message: String) {
        if (BuildConfig.ENABLE_LOGGING) {
            Log.i(formatTag(tag), message)
        }
    }

    /** Warning logging (kept in all builds) */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val logTag = formatTag(tag)

        if (throwable != null) {
            Log.w(logTag, message, throwable)
            logToCrashReporting(tag, message, throwable, severity = "WARNING")
        } else {
            Log.w(logTag, message)
        }
    }

    /** Error logging (kept in all builds) */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val logTag = formatTag(tag)

        if (throwable != null) {
            Log.e(logTag, message, throwable)
            logToCrashReporting(tag, message, throwable, severity = "ERROR")
        } else {
            Log.e(logTag, message)
        }
    }

    /** Fatal errors */
    fun wtf(tag: String, message: String, throwable: Throwable? = null) {
        val logTag = formatTag(tag)

        if (throwable != null) {
            Log.wtf(logTag, message, throwable)
            logToCrashReporting(tag, message, throwable, severity = "FATAL")
        } else {
            Log.wtf(logTag, message)
        }
    }

    /**
     * Non-fatal crash reporting hook (Crashlytics-safe).
     * Never throws.
     */
    private fun logToCrashReporting(
        tag: String,
        message: String,
        throwable: Throwable,
        severity: String
    ) {
        if (!BuildConfig.ENABLE_CRASHLYTICS) return

        try {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("severity", severity)
                setCustomKey("tag", tag)
                log(message)
                recordException(throwable)
            }

        } catch (e: Exception) {
            Log.e(formatTag("Logger"), "Crash reporting failed", e)
        }
    }
}
