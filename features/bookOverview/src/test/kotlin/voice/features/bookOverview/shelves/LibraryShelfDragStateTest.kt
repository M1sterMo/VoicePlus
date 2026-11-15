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

    drag.start("first:series:one", "series:one", books, "first", Offset(50f, 25f))
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

    drag.start("one", "book:one", setOf(BookId("one")), "first", Offset(50f, 25f))
    drag.hoveredShelf shouldBe null
  }

  @Test fun `Current and Up next neither take books from shelves nor send them to one`() = runTest {
    val drag = LibraryShelfDragState(this, { _, _ -> error("Dropped across a projection") }, canDrop = { it != UP_NEXT }) {}
    drag.registerShelf("first", Rect(0f, 0f, 100f, 50f))
    drag.registerEntry("queued", UP_NEXT, "book:two", Rect(0f, 100f, 100f, 200f))

    drag.start("one", "book:one", setOf(BookId("one")), "first", Offset(50f, 25f))
    drag.drag(Offset(0f, 125f))
    drag.hoveredShelf shouldBe null

    val fromQueue = LibraryShelfDragState(this, { _, _ -> error("Dropped across a projection") }, canDrop = { it != UP_NEXT }) {}
    fromQueue.registerShelf("first", Rect(0f, 0f, 100f, 50f))
    fromQueue.start("queued", "book:two", setOf(BookId("two")), UP_NEXT, Offset(50f, 150f))
    fromQueue.drag(Offset(0f, -125f))
    fromQueue.hoveredShelf shouldBe null
  }

  @Test fun `only the held tile lifts when its book also shows in another section`() = runTest {
    val drag = LibraryShelfDragState(this, { _, _ -> }, canReorder = { true }) {}
    drag.registerEntry("current:book:one", "current", "book:one", Rect(0f, 0f, 100f, 50f))
    drag.registerEntry("$UP_NEXT:book:one", UP_NEXT, "book:one", Rect(0f, 100f, 100f, 150f))

    drag.start("$UP_NEXT:book:one", "book:one", setOf(BookId("one")), UP_NEXT, Offset(50f, 125f))
    drag.dragged shouldBe "$UP_NEXT:book:one"
    drag.visualOffset("current:book:one") shouldBe Offset.Zero
  }

  @Test fun `dropping on a visible book moves into that books shelf`() = runTest {
    val book = BookId("one")
    var destination: String? = null
    val drag = LibraryShelfDragState(this, { _, shelf -> destination = shelf }) {}
    drag.registerShelf("first", Rect(0f, 0f, 100f, 50f))
    drag.registerEntry("second-book", "second", "book:two", Rect(0f, 100f, 100f, 200f))

    drag.start("one", "book:one", setOf(book), "first", Offset(50f, 25f))
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

    drag.start("one", "book:one", setOf(BookId("one")), "first", Offset(50f, 25f))
    drag.drag(Offset(0f, 60f))
    drag.hoveredEntry shouldBe "two"
    drag.drop()

    reordered shouldBe Triple("first", "book:one", "book:two")
  }

  @Test fun `a sorted shelf offers no place to reorder into`() = runTest {
    val drag = LibraryShelfDragState(this, { _, _ -> }, canReorder = { false }) {}
    drag.registerEntry("one", "first", "book:one", Rect(0f, 0f, 100f, 50f))
    drag.registerEntry("two", "first", "book:two", Rect(0f, 60f, 100f, 110f))

    drag.start("one", "book:one", setOf(BookId("one")), "first", Offset(50f, 25f))
    drag.drag(Offset(0f, 60f))
    drag.hoveredEntry shouldBe null
  }
}
