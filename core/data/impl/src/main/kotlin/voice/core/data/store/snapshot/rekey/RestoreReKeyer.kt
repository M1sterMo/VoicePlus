package voice.core.data.store.snapshot.rekey

import voice.core.data.BookCharacter
import voice.core.data.BookContent
import voice.core.data.Bookmark
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.ListeningSession
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

/**
 * Pure, deterministic re-keyer for OS-wipe restore. Maps snapshot books (keyed to dead pre-wipe URIs) onto
 * freshly-scanned books (keyed to new post-re-grant URIs) by volume-namespaced relative path, gated by a
 * strict content confirmer so book A's progress/bookmarks/sessions can never land on book B. No Android, no
 * IO — fully JVM-testable. See docs/superpowers/specs/2026-06-03-oswipe-rekey-design.md.
 */
internal object RestoreReKeyer {

  fun reKey(
    snapshot: List<SnapshotBook>,
    scanned: List<ScannedBook>,
  ): ReKeyResult {
    val matched = mutableListOf<MatchedBook>()
    val unmatched = mutableListOf<UnmatchedBook>()

    // STEP 1 — index scanned by volume-namespaced relPath. Same sub-path on two volumes never collides
    // because the volume prefix (primary: / <uuid>:) is part of the key.
    val scannedByRelPath: Map<String, List<ScannedBook>> = scanned.groupBy { it.stamp.relPath }

    // STEP 0 — eligibility gate. Only location-stable ExternalStorageProvider folders can be auto-re-keyed.
    // Single-file books are eligible here because this restore path uses the export bundle, not sidecars.
    val eligible = mutableListOf<SnapshotBook>()
    for (snap in snapshot) {
      val gate = when {
        snap.stamp.authority != EXTERNAL_STORAGE_AUTHORITY -> UnmatchedReason.OPAQUE_PROVIDER
        else -> null
      }
      if (gate != null) unmatched += snap.unmatched(gate) else eligible += snap
    }

    // Degenerate-snapshot guard: if two snapshot books claim the same folder we cannot tell them apart, so
    // surface both rather than risk a double-attach. Iterate sorted keys for deterministic output ordering.
    val eligibleByRelPath = eligible.groupBy { it.stamp.relPath }
    for (relPath in eligibleByRelPath.keys.sorted()) {
      val snaps = eligibleByRelPath.getValue(relPath)
      if (snaps.size > 1) {
        snaps.forEach { unmatched += it.unmatched(UnmatchedReason.AMBIGUOUS) }
        continue
      }
      val snap = snaps.single()

      // STEP 2 — candidate lookup by exact relPath. Never fall back to name-only fuzzy matching.
      val candidates = scannedByRelPath[relPath].orEmpty()
      if (candidates.isEmpty()) {
        unmatched += snap.unmatched(UnmatchedReason.NO_PATH_MATCH)
        continue
      }

      // STEP 3 — strict confirmer. Attach iff EXACTLY ONE candidate passes.
      val passing = candidates.filter { confirm(snap, it) }
      when (passing.size) {
        1 -> {
          val built = buildMatchedBook(snap, passing.single())
          if (built != null) matched += built else unmatched += snap.unmatched(UnmatchedReason.INVALID)
        }
        0 -> unmatched += snap.unmatched(
          if (candidates.size == 1) UnmatchedReason.CONTENT_CHANGED else UnmatchedReason.AMBIGUOUS,
        )
        else -> unmatched += snap.unmatched(UnmatchedReason.AMBIGUOUS)
      }
    }

    return ReKeyResult(matched = matched, unmatched = unmatched)
  }

  private fun SnapshotBook.unmatched(reason: UnmatchedReason) =
    UnmatchedBook(relPath = stamp.relPath, folderName = stamp.folderName, reason = reason)

  /**
   * The never-cross-attach gate. HARD: identical child count + identical relName multiset. Then one
   * contradiction-fatal soft signal: chapter duration — snapshot vs freshly-scanned duration per relName.
   * This is the live guard against an in-place re-rip / edition swap that kept the same filenames. Minor
   * re-encode jitter is tolerated; a material change fails the match — we refuse to graft a stale position
   * onto different audio.
   */
  private fun confirm(
    snap: SnapshotBook,
    cand: ScannedBook,
  ): Boolean {
    val snapChildren = snap.stamp.children
    val candChildren = cand.stamp.children
    if (snapChildren.size != candChildren.size) return false
    if (snapChildren.sorted() != candChildren.sorted()) return false
    val candDurationByRelName = cand.chapters.associate { it.relName to it.duration }
    for (chapter in snap.chapters) {
      val candDuration = candDurationByRelName[chapter.relName] ?: continue
      if (chapter.duration > 0 && candDuration > 0 && !durationsAgree(chapter.duration, candDuration)) return false
    }
    return true
  }

  /** Tolerate minor re-encode jitter (a few seconds), but reject an edition/content swap. */
  private fun durationsAgree(
    snapMs: Long,
    scannedMs: Long,
  ): Boolean {
    val tolerance = maxOf(DURATION_TOLERANCE_MS, maxOf(snapMs, scannedMs) / 100) // 3s floor, else 1%
    return abs(snapMs - scannedMs) <= tolerance
  }

  /**
   * Build a fully NEW-id-keyed book. chapters = scanned ids verbatim (scanner order, satisfies Book.init's
   * exact-order check). Chapter-scoped data re-points by relName (never index); the unplaceable is dropped,
   * never grafted onto a neighbour. Returns null (→ INVALID) on a structurally impossible candidate.
   */
  private fun buildMatchedBook(
    snap: SnapshotBook,
    cand: ScannedBook,
  ): MatchedBook? {
    if (cand.chapters.isEmpty()) return null // a "book" with no audio files cannot satisfy Book.init.

    val oldIdToRelName: Map<String, String> = snap.chapters.associate { it.oldId.value to it.relName }
    val anchorByRelName: Map<String, ChapterId> = cand.chapters.associate { it.relName to it.newId }
    val durationByRelName: Map<String, Long> = cand.chapters.associate { it.relName to it.duration }
    val newBookId = cand.newBookId

    // Re-key one old ChapterId string → (newId, fresh duration). Null if either hop misses (file gone / not stamped).
    fun reKeyChapter(oldChapterId: String): Pair<ChapterId, Long>? {
      val rel = oldIdToRelName[oldChapterId] ?: return null
      val newId = anchorByRelName[rel] ?: return null
      return newId to durationByRelName.getValue(rel)
    }

    val newChapterIds = cand.chapters.map { it.newId }

    // currentChapter: re-key by relName, else fall back to first()+pos0 (mirrors MediaScanner currentChapterGone).
    val reKeyedCurrent = reKeyChapter(snap.content.currentChapter)
    val currentChapter: ChapterId
    val positionInChapter: Long
    if (reKeyedCurrent != null) {
      currentChapter = reKeyedCurrent.first
      positionInChapter = clamp(snap.content.positionInChapter, reKeyedCurrent.second)
    } else {
      currentChapter = cand.chapters.first().newId
      positionInChapter = 0L
    }

    val content = runCatching {
      BookContent(
        id = newBookId,
        playbackSpeed = snap.content.playbackSpeed,
        skipSilence = snap.content.skipSilence,
        isActive = true,
        lastPlayedAt = Instant.ofEpochMilli(snap.content.lastPlayedAtEpochMillis),
        author = snap.content.author,
        name = snap.content.name,
        addedAt = Instant.ofEpochMilli(snap.content.addedAtEpochMillis),
        chapters = newChapterIds,
        currentChapter = currentChapter,
        positionInChapter = positionInChapter,
        cover = null, // the app-private cover path is dead after a wipe; the scanner re-extracts.
        gain = snap.content.gain,
        genre = snap.content.genre,
        narrator = snap.content.narrator,
        series = snap.content.series,
        part = snap.content.part,
        chapterNameOffset = snap.content.chapterNameOffset,
        seriesGroup = snap.content.seriesGroup,
        seriesOrder = snap.content.seriesOrder,
        nameOverridden = snap.content.nameOverridden,
      )
    }.getOrNull() ?: return null

    val bookmarks = snap.bookmarks.mapNotNull { dto ->
      val re = reKeyChapter(dto.chapterId) ?: return@mapNotNull null
      Bookmark(
        bookId = newBookId,
        chapterId = re.first,
        title = dto.title,
        time = clamp(dto.time, re.second),
        addedAt = Instant.ofEpochMilli(dto.addedAtEpochMillis),
        setBySleepTimer = dto.setBySleepTimer,
        id = Bookmark.Id(UUID.fromString(dto.id)),
      )
    }

    val overrides = snap.overrides.mapNotNull { dto ->
      val re = reKeyChapter(dto.chapterId) ?: return@mapNotNull null
      ChapterNameOverride(
        chapterId = re.first.value,
        markStartMs = dto.markStartMs,
        bookId = newBookId.value,
        name = dto.name,
      )
    }

    val sessions = snap.sessions.mapNotNull { dto ->
      val start = reKeyChapter(dto.chapterId) ?: return@mapNotNull null
      val end = dto.endChapterId?.let { reKeyChapter(it) }
      val endClampDuration = end?.second ?: start.second
      ListeningSession(
        id = 0, // let Room assign a fresh PK; the writer dedups on a natural key for idempotency.
        bookId = newBookId,
        chapterId = start.first,
        startedAt = Instant.ofEpochMilli(dto.startedAtEpochMillis),
        endedAt = Instant.ofEpochMilli(dto.endedAtEpochMillis),
        durationMs = dto.durationMs,
        startPositionMs = clamp(dto.startPositionMs, start.second),
        endPositionMs = clamp(dto.endPositionMs, endClampDuration),
        endChapterId = end?.first,
        endReason = dto.endReason,
      )
    }

    // Characters are book-scoped (no chapter anchor): rewrite bookId, fresh PK, the writer dedups.
    val characters = snap.characters.map { dto ->
      BookCharacter(
        id = 0,
        bookId = newBookId,
        name = dto.name,
        description = dto.description,
        sortOrder = dto.sortOrder,
        createdAt = Instant.ofEpochMilli(dto.createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(dto.updatedAtEpochMillis),
      )
    }

    return MatchedBook(
      sourceId = snap.content.id,
      content = content,
      bookmarks = bookmarks,
      overrides = overrides,
      sessions = sessions,
      characters = characters,
      sourceLastPlayedAt = Instant.ofEpochMilli(snap.content.lastPlayedAtEpochMillis),
    )
  }

  /**
   * Coerce a millisecond offset into a now-fresh chapter. When the fresh duration is known (> 0) we clamp to
   * it so a stale position can never point past a now-shorter re-ripped file; when it is unknown (0) we only
   * floor at 0 rather than zero out an otherwise-valid position.
   */
  private fun clamp(
    positionMs: Long,
    freshDurationMs: Long,
  ): Long = if (freshDurationMs > 0) positionMs.coerceIn(0L, freshDurationMs) else positionMs.coerceAtLeast(0L)

  private const val DURATION_TOLERANCE_MS = 3_000L
}
