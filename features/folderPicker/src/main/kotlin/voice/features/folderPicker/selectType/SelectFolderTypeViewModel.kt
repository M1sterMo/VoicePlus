package voice.features.folderPicker.selectType

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.documentfile.provider.DocumentFile
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.data.folders.AudiobookFolders
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.nameWithoutExtension
import voice.navigation.Destination
import voice.navigation.Navigator
import voice.navigation.Origin

@AssistedInject
class SelectFolderTypeViewModel(
  private val dispatcherProvider: DispatcherProvider,
  private val audiobookFolders: AudiobookFolders,
  private val navigator: Navigator,
  private val documentFileFactory: CachedDocumentFileFactory,
  @Assisted
  private val uri: Uri,
  @Assisted
  private val documentFile: DocumentFile,
  @Assisted
  private val origin: Origin,
) {

  // the guess until the user picks a mode
  private val selectedMode = mutableStateOf<FolderMode?>(null)

  internal fun selectMode(mode: FolderMode) {
    selectedMode.value = mode
  }

  internal fun onCloseClick() {
    navigator.goBack()
  }

  internal fun add() {
    val mode = selectedMode.value ?: return
    audiobookFolders.add(uri, mode.toFolderType())
    when (origin) {
      Origin.Default -> navigator.setRoot(Destination.BookOverview)
      Origin.Onboarding -> navigator.goTo(Destination.OnboardingCompletion)
    }
  }

  @Composable
  internal fun viewState(): SelectFolderTypeViewState {
    val folder = remember { documentFileFactory.create(documentFile.uri) }
    val folderName by produceState(initialValue = "") {
      value = withContext(dispatcherProvider.io) { folder.nameWithoutExtension() }
    }
    val structure by produceState<Structure?>(initialValue = null) {
      value = withContext(dispatcherProvider.io) {
        Structure(
          guess = folder.guessFolderMode(),
          booksByMode = FolderMode.entries.associateWith { folder.books(it) },
        )
      }.also {
        if (selectedMode.value == null) selectedMode.value = it.guess
      }
    }
    return SelectFolderTypeViewState(
      folderName = folderName,
      selectedMode = selectedMode.value ?: FolderMode.SingleBook,
      guessedMode = structure?.guess,
      booksByMode = structure?.booksByMode,
    )
  }

  private class Structure(
    val guess: FolderMode,
    val booksByMode: Map<FolderMode, List<SelectFolderTypeViewState.Book>>,
  )

  @AssistedFactory
  interface Factory {
    fun create(
      uri: Uri,
      documentFile: DocumentFile,
      origin: Origin,
    ): SelectFolderTypeViewModel
  }
}
