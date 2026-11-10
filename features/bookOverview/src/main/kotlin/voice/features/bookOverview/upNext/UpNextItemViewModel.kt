package voice.features.bookOverview.upNext

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.UpNextStore
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
class UpNextItemViewModel(
  @UpNextStore
  private val upNextStore: DataStore<List<BookId>>,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
) : BottomSheetItemViewModel {

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    // The current book is the one playing; queueing it would only replay it.
    if (bookId == currentBookStore.data.first()) return emptyList()
    return listOf(if (bookId in upNextStore.data.first()) BottomSheetItem.RemoveFromUpNext else BottomSheetItem.AddToUpNext)
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    when (item) {
      BottomSheetItem.AddToUpNext -> upNextStore.updateData { queue -> if (bookId in queue) queue else queue + bookId }
      BottomSheetItem.RemoveFromUpNext -> upNextStore.updateData { it - bookId }
      else -> Unit
    }
  }
}
