package voice.features.upNext

import io.kotest.matchers.shouldBe
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
}
