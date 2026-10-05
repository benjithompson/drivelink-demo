package com.drivelink.core.domain

/**
 * Timing markers for Perfecto and BlazeMeter. The app logs each mark to
 * Logcat as `DL_TIMING <name>=<ms> key=value ...`, for example `DL_TIMING command_ms=4210 type=LOCK`.
 */
fun interface TimingLog {
    fun mark(name: String, ms: Long, tags: Map<String, String>)
}
