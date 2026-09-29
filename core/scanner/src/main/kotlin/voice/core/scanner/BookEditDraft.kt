package voice.core.scanner

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.toUri
import java.io.File
import java.util.UUID

public data class BookEditDraft(
  val token: String,
  val bookId: BookId,
  val title: String,
  val cover: File?,
  val coverChanged: Boolean = false,
)

/** One editor session shared with the existing cover search/crop routes. Nothing writes until Save. */
@SingleIn(AppScope::class)
@Inject
public class BookEditDraftStore internal constructor(
  private val repo: BookRepository,
  private val context: Context,
  private val extractor: CoverExtractor,
) {
  private val mutable = MutableStateFlow<BookEditDraft?>(null)
  public val draft: StateFlow<BookEditDraft?> = mutable
  private val mutex = Mutex()

  public suspend fun begin(id: BookId) {
    mutex.withLock {
      val book = repo.get(id) ?: return
      val previous = mutable.value
      mutable.value = BookEditDraft(UUID.randomUUID().toString(), id, book.content.name, book.content.cover)
      discardCover(previous)
      purgeStaleDraftCovers()
    }
  }

  public fun titleChanged(title: String) {
    mutable.update { it?.copy(title = title) }
  }

  @IgnorableReturnValue
  public suspend fun stageCover(
    token: String,
    cover: File,
  ): Boolean = mutex.withLock {
    val current = mutable.value?.takeIf { it.token == token }
    if (current == null) {
      withContext(NonCancellable + Dispatchers.IO) { cover.delete() }
      return@withLock false
    }
    mutable.update { it?.copy(cover = cover, coverChanged = true) }
    if (current.cover != cover) discardCover(current)
    true
  }

  public suspend fun discard() {
    mutex.withLock {
      val previous = mutable.value
      mutable.value = null
      discardCover(previous)
    }
  }

  public suspend fun save(): Boolean = mutex.withLock {
    val current = mutable.value ?: return@withLock false
    if (current.title.isBlank()) return@withLock false
    val original = repo.get(current.bookId) ?: return@withLock false
    // Once the database adopts the draft file, cancellation must not leave it marked disposable.
    withContext(NonCancellable) {
      val committedCover = if (current.coverChanged) commitCover(current.cover) else current.cover
      if (current.coverChanged) mutable.update { it?.copy(cover = committedCover) }
      repo.updateBook(current.bookId) {
        val title = current.title.trim()
        it.copy(
          name = title,
          cover = if (current.coverChanged) committedCover else it.cover,
          nameOverridden = it.nameOverridden || title != original.content.name,
        )
      }
      mutable.value = null
      if (current.coverChanged && original.content.cover != committedCover) {
        withContext(Dispatchers.IO) { original.content.cover?.delete() }
      }
    }
    true
  }

  public suspend fun useEmbeddedCover(): Boolean {
    val current = mutable.value ?: return false
    val book = repo.get(current.bookId) ?: return false
    val cover = withContext(Dispatchers.IO) {
      val directory = draftCoverDirectory().apply { mkdirs() }
      File.createTempFile("edit-", ".img", directory)
    }
    var adopted = false
    try {
      if (book.chapters.take(5).any { extractor.extractCover(it.id.toUri(), cover) }) {
        withContext(NonCancellable) { adopted = stageCover(current.token, cover) }
        return adopted
      }
      return false
    } finally {
      if (!adopted) withContext(NonCancellable + Dispatchers.IO) { cover.delete() }
    }
  }

  private suspend fun discardCover(draft: BookEditDraft?) {
    if (draft?.coverChanged == true) withContext(NonCancellable + Dispatchers.IO) { draft.cover?.delete() }
  }

  private fun draftCoverDirectory(): File = File(context.filesDir, "draftBookCovers")

  private suspend fun purgeStaleDraftCovers() = withContext(NonCancellable + Dispatchers.IO) {
    draftCoverDirectory().listFiles().orEmpty().forEach { it.delete() }
  }

  private suspend fun commitCover(source: File?): File? = source?.let {
    withContext(NonCancellable + Dispatchers.IO) {
      val directory = File(context.filesDir, "bookCovers").apply { mkdirs() }
      val destination = File(directory, "${UUID.randomUUID()}.${source.extension.ifEmpty { "img" }}")
      try {
        if (!source.renameTo(destination)) {
          source.copyTo(destination)
          source.delete()
        }
        destination
      } catch (error: Exception) {
        destination.delete()
        throw error
      }
    }
  }
}
