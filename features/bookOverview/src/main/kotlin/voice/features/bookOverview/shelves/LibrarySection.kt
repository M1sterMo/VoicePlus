package voice.features.bookOverview.shelves

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.BookId
import voice.core.data.LibraryOrganisation
import voice.core.data.LibraryShelf
import voice.core.data.ShelfSort
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.series.LibraryEntry

internal const val UP_NEXT = "upnext"

internal enum class ShelfFilter { ALL, NOT_STARTED, COMPLETED }

internal data class LibrarySection(
  val id: String,
  val shelf: LibraryShelf?,
  val entries: List<LibraryEntry>,
  val totalCount: Int = entries.sumOf { it.allBooks.size },
) {
  /** Current and Up next show books that live on a shelf; they can't be organised themselves. */
  val projection: Boolean get() = shelf == null
  val upNext: Boolean get() = id == UP_NEXT
  val count: Int get() = entries.sumOf { it.books.size }
  val expanded: Boolean get() = shelf?.expanded ?: true
}

/** Listening status is a projection; it never changes a book's stored home or manual position. */
internal fun librarySections(
  books: Map<BookId, State<BookOverviewItemViewState>>,
  current: List<BookId>,
  organisation: LibraryOrganisation,
  filter: ShelfFilter,
  upNext: List<BookId> = emptyList(),
): List<LibrarySection> {
  val placements = organisation.placements.associateBy { BookId(it.bookId) }
  val series = organisation.series.associateBy { it.id }
  val homes = books.values.groupBy { placements[it.value.id]?.shelfId ?: LibraryShelf.OTHER }
  return buildList {
    val currentEntries = current.mapNotNull(books::get).map { book ->
      LibraryEntry(null, listOf(book), sectionId = "current", itemId = "book:${book.value.id.value}")
    }
    if (currentEntries.isNotEmpty()) add(LibrarySection("current", null, currentEntries))
    val queued = upNext.mapNotNull(books::get).mapIndexed { index, book ->
      LibraryEntry(null, listOf(book), sectionId = UP_NEXT, itemId = "book:${book.value.id.value}", queuePosition = index + 1)
    }
    if (queued.isNotEmpty()) add(LibrarySection(UP_NEXT, null, queued))
    organisation.shelves.sortedWith(
      compareBy<LibraryShelf> {
        it.id == LibraryShelf.OTHER
      }.thenBy { it.position }.thenBy { it.id },
    ).forEach { shelf ->
      val entries = homes[shelf.id].orEmpty().groupBy {
        placements[it.value.id]?.itemId ?: "book:${it.value.id.value}"
      }.map { (key, members) ->
        val placement = placements[members.first().value.id]
        val group = placement?.seriesId?.let(series::get)
        val ordered = if (group == null) {
          members
        } else {
          members.sortedWith(
            compareBy<State<BookOverviewItemViewState>> { placements[it.value.id]?.seriesPosition ?: Int.MAX_VALUE }
              .thenBy(NaturalOrderComparator.stringComparator) { it.value.name }.thenBy { it.value.id.value },
          )
        }
        val visible = ordered.filter { book ->
          when (filter) {
            ShelfFilter.ALL -> true
            ShelfFilter.NOT_STARTED -> !book.value.finished && book.value.progress == 0f
            ShelfFilter.COMPLETED -> book.value.finished
          }
        }
        val byLength = shelf.sort == ShelfSort.SHORTEST || shelf.sort == ShelfSort.LONGEST
        val shown = if (byLength) visible.map { book -> derivedStateOf { book.value.copy(showLength = true) } } else visible
        LibraryEntry(group?.name, shown, shelf.id, key, group?.id, ordered)
      }.filter { it.books.isNotEmpty() }
      val comparator = when (shelf.sort) {
        ShelfSort.MANUAL -> compareBy<LibraryEntry> { placements[it.books.first().value.id]?.position ?: Int.MAX_VALUE }
        ShelfSort.TITLE -> compareBy(NaturalOrderComparator.stringComparator) { entry: LibraryEntry -> entry.title }
        ShelfSort.AUTHOR -> compareBy<LibraryEntry, String?>(nullsLast(NaturalOrderComparator.stringComparator)) { entry ->
          entry.allBooks.mapNotNull { it.value.author?.takeIf(String::isNotBlank) }.distinct().singleOrNull()
        }.thenBy(NaturalOrderComparator.stringComparator) { it.title }
        ShelfSort.RECENTLY_ADDED -> compareByDescending<LibraryEntry> { it.allBooks.maxOf { book -> book.value.addedAt } }
        // A series stack counts as one item as long as all its books together.
        ShelfSort.SHORTEST -> compareBy<LibraryEntry> { it.allBooks.sumOf { book -> book.value.durationMs } }
        ShelfSort.LONGEST -> compareByDescending<LibraryEntry> { it.allBooks.sumOf { book -> book.value.durationMs } }
      }.thenBy { it.itemId }
      if (entries.isNotEmpty() || shelf.id != LibraryShelf.OTHER) {
        add(LibrarySection(shelf.id, shelf, entries.sortedWith(comparator), homes[shelf.id].orEmpty().size))
      }
    }
  }
}
