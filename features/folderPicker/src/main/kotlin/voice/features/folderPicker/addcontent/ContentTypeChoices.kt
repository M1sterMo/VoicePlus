package voice.features.folderPicker.addcontent

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.logging.api.Logger
import voice.core.strings.R
import voice.features.folderPicker.folderPicker.FileTypeSelection

/**
 * Picking a folder is the way to go: it can hold one book or a whole library.
 * A single file is the exception, so it is quieter.
 */
@Composable
internal fun ContentTypeChoices(
  onAdd: (FileTypeSelection, Uri) -> Unit,
  modifier: Modifier = Modifier,
) {
  val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      onAdd(FileTypeSelection.File, uri)
    }
  }
  val documentTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) {
      onAdd(FileTypeSelection.Folder, uri)
    }
  }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp),
      onClick = {
        try {
          documentTreeLauncher.launch(null)
        } catch (e: ActivityNotFoundException) {
          Logger.w(e, "Could not add folder")
        }
      },
    ) {
      Text(stringResource(R.string.folder_add_action_folder))
    }
    TextButton(
      modifier = Modifier.fillMaxWidth(),
      onClick = {
        try {
          openDocumentLauncher.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
          Logger.w(e, "Could not add file")
        }
      },
    ) {
      Text(stringResource(R.string.folder_add_action_file))
    }
  }
}
