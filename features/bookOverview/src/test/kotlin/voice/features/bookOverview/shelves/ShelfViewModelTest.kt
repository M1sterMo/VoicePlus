package voice.features.bookOverview.shelves

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import voice.core.data.BookId
import voice.core.data.LibraryChange
import voice.core.data.LibraryOrganisation
import voice.core.data.LibraryPlacement
import voice.core.data.LibrarySeries
import voice.core.data.repo.LibraryOrganisationRepo
import voice.features.bookOverview.views.BookOverviewScrollState

class ShelfViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repo = mockk<LibraryOrganisationRepo>(relaxed = true)
  private val browsing = BookOverviewScrollState()
  private val model = ShelfViewModel(repo, browsing)

  @Before fun setup() {
    Dispatchers.setMain(dispatcher)
  }

  @After fun cleanup() {
    model.onRetired()
    Dispatchers.resetMain()
  }

  @Test fun `cancel discards draft without writing and done commits only once`() = runTest(dispatcher) {
    model.beginOrder("shelf", listOf("a", "b", "c"))
    model.moveDraft("a", 2)
    model.draft!!.ids shouldBe listOf("b", "c", "a")
    model.dismiss()
    coVerify(exactly = 0) { repo.reorder(any(), any()) }
    model.beginOrder("shelf", listOf("a", "b", "c"))
    model.moveDraft("b", 0)
    model.saveOrder()
    model.saveOrder()
    model.moveDraft("c", 0)
    model.dismiss()
    advanceUntilIdle()
    coVerify(exactly = 1) { repo.reorder("shelf", listOf("b", "a", "c")) }
    model.draft shouldBe null
    model.busy shouldBe false
  }

  @Test fun `save failure keeps draft for retry and releases busy state`() = runTest(dispatcher) {
    coEvery { repo.reorderShelves(any()) } throws IllegalStateException("storage unavailable")
    model.beginOrder(null, listOf("a", "b"))
    model.moveDraft("b", 0)
    model.saveOrder()
    advanceUntilIdle()
    model.draft!!.ids shouldBe listOf("b", "a")
    model.busy shouldBe false
    (model.error != null) shouldBe true
    coEvery { repo.reorderShelves(any()) } returns LibraryChange(LibraryOrganisation(), LibraryOrganisation())
    model.saveOrder()
    advanceUntilIdle()
    model.draft shouldBe null
    model.error shouldBe null
  }

  @Test fun `selection counts hidden series members and toggles the whole stack`() = runTest(dispatcher) {
    val state = LibraryOrganisation(
      series = listOf(LibrarySeries("s", "Series")),
      placements = listOf(
        LibraryPlacement("visible", position = 0, seriesId = "s", seriesPosition = 0),
        LibraryPlacement("hidden", position = 0, seriesId = "s", seriesPosition = 1),
      ),
    )
    model.beginSelection()
    model.toggle(setOf(BookId("visible")), state)
    model.selected shouldBe setOf(BookId("visible"), BookId("hidden"))
    model.toggle(setOf(BookId("visible")), state)
    model.selected shouldBe emptySet()
  }

  @Test fun `filter survives returning from a book through a new library view model`() = runTest(dispatcher) {
    model.filter = ShelfFilter.COMPLETED
    val returned = ShelfViewModel(repo, browsing)
    try {
      returned.filter shouldBe ShelfFilter.COMPLETED
    } finally {
      returned.onRetired()
    }
  }

  @Test fun `organise mode resets filters and remains active across repeated moves`() = runTest(dispatcher) {
    val books = setOf(BookId("one"), BookId("two"))
    val change = LibraryChange(LibraryOrganisation(), LibraryOrganisation())
    coEvery { repo.move(books, "later") } returns change
    model.filter = ShelfFilter.COMPLETED

    model.beginOrganising()
    model.organising shouldBe true
    model.filter shouldBe ShelfFilter.ALL
    model.chooseShelf(books)
    model.moveTo("later")
    advanceUntilIdle()

    coVerify(exactly = 1) { repo.move(books, "later") }
    model.organising shouldBe true
    model.dialog shouldBe null
    model.finishOrganising()
    model.organising shouldBe false
  }
}
