package voice.features.playbackScreen.view

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import voice.core.strings.R
import voice.core.ui.formatTime
import voice.features.playbackScreen.BookPlayViewState
import voice.features.playbackScreen.atSpeed

/** The book's title, with how far through the whole book the listener is beneath it. */
@Composable
internal fun AppBarTitle(viewState: BookPlayViewState) {
  Column {
    Text(
      text = viewState.title,
      modifier = Modifier.basicMarquee(),
    )
    Text(
      text = stringResource(R.string.book_progress_completed, (viewState.bookProgress * 100).toInt()) + " · " +
        stringResource(
          R.string.book_time_left,
          formatTime(viewState.bookTimeLeft.atSpeed(viewState.playbackSpeed).inWholeMilliseconds),
        ),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}
