package com.costproject.app.ui.util

import kotlin.math.abs

/**
 * Money is handled as whole rupiah in a [Long]. Rupiah has no sub-unit in practice,
 * and floating point drifts once values are summed, so [Double] is never used for money.
 */

/**
 * Formats a rupiah amount with Indonesian thousands separators.
 *
 * ```
 * 5_000_000L.formatRupiah()  // "Rp5.000.000"
 * (-250_000L).formatRupiah() // "-Rp250.000"
 * ```
 */
fun Long.formatRupiah(): String {
    val sign = if (this < 0) "-" else ""
    return sign + "Rp" + abs(this).formatThousands()
}

/** Groups digits with Indonesian dot separators: 5000000 -> "5.000.000". Sign is dropped. */
fun Long.formatThousands(): String =
    abs(this).toString().reversed().chunked(3).joinToString(".").reversed()

/**
 * Converts a stored amount back into text for an editable field.
 *
 * Zero becomes an empty field rather than "0", so a row the user never filled in
 * looks the same after a round trip through the server as it did before.
 */
fun Long.toAmountInput(): String = if (this == 0L) "" else formatThousands()

/** Quantity counterpart of [toAmountInput]: plain digits, empty for zero or absent. */
fun Long?.toQuantityInput(): String = if (this == null || this == 0L) "" else toString()

/**
 * Parses user-entered money into whole rupiah, tolerating the separators the app
 * itself displays.
 *
 * The previous implementation used [String.toDoubleOrNull], which rejects "5.000.000"
 * and silently returned 0 — so a user typing exactly what the app showed them got
 * zero. Everything except digits and a leading minus is ignored, which accepts
 * "5.000.000", "Rp 5.000.000" and "5000000" alike.
 *
 * Returns 0 for blank or non-numeric input.
 */
fun String.parseRupiah(): Long {
    val negative = trimStart().startsWith("-")
    val digits = filter { it.isDigit() }
    if (digits.isEmpty()) return 0L
    val magnitude = digits.toLongOrNull() ?: return 0L
    return if (negative) -magnitude else magnitude
}

/**
 * Parses a quantity field. Digits only; blank or non-numeric input is 0.
 */
fun String.parseQuantity(): Long {
    val digits = filter { it.isDigit() }
    if (digits.isEmpty()) return 0L
    return digits.toLongOrNull() ?: 0L
}

/**
 * Keeps only the characters that are meaningful in a money field, so the text field
 * cannot accumulate stray input. Applied as the user types.
 */
fun String.sanitizeAmountInput(): String = filter { it.isDigit() || it == '.' }
