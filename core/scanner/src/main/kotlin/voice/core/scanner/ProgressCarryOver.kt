package voice.core.scanner

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.FileFingerprint
import voice.core.data.fingerprints
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookMover
import voice.core.data.repo.ChapterRepo
import voice.core.data.toUri

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

  suspend fun carryOver(
    id: BookId,
    previousBooks: PreviousBooks,
  ) = mutex.withLock {
    val content = contentRepo.get(id) ?: return@withLock
    val predecessors = previousBooks.of(content) ?: return@withLock
    val moved = predecessors.chapters
    val playedHere = predecessors.books.filter { it.currentChapter in moved }
    bookMover.move(
      from = predecessors.books.map { it.id },
      to = id,
      chapters = moved,
      sameBook = predecessors.sameBook?.id,
      playedHere = playedHere.map { it.id },
    )
    // a carry-over that runs again, on the next scan, changes nothing: the position only comes along if it's newer
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
  }

  private fun BookContent.withUserDataOf(sameBook: BookContent): BookContent {
    return copy(
      addedAt = minOf(addedAt, sameBook.addedAt),
      name = if (sameBook.nameOverridden) sameBook.name else name,
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
  private val inLibrary = library.filter { it.isActive }.map { it.id }.toSet()

  private val byDocument: Map<String, List<BookContent>> = books
    .flatMap { book -> book.chapters.map { it.documentKey() to book } }
    .groupBy(keySelector = { it.first }, valueTransform = { it.second })

  private var byFiles: Map<List<FileFingerprint>, BookContent>? = null

  suspend fun of(content: BookContent): Predecessors? {
    if (books.isEmpty()) return null
    val isNew = content.id !in inLibrary
    return sharingDocuments(content, isNew)
      // a book that was in the library already is a copy, not the one that moved
      ?: if (isNew) withSameFiles(content.chapters) else null
  }

  private fun sharingDocuments(
    content: BookContent,
    isNew: Boolean,
  ): Predecessors? {
    val chapterByKey = content.chapters.associateBy { it.documentKey() }
    val books = chapterByKey.keys.flatMap { byDocument[it].orEmpty() }.distinctBy { it.id }
    if (books.isEmpty()) return null
    val moved = books
      .flatMap { it.chapters }
      .mapNotNull { chapter -> chapterByKey[chapter.documentKey()]?.let { chapter to it } }
      .toMap()
    // a split up book gets its names from its files, and a book that was in the library already keeps its own
    val bookKey = content.id.toUri().documentKey()
    val sameBook = if (isNew) {
      books.filter { it.id.toUri().documentKey() == bookKey }.maxByOrNull { it.lastPlayedAt }
    } else {
      null
    }
    return Predecessors(books = books, chapters = moved, sameBook = sameBook)
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
