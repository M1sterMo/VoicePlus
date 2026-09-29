package voice.features.chapterEditor

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ChapterNameOverride
import voice.core.data.MarkData
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.navigation.Navigator
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ChapterEditorViewModelTest {

  private val bookId = BookId("content://books/1")
  private val chapterId = ChapterId("content://chapters/1")

  private fun chapter(
    id: ChapterId = chapterId,
    durationMs: Long = 5.minutesMs(),
    marks: List<MarkData> = listOf(MarkData(startMs = 0L, name = "Chapter 1")),
  ): Chapter {
    return Chapter(
      id = id,
      name = "name",
      duration = durationMs,
      fileLastModified = Instant.EPOCH,
      markData = marks,
    )
  }

  private fun book(
    chapters: List<Chapter> = listOf(chapter()),
    offset: Int = 0,
    positionInChapter: Long = 0L,
    currentChapterIndex: Int = 0,
  ): Book {
    return Book(
      content = BookContent(
        id = bookId,
        playbackSpeed = 1F,
        skipSilence = false,
        isActive = true,
        lastPlayedAt = Instant.EPOCH,
        author = null,
        name = "TestBook",
        addedAt = Instant.EPOCH,
        chapters = chapters.map { it.id },
        currentChapter = chapters[currentChapterIndex].id,
        positionInChapter = positionInChapter,
        cover = null,
        gain = 0F,
        genre = null,
        narrator = null,
        series = null,
        part = null,
        chapterNameOffset = offset,
      ),
      chapters = chapters,
    )
  }

  private fun viewModel(
    book: Book,
    overrides: List<ChapterNameOverride> = emptyList(),
    overrideRepo: ChapterNameOverrideRepo = mockk(relaxed = true) {
      every { overridesForBook(any()) } returns MutableStateFlow(overrides)
    },
    playerController: PlayerController = mockk {
      every { livePlaybackStateFlow(any()) } returns MutableStateFlow<LivePlaybackState?>(null)
    },
    bookRepository: BookRepository = mockk(relaxed = true) {
      every { flow(book.id) } returns MutableStateFlow(book)
      coEvery { get(book.id) } returns book
    },
    navigator: Navigator = mockk(relaxed = true),
  ): ChapterEditorViewModel {
    return ChapterEditorViewModel(
      bookRepository = bookRepository,
      overrideRepo = overrideRepo,
      playerController = playerController,
      navigator = navigator,
      dispatcherProvider = DispatcherProvider(
        main = kotlinx.coroutines.Dispatchers.Unconfined,
        io = kotlinx.coroutines.Dispatchers.Unconfined,
        mainImmediate = kotlinx.coroutines.Dispatchers.Unconfined,
      ),
      bookId = book.id,
    )
  }

  @Test
  fun `initial state shows chapters with stored offset`() = runTest {
    val chapter = chapter(
      marks = listOf(
        MarkData(startMs = 0L, name = "Chapter 3"),
        MarkData(startMs = 2.minutesMs(), name = "Chapter 4"),
      ),
    )
    val vm = viewModel(book(chapters = listOf(chapter), offset = 2))

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val state = awaitNonNull()
      assertEquals(2, state.offset)
      assertEquals("Chapter 5", state.chapters[0].displayName)
      assertEquals("Chapter 6", state.chapters[1].displayName)
    }
  }

  @Test
  fun `incrementing offset updates display names live`() = runTest {
    val vm = viewModel(book(offset = 0))

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val initial = awaitNonNull()
      assertEquals(0, initial.offset)
      assertEquals("Chapter 1", initial.chapters[0].displayName)

      vm.onOffsetIncrement()

      val updated = awaitNonNull()
      assertEquals(1, updated.offset)
      assertEquals("Chapter 2", updated.chapters[0].displayName)
    }
  }

  @Test
  fun `later correction preserves prior names and applies new offset from current chapter`() = runTest {
    val chapter = chapter(
      durationMs = 4.minutesMs(),
      marks = listOf(
        MarkData(startMs = 0L, name = "Chapter 10"),
        MarkData(startMs = 60_000L, name = "Chapter 11"),
        MarkData(startMs = 120_000L, name = "Chapter 12"),
        MarkData(startMs = 180_000L, name = "Chapter 13"),
      ),
    )
    val bookFlow = MutableStateFlow(book(chapters = listOf(chapter), offset = -2, positionInChapter = 130_000L))
    val overrideFlow = MutableStateFlow<List<ChapterNameOverride>>(emptyList())
    val overrideRepo = mockk<ChapterNameOverrideRepo> {
      every { overridesForBook(bookId) } returns overrideFlow
      coEvery { delete(any(), any()) } answers {
        val id = firstArg<ChapterId>().value
        val start = secondArg<Long>()
        overrideFlow.value = overrideFlow.value.filterNot { it.chapterId == id && it.markStartMs == start }
      }
      coEvery { set(any(), any(), any(), any()) } answers {
        overrideFlow.value += ChapterNameOverride(
          chapterId = firstArg<ChapterId>().value,
          markStartMs = secondArg(),
          bookId = thirdArg<BookId>().value,
          name = arg(3),
        )
      }
    }
    val bookRepository = mockk<BookRepository> {
      every { flow(bookId) } returns bookFlow
      coEvery { updateBook(bookId, any()) } answers {
        val update = secondArg<(BookContent) -> BookContent>()
        bookFlow.value = bookFlow.value.copy(content = update(bookFlow.value.content))
      }
    }
    val vm = viewModel(bookFlow.value, overrideRepo = overrideRepo, bookRepository = bookRepository)

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitNonNull()
      while (state.currentChapterIndex != 2) state = awaitNonNull()
      assertEquals(listOf("Chapter 8", "Chapter 9", "Chapter 10", "Chapter 11"), state.chapters.map { it.displayName })

      vm.onOffsetSet(-9)

      state = awaitNonNull()
      while (state.offset != -9 || state.chapters.take(2).any { !it.hasOverride }) state = awaitNonNull()
      assertEquals(listOf("Chapter 8", "Chapter 9", "Chapter 3", "Chapter 4"), state.chapters.map { it.displayName })
      assertEquals(listOf(0L, 60_000L), overrideFlow.value.map { it.markStartMs })
      assertFalse(state.chapters[2].hasOverride)
      assertFalse(state.chapters[3].hasOverride)

      // Restoring one preserved name must remain effective when adjusting the offset again.
      vm.onDeleteOverride(state.chapters[0])
      state = awaitNonNull()
      while (state.chapters[0].hasOverride) state = awaitNonNull()
      vm.onOffsetSet(-8)
      state = awaitNonNull()
      while (state.offset != -8) state = awaitNonNull()
      assertEquals(listOf("Chapter 2", "Chapter 9", "Chapter 4", "Chapter 5"), state.chapters.map { it.displayName })
      assertFalse(state.chapters[0].hasOverride)
      coVerify(exactly = 1) { overrideRepo.set(chapterId, 0L, bookId, "Chapter 8") }
    }
  }

  @Test
  fun `first correction remains global and creates no frozen names`() = runTest {
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(bookId) } returns MutableStateFlow(emptyList())
    }
    val chapter = chapter(
      durationMs = 3.minutesMs(),
      marks = listOf(
        MarkData(startMs = 0L, name = "Chapter 10"),
        MarkData(startMs = 60_000L, name = "Chapter 11"),
        MarkData(startMs = 120_000L, name = "Chapter 12"),
      ),
    )
    val vm = viewModel(
      book = book(chapters = listOf(chapter), offset = 0, positionInChapter = 130_000L),
      overrideRepo = overrideRepo,
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitNonNull()
      while (state.currentChapterIndex != 2) state = awaitNonNull()

      vm.onOffsetSet(-2)
      state = awaitNonNull()
      while (state.offset != -2) state = awaitNonNull()

      assertEquals(listOf("Chapter 8", "Chapter 9", "Chapter 10"), state.chapters.map { it.displayName })
      coVerify(exactly = 0) { overrideRepo.set(any(), any(), any(), any()) }
    }
  }

  @Test
  fun `later correction does not replace manual names`() = runTest {
    val chapter = chapter(
      durationMs = 3.minutesMs(),
      marks = listOf(
        MarkData(startMs = 0L, name = "Chapter 10"),
        MarkData(startMs = 60_000L, name = "Chapter 11"),
        MarkData(startMs = 120_000L, name = "Chapter 12"),
      ),
    )
    val manual = ChapterNameOverride(chapterId.value, 0L, bookId.value, "Prologue")
    val overrideFlow = MutableStateFlow(listOf(manual))
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(bookId) } returns overrideFlow
    }
    val vm = viewModel(
      book = book(chapters = listOf(chapter), offset = 0, positionInChapter = 130_000L),
      overrides = listOf(manual),
      overrideRepo = overrideRepo,
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitNonNull()
      while (state.currentChapterIndex != 2 || !state.chapters[0].hasOverride) state = awaitNonNull()

      vm.onOffsetSet(-9)
      state = awaitNonNull()
      while (state.offset != -9) state = awaitNonNull()

      assertEquals("Prologue", state.chapters[0].displayName)
      coVerify(exactly = 0) { overrideRepo.set(chapterId, 0L, bookId, any()) }
      coVerify { overrideRepo.set(chapterId, 60_000L, bookId, "Chapter 11") }
    }
  }

  @Test
  fun `override takes precedence over offset`() = runTest {
    val override = ChapterNameOverride(
      chapterId = chapterId.value,
      markStartMs = 0L,
      bookId = bookId.value,
      name = "Intro",
    )
    val vm = viewModel(book(offset = 5), overrides = listOf(override))

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitNonNull()
      // override flow may emit its initial empty value before the real value; drain until present
      while (!state.chapters[0].hasOverride) {
        state = awaitNonNull()
      }
      assertEquals("Intro", state.chapters[0].displayName)
      assertTrue(state.chapters[0].hasOverride)
    }
  }

  @Test
  fun `reset all clears offset and overrides`() = runTest {
    val chapter = chapter(marks = listOf(MarkData(startMs = 0L, name = "Chapter 3")))
    val bookFlow = MutableStateFlow(book(chapters = listOf(chapter), offset = 2))
    val overrideFlow = MutableStateFlow(
      listOf(
        ChapterNameOverride(
          chapterId = chapterId.value,
          markStartMs = 0L,
          bookId = bookId.value,
          name = "Custom",
        ),
      ),
    )
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(any()) } returns overrideFlow
      coEvery { deleteAll(any()) } answers { overrideFlow.value = emptyList() }
    }
    val bookRepository = mockk<BookRepository>(relaxed = true) {
      every { flow(bookId) } returns bookFlow
      coEvery { get(bookId) } returns bookFlow.value
      coEvery { updateBook(any(), any()) } answers {
        val update = secondArg<(BookContent) -> BookContent>()
        bookFlow.value = bookFlow.value.copy(content = update(bookFlow.value.content))
      }
    }
    val vm = viewModel(bookFlow.value, overrideRepo = overrideRepo, bookRepository = bookRepository)

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      val initial = awaitNonNull()
      assertEquals(2, initial.offset)

      vm.onResetAllClick()
      vm.onResetAllConfirm()

      var state = awaitNonNull()
      // drain until offset reaches 0 and override cleared
      while (state.offset != 0 || state.chapters[0].hasOverride) {
        state = awaitNonNull()
      }
      assertEquals(0, state.offset)
      assertFalse(state.chapters[0].hasOverride)
      assertEquals("Chapter 3", state.chapters[0].displayName)
    }
  }

  @Test
  fun `current chapter index is correctly identified`() = runTest {
    val chapter = chapter(
      durationMs = 3.minutesMs(),
      marks = listOf(
        MarkData(startMs = 0L, name = "Chapter 1"),
        MarkData(startMs = 60_000L, name = "Chapter 2"),
        MarkData(startMs = 120_000L, name = "Chapter 3"),
      ),
    )
    val vm = viewModel(
      book(chapters = listOf(chapter), positionInChapter = 70_000L),
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitNonNull()
      while (state.currentChapterIndex != 1) state = awaitNonNull()
      assertEquals(1, state.currentChapterIndex)
      assertTrue(state.chapters[1].isCurrent)
    }
  }

  @Test
  fun `current chapter follows live playback when persisted position is stale`() = runTest {
    val chapter = chapter(
      durationMs = 3.minutesMs(),
      marks = listOf(
        MarkData(startMs = 0L, name = "Chapter 1"),
        MarkData(startMs = 60_000L, name = "Chapter 2"),
      ),
    )
    val playerController = mockk<PlayerController> {
      every { livePlaybackStateFlow(bookId) } returns MutableStateFlow(
        LivePlaybackState(
          bookId = bookId,
          chapterId = chapterId,
          positionMs = 70_000L,
          isPlaying = true,
          playbackSpeed = 1F,
        ),
      )
    }
    val vm = viewModel(
      book = book(chapters = listOf(chapter), positionInChapter = 0L),
      playerController = playerController,
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      var state = awaitNonNull()
      while (state.currentChapterIndex != 1) state = awaitNonNull()
      assertEquals(1, state.currentChapterIndex)
      assertTrue(state.chapters[1].isCurrent)
    }
  }

  @Test
  fun `offset buttons saturate at integer bounds`() = runTest {
    val vm = viewModel(book())

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { vm.viewState() }.test {
      assertEquals(0, awaitNonNull().offset)

      vm.onOffsetSet(Int.MAX_VALUE)
      vm.onOffsetIncrement()
      var state = awaitNonNull()
      while (state.offset != Int.MAX_VALUE) state = awaitNonNull()

      vm.onOffsetSet(Int.MIN_VALUE)
      vm.onOffsetDecrement()
      state = awaitNonNull()
      while (state.offset != Int.MIN_VALUE) state = awaitNonNull()
    }
  }

  @Test
  fun `override writes use the exact mark key and trim the name`() = runTest {
    val overrideRepo = mockk<ChapterNameOverrideRepo>(relaxed = true) {
      every { overridesForBook(any()) } returns MutableStateFlow(emptyList())
    }
    val vm = viewModel(book(), overrideRepo = overrideRepo)
    val item = ChapterItemState(
      chapterId = chapterId,
      markStartMs = 12_345L,
      displayNumber = 1,
      displayName = "Chapter 1",
      hasOverride = false,
      isCurrent = true,
    )

    vm.onEditConfirm(item, "  A new name  ")
    vm.onDeleteOverride(item)

    coVerify { overrideRepo.set(chapterId, 12_345L, bookId, "A new name") }
    coVerify { overrideRepo.delete(chapterId, 12_345L) }
  }
}

private fun Int.minutesMs(): Long = this * 60_000L

private suspend fun app.cash.turbine.ReceiveTurbine<ChapterEditorViewState?>.awaitNonNull(): ChapterEditorViewState {
  while (true) {
    val item = awaitItem()
    if (item != null) return item
  }
}
