package voice.features.playbackScreen.view

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import voice.core.data.BookId
import voice.core.data.PlaybackToolbarAction
import voice.core.ui.VoiceTheme
import voice.features.playbackScreen.BookPlayViewState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Composable
internal fun BookPlayView(
  bookId: BookId,
  sharedTransitionScope: SharedTransitionScope?,
  viewState: BookPlayViewState,
  useLandscapeLayout: Boolean,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSeek: (Duration) -> Unit,
  onSleepTimerClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onBookmarkLongClick: () -> Unit,
  onSpeedChangeClick: () -> Unit,
  onSkipSilenceClick: () -> Unit,
  onVolumeBoostClick: () -> Unit,
  onListeningLogClick: () -> Unit,
  onCharacterListClick: () -> Unit,
  onUpNextClick: () -> Unit,
  onAboutBookClick: () -> Unit,
  onCustomizeToolbarClick: () -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCloseClick: () -> Unit,
  onCurrentChapterClick: () -> Unit,
  onChapterTimeClick: () -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  onEditChapterNamesClick: (() -> Unit)? = null,
) {
  Scaffold(
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState)
    },
    topBar = {
      BookPlayAppBar(
        viewState = viewState,
        onSleepTimerClick = onSleepTimerClick,
        onBookmarkClick = onBookmarkClick,
        onBookmarkLongClick = onBookmarkLongClick,
        onSpeedChangeClick = onSpeedChangeClick,
        onSkipSilenceClick = onSkipSilenceClick,
        onVolumeBoostClick = onVolumeBoostClick,
        onListeningLogClick = onListeningLogClick,
        onCharacterListClick = onCharacterListClick,
        onUpNextClick = onUpNextClick,
        onAboutBookClick = onAboutBookClick,
        onCustomizeToolbarClick = onCustomizeToolbarClick,
        onCloseClick = onCloseClick,
        useLandscapeLayout = useLandscapeLayout,
        onEditChapterNamesClick = onEditChapterNamesClick,
      )
    },
    content = {
      BookPlayContent(
        bookId = bookId,
        sharedTransitionScope = sharedTransitionScope,
        contentPadding = it,
        viewState = viewState,
        onPlayClick = onPlayClick,
        onRewindClick = onRewindClick,
        onFastForwardClick = onFastForwardClick,
        onSeek = onSeek,
        onSkipToNext = onSkipToNext,
        onSkipToPrevious = onSkipToPrevious,
        onCurrentChapterClick = onCurrentChapterClick,
        onChapterTimeClick = onChapterTimeClick,
        useLandscapeLayout = useLandscapeLayout,
      )
    },
  )
}

@Composable
@Preview
private fun BookPlayPreview(
  @PreviewParameter(BookPlayViewStatePreviewProvider::class)
  viewState: BookPlayViewState,
) {
  VoiceTheme {
    BookPlayView(
      bookId = BookId("preview"),
      sharedTransitionScope = null,
      viewState = viewState,
      onPlayClick = {},
      onRewindClick = {},
      onFastForwardClick = {},
      onSeek = {},
      onSleepTimerClick = {},
      onBookmarkClick = {},
      onBookmarkLongClick = {},
      onSpeedChangeClick = {},
      onSkipSilenceClick = {},
      onVolumeBoostClick = {},
      onListeningLogClick = {},
      onCharacterListClick = {},
      onUpNextClick = {},
      onAboutBookClick = {},
      onCustomizeToolbarClick = {},
      onSkipToNext = {},
      onSkipToPrevious = {},
      onCloseClick = {},
      onCurrentChapterClick = {},
      onChapterTimeClick = {},
      useLandscapeLayout = false,
    )
  }
}

private class BookPlayViewStatePreviewProvider : PreviewParameterProvider<BookPlayViewState> {
  override val values = sequence {
    val initial = BookPlayViewState(
      chapterName = "My Chapter",
      showPreviousNextButtons = false,
      cover = null,
      bookJacket = false,
      coverGlow = true,
      chapterCountdown = false,
      duration = 10.minutes,
      playedTime = 3.minutes,
      playbackSpeed = 1F,
      bookProgress = 0.42F,
      bookTimeLeft = 109.minutes,
      playing = true,
      skipSilence = true,
      sleepTimerState = BookPlayViewState.SleepTimerViewState.Disabled,
      title = "Das Ende der Welt",
      characterCount = 0,
      editChapterNamesVisible = false,
      toolbarActions = PlaybackToolbarAction.DEFAULT,
      ratePrompt = false,
    )
    yield(initial)
    yield(
      initial.copy(
        showPreviousNextButtons = !initial.showPreviousNextButtons,
        playing = !initial.playing,
        skipSilence = !initial.skipSilence,
      ),
    )
    yield(initial.copy(chapterName = null))
  }
}
