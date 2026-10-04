package voice.features.bookOverview.shelves

import androidx.compose.runtime.mutableStateOf
import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.data.BookId
import voice.core.data.LibraryOrganisation
import voice.core.data.LibraryPlacement
import voice.core.data.LibrarySeries
import voice.core.data.LibraryShelf
import voice.core.data.ShelfSort
import voice.features.bookOverview.overview.BookOverviewItemViewState

class LibrarySectionTest {
  private fun book(
    id: String,
    progress: Float = 0f,
    finished: Boolean = false,
    author: String? = "Author",
    added: Long = 0,
  ) = mutableStateOf(
    BookOverviewItemViewState(id, author, null, progress, BookId(id), "1h", finished = finished, addedAt = added),
  )
  private val current = book("Book 2", .5f)
  private val finished = book("Book 1", 1f, true)
  private val next = book("Book 10")
  private val books = listOf(current, finished, next).associateBy { it.value.id }
  private val organisation = LibraryOrganisation(
    listOf(LibraryShelf.Other, LibraryShelf("fantasy", "Fantasy", 0)),
    listOf(LibrarySeries("series", "Chrysalis")),
    listOf(
      LibraryPlacement("Book 1", "fantasy", 0, "series", 0),
      LibraryPlacement("Book 2", "fantasy", 0, "series", 1),
      LibraryPlacement("Book 10", "fantasy", 0, "series", 2),
    ),
  )

  @Test fun `current is a shortcut while the home shelf keeps the entire series`() {
    val sections = librarySections(books, listOf(current.value.id), organisation, ShelfFilter.ALL)
    sections.map { it.id } shouldBe listOf("current", "fantasy")
    sections[0].entries.single().books.single().value.id shouldBe current.value.id
    sections[1].count shouldBe 3
    sections[1].entries.single().books.map { it.value.id.value } shouldBe listOf("Book 1", "Book 2", "Book 10")
    sections.flatMap { it.entries }.map { it.key }.distinct().size shouldBe 2
  }

  @Test fun `filter leaves current unchanged and retains whole series for its editor`() {
    val sections = librarySections(books, listOf(current.value.id), organisation, ShelfFilter.COMPLETED)
    sections[0].entries.single().books.single() shouldBe current
    val stack = sections[1].entries.single()
    stack.books shouldBe listOf(finished)
    stack.allBooks.size shouldBe 3
    sections[1].count shouldBe 1
    librarySections(books, emptyList(), organisation, ShelfFilter.NOT_STARTED).single().entries.single().books shouldBe listOf(next)
  }

  @Test fun `empty user shelves remain and other books hides only when empty`() {
    val empty = organisation.copy(placements = emptyList(), series = emptyList())
    librarySections(emptyMap(), emptyList(), empty, ShelfFilter.ALL).map { it.id } shouldBe listOf("fantasy")
    librarySections(books, emptyList(), empty, ShelfFilter.ALL).map { it.id } shouldBe listOf("fantasy", "other")
  }

  @Test fun `current and home occurrences have different stable keys across series rename`() {
    val first = librarySections(books, listOf(current.value.id), organisation, ShelfFilter.ALL)
    val renamed =
      librarySections(
        books,
        listOf(current.value.id),
        organisation.copy(series = listOf(LibrarySeries("series", "Renamed"))),
        ShelfFilter.ALL,
      )
    first.flatMap { it.entries }.map { it.key } shouldBe renamed.flatMap { it.entries }.map { it.key }
  }

  @Test fun `title sort is natural and switching back restores manual arrangement`() {
    val ungrouped = organisation.copy(
      series = emptyList(),
      placements = listOf(next, finished, current).mapIndexed { index, book ->
        LibraryPlacement(book.value.id.value, "fantasy", index)
      },
    )
    fun names(state: LibraryOrganisation) = librarySections(books, emptyList(), state, ShelfFilter.ALL).single().entries.map { it.title }
    names(ungrouped) shouldBe listOf("Book 10", "Book 1", "Book 2")
    names(ungrouped.copy(shelves = ungrouped.shelves.map { it.copy(sort = ShelfSort.TITLE) })) shouldBe
      listOf("Book 1", "Book 2", "Book 10")
    names(ungrouped) shouldBe listOf("Book 10", "Book 1", "Book 2")
  }

  @Test fun `mixed author series sort after known authors and recent uses newest member`() {
    val lone = book("Standalone", author = "Zed", added = 5)
    val mixed = books + (next.value.id to book("Book 10", author = "Other author", added = 10)) + (lone.value.id to lone)
    val state = organisation.copy(placements = organisation.placements + LibraryPlacement("Standalone", "fantasy", 1))
    val authors = state.copy(shelves = state.shelves.map { it.copy(sort = ShelfSort.AUTHOR) })
    librarySections(mixed, emptyList(), authors, ShelfFilter.ALL).single().entries.map { it.title } shouldBe
      listOf("Standalone", "Chrysalis")
    val recent = state.copy(shelves = state.shelves.map { it.copy(sort = ShelfSort.RECENTLY_ADDED) })
    librarySections(mixed, emptyList(), recent, ShelfFilter.ALL).single().entries.map { it.title } shouldBe
      listOf("Chrysalis", "Standalone")
  }

  @Test fun `author sort ignores missing metadata when a series has one known author`() {
    val known = book("Known", author = "Ada")
    val missing = book("Missing", author = null)
    val standalone = book("Standalone", author = "Zed")
    val sortedBooks = listOf(known, missing, standalone).associateBy { it.value.id }
    val state = LibraryOrganisation(
      shelves = listOf(LibraryShelf.Other, LibraryShelf("fantasy", "Fantasy", 0, ShelfSort.AUTHOR)),
      series = listOf(LibrarySeries("series", "Chrysalis")),
      placements = listOf(
        LibraryPlacement("Known", "fantasy", 0, "series", 0),
        LibraryPlacement("Missing", "fantasy", 0, "series", 1),
        LibraryPlacement("Standalone", "fantasy", 1),
      ),
    )

    librarySections(sortedBooks, emptyList(), state, ShelfFilter.ALL).single().entries.map { it.title } shouldBe
      listOf("Chrysalis", "Standalone")
  }

  @Test fun `legacy names and imported tags never recreate a group after ungrouping`() {
    val ungrouped = organisation.copy(
      series = emptyList(),
      placements = organisation.placements.map {
        it.copy(seriesId = null, seriesPosition = null)
      },
    )
    next.value = next.value.copy(seriesGroup = "Old group", seriesPart = "10")
    librarySections(books, emptyList(), ungrouped, ShelfFilter.ALL).single().entries.size shouldBe 3
  }

  @Test fun `singleton series and same-named standalone retain separate stable identities`() {
    val standalone = book("Chrysalis")
    val state = organisation.copy(placements = listOf(organisation.placements.first(), LibraryPlacement("Chrysalis", "fantasy", 1)))
    val entries = librarySections(
      mapOf(finished.value.id to finished, standalone.value.id to standalone),
      emptyList(),
      state,
      ShelfFilter.ALL,
    )
      .single().entries
    entries.map { it.title } shouldBe listOf("Chrysalis", "Chrysalis")
    entries.map { it.key }.distinct().size shouldBe 2
    entries.first().seriesId shouldBe "series"
  }

  @Test fun `completing a book keeps its home stack and manual position unchanged`() {
    val before = librarySections(books, listOf(current.value.id), organisation, ShelfFilter.ALL).last()
    current.value = current.value.copy(progress = 1f, finished = true, name = "Renamed book")
    val after = librarySections(books, emptyList(), organisation, ShelfFilter.ALL).single()
    after.entries.map { it.key } shouldBe before.entries.map { it.key }
    after.entries.single().books.map { it.value.id } shouldBe before.entries.single().books.map { it.value.id }
    after.count shouldBe 3
  }
}
