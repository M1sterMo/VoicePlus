package voice.core.data.store.snapshot

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The file name identifies the backup kind and timestamp. No separate directory index is needed.
 *
 * New saves are ZIP archives containing JSON and artwork; old JSON-only saves remain readable.
 *
 * Timestamps are UTC so file names sort identically everywhere.
 */
internal object BackupFileNames {

  const val LEGACY_PRIMARY = "voiceplus-backup.json"
  const val LEGACY_PREVIOUS = "voiceplus-backup.previous.json"

  private const val AUTO_PREFIX = "voiceplus-backup-"
  private const val MANUAL_PREFIX = "voiceplus-manual-"
  private const val SUFFIX = ".zip"

  private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
  private val LEGACY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
  private val NAME = Regex("""^voiceplus-(backup|manual)-(\d{8}-\d{6}(?:-\d{3})?)(?:-([0-9a-f]{8}))?\.(?:json|zip)$""")

  fun fileName(
    manual: Boolean,
    at: Instant,
    unique: String = UUID.randomUUID().toString().take(8),
  ): String = (if (manual) MANUAL_PREFIX else AUTO_PREFIX) + FORMAT.format(at) + "-$unique" + SUFFIX

  data class Parsed(
    val savedAt: Instant?,
    val manual: Boolean,
    val legacy: Boolean,
  )

  /** Null when [displayName] is not a VoicePlus backup file. */
  fun parse(displayName: String): Parsed? {
    if (displayName == LEGACY_PRIMARY || displayName == LEGACY_PREVIOUS) {
      return Parsed(savedAt = null, manual = false, legacy = true)
    }
    val match = NAME.matchEntire(displayName) ?: return null
    val timestamp = match.groupValues[2]
    val format = if (timestamp.length == 15) LEGACY_FORMAT else FORMAT
    val savedAt = runCatching { Instant.from(format.parse(timestamp)) }.getOrNull() ?: return null
    return Parsed(savedAt = savedAt, manual = match.groupValues[1] == "manual", legacy = false)
  }
}
