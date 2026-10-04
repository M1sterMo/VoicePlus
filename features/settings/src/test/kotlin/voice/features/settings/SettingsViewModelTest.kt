package voice.features.settings

import androidx.datastore.core.DataStore
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.common.AppInfoProvider
import voice.core.common.DispatcherProvider
import voice.core.data.GridMode
import voice.core.data.LockscreenSecondaryTextMode
import voice.core.data.LockscreenSliderMode
import voice.core.data.MediaButtonClickAction
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.featureflag.MemoryFeatureFlag
import voice.core.scanner.MediaScanTrigger
import voice.navigation.Destination
import voice.navigation.Navigator

class SettingsViewModelTest {

  private val scope = TestScope()
  private val useDarkThemeStore = MemoryDataStore(false)
  private val autoRewindAmountStore = MemoryDataStore(10)
  private val seekTimeStore = MemoryDataStore(30)
  private val gridModeStore = MemoryDataStore(GridMode.GRID)
  private val booksPerRowStore = MemoryDataStore(2)
  private val sleepTimerPreferenceStore = MemoryDataStore(SleepTimerPreference.Default)
  private val navigator = mockk<Navigator> {
    every { goTo(any()) } just Runs
  }
  private val appInfoProvider = mockk<AppInfoProvider> {
    every { versionName } returns "1.2.3"
  }
  private val folderPickerFeatureFlag = MemoryFeatureFlag(false)
  private val mediaButtonDoubleClickHandlerStore = MemoryDataStore(MediaButtonClickAction.SKIP_FORWARD)
  private val mediaButtonTripleClickHandlerStore = MemoryDataStore(MediaButtonClickAction.SKIP_BACKWARD)
  private val lockscreenSliderModeStore = MemoryDataStore(LockscreenSliderMode.CHAPTER)
  private val lockscreenSecondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.CHAPTER)
  private val experimentalPlaybackPersistenceStore = MemoryDataStore(false)
  private val ignoreFileTagsStore = MemoryDataStore(false)
  private val mediaScanTrigger = mockk<MediaScanTrigger>(relaxed = true)

  private val viewModel = SettingsViewModel(
    useDarkThemeStore = useDarkThemeStore,
    autoRewindAmountStore = autoRewindAmountStore,
    seekTimeStore = seekTimeStore,
    navigator = navigator,
    appInfoProvider = appInfoProvider,
    gridModeStore = gridModeStore,
    booksPerRowStore = booksPerRowStore,
    sleepTimerPreferenceStore = sleepTimerPreferenceStore,
    folderPickerInSettingsFeatureFlag = folderPickerFeatureFlag,
    mediaButtonDoubleClickHandlerStore = mediaButtonDoubleClickHandlerStore,
    mediaButtonTripleClickHandlerStore = mediaButtonTripleClickHandlerStore,
    lockscreenSliderModeStore = lockscreenSliderModeStore,
    lockscreenSecondaryTextModeStore = lockscreenSecondaryTextModeStore,
    experimentalPlaybackPersistenceStore = experimentalPlaybackPersistenceStore,
    ignoreFileTagsStore = ignoreFileTagsStore,
    mediaScanTrigger = mediaScanTrigger,
    dispatcherProvider = DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
  )

  @Test
  fun `books per row persists independently of library view`() = scope.runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      awaitItem().booksPerRow shouldBe 2
      viewModel.setBooksPerRow(3)
      awaitItem().booksPerRow shouldBe 3
      viewModel.setLibraryView(GridMode.BOOKS)
      awaitItem().booksPerRow shouldBe 3
      viewModel.setBooksPerRow(2)
      awaitItem().booksPerRow shouldBe 2
    }
  }

  @Test
  fun `library view persists books and can return to list and grid`() = scope.runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem().gridMode shouldBe GridMode.GRID

      viewModel.setLibraryView(GridMode.BOOKS)
      awaitItem().gridMode shouldBe GridMode.BOOKS
      viewModel.setLibraryView(GridMode.LIST)
      awaitItem().gridMode shouldBe GridMode.LIST
      viewModel.setLibraryView(GridMode.GRID)
      awaitItem().gridMode shouldBe GridMode.GRID
    }
  }

  @Test
  fun `library view changes inline without opening a dialog`() = scope.runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      awaitItem().dialog shouldBe null
      viewModel.setLibraryView(GridMode.BOOKS)
      val changed = awaitItem()
      changed.gridMode shouldBe GridMode.BOOKS
      changed.dialog shouldBe null
    }
  }

  @Test
  fun `lockscreen slider mode is persisted`() = scope.runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem().lockscreenSliderMode shouldBe LockscreenSliderMode.CHAPTER

      viewModel.setLockscreenSliderMode(LockscreenSliderMode.AUDIOBOOK)

      awaitItem().lockscreenSliderMode shouldBe LockscreenSliderMode.AUDIOBOOK
    }
  }

  @Test
  fun `lockscreen secondary text mode is persisted`() = scope.runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      awaitItem().lockscreenSecondaryTextMode shouldBe LockscreenSecondaryTextMode.CHAPTER

      viewModel.setLockscreenSecondaryTextMode(LockscreenSecondaryTextMode.AUTHOR)

      awaitItem().lockscreenSecondaryTextMode shouldBe LockscreenSecondaryTextMode.AUTHOR
    }
  }

  @Test
  fun `suggest idea opens the ideas discussion form`() {
    viewModel.suggestIdea()

    verify {
      navigator.goTo(
        Destination.Website(
          "https://github.com/mistermo-vibecode/VoicePlus/discussions/categories/ideas",
        ),
      )
    }
  }

  @Test
  fun `report problem opens the bug form with app version`() {
    viewModel.reportProblem()

    verify {
      navigator.goTo(
        match<Destination.Website> {
          it.url.startsWith("https://github.com/mistermo-vibecode/VoicePlus/issues/new?") &&
            it.url.contains("template=bug.yml") &&
            it.url.contains("version=1.2.3") &&
            it.url.contains("androidversion=") &&
            it.url.contains("device=")
        },
      )
    }
  }
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {

  private val value = MutableStateFlow(initial)

  override val data: Flow<T> get() = value

  override suspend fun updateData(transform: suspend (t: T) -> T): T {
    return value.updateAndGet { transform(it) }
  }
}
