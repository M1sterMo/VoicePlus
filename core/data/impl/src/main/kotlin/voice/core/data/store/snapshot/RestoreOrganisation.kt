package voice.core.data.store.snapshot

import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.LibraryOrganisation
import voice.core.data.LibraryPlacement
import voice.core.data.LibrarySeries
import voice.core.data.LibraryShelf
import voice.core.data.repo.internals.dao.LibraryOrganisationDao
import java.util.UUID

/** Called inside the same transaction as restored books. Only positively matched IDs are re-keyed. */
internal suspend fun LibraryOrganisationDao.restore(
  snapshot: LibrarySnapshot,
  bookIds: Map<String, String>,
) {
  val incoming = snapshot.organisation ?: snapshot.legacyOrganisation()
  incoming.validate()
  val before = get()
  val placements = incoming.placements.mapNotNull { placement ->
    bookIds[placement.bookId]?.let { placement.copy(bookId = it) }
  }
  // A confirmed URI re-key replaces the dead placement as well as the newly scanned one.
  val replaced = placements.map { it.bookId }.toSet() + bookIds.filter { it.key != it.value }.keys
  val restoredSeries = placements.filter { it.seriesId != null }.groupBy { it.seriesId }
  val retained = before.placements.filterNot { it.bookId in replaced }.toMutableList()
  // Books added since the backup stay in their series, after its restored reading order.
  // Reusing their old positions/home could otherwise split a series or collide with restored members.
  restoredSeries.forEach { (id, members) ->
    val home = members.first()
    val next = members.maxOf { it.seriesPosition!! } + 1
    retained.filter { it.seriesId == id }.sortedWith(compareBy<LibraryPlacement> { it.seriesPosition }.thenBy { it.bookId })
      .forEachIndexed { index, placement ->
        retained[retained.indexOf(placement)] =
          placement.copy(shelfId = home.shelfId, position = home.position, seriesPosition = next + index)
      }
  }
  val mergedPlacements = retained + placements
  val seriesIds = mergedPlacements.mapNotNull { it.seriesId }.toSet()
  val after = LibraryOrganisation(
    shelves = (before.shelves + incoming.shelves).associateBy { it.id }.values.toList(),
    series = (before.series + incoming.series).associateBy { it.id }.values.filter { it.id in seriesIds },
    placements = mergedPlacements,
  )
  replace(before, after)
}

internal fun LibrarySnapshot.legacyOrganisation(): LibraryOrganisation {
  val series = mutableListOf<LibrarySeries>()
  val placements = mutableListOf<LibraryPlacement>()
  books.groupBy { it.seriesGroup?.let { name -> "series:$name" } ?: "book:${it.id}" }.values
    .sortedWith(
      compareBy<List<BookContentDto>, String>(NaturalOrderComparator.stringComparator) {
        it.first().seriesGroup ?: it.first().name
      }.thenBy { it.first().id },
    )
    .forEachIndexed { position, members ->
      val name = members.first().seriesGroup
      val id = name?.let { UUID.nameUUIDFromBytes("legacy-series:$it".toByteArray(Charsets.UTF_8)).toString() }
      if (id != null) series += LibrarySeries(id, name)
      members.sortedWith(
        compareBy<BookContentDto, Int?>(nullsLast()) { it.seriesOrder }
          .thenBy(nullsLast(NaturalOrderComparator.stringComparator)) { it.part?.takeIf(String::isNotBlank) }
          .thenBy(NaturalOrderComparator.stringComparator) { it.name }.thenBy { it.id },
      )
        .forEachIndexed { index, book ->
          placements +=
            LibraryPlacement(book.id, position = position, seriesId = id, seriesPosition = index.takeIf { id != null })
        }
    }
  return LibraryOrganisation(series = series, placements = placements)
}
