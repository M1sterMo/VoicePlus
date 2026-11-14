package voice.features.upNext

import androidx.datastore.core.DataStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository

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
  fun `an open book that is still queued keeps its place and is not shown as now playing`() = runTest {
    val books = listOf(a, b, c).map { id -> mockk<Book> { every { this@mockk.id } returns id } }
    val repo = mockk<BookRepository> { every { flow() } returns flowOf(books) }

    val opened = UpNextViewModel(MemoryDataStore(listOf(a, b)), MemoryDataStore(b), repo).state.first()
    opened.nowPlaying shouldBe null
    opened.queue.map { it.id } shouldBe listOf(a, b)

    val playing = UpNextViewModel(MemoryDataStore(listOf(a, b)), MemoryDataStore(c), repo).state.first()
    playing.nowPlaying?.id shouldBe c
  }
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {
  private val value = MutableStateFlow(initial)
  override val data: Flow<T> get() = value
  override suspend fun updateData(transform: suspend (t: T) -> T): T = transform(value.value).also { value.value = it }
}
