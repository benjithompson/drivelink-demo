package com.drivelink.demo.timing

import android.util.Log
import com.drivelink.core.domain.TimingLog

/**
 * Writes timing marks to Logcat with tag [TAG], for example
 * `DL_TIMING command_ms=4210 type=LOCK result=SUCCEEDED`.
 * Perfecto and BlazeMeter tests read these lines: `adb logcat -d -s DL_TIMING`.
 */
object LogcatTimingLog : TimingLog {
    const val TAG = "DL_TIMING"

    override fun mark(name: String, ms: Long, tags: Map<String, String>) {
        Log.i(TAG, format(name, ms, tags))
    }

    /** `DL_TIMING <name>=<ms> k=v ...`. Spaces in tag values become underscores, so each pair stays one token. */
    fun format(name: String, ms: Long, tags: Map<String, String>): String = buildString {
        append(TAG).append(' ').append(name).append('=').append(ms)
        tags.forEach { (key, value) -> append(' ').append(key).append('=').append(value.replace(' ', '_')) }
    }
}
