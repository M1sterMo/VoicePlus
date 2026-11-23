package voice.features.folderPicker.folderPicker

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.documentfile.nameFromPath
import voice.core.documentfile.nameWithoutExtension
import voice.navigation.Destination
import voice.navigation.Navigator
import voice.navigation.Origin

@Inject
class FolderPickerViewModel(
  private val audiobookFolders: AudiobookFolders,
  private val navigator: Navigator,
) {

  @Composable
  fun viewState(): FolderPickerViewState {
    val folders: List<FolderPickerViewState.Item> by remember {
      items()
    }.collectAsState(initial = emptyList())
    return FolderPickerViewState(folders)
  }

  private fun items(): Flow<List<FolderPickerViewState.Item>> {
    return combine(audiobookFolders.all(), audiobookFolders.withoutAccess()) { folders, foldersWithoutAccess ->
      withContext(Dispatchers.IO) {
        val withAccess = folders.flatMap { (folderType, folders) ->
          folders.map { (documentFile, uri) ->
            FolderPickerViewState.Item(
              name = documentFile.nameWithoutExtension(),
              id = uri,
              folderType = folderType,
            )
          }
        }
        // right after access is allowed again, the two flows can disagree for a moment
        val uris = withAccess.mapTo(mutableSetOf()) { it.id }
        val withoutAccess = foldersWithoutAccess.flatMap { (folderType, uris) ->
          uris.map { uri ->
            // the folder can't be asked for its name without access
            FolderPickerViewState.Item(name = uri.nameFromPath(), id = uri, folderType = folderType, hasAccess = false)
          }
        }.filter { it.id !in uris }
        // the folders that need attention first
        withoutAccess.sortedDescending() + withAccess.sortedDescending()
      }
    }
  }

  /**
   * Access to [uri] was allowed again by picking [pickedUri]. The same folder gets its grant back; another one
   * replaces it, and for a folder its type is chosen again, as it can hold its books differently.
   */
  internal fun onAccessAllowed(
    uri: Uri,
    folderType: FolderType,
    pickedUri: Uri,
  ) {
    if (pickedUri == uri) {
      audiobookFolders.add(uri, folderType)
      return
    }
    audiobookFolders.remove(uri, folderType)
    if (folderType == FolderType.SingleFile) {
      audiobookFolders.add(pickedUri, folderType)
    } else {
      navigator.goTo(Destination.SelectFolderType(uri = pickedUri, origin = Origin.Default))
    }
  }

  internal fun onCloseClick() {
    navigator.goBack()
  }

  internal fun add() {
    navigator.goTo(
      Destination.AddContent(
        Origin.Default,
      ),
    )
  }

  fun removeFolder(item: FolderPickerViewState.Item) {
    audiobookFolders.remove(item.id, item.folderType)
  }
}
