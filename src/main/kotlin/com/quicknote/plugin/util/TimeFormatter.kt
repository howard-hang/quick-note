package com.quicknote.plugin.util

import com.quicknote.plugin.model.NoteConstants
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility object for formatting timestamps
 */
object TimeFormatter {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3600_000L
    private const val DAY_MS = 86400_000L
    private const val WEEK_MS = 604800_000L
    private const val MONTH_MS = 2592000_000L

    /**
     * Format timestamp as relative time string
     *
     * Examples:
     * - "Just now" (< 1 minute)
     * - "5 minutes ago"
     * - "2 hours ago"
     * - "Yesterday"
     * - "3 days ago"
     * - "Last week"
     * - "Jan 15, 2025" (older dates)
     *
     * @param timestamp Timestamp in milliseconds
     * @return Formatted relative time string
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < MINUTE_MS -> "Just now"
            diff < HOUR_MS -> {
                val minutes = (diff / MINUTE_MS).toInt()
                if (minutes == 1) "1 minute ago" else "$minutes minutes ago"
            }
            diff < DAY_MS -> {
                val hours = (diff / HOUR_MS).toInt()
                if (hours == 1) "1 hour ago" else "$hours hours ago"
            }
            diff < DAY_MS * 2 -> "Yesterday"
            diff < WEEK_MS -> {
                val days = (diff / DAY_MS).toInt()
                "$days days ago"
            }
            diff < WEEK_MS * 2 -> "Last week"
            diff < MONTH_MS -> {
                val weeks = (diff / WEEK_MS).toInt()
                if (weeks == 1) "1 week ago" else "$weeks weeks ago"
            }
            else -> {
                val date = Date(timestamp)
                SimpleDateFormat(NoteConstants.TIME_FORMAT_SHORT, Locale.getDefault()).format(date)
            }
        }
    }

    /**
     * Format timestamp as full date and time
     *
     * @param timestamp Timestamp in milliseconds
     * @return Formatted date time string (e.g., "Jan 15, 2025 14:30")
     */
    fun formatFullDateTime(timestamp: Long): String {
        val date = Date(timestamp)
        return SimpleDateFormat(NoteConstants.TIME_FORMAT_FULL, Locale.getDefault()).format(date)
    }

    /**
     * Format timestamp as short date only
     *
     * @param timestamp Timestamp in milliseconds
     * @return Formatted date string (e.g., "Jan 15, 2025")
     */
    fun formatShortDate(timestamp: Long): String {
        val date = Date(timestamp)
        return SimpleDateFormat(NoteConstants.TIME_FORMAT_SHORT, Locale.getDefault()).format(date)
    }
}
