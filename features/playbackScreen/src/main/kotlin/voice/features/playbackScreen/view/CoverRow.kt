package voice.features.playbackScreen.view

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import voice.core.data.BookId
import voice.core.strings.R
import voice.core.ui.ImmutableFile
import voice.core.ui.formatTime
import voice.features.playbackScreen.BookPlayViewState

@Composable
internal fun CoverRow(
  bookId: BookId,
  sharedTransitionScope: SharedTransitionScope?,
  cover: ImmutableFile?,
  bookJacket: Boolean,
  playing: Boolean,
  sleepTimerState: BookPlayViewState.SleepTimerViewState,
  onPlayClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // A jacket shrinks to the largest 2:3 box that fits, so the sleep timer badge stays on it.
  Box(if (bookJacket) modifier.wrapContentSize().aspectRatio(2F / 3F, matchHeightConstraintsFirst = true) else modifier) {
    Cover(
      bookId = bookId,
      sharedTransitionScope = sharedTransitionScope,
      onDoubleClick = onPlayClick,
      cover = cover,
      bookJacket = bookJacket,
      playing = playing,
    )
    when (sleepTimerState) {
      BookPlayViewState.SleepTimerViewState.Disabled -> {
      }
      is BookPlayViewState.SleepTimerViewState.Enabled -> {
        Text(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 8.dp)
            .background(
              color = Color(0x7E000000),
              shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
          text = when (sleepTimerState) {
            is BookPlayViewState.SleepTimerViewState.Enabled.WithDuration -> formatTime(
              timeMs = sleepTimerState.leftDuration.inWholeMilliseconds,
            )
            is BookPlayViewState.SleepTimerViewState.Enabled.WithEndOfChapter -> pluralStringResource(
              R.plurals.end_of_chapter_count,
              sleepTimerState.chaptersRemaining,
              sleepTimerState.chaptersRemaining,
            )
          },
          color = Color.White,
        )
      }
    }
  }
}
