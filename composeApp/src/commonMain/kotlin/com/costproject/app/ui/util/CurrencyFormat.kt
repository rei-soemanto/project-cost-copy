package com.costproject.app.ui.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats a rupiah amount using Indonesian thousands separators, e.g. 5000000 -> "Rp5.000.000".
 *
 * Note: the inverse (parsing "5.000.000" back to a number) is not yet implemented, so
 * input fields currently only accept bare digits. Addressed in Phase 3.
 */
fun Double.formatRupiah(): String {
    val rounded = roundToLong()
    val negative = rounded < 0
    val digits = abs(rounded).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return (if (negative) "-Rp" else "Rp") + grouped
}
