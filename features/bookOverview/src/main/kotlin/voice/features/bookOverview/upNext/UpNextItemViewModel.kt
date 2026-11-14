package voice.features.bookOverview.upNext

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.store.UpNextStore
import voice.core.playback.PlayerController
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
class UpNextItemViewModel(
  @UpNextStore
  private val upNextStore: DataStore<List<BookId>>,
  private val playerController: PlayerController,
) : BottomSheetItemViewModel {

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    // Queueing the book that is playing would only replay it. A book that is merely open can be queued.
    if (playerController.currentPlaybackSnapshot() == (bookId to true)) return emptyList()
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
