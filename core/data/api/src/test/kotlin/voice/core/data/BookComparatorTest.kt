package voice.core.data

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.File

class BookComparatorTest {

  private val b1 = book(name = "A", lastPlayedAtMillis = 0, addedAtMillis = 2)
  private val b2 = book(name = "B", lastPlayedAtMillis = 0, addedAtMillis = 2)
  private val b3 = book(name = "B", lastPlayedAtMillis = 2, addedAtMillis = 1)
  private val b4 = book(name = "D", lastPlayedAtMillis = 5, addedAtMillis = 7)
  private val b5 = book(name = "C", lastPlayedAtMillis = 5, addedAtMillis = 6)
  private val books = listOf(b1, b2, b3, b4, b5)

  @Test
  fun `cover updates and input order cannot reorder tied books`() {
    val a = b1.copy(content = b1.content.copy(id = BookId("a")))
    val b = b1.copy(content = b1.content.copy(id = BookId("b")))
    BookComparator.entries.forEach { comparator ->
      val originals = listOf(a, b)
      val afterCoverSave = listOf(b, a.copy(content = a.content.copy(cover = File("new-cover.png"))))
      afterCoverSave.sortedWith(comparator).map { it.id } shouldBe originals.sortedWith(comparator).map { it.id }
    }
  }

  @Test
  fun byLastPlayed() {
    val sorted = books.sortedWith(BookComparator.ByLastPlayed)
    sorted.shouldContainExactly(b5, b4, b3, b1, b2)
  }

  @Test
  fun byName() {
    val sorted = books.sortedWith(BookComparator.ByName)
    sorted.shouldContainExactly(b1, *listOf(b2, b3).sortedBy { it.id.value }.toTypedArray(), b5, b4)
  }
}
