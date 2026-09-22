package com.costproject.app.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyFormatTest {

    @Test
    fun formats_with_indonesian_thousands_separators() {
        assertEquals("Rp0", 0L.formatRupiah())
        assertEquals("Rp500", 500L.formatRupiah())
        assertEquals("Rp5.000", 5_000L.formatRupiah())
        assertEquals("Rp5.000.000", 5_000_000L.formatRupiah())
        assertEquals("Rp1.234.567.890", 1_234_567_890L.formatRupiah())
    }

    @Test
    fun formats_negative_amounts_with_sign_before_currency() {
        assertEquals("-Rp250.000", (-250_000L).formatRupiah())
    }

    /**
     * The regression this whole change exists for: the app displays "Rp5.000.000",
     * but the old parser used toDoubleOrNull(), which rejects "5.000.000" and
     * silently produced 0. A user typing what the app showed them got zero.
     */
    @Test
    fun parses_the_separator_format_the_app_itself_displays() {
        assertEquals(5_000_000L, "5.000.000".parseRupiah())
        assertEquals(5_000_000L, "Rp5.000.000".parseRupiah())
        assertEquals(5_000_000L, "Rp 5.000.000".parseRupiah())
        assertEquals(5_000_000L, "5000000".parseRupiah())
    }

    @Test
    fun parses_blank_and_junk_input_as_zero() {
        assertEquals(0L, "".parseRupiah())
        assertEquals(0L, "   ".parseRupiah())
        assertEquals(0L, "abc".parseRupiah())
        assertEquals(0L, "Rp".parseRupiah())
    }

    @Test
    fun parses_negative_input() {
        assertEquals(-250_000L, "-250.000".parseRupiah())
    }

    @Test
    fun format_and_parse_round_trip() {
        listOf(0L, 1L, 999L, 1_000L, 5_000_000L, 987_654_321L).forEach { amount ->
            assertEquals(amount, amount.formatRupiah().parseRupiah(), "round trip failed for $amount")
        }
    }

    @Test
    fun quantity_parses_digits_only() {
        assertEquals(0L, "".parseQuantity())
        assertEquals(12L, "12".parseQuantity())
        assertEquals(0L, "abc".parseQuantity())
    }

    @Test
    fun amount_input_is_sanitized_to_digits_and_separators() {
        assertEquals("5.000", "5.000".sanitizeAmountInput())
        assertEquals("5000", "Rp5000".sanitizeAmountInput())
        assertEquals("123", "1a2b3c".sanitizeAmountInput())
    }
}
