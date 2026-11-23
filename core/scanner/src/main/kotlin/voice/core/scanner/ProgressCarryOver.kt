package voice.core.scanner

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.FileFingerprint
import voice.core.data.fingerprints
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookMover
import voice.core.data.repo.ChapterRepo
import voice.core.data.toUri
import java.time.Instant

/**
 * Audio files show up under a new book id when a book folder is renamed or moved, when a folder's mode
 * changes, or when a folder is added again through another folder. This hands what the listener built up with
 * the book before over to the new id: progress, bookmarks, history and stats, and for the same book its name,
 * shelf, series, characters and up next place. Ported from upstream Voice (#3803, #3911).
 */
@Inject
internal class ProgressCarryOver(
  private val contentRepo: BookContentRepo,
  private val bookMover: BookMover,
) {

  // two copies of a book carrying over at the same time would split its bookmarks and history between them
  private val mutex = Mutex()

  // All or nothing: a scan restarted halfway would leave bookmarks moved but the position behind.
  suspend fun carryOver(
    id: BookId,
    previousBooks: PreviousBooks,
  ) = withContext(NonCancellable) {
    mutex.withLock { carryOverNow(id, previousBooks) }
  }

  private suspend fun carryOverNow(
    id: BookId,
    previousBooks: PreviousBooks,
  ) {
    val content = contentRepo.get(id) ?: return
    // Only a book that was never played takes over. That's every book the scan just added, and it stays
    // true until the carry-over is done, so one that a restarted scan left out is picked up by the next.
    if (content.lastPlayedAt != Instant.EPOCH) return
    val predecessors = previousBooks.of(content) ?: return
    val moved = predecessors.chapters
    val playedHere = predecessors.books.filter { it.currentChapter in moved }
    bookMover.move(
      from = predecessors.books.map { it.id },
      to = id,
      chapters = moved,
      sameBook = predecessors.sameBook?.id,
      playedHere = playedHere.map { it.id },
    )
    val source = playedHere.maxByOrNull { it.lastPlayedAt }?.takeIf { it.lastPlayedAt > content.lastPlayedAt }
    contentRepo.update(id) { current ->
      val withBook = predecessors.sameBook?.let { current.withUserDataOf(it) } ?: current
      if (source == null) {
        withBook
      } else {
        withBook.copy(
          currentChapter = moved.getValue(source.currentChapter),
          positionInChapter = source.positionInChapter,
          lastPlayedAt = source.lastPlayedAt,
          playbackSpeed = source.playbackSpeed,
          skipSilence = source.skipSilence,
          gain = source.gain,
        )
      }
    }
    // The old book handed over what it had, so it has nothing to hand over again: not to a later copy, not to a
    // deleted book's files coming back, and its name can't override a newer one.
    predecessors.sameBook?.let { sameBook ->
      contentRepo.update(sameBook.id) {
        it.copy(
          lastPlayedAt = Instant.EPOCH,
          nameOverridden = false,
          chapterNameOffset = 0,
          seriesGroup = null,
          seriesOrder = null,
          cover = null,
        )
      }
    }
  }

  private fun BookContent.withUserDataOf(sameBook: BookContent): BookContent {
    return copy(
      addedAt = minOf(addedAt, sameBook.addedAt),
      // a name given to the new book already is newer
      name = if (!nameOverridden && sameBook.nameOverridden) sameBook.name else name,
      nameOverridden = nameOverridden || sameBook.nameOverridden,
      chapterNameOffset = if (chapterNameOffset == 0) sameBook.chapterNameOffset else chapterNameOffset,
      seriesGroup = seriesGroup ?: sameBook.seriesGroup,
      seriesOrder = seriesOrder ?: sameBook.seriesOrder,
      // the files are the same, so their cover is too, and a cover the listener picked stays
      cover = cover?.takeIf { it.exists() } ?: sameBook.cover?.takeIf { it.exists() },
    )
  }
}

/** The library as it was before a scan, to find the books a newly found book was before. */
internal class PreviousBooks(
  library: List<BookContent>,
  scanned: Set<BookId>,
  excluded: Set<String>,
  private val chapterRepo: ChapterRepo,
) {

  // a hidden or deleted book stays behind: its files show up as a new book
  private val books = library.filter { it.id !in scanned && it.id.value !in excluded }

  private val byDocument: Map<String, List<BookContent>> = books
    .flatMap { book -> book.chapters.map { it.documentKey() to book } }
    .groupBy(keySelector = { it.first }, valueTransform = { it.second })

  private var byFiles: Map<List<FileFingerprint>, BookContent>? = null

  suspend fun of(content: BookContent): Predecessors? {
    if (books.isEmpty()) return null
    // A book played already is a copy, not the one that moved. Never played is what a scan gives a book it
    // adds, and it lasts until the carry-over is done, unlike "not in the library when the scan started".
    val isNew = content.lastPlayedAt == Instant.EPOCH
    return sharingDocuments(content, isNew)
      ?: if (isNew) withSameFiles(content.chapters) else null
  }

  private suspend fun sharingDocuments(
    content: BookContent,
    isNew: Boolean,
  ): Predecessors? {
    val chapterByKey = content.chapters.associateBy { it.documentKey() }
    val books = chapterByKey.keys.flatMap { byDocument[it].orEmpty() }.distinctBy { it.id }
    if (books.isEmpty()) return null
    val moved = books
      .flatMap { it.chapters }
      .mapNotNull { chapter -> chapterByKey[chapter.documentKey()]?.let { chapter to it } }
      // the same document can hold another file by now, like after replacing a book's files
      .filter { (old, new) -> sameSize(old, new) }
      .toMap()
    if (moved.isEmpty()) return null
    // A split up book gets its names from its files, and a book that was in the library already keeps its own.
    // The same book is in the same folder, or has the same files after its folder's mode changed.
    val bookKey = content.id.toUri().documentKey()
    val files = content.chapters.toSet()
    val sameBook = if (isNew) {
      books
        .filter { book -> book.id.toUri().documentKey() == bookKey || book.chapters.mapTo(mutableSetOf()) { moved[it] } == files }
        .maxByOrNull { it.lastPlayedAt }
    } else {
      null
    }
    return Predecessors(books = books, chapters = moved, sameBook = sameBook)
  }

  private suspend fun sameSize(
    old: ChapterId,
    new: ChapterId,
  ): Boolean {
    val oldSize = chapterRepo.get(old)?.fileSize ?: 0
    val newSize = chapterRepo.get(new)?.fileSize ?: 0
    // some providers don't report sizes
    return oldSize <= 0 || newSize <= 0 || oldSize == newSize
  }

  private suspend fun withSameFiles(chapters: List<ChapterId>): Predecessors? {
    val stored = chapters.map { chapterRepo.get(it) ?: return null }
    val files = stored.fingerprints() ?: return null
    val book = byFiles()[files] ?: return null
    return Predecessors(books = listOf(book), chapters = book.chapters.zip(chapters).toMap(), sameBook = book)
  }

  private suspend fun byFiles(): Map<List<FileFingerprint>, BookContent> {
    byFiles?.let { return it }
    return books
      // of copies with the same files, the one played last wins
      .sortedBy { it.lastPlayedAt }
      .mapNotNull { book ->
        val chapters = book.chapters.map { chapterRepo.get(it) ?: return@mapNotNull null }
        chapters.fingerprints()?.let { it to book }
      }
      .toMap()
      .also { byFiles = it }
  }
}

internal class Predecessors(
  val books: List<BookContent>,
  // the chapters of [books] that are now chapters of the new book, mapped to them
  val chapters: Map<ChapterId, ChapterId>,
  val sameBook: BookContent?,
)

private fun ChapterId.documentKey(): String = value.toUri().documentKey()

// the same document opened through another folder has another uri
internal fun Uri.documentKey(): String {
  if (scheme != "content") return toString()
  return try {
    "$authority:${DocumentsContract.getDocumentId(this)}"
  } catch (_: IllegalArgumentException) {
    toString()
  }
}
