package voice.features.bookOverview.shelves

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.data.BookId

class LibraryShelfDragStateTest {
  @Test fun `dropping an entry on another shelf moves every book in the entry`() = runTest {
    val books = setOf(BookId("one"), BookId("two"))
    var dropped: Pair<Set<BookId>, String>? = null
    var haptics = 0
    val drag = LibraryShelfDragState(this, { ids, shelf -> dropped = (ids to shelf) }) { haptics++ }
    drag.registerShelf("first", Rect(0f, 0f, 100f, 50f))
    drag.registerShelf("second", Rect(0f, 100f, 100f, 150f))

    drag.start("series:one", books, "first", Offset(50f, 25f))
    drag.drag(Offset(0f, 100f))
    drag.hoveredShelf shouldBe "second"
    drag.drop()

    dropped shouldBe (books to "second")
    drag.dragged shouldBe null
    drag.hoveredShelf shouldBe null
    haptics shouldBe 1
  }

  @Test fun `the source shelf is never treated as a drop target`() = runTest {
    val drag = LibraryShelfDragState(this, { _, _ -> error("Source shelf accepted a drop") }) {}
    drag.registerShelf("first", Rect(0f, 0f, 100f, 50f))

    drag.start("book:one", setOf(BookId("one")), "first", Offset(50f, 25f))
    drag.hoveredShelf shouldBe null
  }

  @Test fun `dropping on a visible book moves into that books shelf`() = runTest {
    val book = BookId("one")
    var destination: String? = null
    val drag = LibraryShelfDragState(this, { _, shelf -> destination = shelf }) {}
    drag.registerShelf("first", Rect(0f, 0f, 100f, 50f))
    drag.registerEntry("second-book", "second", "book:two", Rect(0f, 100f, 100f, 200f))

    drag.start("book:one", setOf(book), "first", Offset(50f, 25f))
    drag.drag(Offset(0f, 125f))
    drag.hoveredShelf shouldBe "second"
    drag.drop()

    destination shouldBe "second"
  }

  @Test fun `dropping on another entry of a manually ordered shelf takes its place`() = runTest {
    var reordered: Triple<String, String, String>? = null
    val drag = LibraryShelfDragState(
      scope = this,
      onDrop = { _, _ -> error("Reorder became a move") },
      canReorder = { it == "first" },
      onReorder = { shelf, item, target -> reordered = Triple(shelf, item, target) },
    ) {}
    drag.registerEntry("one", "first", "book:one", Rect(0f, 0f, 100f, 50f))
    drag.registerEntry("two", "first", "book:two", Rect(0f, 60f, 100f, 110f))

    drag.start("book:one", setOf(BookId("one")), "first", Offset(50f, 25f))
    drag.drag(Offset(0f, 60f))
    drag.hoveredEntry shouldBe "book:two"
    drag.drop()

    reordered shouldBe Triple("first", "book:one", "book:two")
  }

  @Test fun `a sorted shelf offers no place to reorder into`() = runTest {
    val drag = LibraryShelfDragState(this, { _, _ -> }, canReorder = { false }) {}
    drag.registerEntry("one", "first", "book:one", Rect(0f, 0f, 100f, 50f))
    drag.registerEntry("two", "first", "book:two", Rect(0f, 60f, 100f, 110f))

    drag.start("book:one", setOf(BookId("one")), "first", Offset(50f, 25f))
    drag.drag(Offset(0f, 60f))
    drag.hoveredEntry shouldBe null
  }
}
