package voice.features.folderPicker.selectType

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.documentfile.FileBasedDocumentFactory
import voice.navigation.Destination
import voice.navigation.Navigator
import voice.navigation.Origin

@RunWith(AndroidJUnit4::class)
class SelectFolderTypeViewModelTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `starts with the guess and adds the folder in the selected mode`() = runTest {
    val audiobookFolder = temporaryFolder.newFolder("audiobooks")
    with(temporaryFolder) {
      newFile("audiobooks/FirstBook.m4b")
      newFolder("audiobooks/SecondBook")
      newFile("audiobooks/SecondBook/1.mp3")
      newFile("audiobooks/SecondBook/2.mp3")
    }
    val audiobookFolders = mockk<AudiobookFolders>(relaxed = true)
    val navigator = mockk<Navigator>(relaxed = true)
    val viewModel = SelectFolderTypeViewModel(
      dispatcherProvider = DispatcherProvider(io = UnconfinedTestDispatcher(testScheduler)),
      documentFileFactory = FileBasedDocumentFactory,
      excludedBooksStore = mockk { every { data } returns flowOf(emptySet()) },
      audiobookFolders = audiobookFolders,
      navigator = navigator,
      uri = audiobookFolder.toUri(),
      documentFile = DocumentFile.fromFile(audiobookFolder),
      origin = Origin.Onboarding,
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      var state = awaitItem()
      while (state.loading || state.folderName.isEmpty()) state = awaitItem()
      state.folderName shouldBe "audiobooks"
      state.guessedMode shouldBe FolderMode.Audiobooks
      state.selectedMode shouldBe FolderMode.Audiobooks
      state.books.map { it.name }.sorted() shouldBe listOf("FirstBook", "SecondBook")

      viewModel.selectMode(FolderMode.SingleBook)
      with(awaitItem()) {
        selectedMode shouldBe FolderMode.SingleBook
        guessedMode shouldBe FolderMode.Audiobooks
        books shouldBe listOf(SelectFolderTypeViewState.Book("audiobooks", author = null, fileCount = 3))
      }
    }

    viewModel.add()
    verify {
      audiobookFolders.add(audiobookFolder.toUri(), FolderType.SingleFolder)
      navigator.goTo(Destination.OnboardingCompletion)
    }
  }
}
