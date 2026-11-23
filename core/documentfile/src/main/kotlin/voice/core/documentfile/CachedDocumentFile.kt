package voice.core.documentfile

import android.net.Uri

interface CachedDocumentFile {
  val children: List<CachedDocumentFile>

  /** False for a folder whose entries couldn't be listed: its [children] are empty then, like an empty folder's. */
  val childrenListed: Boolean
  val name: String?
  val isDirectory: Boolean
  val isFile: Boolean
  val length: Long
  val lastModified: Long
  val uri: Uri
}

fun CachedDocumentFile.nameWithoutExtension(): String {
  val name = name
  return if (name == null) {
    uri.pathSegments.lastOrNull()
      ?.dropWhile { it != ':' }
      ?.removePrefix(":")
      ?.takeUnless { it.isBlank() }
      ?: uri.toString()
  } else {
    if (isFile) {
      name.substringBeforeLast(".")
        .takeUnless { it.isEmpty() }
        ?: name
    } else {
      name
    }
  }
}

/** A name for a file or folder that can't be asked for it, like one without access: the last part of its path. */
fun Uri.nameFromPath(): String {
  return pathSegments
    .lastOrNull()
    ?.dropWhile { it != ':' }
    ?.removePrefix(":")
    ?.substringAfterLast('/')
    ?.takeUnless { it.isBlank() } ?: toString()
}
