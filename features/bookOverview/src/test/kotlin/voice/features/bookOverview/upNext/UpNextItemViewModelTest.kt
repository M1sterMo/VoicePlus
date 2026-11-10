package voice.features.bookOverview.upNext

import androidx.datastore.core.DataStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.data.BookId
import voice.features.bookOverview.bottomSheet.BottomSheetItem

class UpNextItemViewModelTest {

  private val current = BookId("current")
  private val queued = BookId("queued")
  private val other = BookId("other")
  private val queue = MemoryDataStore(listOf(queued))
  private val viewModel = UpNextItemViewModel(queue, MemoryDataStore(current))

  @Test
  fun `a book is added to the end of the queue`() = runTest {
    viewModel.items(other) shouldBe listOf(BottomSheetItem.AddToUpNext)
    viewModel.onItemClick(other, BottomSheetItem.AddToUpNext)
    queue.data.first() shouldBe listOf(queued, other)
  }

  @Test
  fun `a queued book can be removed`() = runTest {
    viewModel.items(queued) shouldBe listOf(BottomSheetItem.RemoveFromUpNext)
    viewModel.onItemClick(queued, BottomSheetItem.RemoveFromUpNext)
    queue.data.first() shouldBe emptyList()
  }

  @Test
  fun `the current book cannot be queued`() = runTest {
    viewModel.items(current) shouldBe emptyList()
  }
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {
  private val value = MutableStateFlow(initial)
  override val data: Flow<T> get() = value
  override suspend fun updateData(transform: suspend (t: T) -> T): T = transform(value.value).also { value.value = it }
}
