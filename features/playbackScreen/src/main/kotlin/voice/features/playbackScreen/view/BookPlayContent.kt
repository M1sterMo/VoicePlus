package voice.features.playbackScreen.view

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import voice.core.data.BookId
import voice.features.playbackScreen.BookPlayViewState
import kotlin.time.Duration

@Composable
internal fun BookPlayContent(
  bookId: BookId,
  sharedTransitionScope: SharedTransitionScope?,
  contentPadding: PaddingValues,
  viewState: BookPlayViewState,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSeek: (Duration) -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCurrentChapterClick: () -> Unit,
  onChapterTimeClick: () -> Unit,
  useLandscapeLayout: Boolean,
) {
  if (useLandscapeLayout) {
    Row(Modifier.padding(contentPadding)) {
      CoverRow(
        bookId = bookId,
        sharedTransitionScope = sharedTransitionScope,
        cover = viewState.cover,
        bookJacket = viewState.bookJacket,
        glow = viewState.coverGlow,
        playing = viewState.playing,
        onPlayClick = onPlayClick,
        sleepTimerState = viewState.sleepTimerState,
        modifier = Modifier
          .fillMaxHeight()
          .weight(1F)
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
      )
      Column(
        modifier = Modifier
          .fillMaxHeight()
          .weight(1F),
        verticalArrangement = Arrangement.Center,
      ) {
        PlaybackControls(
          viewState,
          onPlayClick,
          onRewindClick,
          onFastForwardClick,
          onSeek,
          onSkipToNext,
          onSkipToPrevious,
          onCurrentChapterClick,
          onChapterTimeClick,
        )
      }
    }
  } else {
    Column(Modifier.padding(contentPadding)) {
      CoverRow(
        bookId = bookId,
        sharedTransitionScope = sharedTransitionScope,
        onPlayClick = onPlayClick,
        cover = viewState.cover,
        bookJacket = viewState.bookJacket,
        glow = viewState.coverGlow,
        playing = viewState.playing,
        sleepTimerState = viewState.sleepTimerState,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1F)
          .padding(start = 16.dp, end = 16.dp, top = 8.dp),
      )
      Spacer(modifier = Modifier.size(16.dp))
      PlaybackControls(
        viewState,
        onPlayClick,
        onRewindClick,
        onFastForwardClick,
        onSeek,
        onSkipToNext,
        onSkipToPrevious,
        onCurrentChapterClick,
        onChapterTimeClick,
      )
      Spacer(modifier = Modifier.size(24.dp))
    }
  }
}

/** Position first, then the chapter, then play: the same order in both layouts. */
@Composable
private fun PlaybackControls(
  viewState: BookPlayViewState,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSeek: (Duration) -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCurrentChapterClick: () -> Unit,
  onChapterTimeClick: () -> Unit,
) {
  SliderRow(
    duration = viewState.duration,
    playedTime = viewState.playedTime,
    playbackSpeed = viewState.playbackSpeed,
    onSeek = onSeek,
    showRemaining = viewState.chapterCountdown,
    onTimeClick = onChapterTimeClick,
  )
  viewState.chapterName?.let { chapterName ->
    ChapterRow(
      chapterName = chapterName,
      nextPreviousVisible = viewState.showPreviousNextButtons,
      onSkipToNext = onSkipToNext,
      onSkipToPrevious = onSkipToPrevious,
      onCurrentChapterClick = onCurrentChapterClick,
    )
  }
  PlaybackRow(
    playing = viewState.playing,
    onPlayClick = onPlayClick,
    onRewindClick = onRewindClick,
    onFastForwardClick = onFastForwardClick,
  )
}
