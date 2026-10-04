package voice.features.bookOverview.shelves

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.data.BookId
import voice.core.data.LibraryChange
import voice.core.data.LibraryOrganisation
import voice.core.data.ShelfSort
import voice.core.data.repo.LibraryOrganisationRepo
import voice.core.logging.api.Logger
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.views.BookOverviewScrollState
import voice.core.strings.R as StringsR

internal sealed interface ShelfDialog {
  data class Name(
    val id: String?,
    val name: String = "",
  ) : ShelfDialog
  data class Move(val books: Set<BookId>) : ShelfDialog
  data class Menu(val id: String) : ShelfDialog
  data class Sort(val id: String) : ShelfDialog
  data class Remove(val id: String) : ShelfDialog
  data object Filter : ShelfDialog
}

internal data class ShelfOrderDraft(
  val shelfId: String?,
  val ids: List<String>,
)
internal data class ShelfNotice(
  @StringRes val message: Int,
  val change: LibraryChange? = null,
)

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(scope = BookOverviewScope::class, binding = binding<BottomSheetItemViewModel>())
class ShelfViewModel(
  private val repo: LibraryOrganisationRepo,
  private val browseState: BookOverviewScrollState,
) : RetainedViewModel(),
  BottomSheetItemViewModel {
  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    val grouped = repo.get().placements.find { it.bookId == bookId.value }?.seriesId != null
    return if (grouped) listOf(BottomSheetItem.MoveSeriesToShelf, BottomSheetItem.RemoveFromSeries) else listOf(BottomSheetItem.MoveToShelf)
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    when (item) {
      BottomSheetItem.MoveToShelf, BottomSheetItem.MoveSeriesToShelf -> dialog = ShelfDialog.Move(setOf(bookId))
      BottomSheetItem.RemoveFromSeries -> removeFromSeries(bookId)
      else -> Unit
    }
  }
  internal var filter: ShelfFilter
    get() = browseState.filter
    set(value) {
      browseState.filter = value
    }
  internal var dialog: ShelfDialog? by mutableStateOf(null)
  internal var draft: ShelfOrderDraft? by mutableStateOf(null)
    private set
  internal var selecting by mutableStateOf(false)
  internal var organising by mutableStateOf(false)
    private set
  internal var selectionDestination: String? by mutableStateOf(null)
    private set
  internal var selected by mutableStateOf(emptySet<BookId>())
    private set
  internal var busy by mutableStateOf(false)
    private set
  internal var error: Int? by mutableStateOf(null)
    private set
  private val noticeChannel = Channel<ShelfNotice>(Channel.BUFFERED)
  internal val notices = noticeChannel.receiveAsFlow()

  internal fun dismiss() {
    if (busy) return
    dialog = null
    draft = null
    error = null
  }

  internal fun beginSelection() {
    organising = false
    selectionDestination = null
    selected = emptySet()
    selecting = true
  }

  internal fun beginOrganising() {
    if (busy) return
    cancelSelection()
    filter = ShelfFilter.ALL
    organising = true
  }

  internal fun finishOrganising() {
    if (!busy) organising = false
  }

  internal fun chooseShelf(books: Set<BookId>) {
    if (!busy) dialog = ShelfDialog.Move(books)
  }

  internal fun addToShelf(id: String) {
    beginSelection()
    dialog = null
    filter = ShelfFilter.ALL
    selectionDestination = id
  }

  internal fun moveSelection() {
    dialog = ShelfDialog.Move(selected)
    selectionDestination?.let(::moveTo)
  }

  internal fun cancelSelection() {
    if (!busy) {
      selecting = false
      selected = emptySet()
      selectionDestination = null
    }
  }

  internal fun toggle(
    ids: Set<BookId>,
    organisation: LibraryOrganisation,
  ) {
    val items = organisation.placements.filter { BookId(it.bookId) in ids }.map { it.itemId }.toSet()
    val affected = organisation.placements.filter { it.itemId in items }.map { BookId(it.bookId) }.toSet() + ids
    if (!busy) selected = if (selected.containsAll(affected)) selected - affected else selected + affected
  }

  internal fun beginOrder(
    shelfId: String?,
    ids: List<String>,
  ) {
    if (busy) return
    dialog = null
    draft = ShelfOrderDraft(shelfId, ids)
  }

  internal fun moveDraft(
    id: String,
    to: Int,
  ) {
    val current = draft ?: return
    val from = current.ids.indexOf(id)
    if (busy || from == -1 || to !in current.ids.indices || from == to) return
    draft = current.copy(ids = current.ids.toMutableList().apply { add(to, removeAt(from)) })
  }

  internal fun saveOrder() {
    val current = draft ?: return
    change(StringsR.string.library_order_saved) {
      if (current.shelfId == null) repo.reorderShelves(current.ids) else repo.reorder(current.shelfId, current.ids)
    }
  }

  internal fun nameChanged(name: String) {
    val current = dialog as? ShelfDialog.Name ?: return
    if (!busy) {
      dialog = current.copy(name = name)
      error = null
    }
  }

  internal fun saveName() {
    val current = dialog as? ShelfDialog.Name ?: return
    change(if (current.id == null) StringsR.string.library_shelf_created else StringsR.string.library_shelf_renamed) {
      if (current.id == null) repo.createShelf(current.name) else repo.renameShelf(current.id, current.name)
    }
  }

  internal fun moveTo(shelfId: String) {
    val move = dialog as? ShelfDialog.Move ?: return
    change(StringsR.string.library_moved) { repo.move(move.books, shelfId) }
  }

  internal fun dropOnShelf(
    books: Set<BookId>,
    shelfId: String,
  ) = change(StringsR.string.library_moved) { repo.move(books, shelfId) }

  internal fun removeShelf(id: String) = change(StringsR.string.library_shelf_removed) { repo.removeShelf(id) }
  internal fun sort(
    id: String,
    sort: ShelfSort,
  ) = change(StringsR.string.library_sort_updated) { repo.setSort(id, sort) }
  internal fun removeFromSeries(id: BookId) = change(StringsR.string.library_series_member_removed) { repo.removeFromSeries(id) }

  internal fun toggleExpanded(
    id: String,
    expanded: Boolean,
  ) {
    scope.launch {
      try {
        repo.setExpanded(id, !expanded)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        failed(e)
      }
    }
  }

  internal fun undo(change: LibraryChange) {
    if (busy) return
    busy = true
    scope.launch {
      try {
        if (!repo.undo(change)) noticeChannel.send(ShelfNotice(StringsR.string.library_undo_unavailable))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        failed(e)
      } finally {
        busy = false
      }
    }
  }

  private fun change(
    @StringRes message: Int,
    operation: suspend () -> LibraryChange,
  ) {
    if (busy) return
    busy = true
    error = null
    scope.launch {
      try {
        val change = operation()
        dialog = null
        draft = null
        selecting = false
        selected = emptySet()
        noticeChannel.send(ShelfNotice(message, change))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        failed(e)
      } finally {
        busy = false
      }
    }
  }

  private suspend fun failed(error: Exception) {
    Logger.w(error, "Could not save library organisation")
    this.error = error.libraryErrorMessage()
    noticeChannel.send(ShelfNotice(error.libraryErrorMessage()))
  }
}
