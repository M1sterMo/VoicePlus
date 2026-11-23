package voice.features.bookOverview.about

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.repo.LibraryOrganisationRepo
import voice.core.scanner.BookEditDraftStore
import voice.core.ui.AboutBookState
import voice.core.ui.aboutBookState
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope

/** Opens "About this book" from a book's long-press menu. */
@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(scope = BookOverviewScope::class, binding = binding<BottomSheetItemViewModel>())
class AboutBookViewModel(
  private val repo: BookRepository,
  private val organisation: LibraryOrganisationRepo,
  private val drafts: BookEditDraftStore,
) : RetainedViewModel(),
  BottomSheetItemViewModel {

  private var bookId by mutableStateOf<BookId?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  internal val state: Flow<AboutBookState?> = snapshotFlow { bookId }
    .flatMapLatest { id -> id?.let { aboutBookState(repo, organisation, it) } ?: flowOf(null) }

  override suspend fun items(bookId: BookId): List<BottomSheetItem> = listOf(BottomSheetItem.AboutBook)

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (item == BottomSheetItem.AboutBook) this.bookId = bookId
  }

  internal fun dismiss() {
    bookId = null
  }

  internal fun rate(rating: Int?) {
    val id = bookId ?: return
    // A rating given (or cleared) here also settles the end-of-book question.
    scope.launch { repo.updateBook(id) { it.copy(rating = rating, ratingPrompted = true) } }
  }

  /** Edit book replaces the sheet; the synopsis typed there shows here next time. */
  internal fun edit() {
    val id = bookId ?: return
    bookId = null
    scope.launch { drafts.begin(id) }
  }
}
