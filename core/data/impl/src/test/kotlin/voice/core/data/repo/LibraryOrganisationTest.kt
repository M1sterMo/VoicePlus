package voice.core.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.LibraryShelf
import voice.core.data.ShelfSort
import voice.core.data.repo.internals.AppDb
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class LibraryOrganisationTest {
  private lateinit var db: AppDb
  private lateinit var repo: LibraryOrganisationRepoImpl
  private lateinit var books: BookContentRepoImpl

  @Before fun setup() {
    db =
      Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDb::class.java).allowMainThreadQueries().build()
    repo = LibraryOrganisationRepoImpl(db)
    books = BookContentRepoImpl(db.bookContentDao())
  }

  @After fun close() = db.close()

  private suspend fun seed(vararg ids: String) {
    ids.forEach { id ->
      books.put(
        BookContent(
          BookId(id), 1.5f, true, true, Instant.EPOCH, "Author", id, Instant.EPOCH,
          listOf(ChapterId("chapter")), ChapterId("chapter"), 42, File("cover.jpg").absoluteFile, 2f, null, null, null, null, -9,
        ),
      )
    }
  }

  private suspend fun shelf(name: String): String = repo.createShelf(name).after.shelves.single { it.name == name }.id
  private suspend fun order(id: String = LibraryShelf.OTHER) = repo.get().itemOrder(id)

  @Test fun `shelf and series organisation survives closing and reopening the database`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val name = "shelf-roundtrip-${UUID.randomUUID()}"
    fun open() = Room.databaseBuilder(context, AppDb::class.java, name).allowMainThreadQueries().build()
    val persistent = open()
    try {
      repo = LibraryOrganisationRepoImpl(persistent)
      books = BookContentRepoImpl(persistent.bookContentDao())
      seed("a", "b", "c")
      val fantasy = shelf("Fantasy")
      val later = shelf("Later")
      repo.saveSeries(null, "Series", listOf(BookId("b"), BookId("a")), fantasy)
      repo.move(setOf(BookId("c")), fantasy)
      repo.reorder(fantasy, order(fantasy).reversed())
      repo.setSort(fantasy, ShelfSort.TITLE)
      repo.setExpanded(later, false)
      repo.reorderShelves(listOf(later, fantasy))
      books.put(books.get(BookId("a"))!!.copy(name = "Edited title", nameOverridden = true))
      val expected = repo.get()
      val contents = books.all()
      persistent.close()
      val reopened = open()
      try {
        LibraryOrganisationRepoImpl(reopened).get() shouldBe expected
        BookContentRepoImpl(reopened.bookContentDao()).all().sortedBy { it.id.value } shouldBe
          contents.sortedBy { it.id.value }
      } finally {
        reopened.close()
      }
    } finally {
      persistent.close()
      context.deleteDatabase(name)
    }
  }

  @Test fun `empty shelf creation rename and validation persist without any books`() = runTest {
    val id = shelf("Fantasy")
    repo.renameShelf(id, "  Sci-fi  ")
    repo.flow().first().shelves.single { it.id == id }.name shouldBe "Sci-fi"
    runCatching { repo.createShelf("sci-fi") }.isFailure shouldBe true
    runCatching { repo.createShelf("   ") }.isFailure shouldBe true
    runCatching { repo.removeShelf(LibraryShelf.OTHER) }.isFailure shouldBe true
    runCatching { repo.renameShelf(LibraryShelf.OTHER, "Other") }.isFailure shouldBe true
  }

  @Test fun `new imports append and updates hiding and rescans cannot reorder anything`() = runTest {
    seed("a", "b", "c")
    repo.reorder(LibraryShelf.OTHER, listOf("book:c", "book:a", "book:b"))
    val saved = repo.get()
    books.put(books.get(BookId("a"))!!.copy(cover = File("new-cover"), positionInChapter = 99, name = "Renamed"))
    books.setAllInactiveExcept(emptyList())
    repo.get() shouldBe saved
    seed("d")
    order() shouldBe listOf("book:c", "book:a", "book:b", "book:d")
  }

  @Test fun `sort selection preserves independent manual shelf item and series order`() = runTest {
    seed("a", "b", "c", "d")
    val one = shelf("One")
    val two = shelf("Two")
    repo.move(setOf(BookId("a"), BookId("b")), one)
    repo.move(setOf(BookId("c"), BookId("d")), two)
    repo.reorder(one, listOf("book:b", "book:a"))
    repo.setSort(one, ShelfSort.TITLE)
    repo.reorderShelves(listOf(two, one))
    order(one) shouldBe listOf("book:b", "book:a")
    order(two) shouldBe listOf("book:c", "book:d")
    repo.setSort(one, ShelfSort.MANUAL)
    LibraryOrganisationRepoImpl(db).get().itemOrder(one) shouldBe listOf("book:b", "book:a")
  }

  @Test fun `moving one series member moves the entire series without playback edits`() = runTest {
    seed("a", "b", "c")
    val originals = books.all()
    repo.saveSeries(null, "Series", listOf(BookId("b"), BookId("a")))
    val id = repo.get().series.single().id
    val destination = shelf("Fantasy")
    repo.move(setOf(BookId("a")), destination)
    order(destination) shouldBe listOf("series:$id")
    order() shouldBe listOf("book:c")
    repo.saveSeries(id, "Renamed", listOf(BookId("b"), BookId("a")))
    repo.get().series.single().id shouldBe id
    books.all() shouldBe originals
  }

  @Test fun `mixed source series needs an explicit destination and names never silently merge`() = runTest {
    seed("a", "b", "c")
    val destination = shelf("Fantasy")
    repo.move(setOf(BookId("b")), destination)
    val before = repo.get()
    runCatching { repo.saveSeries(null, "Series", listOf(BookId("a"), BookId("b"))) }.isFailure shouldBe true
    repo.get() shouldBe before
    repo.saveSeries(null, "Series", listOf(BookId("a"), BookId("b")), destination)
    runCatching { repo.saveSeries(null, "series", listOf(BookId("c"))) }.isFailure shouldBe true
    repo.get().placements.filter { it.seriesId != null }.map { it.shelfId }.toSet() shouldBe setOf(destination)
  }

  @Test fun `ungroup expands at former stack position in reading order`() = runTest {
    seed("first", "a", "b", "last")
    repo.saveSeries(null, "Series", listOf(BookId("b"), BookId("a")))
    val id = repo.get().series.single().id
    order() shouldBe listOf("book:first", "series:$id", "book:last")
    repo.saveSeries(id, "Series", emptyList())
    order() shouldBe listOf("book:first", "book:b", "book:a", "book:last")
    repo.get().series shouldBe emptyList()
  }

  @Test fun `removed member follows stack and removing last member removes empty series`() = runTest {
    seed("a", "b", "c")
    repo.saveSeries(null, "Series", listOf(BookId("a"), BookId("b")))
    val id = repo.get().series.single().id
    repo.removeFromSeries(BookId("a"))
    order() shouldBe listOf("series:$id", "book:a", "book:c")
    repo.removeFromSeries(BookId("b"))
    order() shouldBe listOf("book:b", "book:a", "book:c")
    repo.get().series shouldBe emptyList()
  }

  @Test fun `removing shelf only moves contents and undo preserves later playback and unrelated shelves`() = runTest {
    seed("a", "b")
    val destination = shelf("Fantasy")
    repo.move(setOf(BookId("a")), destination)
    val removal = repo.removeShelf(destination)
    order() shouldBe listOf("book:b", "book:a")
    val unrelated = shelf("Later")
    books.put(books.get(BookId("a"))!!.copy(positionInChapter = 1000))
    repo.undo(removal) shouldBe true
    order(destination) shouldBe listOf("book:a")
    repo.get().shelves.any { it.id == unrelated } shouldBe true
    books.get(BookId("a"))!!.positionInChapter shouldBe 1000L
  }

  @Test fun `stale undo never overwrites subsequent organisation`() = runTest {
    seed("a")
    val one = shelf("One")
    val two = shelf("Two")
    val move = repo.move(setOf(BookId("a")), one)
    repo.move(setOf(BookId("a")), two)
    repo.undo(move) shouldBe false
    order(two) shouldBe listOf("book:a")
  }

  @Test fun `database failure halfway through move rolls back all placements`() = runTest {
    seed("a", "b")
    val destination = shelf("Fantasy")
    val before = repo.get()
    db.openHelper.writableDatabase.execSQL(
      "CREATE TRIGGER reject_placement BEFORE INSERT ON libraryPlacement WHEN NEW.bookId = 'b' AND NEW.shelfId != 'other' BEGIN SELECT RAISE(ABORT, 'test'); END",
    )
    runCatching { repo.move(setOf(BookId("a"), BookId("b")), destination) }.isFailure shouldBe true
    repo.get() shouldBe before
  }

  @Test fun `concurrent playback and organisation remain independent`() = runTest {
    seed("a", "b")
    val grouping = async { repo.saveSeries(null, "Series", listOf(BookId("b"), BookId("a"))) }
    val playback = async { repeat(20) { books.put(books.get(BookId("a"))!!.copy(positionInChapter = 42L + it)) } }
    grouping.await()
    playback.await()
    repo.get().placements.sortedBy { it.seriesPosition }.map { it.bookId } shouldBe listOf("b", "a")
    books.get(BookId("a"))!!.positionInChapter shouldBe 61L
    books.get(BookId("a"))!!.chapterNameOffset shouldBe -9
  }
}
