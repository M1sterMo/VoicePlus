package voice.core.data.store.snapshot

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.CRC32

private const val FORMAT_VERSION = 1

@Serializable
private data class ExternalBackupBundle(
  val formatVersion: Int = FORMAT_VERSION,
  val savedAtEpochMillis: Long,
  val payload: LibrarySnapshot,
  val payloadCrc32: Long,
)

internal sealed interface ExternalBackupBundleDecodeResult {
  data class Valid(
    val snapshot: LibrarySnapshot,
    val covers: Map<String, File> = emptyMap(),
  ) : ExternalBackupBundleDecodeResult
  data object Corrupt : ExternalBackupBundleDecodeResult

  // Parseable envelope from a NEWER app version. Distinct from Corrupt: restore must STOP and
  // tell the user to update, not silently skip past it to older data.
  data object NewerFormat : ExternalBackupBundleDecodeResult
}

internal object ExternalBackupBundleCodec {

  fun encode(
    json: Json,
    snapshot: LibrarySnapshot,
  ): String {
    val bundle = ExternalBackupBundle(
      savedAtEpochMillis = System.currentTimeMillis(),
      payload = snapshot,
      payloadCrc32 = snapshot.crc32(json),
    )
    return json.encodeToString(bundle)
  }

  fun decode(
    json: Json,
    text: String,
  ): ExternalBackupBundleDecodeResult {
    runCatching { json.parseToJsonElement(text).jsonObject }
      .getOrNull()
      ?.let { root ->
        val bundle = runCatching {
          json.decodeFromJsonElement(ExternalBackupBundle.serializer(), root)
        }.getOrNull()
          ?: return@let
        if (bundle.formatVersion > FORMAT_VERSION) return ExternalBackupBundleDecodeResult.NewerFormat
        if (bundle.formatVersion != FORMAT_VERSION) return ExternalBackupBundleDecodeResult.Corrupt
        val storedPayload = root["payload"] ?: return ExternalBackupBundleDecodeResult.Corrupt
        return if (storedPayload.crc32(json) == bundle.payloadCrc32) {
          ExternalBackupBundleDecodeResult.Valid(bundle.payload)
        } else {
          ExternalBackupBundleDecodeResult.Corrupt
        }
      }

    // Earlier v1.23 bundles were raw LibrarySnapshot JSON. Keep them importable; new writes use the wrapper.
    return runCatching {
      ExternalBackupBundleDecodeResult.Valid(json.decodeFromString<LibrarySnapshot>(text))
    }.getOrElse {
      ExternalBackupBundleDecodeResult.Corrupt
    }
  }

  fun meaningfulFingerprint(
    json: Json,
    snapshot: LibrarySnapshot,
  ): Long {
    return snapshot.copy(sequence = 0L, savedAtEpochMillis = 0L).crc32(json)
  }

  private fun LibrarySnapshot.crc32(json: Json): Long {
    val bytes = json.encodeToString(LibrarySnapshot.serializer(), this).encodeToByteArray()
    return CRC32().apply { update(bytes) }.value
  }

  private fun JsonElement.crc32(json: Json): Long {
    val bytes = json.encodeToString(JsonElement.serializer(), this).encodeToByteArray()
    return CRC32().apply { update(bytes) }.value
  }
}
