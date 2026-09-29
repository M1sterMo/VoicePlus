package voice.core.data.repo

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import voice.core.data.BookId
import voice.core.data.LibraryChange
import voice.core.data.LibraryEditError
import voice.core.data.LibraryEditException
import voice.core.data.LibraryOrganisation
import voice.core.data.LibraryPlacement
import voice.core.data.LibrarySeries
import voice.core.data.LibraryShelf
import voice.core.data.ShelfSort
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.transaction
import java.util.UUID

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class LibraryOrganisationRepoImpl(private val db: AppDb) : LibraryOrganisationRepo {
  private val dao get() = db.libraryOrganisationDao()

  override fun flow(): Flow<LibraryOrganisation> = dao.changes().map { get() }.distinctUntilChanged()
  override suspend fun get(): LibraryOrganisation = dao.get()

  @IgnorableReturnValue
  private suspend fun edit(update: (LibraryOrganisation) -> LibraryOrganisation): LibraryChange = db.transaction {
    val before = dao.get()
    val after = update(before)
    dao.replace(before, after)
    LibraryChange(before, after)
  }

  override suspend fun createShelf(name: String): LibraryChange = edit { state ->
    val position = (state.shelves.filterNot { it.id == LibraryShelf.OTHER }.maxOfOrNull { it.position } ?: -1) + 1
    state.copy(shelves = state.shelves + LibraryShelf(UUID.randomUUID().toString(), state.shelfName(name), position))
  }

  override suspend fun renameShelf(
    id: String,
    name: String,
  ): LibraryChange = edit { state ->
    require(id != LibraryShelf.OTHER)
    val trimmed = state.shelfName(name, id)
    state.copy(shelves = state.shelves.map { if (it.id == id) it.copy(name = trimmed) else it })
  }

  override suspend fun removeShelf(id: String): LibraryChange = edit { state ->
    require(id != LibraryShelf.OTHER)
    state.moveItems(state.itemOrder(id).toSet(), LibraryShelf.OTHER).copy(shelves = state.shelves.filterNot { it.id == id })
  }

  override suspend fun setSort(
    id: String,
    sort: ShelfSort,
  ): LibraryChange = edit { state ->
    state.copy(shelves = state.shelves.map { if (it.id == id) it.copy(sort = sort) else it })
  }

  override suspend fun setExpanded(
    id: String,
    expanded: Boolean,
  ) {
    edit { state -> state.copy(shelves = state.shelves.map { if (it.id == id) it.copy(expanded = expanded) else it }) }
  }

  override suspend fun reorderShelves(ids: List<String>): LibraryChange = edit { state ->
    require(ids.distinct().size == ids.size && LibraryShelf.OTHER !in ids)
    if (ids.toSet() != state.shelves.filterNot { it.id == LibraryShelf.OTHER }.map { it.id }.toSet()) {
      throw LibraryEditException(LibraryEditError.STALE_ORDER)
    }
    state.copy(shelves = state.shelves.map { if (it.id == LibraryShelf.OTHER) it else it.copy(position = ids.indexOf(it.id)) })
  }

  override suspend fun move(
    books: Set<BookId>,
    shelfId: String,
  ): LibraryChange = edit { state ->
    val ids = books.map { it.value }.toSet()
    state.moveItems(state.placements.filter { it.bookId in ids }.map { it.itemId }.toSet(), shelfId)
  }

  override suspend fun reorder(
    shelfId: String,
    itemIds: List<String>,
  ): LibraryChange = edit { state ->
    require(itemIds.distinct().size == itemIds.size)
    val existing = state.itemOrder(shelfId)
    if (!existing.containsAll(itemIds)) throw LibraryEditException(LibraryEditError.STALE_ORDER)
    // Hidden and newly imported entries are retained, not silently deleted by a visible-only draft.
    val requested = itemIds.iterator()
    val visible = itemIds.toSet()
    val ordered = existing.map { if (it in visible) requested.next() else it }
    state.withOrder(shelfId, ordered).copy(shelves = state.shelves.map { if (it.id == shelfId) it.copy(sort = ShelfSort.MANUAL) else it })
  }

  override suspend fun saveSeries(
    id: String?,
    name: String,
    books: List<BookId>,
    shelfId: String?,
  ): LibraryChange = edit { state ->
    val selected = books.map { it.value }
    require(selected.distinct().size == selected.size)
    require(selected.all { book -> state.placements.any { it.bookId == book } })
    val oldMembers = state.placements.filter { id != null && it.seriesId == id }.sortedBy { it.seriesPosition }
    require(id == null || state.series.any { it.id == id })
    require(id != null || selected.isNotEmpty())
    val trimmed = name.trim()
    if (trimmed.isEmpty()) throw LibraryEditException(LibraryEditError.EMPTY_NAME)
    if (state.series.any {
        it.id != id && it.name.equals(trimmed, ignoreCase = true)
      }
    ) {
      throw LibraryEditException(LibraryEditError.DUPLICATE_SERIES)
    }
    val selectedPlacements = state.placements.filter { it.bookId in selected }
    val homes = selectedPlacements.map { it.shelfId }.distinct()
    val destination =
      oldMembers.firstOrNull()?.shelfId ?: shelfId ?: homes.singleOrNull() ?: throw LibraryEditException(LibraryEditError.CHOOSE_SHELF)
    if (state.shelves.none { it.id == destination }) throw LibraryEditException(LibraryEditError.CHOOSE_SHELF)
    val seriesId = id ?: UUID.randomUUID().toString()
    val itemId = "series:$seriesId"
    val oldOrder = state.itemOrder(destination)
    val insertion = oldOrder.indexOf(itemId).takeIf { it >= 0 }
      ?: oldOrder.indexOfFirst { key -> selectedPlacements.any { it.itemId == key } }.takeIf { it >= 0 } ?: oldOrder.size
    val removed = oldMembers.filter { it.bookId !in selected }.map { "book:${it.bookId}" }
    val updated = state.copy(
      series = if (selected.isEmpty()) {
        state.series.filterNot { it.id == seriesId }
      } else {
        state.series.filterNot { it.id == seriesId } + LibrarySeries(seriesId, trimmed)
      },
      placements = state.placements.map { placement ->
        when {
          placement.bookId in selected -> placement.copy(
            shelfId = destination,
            seriesId = seriesId,
            seriesPosition = selected.indexOf(placement.bookId),
          )
          placement.seriesId == seriesId -> placement.copy(seriesId = null, seriesPosition = null)
          else -> placement
        }
      },
    ).withoutEmptySeries()
    val keys = updated.placements.filter { it.shelfId == destination }.map { it.itemId }.toSet()
    val ordered = oldOrder.filter { it in keys && it != itemId && it !in removed }.toMutableList()
    ordered.addAll(insertion.coerceAtMost(ordered.size), (if (selected.isEmpty()) emptyList() else listOf(itemId)) + removed)
    updated.withOrder(destination, ordered)
  }

  override suspend fun removeFromSeries(bookId: BookId): LibraryChange = edit { state ->
    val placement = state.placements.single { it.bookId == bookId.value }
    if (placement.seriesId == null) return@edit state
    val updated = state.copy(
      placements = state.placements.map {
        if (it.bookId == bookId.value) it.copy(seriesId = null, seriesPosition = null) else it
      },
    ).withoutEmptySeries()
    val keys = state.itemOrder(placement.shelfId).toMutableList()
    keys.add(keys.indexOf(placement.itemId) + 1, "book:${bookId.value}")
    updated.withOrder(placement.shelfId, keys)
  }

  override suspend fun undo(change: LibraryChange): Boolean = db.transaction {
    val current = dao.get()
    fun <T, K> restore(
      rows: List<T>,
      before: List<T>,
      after: List<T>,
      key: (T) -> K,
    ): List<T>? {
      val old = before.associateBy(key)
      val saved = after.associateBy(key)
      val live = rows.associateBy(key)
      val changed = (old.keys + saved.keys).filter { old[it] != saved[it] }.toSet()
      if (changed.any { live[it] != saved[it] }) return null
      return rows.filterNot { key(it) in changed } + changed.mapNotNull(old::get)
    }
    val restored = LibraryOrganisation(
      restore(current.shelves, change.before.shelves, change.after.shelves) { it.id } ?: return@transaction false,
      restore(current.series, change.before.series, change.after.series) { it.id } ?: return@transaction false,
      restore(current.placements, change.before.placements, change.after.placements) { it.bookId } ?: return@transaction false,
    )
    // A later edit may have put another item on a newly created shelf; do not orphan it on undo.
    if (runCatching { restored.validate() }.isFailure) return@transaction false
    dao.replace(current, restored)
    true
  }
}

private fun LibraryOrganisation.shelfName(
  name: String,
  except: String? = null,
): String {
  val trimmed = name.trim()
  if (trimmed.isEmpty()) throw LibraryEditException(LibraryEditError.EMPTY_NAME)
  if (shelves.any {
      it.id != except && it.name.equals(trimmed, ignoreCase = true)
    }
  ) {
    throw LibraryEditException(LibraryEditError.DUPLICATE_SHELF)
  }
  return trimmed
}

private fun LibraryOrganisation.withOrder(
  shelf: String,
  keys: List<String>,
): LibraryOrganisation {
  val positions = keys.withIndex().associate { it.value to it.index }
  return copy(placements = placements.map { if (it.shelfId == shelf) it.copy(position = positions[it.itemId] ?: it.position) else it })
}

private fun LibraryOrganisation.moveItems(
  items: Set<String>,
  destination: String,
): LibraryOrganisation {
  require(shelves.any { it.id == destination })
  val moved = shelves.filterNot { it.id == destination }.sortedBy { it.position }
    .flatMap { itemOrder(it.id) }.filter { it in items }.distinct()
  val ordered = itemOrder(destination) + moved
  return copy(
    placements = placements.map {
      if (it.itemId in
        items
      ) {
        it.copy(shelfId = destination)
      } else {
        it
      }
    },
  ).withOrder(destination, ordered)
}

private fun LibraryOrganisation.withoutEmptySeries(): LibraryOrganisation = copy(
  series = series.filter { group ->
    placements.any {
      it.seriesId ==
        group.id
    }
  },
)
