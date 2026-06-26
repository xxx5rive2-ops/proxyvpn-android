package app.netguard.common

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Returns current UTC time */
fun now(): Instant = Clock.System.now()

/** Format bytes to human-readable string (e.g. 1.5 MB) */
fun Long.toHumanReadableBytes(): String = when {
    this < 1_024L              -> "${this} B"
    this < 1_048_576L          -> "${"%.1f".format(this / 1_024.0)} KB"
    this < 1_073_741_824L      -> "${"%.1f".format(this / 1_048_576.0)} MB"
    else                       -> "${"%.2f".format(this / 1_073_741_824.0)} GB"
}

/** Safe conversion of ByteArray to hex string */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
