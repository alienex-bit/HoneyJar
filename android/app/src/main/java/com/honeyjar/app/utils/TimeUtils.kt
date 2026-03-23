package com.honeyjar.app.utils

import android.text.format.DateUtils
import java.util.*

object TimeUtils {
    fun formatDuration(postTime: Long): String {
        val now = System.currentTimeMillis()
        val duration = now - postTime
        
        return when {
            duration < 60000 -> "now"
            duration < 3600000 -> "${duration / 60000}m ago"
            duration < 86400000 -> "${duration / 3600000}h ago"
            else -> DateUtils.getRelativeTimeSpanString(postTime, now, DateUtils.DAY_IN_MILLIS).toString()
        }
    }

    fun formatTime(time: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(time))
    }

    fun getDayStart(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getMillisUntilEvening(): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 18)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis - now
    }
}
