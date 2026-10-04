package voice.core.data.store.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.LibraryOrganisation
import voice.core.data.LibraryPlacement
import voice.core.data.LibrarySeries
import voice.core.data.LibraryShelf
import voice.core.data.ShelfSort
import voice.core.data.repo.internals.AppDb

@RunWith(RobolectricTestRunner::class)
class OrganisationRestoreTest {
  private lateinit var db: AppDb
  private val arrangement = LibraryOrganisation(
    shelves = listOf(LibraryShelf.Other, LibraryShelf("fantasy", "Fantasy", 0, ShelfSort.AUTHOR, false), LibraryShelf("empty", "Later", 1)),
    series = listOf(LibrarySeries("series", "Chrysalis")),
    placements = listOf(LibraryPlacement("old-a", "fantasy", 0, "series", 1), LibraryPlacement("old-b", "fantasy", 0, "series", 0)),
  )
  private val snapshot = LibrarySnapshot(
    6, sequence = 1, savedAtEpochMillis = 0, totalCount = 0, activeCount = 0,
    books = emptyList(), bookmarks = emptyList(), characters = emptyList(), chapterNameOverrides = emptyList(), organisation = arrangement,
  )

  @Before fun setup() {
    db =
      Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDb::class.java).allowMainThreadQueries().build()
  }

  @After fun close() = db.close()

  @Test fun `new snapshot round trips shelves empty shelves sort collapse series identity and order`() = runTest {
    val encoded = snapshotTestJson.encodeToString(LibrarySnapshot.serializer(), snapshot)
    val decoded = snapshotTestJson.decodeFromString(LibrarySnapshot.serializer(), encoded)
    decoded shouldBe snapshot
    val dao = db.libraryOrganisationDao()
    dao.restore(decoded, mapOf("old-a" to "old-a", "old-b" to "old-b"))
    val restored = dao.get()
    restored.shelves.toSet() shouldBe arrangement.shelves.toSet()
    restored.series shouldBe arrangement.series
    restored.placements shouldBe arrangement.placements
  }

  @Test fun `storage regrant rekeys only positively matched book placements`() = runTest {
    val dao = db.libraryOrganisationDao()
    dao.restore(snapshot, mapOf("old-b" to "new-b"))
    val restored = dao.get()
    restored.placements.single() shouldBe arrangement.placements[1].copy(bookId = "new-b")
    restored.shelves.toSet() shouldBe arrangement.shelves.toSet()
    dao.restore(snapshot, mapOf("old-a" to "new-a", "old-b" to "new-b"))
    dao.get().placements.map { it.bookId }.toSet() shouldBe setOf("new-a", "new-b")
    dao.get().validate()
  }

  @Test fun `invalid backup organisation fails before any rows change`() = runTest {
    val dao = db.libraryOrganisationDao()
    val before = dao.get()
    val bad = snapshot.copy(organisation = arrangement.copy(placements = listOf(arrangement.placements.first().copy(shelfId = "missing"))))
    runCatching { dao.restore(bad, mapOf("old-a" to "new-a")) }.isFailure shouldBe true
    dao.get() shouldBe before
  }

  @Test fun `restoring an older series home keeps later members without position collisions`() = runTest {
    val dao = db.libraryOrganisationDao()
    val later = arrangement.copy(
      placements = arrangement.placements.map {
        it.copy(shelfId = LibraryShelf.OTHER, position = 5)
      } + LibraryPlacement("new-book", LibraryShelf.OTHER, 5, "series", 2),
    )
    dao.replace(dao.get(), later)
    dao.restore(snapshot, mapOf("old-a" to "old-a", "old-b" to "old-b"))
    val restored = dao.get()
    restored.validate()
    restored.placements.single { it.bookId == "new-book" } shouldBe LibraryPlacement("new-book", "fantasy", 0, "series", 2)
    dao.restore(snapshot, mapOf("old-a" to "old-a", "old-b" to "old-b"))
    dao.get() shouldBe restored
  }

  @Test fun `rekey removes obsolete placement but keeps unmatched members and is repeatable`() = runTest {
    val dao = db.libraryOrganisationDao()
    dao.replace(dao.get(), arrangement)
    dao.restore(snapshot, mapOf("old-a" to "new-a"))
    val restored = dao.get()
    restored.validate()
    restored.placements.map { it.bookId }.toSet() shouldBe setOf("new-a", "old-b")
    dao.restore(snapshot, mapOf("old-a" to "new-a", "old-b" to "new-b"))
    dao.get().placements.map { it.bookId }.toSet() shouldBe setOf("new-a", "new-b")
    dao.get().validate()
  }
}
