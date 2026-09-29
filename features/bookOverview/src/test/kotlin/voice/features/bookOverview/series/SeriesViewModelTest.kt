package voice.features.bookOverview.series

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
import voice.core.data.LibraryShelf
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.LibraryOrganisationRepo
import voice.features.bookOverview.book
import voice.features.bookOverview.bottomSheet.BottomSheetItem

class SeriesViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repo = mockk<BookContentRepo>(relaxed = true)
  private val organisation = mockk<LibraryOrganisationRepo>(relaxed = true)
  private val model = SeriesViewModel(repo, organisation)
  private val first = book(name = "One").let { it.copy(content = it.content.copy(series = "Chrysalis")) }
  private val second = book(name = "Two").let { it.copy(content = it.content.copy(seriesGroup = "Existing")) }

  @Before
  fun setup() {
    Dispatchers.setMain(dispatcher)
    coEvery { repo.all() } returns listOf(first.content, second.content)
    coEvery { organisation.get() } coAnswers {
      val contents = repo.all()
      LibraryOrganisation(
        series = contents.mapNotNull { it.seriesGroup }.distinct().map { LibrarySeries(it, it) },
        placements = contents.mapIndexed { index, book ->
          LibraryPlacement(book.id.value, position = index, seriesId = book.seriesGroup, seriesPosition = book.seriesOrder ?: index)
        },
      )
    }
  }

  @After
  fun cleanup() {
    model.onRetired()
    Dispatchers.resetMain()
  }

  @Test
  fun `metadata suggests a name but does not group books without confirmation`() = runTest(dispatcher) {
    model.onItemClick(first.id, BottomSheetItem.Series)
    model.state!!.name shouldBe "Chrysalis"
    model.state!!.selected shouldBe listOf(first.id)
    model.state!!.suggestions shouldBe listOf("Chrysalis", "Existing")
    model.dismiss()
    coVerify(exactly = 0) { organisation.saveSeries(any(), any(), any(), any()) }
  }

  @Test
  fun `choosing an existing group includes its members and typed casing reuses its name`() = runTest(dispatcher) {
    model.onItemClick(first.id, BottomSheetItem.Series)
    model.selectName("Existing")
    model.state!!.selected shouldBe listOf(second.id, first.id)
    model.nameChanged(" existing ")
    model.save()
    advanceUntilIdle()
    coVerify { organisation.saveSeries("Existing", "Existing", listOf(second.id, first.id), "other") }
    model.state shouldBe null
  }

  @Test
  fun `blank names or empty membership cannot be saved`() = runTest(dispatcher) {
    model.onItemClick(first.id, BottomSheetItem.Series)
    model.nameChanged(" ")
    model.state!!.canSave shouldBe false
    model.save()
    model.nameChanged("New")
    model.toggle(first.id)
    model.save()
    advanceUntilIdle()
    coVerify(exactly = 0) { organisation.saveSeries(any(), any(), any(), any()) }
  }

  @Test
  fun `ungroup only clears the group and never updates books or playback`() = runTest(dispatcher) {
    model.onItemClick(second.id, BottomSheetItem.Series)
    model.save(ungroup = true)
    advanceUntilIdle()
    coVerify { organisation.saveSeries("Existing", "Existing", emptyList(), "other") }
    coVerify(exactly = 0) { repo.put(any()) }
  }

  @Test
  fun `write failure keeps editable input for retry`() = runTest(dispatcher) {
    coEvery { organisation.saveSeries(any(), any(), any(), any()) } throws IllegalStateException("disk full")
    model.onItemClick(first.id, BottomSheetItem.Series)
    model.save()
    model.save()
    advanceUntilIdle()
    model.state!!.failed shouldBe true
    model.state!!.name shouldBe "Chrysalis"
    model.state!!.selected shouldBe listOf(first.id)
    model.state!!.canSave shouldBe true
    coVerify(exactly = 1) { organisation.saveSeries(any(), any(), any(), any()) }
  }

  @Test
  fun `editor opens in saved order and moving is only persisted on save`() = runTest(dispatcher) {
    val originals = listOf(first, second).mapIndexed { index, book ->
      book.copy(content = book.content.copy(seriesGroup = "Series", seriesOrder = 1 - index))
    }
    coEvery { repo.all() } returns originals.map { it.content }
    model.onItemClick(first.id, BottomSheetItem.ManageSeries)
    model.state!!.selected shouldBe listOf(second.id, first.id)
    model.move(first.id, 0)
    model.state!!.selected shouldBe listOf(first.id, second.id)
    coVerify(exactly = 0) { organisation.saveSeries(any(), any(), any(), any()) }
    model.save()
    // Ignore further edits while the write is in flight.
    model.move(second.id, 0)
    model.toggle(first.id)
    model.dismiss()
    advanceUntilIdle()
    coVerify(exactly = 1) { organisation.saveSeries("Series", "Series", listOf(first.id, second.id), "other") }
    model.state shouldBe null
  }

  @Test
  fun `new selections use natural part order until manually arranged then append new books`() = runTest(dispatcher) {
    val ten = first.copy(content = first.content.copy(part = "10", seriesGroup = null))
    val two = second.copy(content = second.content.copy(part = "2", seriesGroup = null))
    val extra = book(name = "Extra")
    coEvery { repo.all() } returns listOf(ten.content, two.content, extra.content)
    model.onItemClick(ten.id, BottomSheetItem.Series)
    model.toggle(two.id)
    model.state!!.selected shouldBe listOf(two.id, ten.id)
    model.move(ten.id, 0)
    model.toggle(extra.id)
    model.state!!.selected shouldBe listOf(ten.id, two.id, extra.id)
    model.toggle(two.id)
    model.state!!.selected shouldBe listOf(ten.id, extra.id)
    model.dismiss()
    coVerify(exactly = 0) { organisation.saveSeries(any(), any(), any(), any()) }
  }

  @Test
  fun `invalid moves and stale book ids do not change the draft`() = runTest(dispatcher) {
    model.onItemClick(first.id, BottomSheetItem.Series)
    val original = model.state
    model.move(first.id, -1)
    model.move(first.id, 1)
    model.move(BookId("missing"), 0)
    model.toggle(BookId("missing"))
    model.state shouldBe original
  }

  @Test
  fun `failed save preserves manually arranged order for retry`() = runTest(dispatcher) {
    model.onItemClick(first.id, BottomSheetItem.Series)
    model.toggle(second.id)
    model.move(second.id, 0)
    coEvery { organisation.saveSeries(any(), any(), any(), any()) } throws IllegalStateException("disk full")
    model.save()
    advanceUntilIdle()
    model.state!!.selected shouldBe listOf(second.id, first.id)
    model.state!!.failed shouldBe true
    coEvery { organisation.saveSeries(any(), any(), any(), any()) } returns LibraryChange(LibraryOrganisation(), LibraryOrganisation())
    model.save()
    advanceUntilIdle()
    coVerify(exactly = 2) { organisation.saveSeries(null, "Chrysalis", listOf(second.id, first.id), "other") }
    model.state shouldBe null
  }

  @Test fun `new groups spanning shelves require an explicit home and include selected hidden books`() = runTest(dispatcher) {
    coEvery { repo.all() } returns listOf(first.content, second.content.copy(isActive = false))
    coEvery { organisation.get() } returns LibraryOrganisation(
      shelves = listOf(LibraryShelf.Other, LibraryShelf("later", "Later", 0)),
      placements = listOf(LibraryPlacement(first.id.value, position = 0), LibraryPlacement(second.id.value, "later", 0)),
    )
    model.create(listOf(first.id, second.id))
    advanceUntilIdle()
    model.nameChanged("New series")
    model.state!!.selectedBooks.map { it.id } shouldBe listOf(first.id, second.id)
    model.state!!.canSave shouldBe false
    model.save()
    coVerify(exactly = 0) { organisation.saveSeries(any(), any(), any(), any()) }
    model.selectShelf("later")
    model.save()
    advanceUntilIdle()
    coVerify { organisation.saveSeries(null, "New series", listOf(first.id, second.id), "later") }
  }
}
