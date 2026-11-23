package voice.core.scanner

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.repo.BookContentRepo
import voice.core.data.store.IgnoreFileTagsStore
import voice.core.data.toUri
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.logging.api.Logger
import java.time.Instant

@Inject
internal class BookParser(
  private val context: Context,
  private val contentRepo: BookContentRepo,
  private val mediaAnalyzer: MediaAnalyzer,
  private val fileFactory: CachedDocumentFileFactory,
  @IgnoreFileTagsStore
  private val ignoreFileTagsStore: DataStore<Boolean>,
) {

  suspend fun parseAndStore(
    chapters: List<Chapter>,
    file: CachedDocumentFile,
    firstChapterMetadata: Metadata? = null,
    forceReParse: Boolean = false,
  ): BookContent {
    val id = BookId(file.uri)
    val existing = contentRepo.get(id)
    // A null synopsis means the book predates synopses: read its files once more to pick one up.
    if (existing != null && !forceReParse && existing.synopsis != null) {
      return existing
    }

    val ignoreFileTags = ignoreFileTagsStore.data.first()
    val analyzed = if (ignoreFileTags) {
      null
    } else {
      firstChapterMetadata ?: run {
        val uri = chapters.first().id.toUri()
        mediaAnalyzer.analyze(fileFactory.create(uri))
      }
    }
    val parsed = parse(chapters, id, analyzed, file)

    // When re-parsing an existing book (e.g. after toggling "ignore file tags"), only the
    // metadata derived from tags/file names is refreshed. Playback-related state such as the
    // position, speed and added/last-played timestamps is preserved. The one-time synopsis
    // look-up for books from before synopses changes nothing else.
    val content = if (existing != null) {
      contentRepo.update(id) { current ->
        if (!forceReParse) return@update current.copy(synopsis = parsed.synopsis)
        current.copy(
          author = parsed.author,
          name = if (current.nameOverridden) current.name else parsed.name,
          genre = parsed.genre,
          narrator = parsed.narrator,
          series = parsed.series,
          part = parsed.part,
          synopsis = parsed.synopsis,
        )
      } ?: parsed.also { contentRepo.put(it) }
    } else {
      parsed.also { contentRepo.put(it) }
    }
    return content
  }

  fun parse(
    chapters: List<Chapter>,
    id: BookId,
    analyzed: Metadata?,
    file: CachedDocumentFile,
  ): BookContent {
    return BookContent(
      id = id,
      isActive = true,
      addedAt = Instant.now(),
      author = analyzed?.artist,
      lastPlayedAt = Instant.EPOCH,
      name = analyzed?.album
        ?: analyzed?.title?.takeIf { file.isFile }
        ?: file.bookName(),
      playbackSpeed = 1F,
      skipSilence = false,
      chapters = chapters.map { it.id },
      positionInChapter = 0L,
      currentChapter = chapters.first().id,
      cover = null,
      gain = 0F,
      genre = analyzed?.genre,
      narrator = analyzed?.narrator,
      series = analyzed?.series,
      part = analyzed?.part,
      // Empty, not null, once looked for: a synopsis file beside the audio wins over the tags.
      synopsis = file.synopsisFile() ?: analyzed?.synopsis ?: "",
    ).also {
      validateIntegrity(it, chapters)
    }
  }

  /** The text of a desc.txt or description.txt in a folder book, as audiobook managers write them. */
  private fun CachedDocumentFile.synopsisFile(): String? {
    if (!isDirectory) return null
    val file = children.firstOrNull { it.isFile && it.name?.lowercase() in SYNOPSIS_FILES && it.length in 1..MAX_SYNOPSIS_BYTES }
      ?: return null
    return try {
      context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().decodeToString() }
        ?.removePrefix("\uFEFF")?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
      Logger.w(e, "Could not read ${file.uri}")
      null
    }
  }

  private fun CachedDocumentFile.bookName(): String {
    val fileName = name
    return if (fileName == null) {
      uri.toString()
        .removePrefix("/storage/emulated/0/")
        .removePrefix("/storage/emulated/")
        .removePrefix("/storage/")
        .also {
          Logger.w("Could not parse fileName from $this. Fallback to $it")
        }
    } else {
      if (isFile) {
        fileName.substringBeforeLast(".")
      } else {
        fileName
      }
    }
  }
}

internal fun validateIntegrity(
  content: BookContent,
  chapters: List<Chapter>,
) {
  // the init block performs integrity validation
  @Suppress("RETURN_VALUE_NOT_USED")
  Book(content, chapters)
}

internal fun Uri.filePath(): String? {
  return pathSegments.lastOrNull()
    ?.dropWhile { it != ':' }
    ?.removePrefix(":")
}

private val SYNOPSIS_FILES = setOf("desc.txt", "description.txt")
private const val MAX_SYNOPSIS_BYTES = 64 * 1024L
