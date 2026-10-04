package voice.core.data.store.snapshot

import voice.core.data.BookContent
import java.io.File

internal fun BookContent.preserveNewerPlaybackFrom(live: BookContent?): BookContent {
  if (live == null) return this
  val preserveLiveProgress = live.lastPlayedAt > lastPlayedAt || currentChapter !in live.chapters
  return copy(
    chapters = live.chapters,
    currentChapter = if (preserveLiveProgress) live.currentChapter else currentChapter,
    positionInChapter = if (preserveLiveProgress) live.positionInChapter else positionInChapter,
    lastPlayedAt = if (preserveLiveProgress) live.lastPlayedAt else lastPlayedAt,
  )
}

/** Delete only replaced files from the same app-owned directories as the imported artwork. */
internal fun deleteReplacedCovers(
  oldCovers: Iterable<File?>,
  importedCovers: Iterable<File>,
  referencedCovers: Iterable<File?>,
) {
  val roots = importedCovers.mapNotNullTo(mutableSetOf()) { runCatching { it.canonicalFile }.getOrNull()?.parentFile }
  if (roots.isEmpty()) return
  val referenced = referencedCovers.mapNotNullTo(mutableSetOf()) { runCatching { it?.canonicalFile }.getOrNull() }
  oldCovers.mapNotNull { it }.distinct().forEach { old ->
    val canonical = runCatching { old.canonicalFile }.getOrNull() ?: return@forEach
    val parent = canonical.parentFile ?: return@forEach
    if (parent in roots && canonical !in referenced) canonical.delete()
  }
}
