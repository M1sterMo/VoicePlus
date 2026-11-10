package voice.core.data.store.snapshot.rekey

import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Bookmark
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.ListeningSession
import voice.core.data.store.snapshot.BookContentDto
import voice.core.data.store.snapshot.BookmarkDto
import voice.core.data.store.snapshot.ChapterNameOverrideDto
import voice.core.data.store.snapshot.ListeningSessionDto
import java.time.Instant

/**
 * SAF authority of the on-device ExternalStorageProvider. Only books served by this provider have a
 * location-stable `documentId` (`<volume>:<relPath>`) that survives an uninstall/re-grant, so it is the
 * only provider eligible for automatic OS-wipe re-keying. Everything else (cloud/opaque) is surfaced.
 */
internal const val EXTERNAL_STORAGE_AUTHORITY: String = "com.android.externalstorage.documents"

/**
 * Stable, re-grant-invariant fingerprint of a book folder. Captured at snapshot-write time (from the live
 * scanner tree) and re-derived at restore time (from the freshly-scanned tree). [relPath] is the
 * volume-namespaced document-id of the folder (the primary key); [children] both confirm the match and
 * anchor per-chapter data by filename.
 */
internal data class BookIdentityStamp(
  val authority: String,
  val isSingleFile: Boolean,
  val relPath: String,
  val folderName: String,
  // Folder-relative document-id tails (with extension), sorted. The relName multiset IS the
  // structural identity; file sizes were dropped as a signal that could never fire (always 0).
  val children: List<String>,
)

/** A snapshot chapter with the stable [relName] anchor and its snapshot-time duration. */
internal data class SnapChapter(
  val oldId: ChapterId,
  val relName: String,
  val duration: Long,
)

/** One decoded book from the external backup bundle, with its stamp + chapter anchors + user-authored data. */
internal data class SnapshotBook(
  val stamp: BookIdentityStamp,
  val content: BookContentDto,
  val chapters: List<SnapChapter>,
  val bookmarks: List<BookmarkDto>,
  val overrides: List<ChapterNameOverrideDto>,
  val sessions: List<ListeningSessionDto>,
)

/** A freshly-scanned chapter under its new (post-re-grant) [newId], with its fresh duration. */
internal data class ScannedChapter(
  val newId: ChapterId,
  val relName: String,
  val duration: Long,
)

/** A freshly-scanned book under its new [newBookId]; [chapters] are in scanner sort order. */
internal data class ScannedBook(
  val newBookId: BookId,
  val stamp: BookIdentityStamp,
  val chapters: List<ScannedChapter>,
)

/** Why a snapshot book could not be safely auto-attached. All are surfaced to the user, never silently dropped. */
internal enum class UnmatchedReason {
  OPAQUE_PROVIDER,
  SINGLE_FILE,
  NO_PATH_MATCH,
  AMBIGUOUS,
  CONTENT_CHANGED,
  INVALID,
}

internal data class UnmatchedBook(
  val relPath: String,
  val folderName: String,
  val reason: UnmatchedReason,
)

/**
 * A snapshot book successfully re-keyed onto a scanned book. Everything is keyed to the NEW ids; the writer
 * freshness-arbitrates [content] against any live row using [sourceLastPlayedAt] before persisting.
 */
internal data class MatchedBook(
  // The snapshot-side (pre-wipe) book id, so callers can translate id-keyed state — e.g. the
  // hidden-books set — onto the new id.
  val sourceId: String,
  val content: BookContent,
  val bookmarks: List<Bookmark>,
  val overrides: List<ChapterNameOverride>,
  val sessions: List<ListeningSession>,
  val sourceLastPlayedAt: Instant,
)

internal data class ReKeyResult(
  val matched: List<MatchedBook>,
  val unmatched: List<UnmatchedBook>,
)
