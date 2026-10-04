package voice.features.bookOverview.editBook

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.data.BookId
import voice.core.scanner.BookEditDraftStore
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.coroutines.cancellation.CancellationException
import voice.core.strings.R as StringsR

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(scope = BookOverviewScope::class, binding = binding<BottomSheetItemViewModel>())
class EditBookViewModel(
  private val drafts: BookEditDraftStore,
  private val navigator: Navigator,
) : RetainedViewModel(),
  BottomSheetItemViewModel {
  internal val state = drafts.draft
  internal var busy by mutableStateOf(false)
    private set
  internal var error by mutableStateOf<Int?>(null)
    private set

  override suspend fun items(bookId: BookId): List<BottomSheetItem> = listOf(BottomSheetItem.EditBook)

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (item == BottomSheetItem.EditBook) {
      error = null
      drafts.begin(bookId)
    }
  }

  internal fun titleChanged(title: String) {
    if (!busy) drafts.titleChanged(title)
  }
  internal fun dismiss() {
    if (!busy) scope.launch { drafts.discard() }
  }
  internal fun save() = perform { drafts.save() }
  internal fun embeddedCover() = perform(StringsR.string.edit_book_no_embedded_cover) { drafts.useEmbeddedCover() }

  internal fun searchCover() {
    val draft = state.value ?: return
    navigator.goTo(Destination.CoverFromInternet(draft.bookId, draft.token))
  }

  internal fun pickedCover(uri: Uri) {
    val draft = state.value ?: return
    navigator.goTo(Destination.EditCover(draft.bookId, uri, draft.token))
  }

  private fun perform(
    failure: Int = StringsR.string.library_save_failed,
    action: suspend () -> Boolean,
  ) {
    if (busy) return
    busy = true
    error = null
    scope.launch {
      try {
        if (!action()) error = failure
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        error = failure
      } finally {
        busy = false
      }
    }
  }
}
