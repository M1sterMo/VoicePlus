package voice.features.bookOverview.series

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
import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.LibraryOrganisation
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.LibraryOrganisationRepo
import voice.core.logging.api.Logger
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.shelves.ShelfNotice
import voice.features.bookOverview.shelves.libraryErrorMessage
import voice.core.strings.R as StringsR

internal data class SeriesEditorState(
  val originalName: String?,
  val name: String,
  val books: List<BookContent>,
  val selected: List<BookId>,
  val suggestions: List<String>,
  val customOrder: Boolean = false,
  val saving: Boolean = false,
  val failed: Boolean = false,
  val seriesId: String? = null,
  val organisation: LibraryOrganisation = LibraryOrganisation(),
  val shelfId: String? = null,
  val error: Int? = null,
) {
  val destination: String? get() = shelfId
    ?: organisation.placements.filter { BookId(it.bookId) in selected }.map { it.shelfId }.distinct().singleOrNull()
  val canSave: Boolean get() = name.isNotBlank() && selected.isNotEmpty() && !saving && destination != null
  val selectedBooks: List<BookContent> get() {
    val byId = books.associateBy { it.id }
    return selected.mapNotNull(byId::get)
  }
}

private val seriesNumberOrder = compareBy<BookContent, String?>(nullsLast(NaturalOrderComparator.stringComparator)) {
  it.part?.takeIf(String::isNotBlank)
}
  .thenBy(NaturalOrderComparator.stringComparator) { it.name }
  .thenBy { it.id.value }

private val seriesBookOrder = compareBy<BookContent, Int?>(nullsLast()) { it.seriesOrder }.then(seriesNumberOrder)

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(scope = BookOverviewScope::class, binding = binding<BottomSheetItemViewModel>())
class SeriesViewModel(
  private val contentRepo: BookContentRepo,
  private val organisationRepo: LibraryOrganisationRepo,
) : RetainedViewModel(),
  BottomSheetItemViewModel {
  internal var state: SeriesEditorState? by mutableStateOf(null)
    private set
  private val noticeChannel = Channel<ShelfNotice>(Channel.BUFFERED)
  internal val notices = noticeChannel.receiveAsFlow()

  override suspend fun items(bookId: BookId): List<BottomSheetItem> = listOf(
    if (organisationRepo.get().placements.find { it.bookId == bookId.value }?.seriesId ==
      null
    ) {
      BottomSheetItem.Series
    } else {
      BottomSheetItem.ManageSeries
    },
  )

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (item != BottomSheetItem.Series && item != BottomSheetItem.ManageSeries) return
    val organisation = organisationRepo.get()
    val placements = organisation.placements.associateBy { it.bookId }
    val series = organisation.series.associateBy { it.id }
    val groupId = placements[bookId.value]?.seriesId
    val books = contentRepo.all()
      .map { content ->
        content.copy(
          seriesGroup = placements[content.id.value]?.seriesId?.let {
            series[it]?.name
          },
          seriesOrder = placements[content.id.value]?.seriesPosition,
        )
      }
      .sortedWith(compareBy<BookContent, String>(NaturalOrderComparator.stringComparator) { it.name }.thenBy { it.id.value })
    val book = books.find { it.id == bookId } ?: return
    val group = book.seriesGroup
    state = SeriesEditorState(
      originalName = group,
      name = group ?: book.series.orEmpty().trim(),
      books = books,
      selected = books.filter { it.id == bookId || (group != null && it.seriesGroup == group) }
        .sortedWith(seriesBookOrder).map { it.id },
      suggestions = (listOfNotNull(book.series) + books.mapNotNull { it.seriesGroup } + books.mapNotNull { it.series })
        .map(String::trim).filter(String::isNotEmpty).distinct(),
      customOrder = books.any { group != null && it.seriesGroup == group && it.seriesOrder != null },
      seriesId = groupId,
      organisation = organisation,
      shelfId = placements[bookId.value]?.shelfId.takeIf { groupId != null },
    )
  }

  internal fun create(ids: List<BookId>) {
    val first = ids.firstOrNull() ?: return
    scope.launch {
      onItemClick(first, BottomSheetItem.Series)
      state = state?.copy(originalName = null, seriesId = null, name = "", selected = ids, shelfId = null, customOrder = true)
    }
  }

  internal fun selectShelf(id: String) {
    state = state?.takeUnless { it.saving }?.copy(shelfId = id)
  }

  internal fun edit(bookId: BookId) {
    scope.launch { onItemClick(bookId, BottomSheetItem.Series) }
  }

  internal fun dismiss() {
    if (state?.saving != true) state = null
  }

  internal fun nameChanged(name: String) {
    state = state?.takeUnless { it.saving }?.copy(name = name, failed = false, error = null) ?: state
  }

  internal fun selectName(name: String) {
    val current = state?.takeUnless { it.saving } ?: return
    state = current.copy(
      name = name,
      selected = (current.books.filter { it.seriesGroup == name }.sortedWith(seriesBookOrder).map { it.id } + current.selected).distinct(),
      customOrder = true,
      failed = false,
      originalName = current.organisation.series.find { it.name == name }?.name ?: current.originalName,
      seriesId = current.organisation.series.find { it.name == name }?.id ?: current.seriesId,
      shelfId =
      current.organisation.series.find {
        it.name == name
      }?.id?.let { id -> current.organisation.placements.find { it.seriesId == id }?.shelfId }
        ?: current.shelfId,
    )
  }

  internal fun toggle(bookId: BookId) {
    val current = state?.takeUnless { it.saving } ?: return
    if (current.books.none { it.id == bookId }) return
    val selected = if (bookId in current.selected) current.selected - bookId else current.selected + bookId
    state = current.copy(
      selected = if (current.customOrder) {
        selected
      } else {
        current.books.filter {
          it.id in selected
        }.sortedWith(seriesNumberOrder).map { it.id }
      },
      failed = false,
    )
  }

  internal fun move(
    bookId: BookId,
    to: Int,
  ) {
    val current = state?.takeUnless { it.saving } ?: return
    val from = current.selected.indexOf(bookId)
    if (from == -1 || to !in current.selected.indices || from == to) return
    state = current.copy(selected = current.selected.toMutableList().apply { add(to, removeAt(from)) }, customOrder = true, failed = false)
  }

  internal fun save(ungroup: Boolean = false) {
    val current = state ?: return
    if (current.saving || (ungroup && current.originalName == null) || (!ungroup && !current.canSave)) return
    state = current.copy(saving = true, failed = false)
    scope.launch {
      try {
        // Reuse an existing name even when the user types different casing.
        val name = current.books.mapNotNull { it.seriesGroup }
          .firstOrNull { it.equals(current.name.trim(), ignoreCase = true) } ?: current.name.trim()
        val change = organisationRepo.saveSeries(
          current.seriesId,
          if (ungroup) current.originalName!! else name,
          if (ungroup) emptyList() else current.selected,
          current.destination,
        )
        state = null
        noticeChannel.send(
          ShelfNotice(if (ungroup) StringsR.string.library_series_ungrouped else StringsR.string.library_series_saved, change),
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Logger.w("Could not save series group: $e")
        state = current.copy(failed = true, error = e.libraryErrorMessage())
      }
    }
  }
}
