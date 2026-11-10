package voice.features.folderPicker.selectType

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.documentfile.provider.DocumentFile
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.RetainedViewModel
import voice.core.data.folders.AudiobookFolders
import voice.core.data.store.ExcludedBooksStore
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.nameWithoutExtension
import voice.navigation.Destination
import voice.navigation.Navigator
import voice.navigation.Origin

@AssistedInject
class SelectFolderTypeViewModel(
  dispatcherProvider: DispatcherProvider,
  documentFileFactory: CachedDocumentFileFactory,
  @ExcludedBooksStore
  excludedBooksStore: DataStore<Set<String>>,
  private val audiobookFolders: AudiobookFolders,
  private val navigator: Navigator,
  @Assisted
  private val uri: Uri,
  @Assisted
  documentFile: DocumentFile,
  @Assisted
  private val origin: Origin,
) : RetainedViewModel() {

  private val folderName = mutableStateOf("")

  // the guess until the user picks a mode
  private val selectedMode = mutableStateOf<FolderMode?>(null)
  private val structure = mutableStateOf<Structure?>(null)

  init {
    // looked through once for as long as the screen is retained, and stopped when it is left
    scope.launch(dispatcherProvider.io) {
      val folder = documentFileFactory.create(documentFile.uri)
      folderName.value = folder.nameWithoutExtension()
      val excludedIds = excludedBooksStore.data.first()
      val loaded = Structure(
        guess = folder.guessFolderMode(),
        booksByMode = FolderMode.entries.associateWith { folder.books(it, excludedIds) },
      )
      if (selectedMode.value == null) selectedMode.value = loaded.guess
      structure.value = loaded
    }
  }

  internal fun selectMode(mode: FolderMode) {
    selectedMode.value = mode
  }

  internal fun onCloseClick() {
    navigator.goBack()
  }

  internal fun chooseAnotherFolder(uri: Uri) {
    navigator.goBack()
    navigator.goTo(Destination.SelectFolderType(uri = uri, origin = origin))
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
    val structure = structure.value
    return SelectFolderTypeViewState(
      folderName = folderName.value,
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
