package voice.core.data.store.snapshot

import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import voice.core.data.BookContent
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.dao.BookCharacterDao
import voice.core.data.repo.internals.dao.BookmarkDao
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.data.repo.internals.dao.ChapterNameOverrideDao
import voice.core.data.repo.internals.dao.ListeningEventDao
import voice.core.data.repo.internals.dao.ListeningSessionDao
import voice.core.data.store.ExcludedBooksStore
import voice.core.data.store.snapshot.identity.DeviceRelativePath
import voice.core.logging.api.Logger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SingleIn(AppScope::class)
@Inject
internal class SnapshotWriter(
  private val contentRepo: BookContentRepo,
  private val bookmarkDao: BookmarkDao,
  private val bookCharacterDao: BookCharacterDao,
  private val chapterDao: ChapterDao,
  private val chapterNameOverrideDao: ChapterNameOverrideDao,
  private val listeningSessionDao: ListeningSessionDao,
  private val listeningEventDao: ListeningEventDao,
  private val ring: SnapshotRing,
  @ExcludedBooksStore private val excludedBooksStore: DataStore<Set<String>>,
  private val settingsSnapshotter: SettingsSnapshotter,
  private val backupRepository: BackupRepository,
  private val restoreGate: RestoreGate,
  private val clock: Clock,
  private val appDb: AppDb,
) {

  private val dirty = AtomicBoolean(false)
  private val flushMutex = Mutex()

  fun start(scope: CoroutineScope) {
    // Re-snapshot on any change the snapshot captures: books, user-authored data (bookmarks,
    // character notes, chapter-name overrides, listening sessions/events), hidden books, and
    // settings. This keeps the on-device ring AND the external bundle current, so a user deletion
    // is reflected promptly and never silently resurrected by a later restore reading a stale
    // bundle — and so the flushNow() before a manual "Back up now" actually has dirt to drain
    // (a source missing here means that change silently never reaches any backup).
    merge(
      contentRepo.flow().map { },
      bookmarkDao.count().map { },
      bookCharacterDao.count().map { },
      chapterNameOverrideDao.count().map { },
      listeningSessionDao.count().map { },
      listeningEventDao.count().map { },
      excludedBooksStore.data.drop(1).map { },
      settingsSnapshotter.changes(),
      appDb.libraryOrganisationDao().changes().map { },
    )
      .onEach { dirty.set(true) }
      .debounce(DEBOUNCE)
      .onEach { flushIfDirty() }
      .launchIn(scope)

    scope.launchPeriodicFlush()

    // A fully-successful restore asks for an immediate flush (bypassing the debounce) so the re-keyed state
    // reaches the ring + external bundle deterministically, even under steady playback or an early process kill.
    restoreGate.flushRequests
      .onEach {
        dirty.set(true)
        flushIfDirty(forceExternalBackup = true)
      }
      .launchIn(scope)
  }

  private fun CoroutineScope.launchPeriodicFlush() {
    launch {
      // Catch up on app start: if a UTC day boundary passed since the last export (or none was
      // ever due), write today's automatic save without waiting for a library change.
      exportExternalBackup(force = false)
      while (isActive) {
        delay(PERIODIC_FLUSH)
        flushIfDirty()
        exportExternalBackup(force = false)
      }
    }
  }

  /** Snapshot the current library immediately, even if a source emission has not marked it dirty yet. */
  suspend fun flushNow() {
    flushMutex.withLock {
      dirty.set(false)
      writeSnapshot(contentRepo.all())
    }
  }

  private suspend fun flushIfDirty(forceExternalBackup: Boolean = false) {
    if (!dirty.getAndSet(false)) return
    flushMutex.withLock {
      writeSnapshot(contentRepo.all(), forceExternalBackup = forceExternalBackup)
    }
  }

  internal suspend fun writeSnapshot(
    books: List<BookContent>,
    forceExternalBackup: Boolean = false,
  ) {
    withContext(Dispatchers.IO) {
      try {
        // An OS-wipe restore is mid-flight: the live library is the freshly-scanned, not-yet-re-keyed set
        // (active books, no user data). Writing it now would clobber the ring/bundle we are restoring from.
        if (restoreGate.active.value) return@withContext
        val excludedIds = excludedBooksStore.data.first()
        val allChapters = chapterDao.all()
        // Identity stamps for re-keying are DERIVED from the stored URIs on restore
        // (OsWipeRestorer.reconstructStamp), so the bundle doesn't carry them.
        val bookDtos = books.map { it.toDto() }
        val bookRelPathByChapterId: Map<String, String> = buildMap {
          books.forEach { book ->
            val relPath = DeviceRelativePath.documentId(book.id.value.toUri())
            book.chapters.forEach { put(it.value, relPath) }
          }
        }
        val chapterDtos = allChapters.map { chapter ->
          val relName = bookRelPathByChapterId[chapter.id.value]
            ?.let { DeviceRelativePath.relName(chapter.id.value.toUri(), it) }
            .orEmpty()
          chapter.toDto(relName = relName)
        }
        // Newest events per book, mirroring the UI's own 500-row read cap so bundle size stays bounded.
        val events = listeningEventDao.all()
          .groupBy { it.bookId }
          .values
          .flatMap { it.take(EVENTS_PER_BOOK) }
        val snapshot = LibrarySnapshot(
          schemaVersion = LibrarySnapshot.SCHEMA_VERSION,
          dbVersion = AppDb.VERSION,
          sequence = 0L, // assigned by the ring
          savedAtEpochMillis = clock.millis(),
          totalCount = books.size,
          activeCount = books.count { it.isActive },
          books = bookDtos,
          bookmarks = bookmarkDao.all().map { it.toDto() },
          characters = bookCharacterDao.all().map { it.toDto() },
          chapterNameOverrides = chapterNameOverrideDao.all().map { it.toDto() },
          sessions = listeningSessionDao.all().map { it.toDto() },
          chapters = chapterDtos,
          events = events.map { it.toDto() },
          hiddenBooks = excludedIds,
          settings = settingsSnapshotter.capture(),
          organisation = appDb.libraryOrganisationDao().get(),
        )
        if (RotationGuard.isSuspiciousShrink(ring.best(), snapshot, excludedIds)) {
          Logger.w(
            "Snapshot rotation declined: suspicious active shrink " +
              "(incoming=${snapshot.activeCount}, totalKnown=${snapshot.totalCount})",
          )
          return@withContext
        }
        ring.writeNext(snapshot)
        exportExternalBackup(force = forceExternalBackup)
      } catch (e: CancellationException) {
        throw e
      } catch (t: Throwable) {
        Logger.w(t, "Snapshot write failed; library is unaffected")
      }
    }
  }

  // At most one automatic save per UTC day: the snapshot contains the playback position, so any
  // change-driven cadence would mint a file per minute of listening and the 7-file retention
  // would span minutes instead of a week. The on-device ring covers fine-grained recovery;
  // the daily file is the off-device disaster copy. Manual saves are not gated.
  private suspend fun exportExternalBackup(force: Boolean) {
    if (!force && !dueDaily()) return
    backupRepository.exportAfterSnapshot()
  }

  private suspend fun dueDaily(): Boolean {
    val last = backupRepository.lastBackupAt.first() ?: return true
    val lastDay = last.atZone(ZoneOffset.UTC).toLocalDate()
    val today = Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate()
    return lastDay != today
  }

  companion object {
    private val DEBOUNCE = 3.seconds
    private val PERIODIC_FLUSH = 5.minutes
    private const val EVENTS_PER_BOOK = 500
  }
}
