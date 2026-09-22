package com.costproject.app.ui.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Indonesian short month names, matching the id_ID locale (CLDR). */
private val MONTHS_ID = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

/**
 * Formats a server timestamp as a date label, e.g. "22 Sep 2026".
 *
 * The server sends UTC. Converting to [timeZone] first matters: a project created
 * at 23:30 WIB is 16:30 UTC the same day, but one created at 02:00 WIB on the
 * 23rd is 19:00 UTC on the 22nd - formatting the UTC date would show the wrong day.
 *
 * Returns the input unchanged if it is not a valid ISO-8601 instant, so a bad
 * value shows up as-is instead of crashing the screen.
 */
fun formatDateLabel(isoTimestamp: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val instant = try {
        Instant.parse(isoTimestamp)
    } catch (_: IllegalArgumentException) {
        return isoTimestamp
    }
    val date = instant.toLocalDateTime(timeZone).date
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "$day ${MONTHS_ID[date.monthNumber - 1]} ${date.year}"
}
