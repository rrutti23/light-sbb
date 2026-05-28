package ch.lightsbb.util

/**
 * Extracts the `HH:MM` time portion from an ISO 8601 timestamp string.
 *
 * The transport.opendata.ch API returns departure and arrival times as full ISO 8601 strings
 * with a UTC offset, for example `"2024-01-15T10:32:00+0100"`. Only the hours and minutes
 * are relevant for the departure board view — seconds and the timezone offset are discarded.
 *
 * The function deliberately swallows parse exceptions and returns the raw [iso] string as a
 * fallback, so a malformed API response degrades gracefully rather than crashing the UI.
 *
 * @param iso An ISO 8601 datetime string, e.g. `"2024-01-15T10:32:00+0100"`.
 * @return The `HH:MM` substring, e.g. `"10:32"`, or [iso] unchanged if parsing fails.
 */
internal fun formatTime(iso: String): String = try {
    // Everything after the 'T' separator starts with HH:MM:SS... — take the first 5 chars.
    iso.substringAfter("T").take(5)
} catch (_: Exception) {
    iso
}

/**
 * Converts the API's custom duration string into a concise human-readable label.
 *
 * The transport.opendata.ch API encodes connection durations as `"DDdHH:MM:SS"`, where `DD`
 * is days, `HH` hours, `MM` minutes, and `SS` seconds. In practice Swiss domestic connections
 * are always less than one day, so the days component is always `"00"`. Seconds are also
 * omitted from the display as they add no useful information for journey planning.
 *
 * Examples:
 * - `"00d00:57:00"` → `"57m"`
 * - `"00d01:23:00"` → `"1h 23m"`
 * - `"00d02:05:00"` → `"2h 05m"` (minutes padded to two digits for visual alignment)
 *
 * Like [formatTime], parse failures return the raw string rather than throwing, so unexpected
 * API format changes surface as visible but non-fatal display glitches.
 *
 * @param raw Duration string in the API format, e.g. `"00d00:57:00"`.
 * @return A short label like `"57m"` or `"1h 23m"`, or [raw] unchanged if parsing fails.
 */
internal fun formatDuration(raw: String): String = try {
    // Strip the leading days component (always "00d" for domestic Swiss connections).
    val time = raw.substringAfter("d")
    val parts = time.split(":")
    val h = parts[0].toInt()
    val m = parts[1].toInt()
    // Pad minutes to two digits so "1h 05m" aligns visually with "1h 23m".
    if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
} catch (_: Exception) {
    raw
}
