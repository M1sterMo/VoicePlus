package voice.core.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.BookCharacter
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.repo.internals.AppDb
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SeriesCharactersTest {
  private lateinit var db: AppDb
  private lateinit var organisation: LibraryOrganisationRepoImpl
  private lateinit var characters: BookCharacterRepoImpl

  @Before fun setup() {
    db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDb::class.java)
      .allowMainThreadQueries().build()
    organisation = LibraryOrganisationRepoImpl(db)
    characters = BookCharacterRepoImpl(db.bookCharacterDao())
  }

  @After fun close() = db.close()

  private suspend fun seed(vararg ids: String) {
    val books = BookContentRepoImpl(db.bookContentDao())
    ids.forEach { id ->
      books.put(
        BookContent(
          BookId(id), 1f, false, true, Instant.EPOCH, null, id, Instant.EPOCH,
          listOf(ChapterId("c$id")), ChapterId("c$id"), 0, null, 0f, null, null, null, null,
        ),
      )
    }
  }

  private suspend fun add(
    book: String,
    name: String,
    description: String = "",
  ) {
    characters.upsert(
      BookCharacter(
        bookId = BookId(book),
        name = name,
        description = description,
        sortOrder = characters.nextSortOrder(BookId(book)),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
      ),
    )
  }

  private suspend fun list(book: String) = characters.characters(BookId(book)).first()
  private suspend fun names(book: String) = list(book).map { it.name }

  @IgnorableReturnValue
  private suspend fun group(vararg books: String) = organisation.saveSeries(null, "Dune", books.map(::BookId)).after.series.single().id

  @Test fun `standalone books keep their own lists`() = runTest {
    seed("a", "b")
    add("a", "Paul")
    add("b", "Leto")

    names("a") shouldBe listOf("Paul")
    names("b") shouldBe listOf("Leto")
    list("a").single().seriesId shouldBe null
  }

  @Test fun `grouping merges lists in reading order and collapses exact duplicates`() = runTest {
    seed("a", "b", "c")
    add("a", "Paul", "Duke")
    add("a", "Chani")
    add("b", "Paul", "Duke")
    add("b", "Paul", "Emperor")
    add("b", "Leto")
    add("c", "Solo")

    val series = group("b", "a")

    names("a") shouldBe listOf("Paul", "Paul", "Leto", "Chani")
    list("a").map { it.description }.take(2) shouldBe listOf("Duke", "Emperor")
    list("a").map { it.bookId.value } shouldBe listOf("b", "b", "b", "a")
    list("a").map { it.sortOrder } shouldBe listOf(0, 1, 2, 3)
    list("b") shouldBe list("a")
    list("a").all { it.seriesId == series } shouldBe true
    characters.characterCount(BookId("b")).first() shouldBe 4
    names("c") shouldBe listOf("Solo")
  }

  @Test fun `adding, editing, reordering and deleting in one book applies to every book in the series`() = runTest {
    seed("a", "b")
    group("a", "b")

    add("b", "Paul")
    add("a", "Chani")
    names("a") shouldBe listOf("Paul", "Chani")
    list("a").map { it.bookId.value } shouldBe listOf("b", "a")

    val (paul, chani) = list("b")
    characters.updateAll(listOf(chani.copy(sortOrder = 0, description = "Fremen"), paul.copy(sortOrder = 1)))
    list("a").map { it.name to it.description } shouldBe listOf("Chani" to "Fremen", "Paul" to "")

    characters.delete(paul.id)
    names("a") shouldBe listOf("Chani")
    names("b") shouldBe listOf("Chani")
  }

  @Test fun `adding a book to a series merges its list`() = runTest {
    seed("a", "b", "c")
    val series = group("a", "b")
    add("a", "Paul")
    add("c", "Paul")
    add("c", "Alia")

    organisation.saveSeries(series, "Dune", listOf("a", "b", "c").map(::BookId))

    names("b") shouldBe listOf("Paul", "Alia")
    names("c") shouldBe listOf("Paul", "Alia")
    list("c").first().bookId.value shouldBe "a"
  }

  @Test fun `a book leaving a series takes a copy and both lists then diverge`() = runTest {
    seed("a", "b", "c")
    group("a", "b", "c")
    add("a", "Paul")
    add("c", "Leto")

    organisation.removeFromSeries(BookId("c"))

    names("c") shouldBe listOf("Paul", "Leto")
    names("a") shouldBe listOf("Paul", "Leto")
    list("c").all { it.seriesId == null && it.ownerBookId?.value == "c" } shouldBe true
    // Copies remember the book each character was added in.
    list("c").map { it.bookId.value } shouldBe listOf("a", "c")

    add("c", "Ghanima")
    characters.delete(list("a").first().id)
    names("c") shouldBe listOf("Paul", "Leto", "Ghanima")
    names("a") shouldBe listOf("Leto")
    // The series keeps the character added in c, but c is no longer in the series.
    list("b").single().bookId.value shouldBe "c"
  }

  @Test fun `rejoining a series does not duplicate the copy it took`() = runTest {
    seed("a", "b")
    val series = group("a", "b")
    add("a", "Paul")
    organisation.removeFromSeries(BookId("b"))

    organisation.saveSeries(series, "Dune", listOf("a", "b").map(::BookId))

    names("b") shouldBe listOf("Paul")
    list("b").single().bookId.value shouldBe "a"
  }

  @Test fun `dissolving a series gives every former member a full copy`() = runTest {
    seed("a", "b")
    val series = group("a", "b")
    add("a", "Paul")
    add("b", "Leto")

    organisation.saveSeries(series, "Dune", emptyList())

    names("a") shouldBe listOf("Paul", "Leto")
    names("b") shouldBe listOf("Paul", "Leto")
    db.bookCharacterDao().all().none { it.seriesId != null } shouldBe true
    characters.delete(list("a").first().id)
    names("b") shouldBe listOf("Paul", "Leto")
  }

  @Test fun `moving a book straight into another series carries its list across`() = runTest {
    seed("a", "b", "c", "d")
    group("a", "b")
    add("a", "Paul")
    val other = organisation.saveSeries(null, "Foundation", listOf("c", "d").map(::BookId)).after.series.single {
      it.name == "Foundation"
    }.id
    add("c", "Hari")

    organisation.saveSeries(other, "Foundation", listOf("c", "d", "b").map(::BookId))

    names("d") shouldBe listOf("Hari", "Paul")
    list("d").map { it.bookId.value } shouldBe listOf("c", "a")
    names("a") shouldBe listOf("Paul")
  }

  @Test fun `undoing a grouping puts each book's own list back`() = runTest {
    seed("a", "b")
    add("a", "Paul")
    add("b", "Leto")
    val before = db.bookCharacterDao().all()
    val change = organisation.saveSeries(null, "Dune", listOf("a", "b").map(::BookId))

    organisation.undo(change) shouldBe true

    db.bookCharacterDao().all() shouldBe before
  }

  @Test fun `undoing a move between series puts both series' lists back`() = runTest {
    seed("a", "b", "c")
    val dune = group("a", "b")
    add("a", "Paul")
    val foundation = organisation.saveSeries(null, "Foundation", listOf(BookId("c"))).after.series.single { it.name == "Foundation" }.id
    add("c", "Hari")
    val before = db.bookCharacterDao().all()
    val change = organisation.saveSeries(foundation, "Foundation", listOf("c", "b").map(::BookId))
    names("c") shouldBe listOf("Hari", "Paul")

    organisation.undo(change) shouldBe true

    db.bookCharacterDao().all() shouldBe before
    names("b") shouldBe listOf("Paul")
    names("c") shouldBe listOf("Hari")
    organisation.get().placements.single { it.bookId == "b" }.seriesId shouldBe dune
  }

  @Test fun `undo is refused once a list it would restore has been edited`() = runTest {
    seed("a", "b")
    add("a", "Paul")
    val change = organisation.saveSeries(null, "Dune", listOf("a", "b").map(::BookId))
    add("b", "Leto")

    organisation.undo(change) shouldBe false

    names("a") shouldBe listOf("Paul", "Leto")
  }
}
