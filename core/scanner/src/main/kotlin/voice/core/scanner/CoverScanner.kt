package voice.core.scanner

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import voice.core.data.Book
import voice.core.data.store.IgnoreFileTagsStore
import voice.core.data.store.NoEmbeddedCoverStore
import voice.core.data.toUri
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.logging.api.Logger
import java.io.File
import java.io.IOException

// Windows Media Player saves a small Folder.jpg next to the files, so it comes last
private val coverNames = listOf("cover", "front", "folder")

private val imageExtensions = setOf("jpg", "jpeg", "jfif", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

/** Sort key that puts an image named like a cover before the other images of a book folder. */
internal fun coverRank(fileName: String?): Int {
  val rank = coverNames.indexOf(fileName?.substringBeforeLast('.')?.lowercase())
  return if (rank == -1) coverNames.size else rank
}

// The files the embedded cover is looked for in, and their state: a book whose key is remembered had no
// embedded cover in them, and once one of them changes, the key does too and they're read again.
private fun Book.noEmbeddedCoverKey(): String {
  val files = chapters.take(5).map { Triple(it.id.value, it.fileLastModified, it.fileSize) }
  return "${id.value}|${files.hashCode()}"
}

@Inject
internal class CoverScanner(
  private val context: Context,
  private val coverSaver: CoverSaver,
  private val coverExtractor: CoverExtractor,
  private val documentFileFactory: CachedDocumentFileFactory,
  @IgnoreFileTagsStore
  private val ignoreFileTagsStore: DataStore<Boolean>,
  @NoEmbeddedCoverStore
  private val noEmbeddedCoverStore: DataStore<Set<String>>,
) {

  suspend fun scan(books: List<Book>) {
    books.forEach { book ->
      // one book's storage failing must not end the search for all the others
      try {
        findCoverForBook(book)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Logger.w(e, "Could not look for the cover of ${book.id}")
      }
    }
    // forget books that left the library, so the set doesn't grow forever
    val ids = books.mapTo(mutableSetOf()) { it.id.value }
    noEmbeddedCoverStore.updateData { keys -> keys.filterTo(mutableSetOf()) { it.substringBeforeLast('|') in ids } }
  }

  private suspend fun findCoverForBook(book: Book) {
    val coverFile = book.content.cover
    if (coverFile != null && coverFile.exists()) {
      return
    }

    val foundOnDisc = findAndSaveCoverFromDisc(book)
    if (foundOnDisc) {
      return
    }

    if (!ignoreFileTagsStore.data.first() && book.noEmbeddedCoverKey() !in noEmbeddedCoverStore.data.first()) {
      scanForEmbeddedCover(book)
    }
  }

  // Lists the book folder once; asking each file whether it's a readable image costs a query per file.
  private suspend fun findAndSaveCoverFromDisc(book: Book): Boolean = withContext(Dispatchers.IO) {
    documentFileFactory.create(book.id.toUri()).children
      .filter { child ->
        child.isFile && child.name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase() in imageExtensions
      }
      .sortedBy { child -> coverRank(child.name) }
      .any { child -> offerCover(book) { file -> copy(child.uri, file) } }
  }

  private fun copy(
    uri: Uri,
    file: File,
  ): Boolean {
    return try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output ->
          input.copyTo(output)
        }
      } != null
    } catch (e: IOException) {
      Logger.w(e, "Error while copying the cover from $uri")
      false
    } catch (e: IllegalStateException) {
      // On some Samsung Devices, openInputStream throws this exception, though it should not.
      Logger.w(e, "Error while copying the cover from $uri")
      false
    }
  }

  private suspend fun scanForEmbeddedCover(book: Book) {
    var unreadable = false
    book.chapters.take(5).forEach { chapter ->
      var hasCover: Boolean? = null
      val found = offerCover(book) { file ->
        hasCover = coverExtractor.extractCover(input = chapter.id.toUri(), outputFile = file)
        hasCover == true
      }
      if (found) return
      if (hasCover == null) unreadable = true
    }
    // a file that couldn't be read now, like on an offline cloud, is tried again on the next scan
    if (!unreadable) {
      val key = book.noEmbeddedCoverKey()
      val prefix = "${book.id.value}|"
      noEmbeddedCoverStore.updateData { keys -> keys.filterNotTo(mutableSetOf()) { it.startsWith(prefix) } + key }
    }
  }

  /**
   * Has [write] put a cover candidate into a new cover file and offers it to the book. Returns whether a
   * cover was written. The file is deleted unless the book took it, also when the scan is cancelled: the
   * cover search is cancelled whenever a new scan starts.
   */
  private suspend fun offerCover(
    book: Book,
    write: suspend (File) -> Boolean,
  ): Boolean {
    val file = coverSaver.newBookCoverFile()
    var adopted = false
    try {
      if (!write(file) || file.length() == 0L) return false
      // not cancelled halfway, so the book never points at a file deleted below
      withContext(NonCancellable) { adopted = coverSaver.setBookCoverIfMissing(file, book.id) }
      return true
    } finally {
      if (!adopted) file.delete()
    }
  }
}
