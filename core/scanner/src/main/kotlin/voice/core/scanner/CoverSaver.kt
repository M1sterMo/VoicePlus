package voice.core.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

@Inject
public class CoverSaver
internal constructor(
  private val repo: BookRepository,
  private val context: Context,
  private val drafts: BookEditDraftStore,
) {

  @IgnorableReturnValue
  public suspend fun save(
    bookId: BookId,
    cover: Bitmap,
    editSession: String? = null,
  ): Boolean {
    val newCover = if (editSession == null) newBookCoverFile() else newDraftCoverFile()

    val saved = withContext(Dispatchers.IO) {
      // scale down if bitmap is too large
      val preferredSize = 1920
      val bitmapToSave = if (max(cover.width, cover.height) > preferredSize) {
        val scale = preferredSize.toFloat() / max(cover.width, cover.height)
        cover.scale((cover.width * scale).roundToInt().coerceAtLeast(1), (cover.height * scale).roundToInt().coerceAtLeast(1))
      } else {
        cover
      }

      try {
        FileOutputStream(newCover).use {
          if (!bitmapToSave.compress(Bitmap.CompressFormat.PNG, 70, it)) {
            throw IOException("Could not encode cover")
          }
          it.flush()
        }
        true
      } catch (e: IOException) {
        Logger.w(e, "Error at saving image with destination=$newCover")
        newCover.delete()
        false
      } finally {
        if (bitmapToSave !== cover) bitmapToSave.recycle()
      }
    }

    if (saved) {
      if (editSession != null) return drafts.stageCover(editSession, newCover)
      try {
        withContext(kotlinx.coroutines.NonCancellable) { setBookCover(newCover, bookId) }
      } catch (error: Exception) {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { newCover.delete() }
        throw error
      }
    }
    return saved
  }

  internal suspend fun newBookCoverFile(): File {
    val coversFolder = withContext(Dispatchers.IO) {
      File(context.filesDir, "bookCovers")
        .also { coverFolder -> coverFolder.mkdirs() }
    }
    return File(coversFolder, "${UUID.randomUUID()}.png")
  }

  internal suspend fun newDraftCoverFile(): File {
    val coversFolder = withContext(Dispatchers.IO) {
      File(context.filesDir, "draftBookCovers")
        .also { coverFolder -> coverFolder.mkdirs() }
    }
    return File(coversFolder, "${UUID.randomUUID()}.png")
  }

  internal suspend fun setBookCover(
    cover: File,
    bookId: BookId,
  ) {
    val oldCover = repo.get(bookId)?.content?.cover
    repo.updateBook(bookId) {
      it.copy(cover = cover)
    }
    if (oldCover != null && oldCover != cover) {
      withContext(Dispatchers.IO) {
        oldCover.delete()
      }
    }
  }

  /** Scanner-only: adopt [cover] only if a user edit has not supplied a valid cover meanwhile. */
  internal suspend fun setBookCoverIfMissing(
    cover: File,
    bookId: BookId,
  ): Boolean {
    var adopted = false
    var oldCover: File? = null
    repo.updateBook(bookId) { current ->
      if (current.cover?.exists() == true) {
        current
      } else {
        adopted = true
        oldCover = current.cover
        current.copy(cover = cover)
      }
    }
    if (adopted && oldCover != null && oldCover != cover) {
      withContext(Dispatchers.IO) { oldCover?.delete() }
    }
    return adopted
  }
}
