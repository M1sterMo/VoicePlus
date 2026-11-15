package voice.features.bookOverview.bottomSheet

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.launch
import voice.core.common.RetainedViewModel
import voice.core.data.BookId
import voice.features.bookOverview.di.BookOverviewScope

@SingleIn(BookOverviewScope::class)
@Inject
class BottomSheetViewModel(private val viewModels: Set<@JvmSuppressWildcards BottomSheetItemViewModel>) : RetainedViewModel() {

  private val _state: MutableState<EditBookBottomSheetState> = mutableStateOf(EditBookBottomSheetState(emptyList()))
  internal val state: State<EditBookBottomSheetState> get() = _state
  var bookId: BookId? = null
    private set

  /** Returns once the items are ready, so the sheet never opens on the previous book's items. */
  internal suspend fun bookSelected(bookId: BookId) {
    val items = viewModels.flatMap { it.items(bookId) }.toSet().sorted()
    this.bookId = bookId
    _state.value = EditBookBottomSheetState(items)
  }

  internal fun onItemClick(item: BottomSheetItem) {
    val bookId = bookId ?: return
    scope.launch {
      viewModels.forEach {
        it.onItemClick(bookId, item)
      }
    }
  }
}
