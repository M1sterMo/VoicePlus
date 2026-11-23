package voice.features.folderPicker

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import voice.core.logging.api.Logger

/**
 * Returns a function that opens the system folder picker and passes the picked folder to [onPick]. [onCancel]
 * runs when the picker is closed without a folder, [onNoPicker] when the device has no picker to open.
 */
@Composable
internal fun rememberFolderPicker(
  onPick: (Uri) -> Unit,
  onCancel: () -> Unit = {},
  onNoPicker: () -> Unit = {},
): () -> Unit {
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) onPick(uri) else onCancel()
  }
  return {
    try {
      launcher.launch(null)
    } catch (e: ActivityNotFoundException) {
      Logger.w(e, "Could not open the folder picker")
      onNoPicker()
    }
  }
}
