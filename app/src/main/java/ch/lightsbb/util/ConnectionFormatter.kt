package ch.lightsbb.util

// ISO 8601 → "HH:MM"  e.g. "2024-01-15T10:32:00+0100" → "10:32"
internal fun formatTime(iso: String): String = try {
    iso.substringAfter("T").take(5)
} catch (_: Exception) {
    iso
}

// API duration string → human label  e.g. "00d00:57:00" → "57m", "00d01:03:00" → "1h 03m"
internal fun formatDuration(raw: String): String = try {
    val time = raw.substringAfter("d")
    val parts = time.split(":")
    val h = parts[0].toInt()
    val m = parts[1].toInt()
    if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
} catch (_: Exception) {
    raw
}
