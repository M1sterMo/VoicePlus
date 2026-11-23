package voice.core.data

import android.provider.DocumentsContract
import androidx.core.net.toUri

/** What tells a file apart after it moved, where its uri changed with its path. */
public data class FileFingerprint(
  val name: String,
  val size: Long,
)

/** The fingerprints of these chapters' files, or null if one can't be told apart this way. */
public fun List<Chapter>.fingerprints(): List<FileFingerprint>? = map {
  it.fingerprint() ?: return null
}

private fun Chapter.fingerprint(): FileFingerprint? {
  // some providers don't report sizes, and a name like 01.mp3 alone says nothing
  if (fileSize <= 0) return null
  return id.fileName()?.let { FileFingerprint(name = it, size = fileSize) }
}

// other providers have ids without the path, which usually stay the same when a file moves
private fun ChapterId.fileName(): String? {
  val uri = value.toUri()
  return when {
    uri.scheme == "file" -> uri.lastPathSegment
    uri.authority == "com.android.externalstorage.documents" -> try {
      DocumentsContract.getDocumentId(uri).substringAfter(':').substringAfterLast('/')
    } catch (_: IllegalArgumentException) {
      null
    }
    else -> null
  }?.takeIf { it.isNotEmpty() }
}
