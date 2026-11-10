package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.strings.R
import voice.core.ui.formatTime
import voice.features.playbackScreen.atSpeed
import kotlin.time.Duration

@Composable
internal fun BookProgressRow(
  progress: Float,
  timeLeft: Duration,
  playbackSpeed: Float,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
  ) {
    Text(
      modifier = Modifier.weight(1F),
      text = stringResource(R.string.book_progress_completed, (progress * 100).toInt()),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = stringResource(R.string.book_time_left, formatTime(timeLeft.atSpeed(playbackSpeed).inWholeMilliseconds)),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
