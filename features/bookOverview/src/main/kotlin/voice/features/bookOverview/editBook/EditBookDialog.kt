package voice.features.bookOverview.editBook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.scanner.BookEditDraft
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@Composable
internal fun EditBookDialog(
  draft: BookEditDraft,
  model: EditBookViewModel,
) {
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) model.pickedCover(uri)
  }
  AlertDialog(
    onDismissRequest = model::dismiss,
    title = { Text(stringResource(StringsR.string.edit_book_title)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AsyncImage(
            model = draft.cover,
            placeholder = painterResource(UiR.drawable.album_art),
            error = painterResource(UiR.drawable.album_art),
            contentDescription = stringResource(StringsR.string.cover),
            modifier = Modifier.width(80.dp).height(120.dp),
            contentScale = ContentScale.Fit,
          )
          Column {
            TextButton(onClick = model::searchCover, enabled = !model.busy) { Text(stringResource(StringsR.string.edit_book_search_cover)) }
            TextButton(onClick = {
              picker.launch("image/*")
            }, enabled = !model.busy) { Text(stringResource(StringsR.string.edit_book_choose_cover)) }
            TextButton(onClick = model::embeddedCover, enabled = !model.busy) {
              Text(stringResource(StringsR.string.edit_book_embedded_cover))
            }
          }
        }
        OutlinedTextField(
          value = draft.title,
          onValueChange = model::titleChanged,
          label = { Text(stringResource(StringsR.string.edit_book_title_label)) },
          modifier = Modifier.fillMaxWidth(),
          enabled = !model.busy,
          maxLines = 4,
        )
        Text(stringResource(StringsR.string.edit_book_local_description), style = MaterialTheme.typography.bodySmall)
        model.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      TextButton(onClick = model::save, enabled = !model.busy && draft.title.isNotBlank()) {
        Text(stringResource(StringsR.string.character_save))
      }
    },
    dismissButton = {
      TextButton(onClick = model::dismiss, enabled = !model.busy) { Text(stringResource(StringsR.string.dialog_cancel)) }
    },
  )
}
