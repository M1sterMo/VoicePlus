package voice.core.data.store.snapshot

import androidx.datastore.core.DataStore
import androidx.room.RoomDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookContent
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.internals.dao.BookCharacterDao
import voice.core.data.repo.internals.dao.BookContentDao
import voice.core.data.repo.internals.dao.BookmarkDao
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.data.repo.internals.dao.ChapterNameOverrideDao
import voice.core.data.repo.internals.dao.LibraryOrganisationDao
import voice.core.data.repo.internals.dao.ListeningEventDao
import voice.core.data.repo.internals.dao.ListeningSessionDao
import voice.core.data.repo.internals.transaction
import voice.core.data.store.ExcludedBooksStore
import voice.core.logging.api.Logger
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

@SingleIn(AppScope::class)
@Inject
internal class BackupRestorer(
  private val ring: SnapshotRing,
  private val bookContentDao: BookContentDao,
  private val bookmarkDao: BookmarkDao,
  private val bookCharacterDao: BookCharacterDao,
  private val chapterDao: ChapterDao,
  private val chapterNameOverrideDao: ChapterNameOverrideDao,
  private val listeningSessionDao: ListeningSessionDao,
  private val listeningEventDao: ListeningEventDao,
  @ExcludedBooksStore private val excludedBooksStore: DataStore<Set<String>>,
  private val appDb: RoomDatabase,
  private val contentRepo: BookContentRepo,
  private val settingsSnapshotter: SettingsSnapshotter,
  private val restoreGate: RestoreGate,
  private val organisationDao: LibraryOrganisationDao,
) {

  suspend fun restoreIfNeeded() {
    try {
      val live = bookContentDao.all()
      // Only auto-restore when the database is genuinely empty (a real clear-data / reinstall). A library
      // with all books inactive — e.g. the user removed their folders — is intentionally NOT auto-restored;
      // resurrecting it would fight the removal. Explicit Restore covers recovery from a destructive bug.
      if (live.isNotEmpty()) return
      val candidate = RestoreSelector.select(live.size, ring.readAll())?.withTitleOverrideCompatibility() ?: return
      // The snapshot's hidden set filters the restore; the stores are only written once the
      // apply has succeeded, so a failed restore leaves settings and the hidden set untouched.
      val excluded = excludedBooksStore.data.first() + candidate.hiddenBooks
      val restored = apply(candidate, excluded, live)
      excludedBooksStore.updateData { it + candidate.hiddenBooks }
      settingsSnapshotter.apply(candidate.settings)
      contentRepo.invalidateCache()
      Logger.i("Restored $restored books from snapshot generation ${candidate.sequence}")
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      Logger.e(t, "Snapshot restore failed; library is unaffected")
    }
  }

  /**
   * Same-device restore of an external bundle whose book ids are all still present live — no scan,
   * no re-key. Additive and idempotent; returns the number of restored books. Owns the whole
   * commit: rows first, then the hidden set and settings — the same succeed-then-write ordering
   * as [restoreIfNeeded], stated once so the two paths cannot drift. [OsWipeRestorer] remains the
   * door for dead-URI bundles.
   */
  suspend fun applyDirect(
    snapshot: LibrarySnapshot,
    covers: Map<String, File> = emptyMap(),
  ): Int {
    val restored = restoreGate.withRestoreActive {
      val excludedIds = excludedBooksStore.data.first() + snapshot.hiddenBooks
      val live = bookContentDao.all()
      val written = apply(snapshot, excludedIds, live, preserveLiveCovers = true, covers = covers)
      excludedBooksStore.updateData { it + snapshot.hiddenBooks }
      settingsSnapshotter.apply(snapshot.settings)
      contentRepo.invalidateCache()
      deleteReplacedCovers(
        oldCovers = live.filter { it.id.value in covers }.map { it.cover },
        importedCovers = covers.values,
        referencedCovers = bookContentDao.all().map { it.cover },
      )
      Logger.i("Directly restored $written books from an external bundle (same-device ids)")
      written
    }
    restoreGate.requestFlush()
    return restored
  }

  /** True when active and hidden books can all be restored without translating their ids. */
  suspend fun canApplyDirect(snapshot: LibrarySnapshot): Boolean {
    val active = snapshot.activeIds()
    if (active.isEmpty()) return false
    val live = bookContentDao.all()
    val liveActiveIds = live.filter { it.isActive }.mapTo(mutableSetOf()) { it.id.value }
    val liveIds = live.mapTo(mutableSetOf()) { it.id.value }
    return liveActiveIds.containsAll(active) && liveIds.containsAll(snapshot.hiddenBooks)
  }

  private suspend fun apply(
    snapshot: LibrarySnapshot,
    excludedIds: Set<String>,
    live: List<BookContent>,
    preserveLiveCovers: Boolean = false,
    covers: Map<String, File> = emptyMap(),
  ): Int {
    val liveById = live.associateBy { it.id.value }
    fun included(id: String) = id !in excludedIds || id in snapshot.hiddenBooks
    val books = snapshot.books
      .filter { included(it.id) }
      .mapNotNull { dto ->
        dto.toBookContentOrNull()?.let { restored ->
          val withCover = if (preserveLiveCovers) restored.copy(cover = covers[dto.id] ?: liveById[dto.id]?.cover) else restored
          dto.id to withCover.copy(isActive = withCover.isActive && dto.id !in snapshot.hiddenBooks)
        }
      }
    val bookmarks = snapshot.bookmarks.filter { included(it.bookId) }.map { it.toBookmark() }
    val characters = snapshot.characters.filter { included(it.bookId) }.map { it.toBookCharacter() }
    val overrides = snapshot.chapterNameOverrides.filter { included(it.bookId) }.map { it.toOverride() }
    val sessions = snapshot.sessions.filter { included(it.bookId) }.map { it.toListeningSession() }
    val events = snapshot.events.filter { included(it.bookId) }.map { it.toListeningEvent() }
    // chapters2 carries no bookId, so restore them all (REPLACE). A chapter with no surviving content2 row is
    // simply invisible; re-inserting is what lets a restored book's BookRepository.book() resolve at all.
    val chapters = snapshot.chapters.map { it.toChapter() }
    var written = 0
    appDb.transaction {
      // Natural-key dedup for the autoGenerate-PK tables, seeded INSIDE the transaction: snapshot row
      // ids belong to a different database generation, so inserting by id could collide with (or
      // REPLACE) unrelated live rows. Insert with a fresh id instead, and skip rows already present.
      val seenSessionKeys = listeningSessionDao.all().mapTo(mutableSetOf()) { it.naturalKey() }
      val seenCharacterKeys = bookCharacterDao.all().mapTo(mutableSetOf()) { it.naturalKey() }
      val seenEventKeys = listeningEventDao.all().mapTo(mutableSetOf()) { it.naturalKey() }
      val existingChapterIds = if (chapters.isEmpty()) {
        emptySet()
      } else {
        chapterDao.chapters(chapters.map { it.id }).mapTo(mutableSetOf()) { it.id }
      }
      chapters.filter { it.id !in existingChapterIds }.forEach { chapterDao.insert(it) }
      live.filter { it.id.value in excludedIds && it.isActive }
        .forEach { bookContentDao.insert(it.copy(isActive = false)) }
      books.forEach { (id, snap) ->
        val liveRow = liveById[id]
        // Restore user metadata and preferences, but never roll back newer live playback progress.
        bookContentDao.insert(snap.preserveNewerPlaybackFrom(liveRow))
        written++
      }
      bookmarks.forEach { bookmarkDao.addBookmark(it) }
      characters.forEach { character ->
        if (seenCharacterKeys.add(character.naturalKey())) bookCharacterDao.insert(character.copy(id = 0))
      }
      overrides.forEach { chapterNameOverrideDao.insert(it) }
      sessions.forEach { session ->
        if (seenSessionKeys.add(session.naturalKey())) listeningSessionDao.insert(session.copy(id = 0))
      }
      events.forEach { event ->
        if (seenEventKeys.add(event.naturalKey())) listeningEventDao.insert(event.copy(id = 0))
      }
      organisationDao.restore(snapshot, bookContentDao.all().associate { it.id.value to it.id.value })
    }
    return written
  }
}
