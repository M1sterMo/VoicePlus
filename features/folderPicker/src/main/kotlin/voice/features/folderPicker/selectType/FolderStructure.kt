package voice.features.folderPicker.selectType

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import voice.core.data.BookId
import voice.core.data.isAudioFile
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension
import voice.core.documentfile.walk

/**
 * Guesses how the books in this folder are organized, looking at its first two levels:
 * - Only folders like "CD 1" and "CD 2", or mostly loose chapter files: one book.
 * - Mostly folders that hold several books (book folders or whole-book files): authors (or series).
 * - Otherwise: a library where every folder or audiobook file is a book.
 */
internal suspend fun CachedDocumentFile.guessFolderMode(): FolderMode {
  val folders = audioFolders()
  if (folders.isNotEmpty() && folders.all { it.isPartFolder() }) {
    return FolderMode.SingleBook
  }
  val (wholeBooks, chapters) = audioFiles().partition { it.looksLikeWholeBook() }
  if (chapters.size > folders.size + wholeBooks.size) {
    return FolderMode.SingleBook
  }
  val authorFolders = folders.count { it.looksLikeAuthor() }
  return if (authorFolders * 2 > folders.size) FolderMode.Authors else FolderMode.Audiobooks
}

/**
 * The books the scanner adds from this folder in the given [mode], mirroring MediaScanner: its
 * [excludedIds] are left out, as the scan skips them.
 */
internal suspend fun CachedDocumentFile.books(
  mode: FolderMode,
  excludedIds: Set<String> = emptySet(),
): List<SelectFolderTypeViewState.Book> {
  val files = when (mode) {
    FolderMode.SingleBook -> listOf(this to null)
    FolderMode.Audiobooks -> children.map { it to null }
    FolderMode.Authors -> children.flatMap { author ->
      if (author.isFile) {
        listOf(author to null)
      } else {
        val authorName = author.nameWithoutExtension()
        author.children.map { it to authorName }
      }
    }
  }
  return files
    .filter { (file, _) -> BookId(file.uri).value !in excludedIds }
    .map { (file, author) ->
      SelectFolderTypeViewState.Book(name = file.nameWithoutExtension(), author = author, fileCount = file.audioFileCount())
    }
    .filter { it.fileCount > 0 }
}

// walks the whole tree, so it checks for cancellation as it goes
private suspend fun CachedDocumentFile.audioFileCount(): Int {
  var count = 0
  for (file in walk()) {
    currentCoroutineContext().ensureActive()
    if (file.isAudioFile()) count++
  }
  return count
}

private fun CachedDocumentFile.audioFiles(): List<CachedDocumentFile> = children.filter { it.isAudioFile() }

private suspend fun CachedDocumentFile.audioFolders(): List<CachedDocumentFile> {
  return children.filter { it.isDirectory && it.audioFileCount() > 0 }
}

// an author folder holds several books, as book folders or whole-book files, but no loose chapters
private suspend fun CachedDocumentFile.looksLikeAuthor(): Boolean {
  val (wholeBooks, chapters) = audioFiles().partition { it.looksLikeWholeBook() }
  return chapters.isEmpty() && (wholeBooks.size > 1 || audioFolders().any { !it.isPartFolder() })
}

// An m4b or a very large file is a whole book, unless it is named like a part of one, as in a book
// split into "Chapter 01.m4b", "Chapter 02.m4b".
private fun CachedDocumentFile.looksLikeWholeBook(): Boolean {
  val bookSized = name.orEmpty().endsWith(".m4b", ignoreCase = true) || length > WHOLE_BOOK_MIN_BYTES
  return bookSized && !partFileName.matches(nameWithoutExtension().trim())
}

private fun CachedDocumentFile.isPartFolder(): Boolean = partFolderName.matches(name.orEmpty().trim())

// "CD 1", "Disc 02", "My Book - CD2", "Part 3", "Teil 1", "Vol. 2" or just "01"
private val partFolderName = Regex(
  """(?i)^((.*[\s._-])?(cd|disc|disk|tape|side)|part|teil|vol\.?|volume)?[\s._-]*\d{1,3}$""",
)

// "01", "Chapter 01", "Track 3", "My Book - Part 2", "Kapitel 4", but not "Harry Potter 1"
private val partFileName = Regex(
  """(?i)^((.*[\s._-])?(chapter|chap|ch|track|part|teil|kapitel|cd|disc|disk))?[\s._-]*\d{1,3}$""",
)

private const val WHOLE_BOOK_MIN_BYTES = 200_000_000L
