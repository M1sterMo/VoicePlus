package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.core.ui.RatingStars
import voice.core.strings.R as StringsR

/** Asks once, when a book ends, how the listener rates it. Dismissing it counts as "Not now". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RatePromptSheet(
  title: String,
  onRate: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        stringResource(StringsR.string.rating_prompt_title, title),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
      )
      Text(stringResource(StringsR.string.rating_prompt_body), style = MaterialTheme.typography.bodyMedium)
      RatingStars(rating = null, onRate = { it?.let(onRate) }, starSize = 40.dp)
      TextButton(onClick = onDismiss) { Text(stringResource(StringsR.string.rating_prompt_not_now)) }
    }
  }
}
