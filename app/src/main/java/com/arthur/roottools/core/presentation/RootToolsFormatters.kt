package com.arthur.roottools.core.presentation

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatGHz(khz: Long): String =
    if (khz <= 0L) "—" else "%.2f GHz".format(khz / 1_000_000.0)

fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatRelativeTime(timestampMs: Long): String {
    val delta = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    return when {
        delta < 60_000L -> "${delta / 1_000L}s ago"
        delta < 60 * 60_000L -> "${delta / 60_000L}m ago"
        delta < 24 * 60 * 60_000L -> "${delta / (60 * 60_000L)}h ago"
        else -> "${delta / (24 * 60 * 60_000L)}d ago"
    }
}

fun formatStartupSeconds(seconds: Long?): String = when {
    seconds == null -> "—"
    seconds < 60 -> "${seconds}s"
    seconds < 3_600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3_600}h ${(seconds % 3_600) / 60}m"
}

fun formatMemoryKb(kb: Long): String = when {
    kb >= 1024 * 1024 -> "%.2f GB".format(kb / 1_048_576.0)
    kb >= 1024 -> "%.0f MB".format(kb / 1024.0)
    else -> "$kb KB"
}

fun formatClockTime(timestampMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
