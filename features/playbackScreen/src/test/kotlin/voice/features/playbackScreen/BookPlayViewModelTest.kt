package voice.features.playbackScreen

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ListeningEventType
import voice.core.data.MarkData
import voice.core.data.PlaybackToolbarAction
import voice.core.data.repo.BookCharacterRepo
import voice.core.data.repo.BookmarkRepo
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.playback.CurrentBookResolver
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.misc.Decibel
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerMode.TimedWithDuration
import voice.core.sleeptimer.SleepTimerState
import voice.features.sleepTimer.SleepTimerViewState
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BookPlayViewModelTest {

  private val scope = TestScope()
  private val sleepTimerDataStore = MemoryDataStore(SleepTimerPreference.Default.copy(duration = 5.minutes))
  private val book = book()
  private val currentBookStoreId = MemoryDataStore<BookId?>(book.id)
  private val globalVolumeGainStore = MemoryDataStore<Float?>(null)
  private val sleepTimer = mockk<SleepTimer> {
    val stateFlow = MutableStateFlow<SleepTimerState>(SleepTimerState.Disabled)
    every {
      state
    } returns stateFlow
    every {
      enable(any())
    } answers {
      stateFlow.value = when (val mode = firstArg<SleepTimerMode>()) {
        is TimedWithDuration -> SleepTimerState.Enabled.WithDuration(mode.duration)
        SleepTimerMode.TimedWithDefault -> SleepTimerState.Enabled.WithDuration(runBlocking { sleepTimerDataStore.data.first() }.duration)
        is SleepTimerMode.EndOfChapter -> SleepTimerState.Enabled.WithEndOfChapter(mode.chapters)
      }
    }
    every {
      disable()
    } answers {
      stateFlow.value = SleepTimerState.Disabled
    }
  }

  private val player = mockk<PlayerController> {
    every { pauseIfCurrentBookDifferentFrom(any()) } just Runs
    every { setGain(any(), any()) } returns Job().apply { complete() }
    coEvery { awaitGainChanges() } just Runs
  }
  private val playStateManager = mockk<PlayStateManager> {
    every { flow } returns MutableStateFlow(PlayStateManager.PlayState.Paused)
  }
  private val currentBookResolver = mockk<CurrentBookResolver> {
    coEvery { book(book.id) } returns book
  }
  private val bookmarkRepository = mockk<BookmarkRepo>(relaxed = true)
  private val viewModel = BookPlayViewModel(
    bookRepository = mockk {
      coEvery { get(book.id) } returns book
      every { flow(book.id) } returns MutableStateFlow(book)
    },
    currentBookResolver = currentBookResolver,
    player = player,
    sleepTimer = sleepTimer,
    playStateManager = playStateManager,
    currentBookStoreId = currentBookStoreId,
    navigator = mockk(),
    bookmarkRepository = bookmarkRepository,
    characterRepo = mockk<BookCharacterRepo> {
      every { characterCount(any()) } returns flowOf(0)
    },
    chapterNameOverrideRepo = mockk {
      every { overridesForBook(any()) } returns flowOf(emptyList())
    },
    volumeGainFormatter = VolumeGainFormatter(),
    batteryOptimization = mockk(),
    sleepTimerPreferenceStore = sleepTimerDataStore,
    toolbarActionsStore = MemoryDataStore(PlaybackToolbarAction.DEFAULT),
    globalVolumeGainStore = globalVolumeGainStore,
    bookId = book.id,
    dispatcherProvider = DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
  )

  @Test
  fun `volume boost sends global mode and restores saved values across books`() = scope.runTest {
    viewModel.onVolumeGainIconClick()
    advanceUntilIdle()
    viewModel.dialogState.value.shouldBeInstanceOf<BookPlayDialogViewState.VolumeGainDialog>().remember shouldBe false
    val otherBook = book().let { it.copy(content = it.content.copy(gain = 2F)) }
    coEvery { currentBookResolver.book(otherBook.id) } returns otherBook
    for ((gain, remember) in listOf(3F to false, 3F to true, 6F to true, 0F to true, 0F to false)) {
      viewModel.onVolumeGainChanged(Decibel(gain), remember)
      viewModel.onVolumeGainChanged(Decibel(gain)) // Slider changes must keep the checkbox state.
      advanceUntilIdle()
      val dialog = viewModel.dialogState.value.shouldBeInstanceOf<BookPlayDialogViewState.VolumeGainDialog>()
      dialog.gain shouldBe Decibel(gain)
      dialog.remember shouldBe remember
      verify(exactly = 2) { player.setGain(Decibel(gain), remember) }

      globalVolumeGainStore.updateData { if (remember) gain else null } // Saving is owned by the real player.
      val reopened = viewModel(book = otherBook)
      reopened.onVolumeGainIconClick()
      advanceUntilIdle()
      val restored = reopened.dialogState.value.shouldBeInstanceOf<BookPlayDialogViewState.VolumeGainDialog>()
      restored.gain shouldBe Decibel(if (remember) gain else 2F)
      restored.remember shouldBe remember
    }
  }

  @Test
  fun `same and new playback screens wait for saves still in controller transport`() = scope.runTest {
    val save = Job()
    every { player.setGain(Decibel(6F), true) } returns save
    coEvery { player.awaitGainChanges() } coAnswers { save.join() }
    viewModel.onVolumeGainIconClick()
    advanceUntilIdle()
    viewModel.onVolumeGainChanged(Decibel(6F), remember = true)
    viewModel.dismissDialog()
    val screens = listOf(viewModel, viewModel())
    screens.forEach { it.onVolumeGainIconClick() }
    runCurrent()
    screens.forEach { it.dialogState.value shouldBe null }

    globalVolumeGainStore.updateData { 6F }
    save.complete()
    advanceUntilIdle()
    screens.forEach {
      val dialog = it.dialogState.value.shouldBeInstanceOf<BookPlayDialogViewState.VolumeGainDialog>()
      dialog.gain shouldBe Decibel(6F)
      dialog.remember shouldBe true
    }
  }

  @Test
  fun `a new playback screen waits for already queued global saves`() = scope.runTest {
    val releaseSave = CompletableDeferred<Unit>()
    val save = backgroundScope.launch {
      globalVolumeGainStore.updateData {
        releaseSave.await()
        6F
      }
    }
    runCurrent()
    val reopened = viewModel()
    reopened.onVolumeGainIconClick()
    runCurrent()
    reopened.dialogState.value shouldBe null

    releaseSave.complete(Unit)
    save.join()
    advanceUntilIdle()
    val dialog = reopened.dialogState.value.shouldBeInstanceOf<BookPlayDialogViewState.VolumeGainDialog>()
    dialog.gain shouldBe Decibel(6F)
    dialog.remember shouldBe true
  }

  @Test
  fun sleepTimerValueChanging() = scope.runTest {
    fun assertDialogSleepTime(expected: Int) {
      viewModel.dialogState.value shouldBe BookPlayDialogViewState.SleepTimer(SleepTimerViewState(expected))
    }

    viewModel.toggleSleepTimer()
    yield()
    assertDialogSleepTime(5)

    suspend fun incrementAndAssert(time: Int) {
      viewModel.incrementSleepTime()
      yield()
      assertDialogSleepTime(time)
    }

    suspend fun decrementAndAssert(time: Int) {
      viewModel.decrementSleepTime()
      yield()
      assertDialogSleepTime(time)
    }

    decrementAndAssert(4)
    decrementAndAssert(3)
    decrementAndAssert(2)
    decrementAndAssert(1)

    decrementAndAssert(1)

    incrementAndAssert(2)
    incrementAndAssert(3)
  }

  @Test
  fun sleepTimerSettingFixedValue() = scope.runTest {
    viewModel.toggleSleepTimer()
    viewModel.onAcceptSleepTime(10)
    sleepTimerDataStore.data.first().duration shouldBe 5.minutes
    yield()
    verify(exactly = 1) {
      sleepTimer.enable(TimedWithDuration(10.minutes))
    }
    coVerify(exactly = 0) {
      bookmarkRepository.addBookmarkAtBookPosition(any(), any(), setBySleepTimer = true)
    }
  }

  @Test
  fun deactivateSleepTimer() = scope.runTest {
    viewModel.toggleSleepTimer()
    viewModel.onAcceptSleepTime(10)
    viewModel.toggleSleepTimer()
    yield()
    verifyOrder {
      sleepTimer.enable(TimedWithDuration(10.minutes))
      sleepTimer.disable()
    }
    sleepTimer.state.value.shouldBeInstanceOf<SleepTimerState.Disabled>()
  }

  @Test
  fun onCurrentChapterClickShowsDialogWithCorrectState() = scope.runTest {
    viewModel.onCurrentChapterClick()
    yield()

    val dialogState = viewModel.dialogState.value
      .shouldBeInstanceOf<BookPlayDialogViewState.SelectChapterDialog>()

    dialogState.items.shouldContainExactly(
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 1,
        name = "Chapter Start",
        active = false,
        time = "0:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 2,
        name = "Middle Section",
        active = false,
        time = "2:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 3,
        name = "Final Section",
        active = false,
        time = "4:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 4,
        name = "Chapter Start",
        active = false,
        time = "5:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 5,
        name = "Middle Section",
        active = true,
        time = "7:00",
      ),
      BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
        number = 6,
        name = "Final Section",
        active = false,
        time = "9:00",
      ),
    )
  }

  @Test
  fun onChapterClickSetsPositionAndDismissesDialog() = scope.runTest {
    every { player.setPosition(any(), any(), any()) } just Runs

    viewModel.onCurrentChapterClick()
    yield()

    viewModel.dialogState.value.shouldBeInstanceOf<BookPlayDialogViewState.SelectChapterDialog>()

    viewModel.onChapterClick(number = 2)
    yield()

    // Verify player.setPosition was called with correct parameters
    // The second mark starts at 2 minutes position in the first chapter
    verify(exactly = 1) {
      player.setPosition(time = 2.minutes.inWholeMilliseconds, id = book.chapters.first().id, tag = ListeningEventType.GoToChapter)
    }

    viewModel.dialogState.value shouldBe null
  }

  @Test
  fun `scrubbing uses the live resolved chapter mark`() = scope.runTest {
    val liveBook = book.copy(
      content = book.content.copy(positionInChapter = 4.5.minutes.inWholeMilliseconds),
    )
    coEvery { currentBookResolver.book(book.id) } returns liveBook
    every { player.setPosition(any(), any(), any()) } just Runs

    viewModel.seekTo(30.seconds)
    yield()

    verify(exactly = 1) {
      player.setPosition(
        time = 4.5.minutes.inWholeMilliseconds,
        id = liveBook.currentChapter.id,
        tag = null,
      )
    }
  }

  @Test
  fun `overlay prefers live controller position`() {
    val persistedBook = book()
    val overlaidBook = persistedBook.overlay(
      LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = persistedBook.chapters.first().id,
        positionMs = 1.minutes.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      ),
    )

    overlaidBook.currentChapter.id shouldBe persistedBook.chapters.first().id
    overlaidBook.content.positionInChapter shouldBe 1.minutes.inWholeMilliseconds
  }

  @Test
  fun `viewState updates chapter name and played time from live playback state`() = scope.runTest {
    val persistedBook = book()
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(
      LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = persistedBook.currentChapter.id,
        positionMs = persistedBook.content.positionInChapter,
        isPlaying = false,
        playbackSpeed = 1F,
      ),
    )
    val viewModel = viewModel(
      book = persistedBook,
      livePlaybackFlow = livePlaybackFlow,
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      awaitItem()!!.apply {
        chapterName shouldBe "Middle Section"
        playedTime shouldBe 30.seconds
      }

      livePlaybackFlow.value = LivePlaybackState(
        bookId = persistedBook.id,
        chapterId = persistedBook.chapters.first().id,
        positionMs = 4.minutes.inWholeMilliseconds + 45.seconds.inWholeMilliseconds,
        isPlaying = true,
        playbackSpeed = 1F,
      )

      val state = awaitItem()!!
      state.playing shouldBe true
      state.chapterName shouldBe "Final Section"
      state.playedTime shouldBe 45.seconds
    }
  }

  @Test
  fun `viewState falls back to manager play state when live playback is unavailable`() = scope.runTest {
    val viewModel = viewModel(
      livePlaybackFlow = MutableStateFlow(null),
      playStateFlow = MutableStateFlow(PlayStateManager.PlayState.Playing),
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem() shouldBe null
      val state = awaitItem()!!
      state.playing shouldBe true
      state.playedTime shouldBe 30.seconds
    }
  }

  @Test
  fun `edit chapter names is visible for a multi-file book without embedded marks`() = scope.runTest {
    // Common case: one mp3 per chapter, no embedded chapter marks. markData is empty for every
    // chapter, but the editor still lists one synthesized mark per file, so the entry must show.
    val c1 = chapter().copy(markData = emptyList())
    val c2 = chapter().copy(markData = emptyList())
    val base = book()
    val multiFile = base.copy(
      content = base.content.copy(chapters = listOf(c1.id, c2.id), currentChapter = c1.id),
      chapters = listOf(c1, c2),
    )
    val viewModel = viewModel(book = multiFile)
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      var state = awaitItem()
      while (state == null) state = awaitItem()
      state.editChapterNamesVisible shouldBe true
    }
  }

  @Test
  fun `pinning a toolbar action persists it and stops at the cap`() = scope.runTest {
    val store = MemoryDataStore(PlaybackToolbarAction.DEFAULT)
    val viewModel = viewModel(toolbarActionsStore = store)

    viewModel.setToolbarAction(PlaybackToolbarAction.LISTENING_LOG, pinned = true)
    yield()
    store.data.first() shouldBe PlaybackToolbarAction.DEFAULT + PlaybackToolbarAction.LISTENING_LOG

    // Fifth pin is refused; four is all a compact app bar can take.
    viewModel.setToolbarAction(PlaybackToolbarAction.CHAPTER_FIX, pinned = true)
    yield()
    store.data.first() shouldBe PlaybackToolbarAction.DEFAULT + PlaybackToolbarAction.LISTENING_LOG

    viewModel.setToolbarAction(PlaybackToolbarAction.PLAYBACK_SPEED, pinned = false)
    yield()
    store.data.first() shouldBe setOf(
      PlaybackToolbarAction.BOOKMARKS,
      PlaybackToolbarAction.CHARACTER_LIST,
      PlaybackToolbarAction.LISTENING_LOG,
    )
  }

  @Test
  fun `customize toolbar opens its dialog and the view state carries the pinned set`() = scope.runTest {
    val store = MemoryDataStore(setOf(PlaybackToolbarAction.BOOKMARKS))
    val viewModel = viewModel(toolbarActionsStore = store)

    viewModel.onCustomizeToolbarClick()
    viewModel.dialogState.value shouldBe BookPlayDialogViewState.ToolbarActions

    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state == null) state = awaitItem()
      state.toolbarActions shouldBe setOf(PlaybackToolbarAction.BOOKMARKS)
    }
  }

  private fun viewModel(
    book: Book = this.book,
    livePlaybackFlow: MutableStateFlow<LivePlaybackState?> = MutableStateFlow(null),
    playStateFlow: MutableStateFlow<PlayStateManager.PlayState> = MutableStateFlow(PlayStateManager.PlayState.Paused),
    toolbarActionsStore: MemoryDataStore<Set<PlaybackToolbarAction>> = MemoryDataStore(PlaybackToolbarAction.DEFAULT),
  ): BookPlayViewModel {
    return BookPlayViewModel(
      bookRepository = mockk {
        coEvery { get(book.id) } returns book
        every { flow(book.id) } returns MutableStateFlow(book)
      },
      currentBookResolver = currentBookResolver,
      player = player.also {
        every { it.livePlaybackStateFlow(book.id) } returns livePlaybackFlow
      },
      sleepTimer = sleepTimer,
      playStateManager = mockk {
        every { flow } returns playStateFlow
        every { playState } returns playStateFlow.value
      },
      currentBookStoreId = MemoryDataStore(book.id),
      navigator = mockk(),
      bookmarkRepository = mockk(),
      characterRepo = mockk {
        every { characterCount(any()) } returns flowOf(0)
      },
      chapterNameOverrideRepo = mockk {
        every { overridesForBook(any()) } returns flowOf(emptyList())
      },
      volumeGainFormatter = VolumeGainFormatter(),
      batteryOptimization = mockk(),
      sleepTimerPreferenceStore = sleepTimerDataStore,
      toolbarActionsStore = toolbarActionsStore,
      globalVolumeGainStore = globalVolumeGainStore,
      bookId = book.id,
      dispatcherProvider = DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
    )
  }
}

private fun book(
  name: String = "TestBook",
  lastPlayedAtMillis: Long = 0L,
  addedAtMillis: Long = 0L,
): Book {
  val chapters = listOf(
    chapter(),
    chapter(),
  )
  return Book(
    content = BookContent(
      author = UUID.randomUUID().toString(),
      name = name,
      positionInChapter = 2.5.minutes.inWholeMilliseconds,
      playbackSpeed = 1F,
      addedAt = Instant.ofEpochMilli(addedAtMillis),
      chapters = chapters.map { it.id },
      cover = null,
      currentChapter = chapters[1].id,
      isActive = true,
      lastPlayedAt = Instant.ofEpochMilli(lastPlayedAtMillis),
      skipSilence = false,
      id = BookId(UUID.randomUUID().toString()),
      gain = 0F,
      genre = null,
      narrator = null,
      series = null,
      part = null,
    ),
    chapters = chapters,
  )
}

private fun chapter(): Chapter {
  return Chapter(
    id = ChapterId("http://${UUID.randomUUID()}"),
    duration = 5.minutes.inWholeMilliseconds,
    fileLastModified = Instant.EPOCH,
    markData = listOf(
      MarkData(startMs = 0L, name = "Chapter Start"),
      MarkData(startMs = 2.minutes.inWholeMilliseconds, name = "Middle Section"),
      MarkData(startMs = 4.minutes.inWholeMilliseconds, name = "Final Section"),
    ),
    name = "name",
  )
}
