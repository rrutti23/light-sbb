package ch.lightsbb.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionFormatterTest {

    // --- formatTime ---

    @Test
    fun `formatTime extracts HH-MM from a standard ISO timestamp`() {
        assertEquals("10:32", formatTime("2024-01-15T10:32:00+0100"))
    }

    @Test
    fun `formatTime handles midnight correctly`() {
        assertEquals("00:05", formatTime("2024-01-15T00:05:00+0100"))
    }

    @Test
    fun `formatTime handles late-night times`() {
        assertEquals("23:59", formatTime("2024-01-15T23:59:00+0100"))
    }

    @Test
    fun `formatTime returns input unchanged when string has no T separator`() {
        assertEquals("not-a-date", formatTime("not-a-date"))
    }

    @Test
    fun `formatTime handles UTC offset without colon`() {
        assertEquals("08:00", formatTime("2024-06-01T08:00:00+0200"))
    }

    // --- formatDuration ---

    @Test
    fun `formatDuration shows minutes only when under one hour`() {
        assertEquals("57m", formatDuration("00d00:57:00"))
    }

    @Test
    fun `formatDuration shows hours and minutes for multi-hour trips`() {
        assertEquals("1h 23m", formatDuration("00d01:23:00"))
    }

    @Test
    fun `formatDuration pads single-digit minutes to two digits`() {
        assertEquals("2h 05m", formatDuration("00d02:05:00"))
    }

    @Test
    fun `formatDuration handles exactly one hour`() {
        assertEquals("1h 00m", formatDuration("00d01:00:00"))
    }

    @Test
    fun `formatDuration returns raw string when input is malformed`() {
        assertEquals("bad-input", formatDuration("bad-input"))
    }

    @Test
    fun `formatDuration handles zero duration`() {
        assertEquals("0m", formatDuration("00d00:00:00"))
    }
}
