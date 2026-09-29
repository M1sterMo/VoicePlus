package voice.features.playbackScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.common.RetainedViewModel
import voice.core.common.resolveChapterName
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ListeningEventType
import voice.core.data.PlaybackToolbarAction
import voice.core.data.byMarkKey
import voice.core.data.durationMs
import voice.core.data.markForPosition
import voice.core.data.repo.BookCharacterRepo
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.GlobalVolumeGainStore
import voice.core.data.store.PlaybackToolbarActionsStore
import voice.core.data.store.SleepTimerPreferenceStore
import voice.core.logging.api.Logger
import voice.core.playback.CurrentBookResolver
import voice.core.playback.PlayerController
import voice.core.playback.misc.Decibel
import voice.core.playback.misc.VolumeGain
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerMode.TimedWithDuration
import voice.core.sleeptimer.SleepTimerState
import voice.core.ui.ImmutableFile
import voice.core.ui.formatTime
import voice.features.playbackScreen.batteryOptimization.BatteryOptimization
import voice.features.sleepTimer.SleepTimerViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@AssistedInject
class BookPlayViewModel(
  private val bookRepository: BookRepository,
  private val currentBookResolver: CurrentBookResolver,
  private val player: PlayerController,
  private val sleepTimer: SleepTimer,
  private val playStateManager: PlayStateManager,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
  private val navigator: Navigator,
  private val bookmarkRepository: BookmarkRepo,
  private val characterRepo: BookCharacterRepo,
  private val chapterNameOverrideRepo: ChapterNameOverrideRepo,
  private val volumeGainFormatter: VolumeGainFormatter,
  private val batteryOptimization: BatteryOptimization,
  dispatcherProvider: DispatcherProvider,
  @SleepTimerPreferenceStore
  private val sleepTimerPreferenceStore: DataStore<SleepTimerPreference>,
  @PlaybackToolbarActionsStore
  private val toolbarActionsStore: DataStore<Set<PlaybackToolbarAction>>,
  @GlobalVolumeGainStore
  private val globalVolumeGainStore: DataStore<Float?>,
  @Assisted
  private val bookId: BookId,
) : RetainedViewModel(MainScope(dispatcherProvider)) {

  private val _viewEffects = MutableSharedFlow<BookPlayViewEffect>(extraBufferCapacity = 1)
  internal val viewEffects: Flow<BookPlayViewEffect> get() = _viewEffects

  private val _dialogState = mutableStateOf<BookPlayDialogViewState?>(null)
  internal val dialogState: State<BookPlayDialogViewState?> get() = _dialogState

  init {
    scope.launch {
      player.pauseIfCurrentBookDifferentFrom(bookId)
      currentBookStoreId.updateData { bookId }
    }
  }

  @Composable
  fun viewState(): BookPlayViewState? {
    val persistedBook = remember(bookId) {
      bookRepository.flow(bookId).filterNotNull()
    }.collectAsState(initial = null).value ?: return null

    val livePlaybackState = remember(bookId) { player.livePlaybackStateFlow(bookId) }
      .collectAsState(null).value
    val managerPlayState by remember {
      playStateManager.flow
    }.collectAsState()

    val book = if (livePlaybackState != null) {
      persistedBook.overlay(livePlaybackState)
    } else {
      persistedBook
    }
    val isPlaying = livePlaybackState?.isPlaying ?: (managerPlayState == PlayStateManager.PlayState.Playing)

    val characterCount by remember { characterRepo.characterCount(bookId) }.collectAsState(initial = 0)
    // Held until the store answers so a customised toolbar never flashes the default set first.
    val toolbarActions = remember { toolbarActionsStore.data }.collectAsState(initial = null).value
      ?: return null

    val currentMark = book.currentChapter.markForPosition(book.content.positionInChapter)
    val positionInCurrentMark = if (isPlaying && currentMark.durationMs > 0) {
      val relativePosition = book.content.positionInChapter - currentMark.startMs
      relativePosition.coerceIn(0L, currentMark.durationMs)
    } else {
      book.content.positionInChapter - currentMark.startMs
    }

    val sleepTime = remember { sleepTimer.state }.collectAsState().value
    val hasMoreThanOneChapter = book.chapters.sumOf { it.chapterMarks.count() } > 1

    val overrides by remember(persistedBook.content.id) {
      chapterNameOverrideRepo.overridesForBook(persistedBook.content.id)
    }.collectAsState(emptyList())
    val overrideMap = overrides.byMarkKey()
    val offset = persistedBook.content.chapterNameOffset
    val currentOverride = overrideMap[Pair(book.currentChapter.id.value, currentMark.startMs)]
    val chapterName = resolveChapterName(currentMark.name ?: "", offset, currentOverride)

    return BookPlayViewState(
      sleepTimerState = sleepTime.toViewState(),
      playing = isPlaying,
      title = book.content.name,
      showPreviousNextButtons = hasMoreThanOneChapter,
      chapterName = chapterName.takeIf { hasMoreThanOneChapter },
      duration = currentMark.durationMs.milliseconds,
      playedTime = positionInCurrentMark.milliseconds,
      cover = book.content.cover?.let(::ImmutableFile),
      skipSilence = book.content.skipSilence,
      characterCount = characterCount,
      // Gate on chapter MARKS (what the editor actually lists), not raw markData — otherwise the
      // entry is hidden for the common one-file-per-chapter book whose files carry no embedded marks.
      editChapterNamesVisible = hasMoreThanOneChapter,
      toolbarActions = toolbarActions,
    )
  }

  fun onCustomizeToolbarClick() {
    _dialogState.value = BookPlayDialogViewState.ToolbarActions
  }

  fun setToolbarAction(
    action: PlaybackToolbarAction,
    pinned: Boolean,
  ) {
    scope.launch {
      toolbarActionsStore.updateData { current ->
        when {
          !pinned -> current - action
          current.size >= PlaybackToolbarAction.MAX_PINNED -> current
          else -> current + action
        }
      }
    }
  }

  fun dismissDialog() {
    Logger.d("dismissDialog")
    _dialogState.value = null
  }

  fun incrementSleepTime() {
    updateSleepTimeViewState {
      val customTime = it.customSleepTime
      // The +/- controls auto-repeat while held; without a cap a stuck long-press sets an
      // arbitrarily large timer (decrement already clamps at 1).
      val newTime = (customTime + 1).coerceAtMost(MAX_SLEEP_TIME_MINUTES)
      sleepTimerPreferenceStore.updateData { preference -> preference.copy(duration = newTime.minutes) }
      SleepTimerViewState(newTime, it.chapterCount)
    }
  }

  fun decrementSleepTime() {
    updateSleepTimeViewState {
      val customTime = it.customSleepTime
      val newTime = (customTime - 1).coerceAtLeast(1)
      sleepTimerPreferenceStore.updateData { preference ->
        preference.copy(duration = newTime.minutes)
      }
      SleepTimerViewState(newTime, it.chapterCount)
    }
  }

  fun incrementChapterCount() {
    updateSleepTimeViewState {
      val newCount = it.chapterCount + 1
      sleepTimerPreferenceStore.updateData { preference ->
        preference.copy(chaptersCount = newCount)
      }
      SleepTimerViewState(it.customSleepTime, newCount)
    }
  }

  fun decrementChapterCount() {
    updateSleepTimeViewState {
      val newCount = (it.chapterCount - 1).coerceAtLeast(1)
      sleepTimerPreferenceStore.updateData { preference ->
        preference.copy(chaptersCount = newCount)
      }
      SleepTimerViewState(it.customSleepTime, newCount)
    }
  }

  fun onAcceptSleepTime(time: Int) {
    updateSleepTimeViewState {
      sleepTimer.enable(TimedWithDuration(time.minutes))
      null
    }
  }

  fun onAcceptSleepAtEndOfChapter() {
    updateSleepTimeViewState {
      sleepTimer.enable(SleepTimerMode.EndOfChapter(it.chapterCount))
      null
    }
  }

  private fun updateSleepTimeViewState(update: suspend (SleepTimerViewState) -> SleepTimerViewState?) {
    scope.launch {
      val current = dialogState.value
      val updated: SleepTimerViewState? = if (current is BookPlayDialogViewState.SleepTimer) {
        update(current.viewState)
      } else {
        val pref = sleepTimerPreferenceStore.data.first()
        update(
          SleepTimerViewState(
            customSleepTime = pref.duration.inWholeMinutes.toInt(),
            chapterCount = pref.chaptersCount,
          ),
        )
      }
      _dialogState.value = updated?.let(BookPlayDialogViewState::SleepTimer)
    }
  }

  fun onPlaybackSpeedChanged(speed: Float) {
    _dialogState.value = BookPlayDialogViewState.SpeedDialog(speed)
    scope.launch {
      player.setSpeed(speed)
    }
  }

  fun onVolumeGainChanged(
    gain: Decibel,
    remember: Boolean = (_dialogState.value as? BookPlayDialogViewState.VolumeGainDialog)?.remember == true,
  ) {
    _dialogState.value = volumeGainDialogViewState(gain, remember)
    player.setGain(gain, remember)
  }

  fun next() {
    scope.launch {
      player.next()
    }
  }

  fun previous() {
    scope.launch {
      player.previous()
    }
  }

  fun playPause() {
    scope.launch {
      val snapshot = player.currentPlaybackSnapshot() ?: return@launch
      val (playingId, isPlaying) = snapshot
      val willPlay = !(playingId == bookId && isPlaying)
      if (willPlay) {
        if (batteryOptimization.shouldRequest()) {
          _viewEffects.tryEmit(BookPlayViewEffect.RequestIgnoreBatteryOptimization)
          batteryOptimization.onBatteryOptimizationsRequested()
        }
      }
      player.playPauseForBook(bookId)
    }
  }

  fun rewind() {
    scope.launch {
      player.rewind()
    }
  }

  fun fastForward() {
    scope.launch {
      player.fastForward()
    }
  }

  fun onCloseClick() {
    navigator.goBack()
  }

  fun onEditChapterNamesClick() {
    scope.launch {
      val book = currentBook() ?: return@launch
      navigator.goTo(Destination.ChapterEditor(book.content.id))
    }
  }

  fun onCurrentChapterClick() {
    scope.launch {
      val book = currentBook() ?: return@launch
      val offset = book.content.chapterNameOffset
      val overrideList = chapterNameOverrideRepo.overridesForBook(book.content.id).first()
      val overrideMap = overrideList.byMarkKey()
      _dialogState.value = BookPlayDialogViewState.SelectChapterDialog(
        items = book.chapters.flatMapIndexed { chapterIndex, chapter ->
          chapter.chapterMarks.mapIndexed { markIndex, chapterMark ->
            val previousChapters = book.chapters.take(chapterIndex)
            val override = overrideMap[Pair(chapter.id.value, chapterMark.startMs)]
            BookPlayDialogViewState.SelectChapterDialog.ItemViewState(
              number = previousChapters.sumOf { it.chapterMarks.count() } + markIndex + 1,
              name = resolveChapterName(chapterMark.name ?: "", offset, override),
              active = chapterMark == book.currentMark && chapter == book.currentChapter,
              time = formatTime(previousChapters.sumOf { it.duration } + chapterMark.startMs),
            )
          }
        },
      )
    }
  }

  fun onChapterClick(number: Int) {
    scope.launch {
      val book = currentBook() ?: return@launch
      var currentIndex = -1
      book.chapters.forEach { chapter ->
        chapter.chapterMarks.forEach { mark ->
          currentIndex++
          if (currentIndex == number - 1) {
            player.setPosition(mark.startMs, chapter.id, tag = ListeningEventType.GoToChapter)
            _dialogState.value = null
            return@launch
          }
        }
      }
    }
  }

  fun onPlaybackSpeedIconClick() {
    scope.launch {
      val playbackSpeed = currentBook()?.content?.playbackSpeed ?: return@launch
      _dialogState.value = BookPlayDialogViewState.SpeedDialog(playbackSpeed)
    }
  }

  fun onVolumeGainIconClick() {
    scope.launch {
      player.awaitGainChanges()
      // Wait behind queued saves, including those from another playback screen.
      val globalGain = globalVolumeGainStore.updateData { it }
      val content = currentBook()?.content ?: return@launch
      _dialogState.value = volumeGainDialogViewState(Decibel(globalGain ?: content.gain), globalGain != null)
    }
  }

  private fun volumeGainDialogViewState(
    gain: Decibel,
    remember: Boolean,
  ): BookPlayDialogViewState.VolumeGainDialog {
    return BookPlayDialogViewState.VolumeGainDialog(
      gain = gain,
      maxGain = VolumeGain.MAX_GAIN,
      valueFormatted = volumeGainFormatter.format(gain),
      remember = remember,
    )
  }

  fun onBookmarkClick() {
    navigator.goTo(Destination.Bookmarks(bookId))
  }

  fun onListeningLogClick() {
    navigator.goTo(Destination.ListeningLog(bookId))
  }

  fun onCharacterListClick() {
    navigator.goTo(Destination.CharacterList(bookId))
  }

  fun onBookmarkLongClick() {
    scope.launch {
      val book = currentBook() ?: return@launch
      bookmarkRepository.addBookmarkAtBookPosition(
        book = book,
        title = null,
        setBySleepTimer = false,
      )
      _viewEffects.tryEmit(BookPlayViewEffect.BookmarkAdded)
    }
  }

  fun seekTo(position: Duration) {
    scope.launch {
      val book = currentBook() ?: return@launch
      val currentChapter = book.currentChapter
      val currentMark = currentChapter.markForPosition(book.content.positionInChapter)
      player.setPosition(currentMark.startMs + position.inWholeMilliseconds, currentChapter.id)
    }
  }

  fun toggleSleepTimer() {
    scope.launch {
      Logger.d("toggleSleepTimer while active=${sleepTimer.state.value}")
      if (sleepTimer.state.value.enabled) {
        sleepTimer.disable()
        _dialogState.value = null
      } else {
        _dialogState.value = BookPlayDialogViewState.SleepTimer(
          viewState = SleepTimerViewState(
            customSleepTime = sleepTimerPreferenceStore.data.first().duration.inWholeMinutes.toInt(),
          ),
        )
      }
    }
  }

  fun onBatteryOptimizationRequested() {
    navigator.goTo(Destination.BatteryOptimization)
  }

  fun toggleSkipSilence() {
    scope.launch {
      val book = bookRepository.get(bookId) ?: return@launch
      val skipSilence = book.content.skipSilence
      player.skipSilence(!skipSilence)
    }
  }

  private suspend fun currentBook(): Book? {
    return currentBookResolver.book(bookId)
  }

  @AssistedFactory
  interface Factory {
    fun create(bookId: BookId): BookPlayViewModel
  }
}

/** 12 hours — longer than any plausible sleep timer, and short enough to bound a stuck long-press. */
private const val MAX_SLEEP_TIME_MINUTES = 12 * 60

private fun SleepTimerState.toViewState(): BookPlayViewState.SleepTimerViewState = when (this) {
  SleepTimerState.Disabled -> BookPlayViewState.SleepTimerViewState.Disabled
  is SleepTimerState.Enabled.WithDuration -> BookPlayViewState.SleepTimerViewState.Enabled.WithDuration(this.leftDuration)
  is SleepTimerState.Enabled.WithEndOfChapter -> BookPlayViewState.SleepTimerViewState.Enabled.WithEndOfChapter(this.chaptersRemaining)
}
