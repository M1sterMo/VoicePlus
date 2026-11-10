package voice.features.upNext

import android.app.Application
import androidx.datastore.core.DataStore
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.data.BookId

class UpNextTest {

  private val a = BookId("a")
  private val b = BookId("b")
  private val c = BookId("c")
  private val d = BookId("d")

  @Test
  fun `dragging down puts the book after its target`() {
    listOf(a, b, c, d).moved(a, c) shouldBe listOf(b, c, a, d)
  }

  @Test
  fun `dragging up puts the book before its target`() {
    listOf(a, b, c, d).moved(d, b) shouldBe listOf(a, d, b, c)
  }

  @Test
  fun `moving relative to a book that is not queued changes nothing`() {
    listOf(a, b).moved(a, c) shouldBe listOf(a, b)
  }

  @Test
  fun `a queued book leaves the queue when it becomes the current book`() = runTest {
    val current = MemoryDataStore<BookId?>(null)
    val queue = MemoryDataStore(listOf(a, b, c))
    val scope = TestScope(testScheduler)
    LeaveUpNextWhenCurrent(current, queue, scope).onAppStart(mockk<Application>())
    runCurrent()

    current.updateData { b }
    runCurrent()

    queue.data.first() shouldBe listOf(a, c)
    scope.cancel()
  }
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {
  private val value = MutableStateFlow(initial)
  override val data: Flow<T> get() = value
  override suspend fun updateData(transform: suspend (t: T) -> T): T = transform(value.value).also { value.value = it }
}
