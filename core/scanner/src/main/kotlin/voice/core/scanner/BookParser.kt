package voice.core.scanner

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
    if (existing != null && !forceReParse) {
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
    // position, speed and added/last-played timestamps is preserved.
    val content = if (existing != null) {
      contentRepo.update(id) { current ->
        current.copy(
          author = parsed.author,
          name = if (current.nameOverridden) current.name else parsed.name,
          genre = parsed.genre,
          narrator = parsed.narrator,
          series = parsed.series,
          part = parsed.part,
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
    ).also {
      validateIntegrity(it, chapters)
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
