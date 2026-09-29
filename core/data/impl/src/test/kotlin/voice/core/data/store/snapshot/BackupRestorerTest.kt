package voice.core.data.store.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ListeningSession
import voice.core.data.MarkData
import voice.core.data.repo.BookContentRepoImpl
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.MemoryDataStore
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BackupRestorerTest {

  private lateinit var db: AppDb
  private lateinit var contentRepo: BookContentRepoImpl
  private val slot0 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot1 = MemoryDataStore<LibrarySnapshot?>(null)
  private val slot2 = MemoryDataStore<LibrarySnapshot?>(null)
  private val excluded = MemoryDataStore<Set<String>>(emptySet())
  private val ring = SnapshotRing(listOf(slot0, slot1, slot2))

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
    contentRepo = BookContentRepoImpl(db.bookContentDao())
  }

  @After
  fun teardown() = db.close()

  private fun restorer() = BackupRestorer(
    organisationDao = db.libraryOrganisationDao(),
    ring = ring,
    bookContentDao = db.bookContentDao(),
    bookmarkDao = db.bookmarkDao(),
    bookCharacterDao = db.bookCharacterDao(),
    chapterDao = db.chapterDao(),
    chapterNameOverrideDao = db.chapterNameOverrideDao(),
    listeningSessionDao = db.listeningSessionDao(),
    listeningEventDao = db.listeningEventDao(),
    excludedBooksStore = excluded,
    appDb = db,
    contentRepo = contentRepo,
    settingsSnapshotter = testSettingsSnapshotter(),
    restoreGate = RestoreGate(),
  )

  private fun book(
    id: String,
    active: Boolean,
  ) = BookContent(
    id = BookId(id), playbackSpeed = 1f, skipSilence = false, isActive = active,
    lastPlayedAt = Instant.EPOCH, author = null, name = id, addedAt = Instant.EPOCH,
    chapters = listOf(ChapterId("c$id")), currentChapter = ChapterId("c$id"), positionInChapter = 0,
    cover = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
  )

  private fun snapshotOf(vararg ids: String) = LibrarySnapshot(
    schemaVersion = 1, dbVersion = AppDb.VERSION, sequence = 1, savedAtEpochMillis = 0,
    totalCount = ids.size, activeCount = ids.size,
    books = ids.map { book(it, true).toDto() },
    bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
  )

  @Test
  fun `restores content2 when room is empty`() = runTest {
    slot0.updateData { snapshotOf("a", "b") }
    restorer().restoreIfNeeded()
    db.bookContentDao().all().map { it.id.value } shouldContainExactlyInAnyOrder listOf("a", "b")
  }

  @Test
  fun `legacy ring restore protects its saved title from a later metadata scan`() = runTest {
    val legacy = snapshotOf("a").copy(
      schemaVersion = 6,
      books = listOf(book("a", true).copy(name = "My saved title").toDto()),
    )
    slot0.updateData { legacy }

    restorer().restoreIfNeeded()

    db.bookContentDao().all().single().let {
      it.name shouldBe "My saved title"
      it.nameOverridden shouldBe true
    }
  }

  @Test
  fun `does not resurrect an excluded book`() = runTest {
    slot0.updateData { snapshotOf("a", "b") }
    excluded.updateData { setOf("b") }
    restorer().restoreIfNeeded()
    db.bookContentDao().all().map { it.id.value } shouldContainExactlyInAnyOrder listOf("a")
  }

  @Test
  fun `healthy library is left untouched`() = runTest {
    contentRepo.put(book("live", active = true))
    slot0.updateData { snapshotOf("a", "b") }
    restorer().restoreIfNeeded()
    db.bookContentDao().all().map { it.id.value } shouldContainExactlyInAnyOrder listOf("live")
  }

  @Test
  fun `all-inactive non-empty library is not resurrected so an intentional removal sticks`() = runTest {
    // The user removed the folder, leaving the book inactive in a non-empty DB. Auto-restore must NOT bring
    // it back — that was the bug where removed books reappeared on the next launch.
    contentRepo.put(book("a", active = false).copy(positionInChapter = 5000))
    slot0.updateData { snapshotOf("a") }

    restorer().restoreIfNeeded()

    val row = db.bookContentDao().all().single { it.id.value == "a" }
    row.isActive shouldBe false
    row.positionInChapter shouldBe 5000L
  }

  @Test
  fun `restores chapters2 so a restored book is resolvable`() = runTest {
    slot0.updateData {
      LibrarySnapshot(
        schemaVersion = 1, dbVersion = AppDb.VERSION, sequence = 1, savedAtEpochMillis = 0,
        totalCount = 1, activeCount = 1,
        books = listOf(book("a", active = true).toDto()),
        bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
        chapters = listOf(Chapter(ChapterId("ca"), "Ch A", 1_000, Instant.EPOCH, emptyList()).toDto()),
      )
    }
    restorer().restoreIfNeeded()
    db.chapterDao().all().map { it.id.value } shouldContainExactlyInAnyOrder listOf("ca")
  }

  @Test
  fun `restores listening sessions when room is empty`() = runTest {
    slot0.updateData {
      LibrarySnapshot(
        schemaVersion = 1, dbVersion = AppDb.VERSION, sequence = 1, savedAtEpochMillis = 0,
        totalCount = 1, activeCount = 1,
        books = listOf(book("a", active = true).toDto()),
        bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(),
        sessions = listOf(
          ListeningSession(
            id = 7,
            bookId = BookId("a"),
            chapterId = ChapterId("ca"),
            startedAt = Instant.ofEpochMilli(100),
            endedAt = Instant.ofEpochMilli(200),
            durationMs = 100,
            startPositionMs = 0,
            endPositionMs = 100,
          ).toDto(),
        ),
      )
    }
    restorer().restoreIfNeeded()
    // Restored with a FRESH id — snapshot ids belong to a different database generation.
    val restored = db.listeningSessionDao().all().single()
    restored.startedAt shouldBe Instant.ofEpochMilli(100)
    restored.bookId.value shouldBe "a"
  }

  @Test
  fun `released db65 backup restores progress and listening statistics into room`() = runTest {
    val decoded = ExternalBackupBundleCodec.decode(
      snapshotTestJson,
      backupFixture("db65-envelope-without-chapter-file-size.json"),
    ) as ExternalBackupBundleDecodeResult.Valid
    contentRepo.put(
      decoded.snapshot.books.single().toBookContentOrNull()!!.copy(
        positionInChapter = 0,
        lastPlayedAt = Instant.EPOCH,
      ),
    )

    restorer().applyDirect(decoded.snapshot) shouldBe 1

    db.bookContentDao().all().single().positionInChapter shouldBe 42_000
    db.bookmarkDao().all().single().time shouldBe 12_000
    db.bookCharacterDao().all().single().name shouldBe "Test Character"
    db.chapterDao().all().single().fileSize shouldBe 0
    db.listeningSessionDao().all().single().durationMs shouldBe 60_000
    db.listeningEventDao().all().single().positionMs shouldBe 42_000
  }

  @Test
  fun `applyDirect twice never duplicates sessions, characters or events`() = runTest {
    contentRepo.put(book("a", active = true))
    val snapshot = snapshotOf("a").copy(
      sessions = listOf(
        ListeningSession(
          id = 7, bookId = BookId("a"), chapterId = ChapterId("ca"),
          startedAt = Instant.ofEpochMilli(100), endedAt = Instant.ofEpochMilli(200),
          durationMs = 100, startPositionMs = 0, endPositionMs = 100, endReason = 1,
        ).toDto(),
      ),
      characters = listOf(
        BookCharacterDto(
          id = 3,
          bookId = "a",
          name = "Paul",
          description = "Atreides",
          createdAtEpochMillis = 5,
          updatedAtEpochMillis = 5,
        ),
      ),
      events = listOf(
        ListeningEventDto(bookId = "a", type = 0, chapterId = "ca", positionMs = 50, atEpochMillis = 150),
      ),
    )

    val r = restorer()
    r.applyDirect(snapshot) shouldBe 1
    r.applyDirect(snapshot) shouldBe 1

    db.listeningSessionDao().all().size shouldBe 1
    db.listeningSessionDao().all().single().endReason shouldBe 1
    db.bookCharacterDao().all().size shouldBe 1
    db.listeningEventDao().all().size shouldBe 1
  }

  @Test
  fun `canApplyDirect is true only when every active snapshot book exists live`() = runTest {
    contentRepo.put(book("a", active = true))
    restorer().canApplyDirect(snapshotOf("a")) shouldBe true
    restorer().canApplyDirect(snapshotOf("a", "b")) shouldBe false
    restorer().canApplyDirect(snapshotOf()) shouldBe false
  }

  @Test
  fun `canApplyDirect rejects an inactive live book with the same id`() = runTest {
    contentRepo.put(book("a", active = false))

    restorer().canApplyDirect(snapshotOf("a")) shouldBe false
  }

  @Test
  fun `canApplyDirect rejects a hidden book whose old id needs re-keying`() = runTest {
    contentRepo.put(book("a", active = true))
    val snapshot = snapshotOf("a", "old-hidden").copy(
      books = listOf(
        book("a", active = true).toDto(),
        book("old-hidden", active = false).toDto(),
      ),
      hiddenBooks = setOf("old-hidden"),
    )

    restorer().canApplyDirect(snapshot) shouldBe false
  }

  @Test
  fun `auto-restore brings back the hidden set and keeps hidden books out`() = runTest {
    slot0.updateData { snapshotOf("a", "b").copy(hiddenBooks = setOf("b")) }
    restorer().restoreIfNeeded()
    excluded.data.first() shouldBe setOf("b")
    db.bookContentDao().all().single { it.id.value == "a" }.isActive shouldBe true
    db.bookContentDao().all().single { it.id.value == "b" }.isActive shouldBe false
  }

  @Test
  fun `direct restore keeps hidden edited metadata but not library visibility`() = runTest {
    contentRepo.put(book("a", active = true).copy(name = "Live title"))
    val image = File("restored-hidden.png").absoluteFile
    val snapshot = snapshotOf("a").copy(
      books = listOf(book("a", active = false).copy(name = "Edited hidden title").toDto()),
      hiddenBooks = setOf("a"),
    )

    restorer().applyDirect(snapshot, mapOf("a" to image)) shouldBe 1

    db.bookContentDao().all().single().let {
      it.name shouldBe "Edited hidden title"
      it.cover shouldBe image
      it.isActive shouldBe false
    }
    excluded.data.first() shouldBe setOf("a")
  }

  @Test
  fun `applyDirect restores metadata while preserving newer playback`() = runTest {
    contentRepo.put(
      book("a", active = true).copy(
        name = "Live title",
        positionInChapter = 42,
        lastPlayedAt = Instant.ofEpochMilli(9_999),
      ),
    )
    val snapshot = snapshotOf("a").copy(books = listOf(snapshotOf("a").books.single().copy(name = "Backed-up title")))

    restorer().applyDirect(snapshot) shouldBe 1
    db.bookContentDao().all().single().let {
      it.name shouldBe "Backed-up title"
      it.positionInChapter shouldBe 42
      it.lastPlayedAt shouldBe Instant.ofEpochMilli(9_999)
    }
  }

  @Test
  fun `applyDirect preserves the live cover instead of restoring a non-portable path`() = runTest {
    val liveCover = File("/data/user/0/debug/files/covers/live.png")
    contentRepo.put(book("a", active = true).copy(cover = liveCover))
    val releaseCover = File("/data/user/0/release/files/covers/release.png")
    val snapshot = snapshotOf("a").copy(
      books = listOf(book("a", active = true).copy(cover = releaseCover).toDto()),
    )

    restorer().applyDirect(snapshot) shouldBe 1

    db.bookContentDao().all().single().cover shouldBe liveCover
  }

  @Test
  fun `portable cover and edited title replace scanned metadata on restore`() = runTest {
    contentRepo.put(book("a", active = true).copy(name = "Original title", cover = File("scanned.png")))
    val image = File("restored.png").absoluteFile
    val snapshot = snapshotOf("a").let { it.copy(books = listOf(it.books.single().copy(name = "Edited title"))) }
    restorer().applyDirect(snapshot, mapOf("a" to image)) shouldBe 1
    db.bookContentDao().all().single().name shouldBe "Edited title"
    db.bookContentDao().all().single().cover shouldBe image
  }

  @Test
  fun `portable metadata wins while newer live playback survives`() = runTest {
    val liveCover = File("live.png").absoluteFile
    contentRepo.put(
      book("a", active = true).copy(
        name = "Live title",
        cover = liveCover,
        positionInChapter = 33,
        lastPlayedAt = Instant.ofEpochMilli(9_999),
      ),
    )
    val image = File("restored.png").absoluteFile
    val snapshot = snapshotOf("a").copy(books = listOf(snapshotOf("a").books.single().copy(name = "Restored title")))
    restorer().applyDirect(snapshot, mapOf("a" to image)) shouldBe 1
    db.bookContentDao().all().single().let {
      it.name shouldBe "Restored title"
      it.cover shouldBe image
      it.positionInChapter shouldBe 33
      it.lastPlayedAt shouldBe Instant.ofEpochMilli(9_999)
    }
  }

  @Test
  fun `portable metadata does not replace a newer rescanned chapter layout`() = runTest {
    val newChapter = ChapterId("replacement")
    contentRepo.put(
      book("a", active = true).copy(
        name = "Live title",
        chapters = listOf(newChapter),
        currentChapter = newChapter,
        positionInChapter = 17,
        lastPlayedAt = Instant.ofEpochMilli(9_999),
      ),
    )
    val snapshot = snapshotOf("a").copy(
      books = listOf(snapshotOf("a").books.single().copy(name = "Restored title")),
    )
    restorer().applyDirect(snapshot) shouldBe 1
    db.bookContentDao().all().single().let {
      it.name shouldBe "Restored title"
      it.chapters shouldBe listOf(newChapter)
      it.currentChapter shouldBe newChapter
      it.positionInChapter shouldBe 17
      it.lastPlayedAt shouldBe Instant.ofEpochMilli(9_999)
    }
  }

  @Test
  fun `direct restore keeps rescanned chapter metadata even without newer playback`() = runTest {
    val chapterId = ChapterId("ca")
    val liveChapter = Chapter(
      id = chapterId,
      name = "Live chapter",
      duration = 2_000,
      fileLastModified = Instant.ofEpochMilli(2_000),
      markData = listOf(MarkData(0, "Live mark")),
    )
    val backedUpChapter = liveChapter.copy(
      name = "Stale chapter",
      duration = 1_000,
      fileLastModified = Instant.EPOCH,
      markData = listOf(MarkData(0, "Stale mark")),
    )
    contentRepo.put(book("a", active = true))
    db.chapterDao().insert(liveChapter)
    val snapshot = snapshotOf("a").copy(chapters = listOf(backedUpChapter.toDto(relName = "chapter.mp3")))

    restorer().applyDirect(snapshot) shouldBe 1

    db.chapterDao().chapter(chapterId) shouldBe liveChapter
    db.bookContentDao().all().single().chapters shouldBe listOf(chapterId)
  }
}
