package voice.core.data.store.snapshot

import kotlinx.serialization.Serializable
import voice.core.data.BookCharacter
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Bookmark
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.LibraryOrganisation
import voice.core.data.ListeningEvent
import voice.core.data.ListeningSession
import voice.core.data.MarkData
import java.io.File
import java.time.Instant
import java.util.UUID

@Serializable
internal data class LibrarySnapshot(
  val schemaVersion: Int,
  val dbVersion: Int = 0,
  val sequence: Long,
  val savedAtEpochMillis: Long,
  val totalCount: Int,
  val activeCount: Int,
  val books: List<BookContentDto>,
  val bookmarks: List<BookmarkDto>,
  val characters: List<BookCharacterDto>,
  val chapterNameOverrides: List<ChapterNameOverrideDto>,
  val sessions: List<ListeningSessionDto> = emptyList(),
  // chapters2 rows for every book. Required for the OS-wipe restore: without re-inserted Chapter rows
  // BookRepository.book() returns null and the restored book is invisible. Default-empty so legacy bundles decode.
  val chapters: List<ChapterDto> = emptyList(),
  // Listening-log transport events, capped to the newest per book (matching the UI's own read cap).
  val events: List<ListeningEventDto> = emptyList(),
  // Book ids the user hid from the library. Restored books stay hidden instead of resurfacing.
  val hiddenBooks: Set<String> = emptySet(),
  // App settings worth carrying across a wipe, each JSON-encoded by SettingsSnapshotter.
  val settings: Map<String, String> = emptyMap(),
  val organisation: LibraryOrganisation? = null,
) {
  fun activeIds(): Set<String> = books.filter { it.isActive }.map { it.id }.toSet()

  companion object {
    // Bump whenever a restore-affecting field is added/changed. Import/restore accepts any bundle whose
    // schemaVersion is <= this (older bundles decode via default-valued fields); a bundle from a NEWER
    // schema is refused rather than silently truncated. v2 added: listening sessions, chapters2 rows,
    // chapter relNames, character notes on the OS-wipe path. v3 added: session endReason, listening
    // events, hidden books, settings — and dropped the stored per-book identity stamp
    // (it is derived from the URIs already in the bundle; old bundles' `identity` keys are ignored).
    // v4 adds manual series groups, separate from imported series tags.
    // v5 adds the user-chosen order within a manual series group.
    // v6 adds home shelves, stable series identity and independent manual/shelf ordering.
    // v7 preserves whether the user explicitly edited a book title.
    const val SCHEMA_VERSION = 7
  }
}

/** Pre-v7 backups cannot identify edited titles, so preserve every saved title rather than lose one. */
internal fun LibrarySnapshot.withTitleOverrideCompatibility(): LibrarySnapshot {
  return if (schemaVersion < 7) {
    copy(books = books.map { it.copy(nameOverridden = true) })
  } else {
    this
  }
}

@Serializable
internal data class BookContentDto(
  val id: String,
  val playbackSpeed: Float,
  val skipSilence: Boolean,
  val isActive: Boolean,
  val lastPlayedAtEpochMillis: Long,
  val author: String?,
  val name: String,
  val addedAtEpochMillis: Long,
  val chapters: List<String>,
  val currentChapter: String,
  val positionInChapter: Long,
  val coverPath: String?,
  val gain: Float = 0f,
  val genre: String?,
  val narrator: String?,
  val series: String?,
  val part: String?,
  val chapterNameOffset: Int = 0,
  val seriesGroup: String? = null,
  val seriesOrder: Int? = null,
  val nameOverridden: Boolean = false,
)

@Serializable
internal data class BookmarkDto(
  val bookId: String,
  val chapterId: String,
  val title: String?,
  val time: Long,
  val addedAtEpochMillis: Long,
  val setBySleepTimer: Boolean,
  val id: String,
)

@Serializable
internal data class BookCharacterDto(
  val id: Long,
  val bookId: String,
  val name: String,
  val description: String,
  val sortOrder: Int = 0,
  val createdAtEpochMillis: Long,
  val updatedAtEpochMillis: Long,
)

@Serializable
internal data class ChapterNameOverrideDto(
  val chapterId: String,
  val markStartMs: Long,
  val bookId: String,
  val name: String,
)

@Serializable
internal data class ListeningSessionDto(
  val id: Long,
  val bookId: String,
  val chapterId: String,
  val startedAtEpochMillis: Long,
  val endedAtEpochMillis: Long,
  val durationMs: Long,
  val startPositionMs: Long,
  val endPositionMs: Long,
  val endChapterId: String?,
  // v3: why the session ended (stable ListeningSessionEndReason id). Null on pre-v3 bundles.
  val endReason: Int? = null,
)

@Serializable
internal data class ListeningEventDto(
  val bookId: String,
  val type: Int,
  val chapterId: String,
  val positionMs: Long,
  val fromPositionMs: Long? = null,
  val atEpochMillis: Long,
)

@Serializable
internal data class ChapterDto(
  val id: String,
  val name: String?,
  val duration: Long,
  val fileLastModifiedEpochMillis: Long,
  val markData: List<MarkData>,
  // Folder-relative document-id tail (with extension), e.g. "Disc1/01 - Intro.mp3". The stable per-chapter
  // anchor for re-keying bookmarks/overrides/positions after an OS-wipe. Empty until the identity store fills it.
  val relName: String = "",
  val fileSize: Long = 0L,
)

internal fun BookContent.toDto() = BookContentDto(
  id = id.value,
  playbackSpeed = playbackSpeed,
  skipSilence = skipSilence,
  isActive = isActive,
  lastPlayedAtEpochMillis = lastPlayedAt.toEpochMilli(),
  author = author,
  name = name,
  addedAtEpochMillis = addedAt.toEpochMilli(),
  chapters = chapters.map { it.value },
  currentChapter = currentChapter.value,
  positionInChapter = positionInChapter,
  coverPath = cover?.absolutePath,
  gain = gain,
  genre = genre,
  narrator = narrator,
  series = series,
  part = part,
  chapterNameOffset = chapterNameOffset,
  seriesGroup = seriesGroup,
  seriesOrder = seriesOrder,
  nameOverridden = nameOverridden,
)

// Reconstruction runs BookContent.init{ require(...) }; wrap so one bad row is dropped, not fatal.
internal fun BookContentDto.toBookContentOrNull(): BookContent? = runCatching {
  BookContent(
    id = BookId(id),
    playbackSpeed = playbackSpeed,
    skipSilence = skipSilence,
    isActive = isActive,
    lastPlayedAt = Instant.ofEpochMilli(lastPlayedAtEpochMillis),
    author = author,
    name = name,
    addedAt = Instant.ofEpochMilli(addedAtEpochMillis),
    chapters = chapters.map { ChapterId(it) },
    currentChapter = ChapterId(currentChapter),
    positionInChapter = positionInChapter,
    cover = coverPath?.let { File(it) },
    gain = gain,
    genre = genre,
    narrator = narrator,
    series = series,
    part = part,
    chapterNameOffset = chapterNameOffset,
    seriesGroup = seriesGroup,
    seriesOrder = seriesOrder,
    nameOverridden = nameOverridden,
  )
}.getOrNull()

internal fun Bookmark.toDto() = BookmarkDto(
  bookId = bookId.value,
  chapterId = chapterId.value,
  title = title,
  time = time,
  addedAtEpochMillis = addedAt.toEpochMilli(),
  setBySleepTimer = setBySleepTimer,
  id = id.value.toString(),
)

internal fun BookmarkDto.toBookmark() = Bookmark(
  bookId = BookId(bookId),
  chapterId = ChapterId(chapterId),
  title = title,
  time = time,
  addedAt = Instant.ofEpochMilli(addedAtEpochMillis),
  setBySleepTimer = setBySleepTimer,
  id = Bookmark.Id(UUID.fromString(id)),
)

internal fun BookCharacter.toDto() = BookCharacterDto(
  id = id,
  bookId = bookId.value,
  name = name,
  description = description,
  sortOrder = sortOrder,
  createdAtEpochMillis = createdAt.toEpochMilli(),
  updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun BookCharacterDto.toBookCharacter() = BookCharacter(
  id = id,
  bookId = BookId(bookId),
  name = name,
  description = description,
  sortOrder = sortOrder,
  createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
  updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun ChapterNameOverride.toDto() = ChapterNameOverrideDto(chapterId, markStartMs, bookId, name)

internal fun ChapterNameOverrideDto.toOverride() = ChapterNameOverride(chapterId, markStartMs, bookId, name)

internal fun ListeningSession.toDto() = ListeningSessionDto(
  id = id,
  bookId = bookId.value,
  chapterId = chapterId.value,
  startedAtEpochMillis = startedAt.toEpochMilli(),
  endedAtEpochMillis = endedAt.toEpochMilli(),
  durationMs = durationMs,
  startPositionMs = startPositionMs,
  endPositionMs = endPositionMs,
  endChapterId = endChapterId?.value,
  endReason = endReason,
)

internal fun ListeningSessionDto.toListeningSession() = ListeningSession(
  id = id,
  bookId = BookId(bookId),
  chapterId = ChapterId(chapterId),
  startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
  endedAt = Instant.ofEpochMilli(endedAtEpochMillis),
  durationMs = durationMs,
  startPositionMs = startPositionMs,
  endPositionMs = endPositionMs,
  endChapterId = endChapterId?.let { ChapterId(it) },
  endReason = endReason,
)

internal fun ListeningEvent.toDto() = ListeningEventDto(
  bookId = bookId.value,
  type = type,
  chapterId = chapterId.value,
  positionMs = positionMs,
  fromPositionMs = fromPositionMs,
  atEpochMillis = at.toEpochMilli(),
)

internal fun ListeningEventDto.toListeningEvent() = ListeningEvent(
  bookId = BookId(bookId),
  type = type,
  chapterId = ChapterId(chapterId),
  positionMs = positionMs,
  fromPositionMs = fromPositionMs,
  at = Instant.ofEpochMilli(atEpochMillis),
)

internal fun Chapter.toDto(relName: String = "") = ChapterDto(
  id = id.value,
  name = name,
  duration = duration,
  fileLastModifiedEpochMillis = fileLastModified.toEpochMilli(),
  markData = markData,
  relName = relName,
  fileSize = fileSize,
)

internal fun ChapterDto.toChapter() = Chapter(
  id = ChapterId(id),
  name = name,
  duration = duration,
  fileLastModified = Instant.ofEpochMilli(fileLastModifiedEpochMillis),
  markData = markData,
  fileSize = fileSize,
)
