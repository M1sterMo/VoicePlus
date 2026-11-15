package voice.features.playbackScreen.view

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import voice.core.ui.formatTime
import voice.features.playbackScreen.BookPlayViewState
import voice.features.playbackScreen.atSpeed

/**
 * The book's title, with how far through the whole book the listener is beneath it. Under the large
 * portrait title, time left sits at the right edge, over the chapter times; beside the landscape
 * toolbar it follows the percentage.
 */
@Composable
internal fun AppBarTitle(
  viewState: BookPlayViewState,
  large: Boolean,
) {
  Column {
    Text(
      text = viewState.title,
      modifier = Modifier.basicMarquee(),
    )
    ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
      // The large bar gives its title no end inset; match the 16dp edge of the times below.
      Row(if (large) Modifier.fillMaxWidth().padding(end = 16.dp) else Modifier) {
        Text(
          stringResource(R.string.book_progress_completed, (viewState.bookProgress * 100).toInt()),
          if (large) Modifier.weight(1F) else Modifier,
        )
        if (!large) Text(" · ")
        Text(
          stringResource(R.string.book_time_left, formatTime(viewState.bookTimeLeft.atSpeed(viewState.playbackSpeed).inWholeMilliseconds)),
        )
      }
    }
  }
}
