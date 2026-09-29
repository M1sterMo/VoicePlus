package voice.features.playbackScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import voice.core.playback.misc.Decibel
import voice.core.strings.R as StringsR

@Composable
internal fun VolumeGainDialog(
  dialogState: BookPlayDialogViewState.VolumeGainDialog,
  viewModel: BookPlayViewModel,
) {
  AlertDialog(
    onDismissRequest = { viewModel.dismissDialog() },
    confirmButton = {},
    text = {
      Column {
        Text(stringResource(id = StringsR.string.volume_boost) + ": " + dialogState.valueFormatted)
        Slider(
          valueRange = 0F..dialogState.maxGain.value,
          value = dialogState.gain.value,
          onValueChange = {
            viewModel.onVolumeGainChanged(Decibel(it))
          },
        )
        Row(
          modifier = Modifier.fillMaxWidth().toggleable(
            value = dialogState.remember,
            role = Role.Checkbox,
            onValueChange = { viewModel.onVolumeGainChanged(dialogState.gain, remember = it) },
          ),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(checked = dialogState.remember, onCheckedChange = null)
          Text(stringResource(StringsR.string.volume_boost_remember))
        }
      }
    },
  )
}
