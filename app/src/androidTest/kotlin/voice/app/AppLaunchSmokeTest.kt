package voice.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.zacsweers.metro.Inject
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.common.rootGraphAs
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.GridMode
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.store.GridModeStore
import voice.core.data.store.OnboardingCompletedStore
import voice.core.scanner.MediaScanTrigger
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {

  @get:Rule
  @Suppress("DEPRECATION") // Real controller listeners must be disposed on the Android main thread.
  val composeRule = createEmptyComposeRule()

  @Inject
  lateinit var bookContentRepo: BookContentRepo

  @Inject
  lateinit var chapterRepo: ChapterRepo

  @field:[Inject GridModeStore]
  lateinit var gridModeStore: DataStore<GridMode>

  @Inject
  lateinit var scanner: MediaScanTrigger

  @field:[Inject OnboardingCompletedStore]
  lateinit var onboardingCompletedStore: DataStore<Boolean>

  private var oldActive = emptyList<BookId>()
  private var oldLayout = GridMode.FOLLOW_DEVICE
  private var oldOnboarding = false

  @Before
  fun rememberLibrary() = runBlocking {
    rootGraphAs<TestGraph>().inject(this@AppLaunchSmokeTest)
    oldActive = bookContentRepo.all().filter { it.isActive }.map { it.id }
    oldLayout = gridModeStore.data.first()
    oldOnboarding = onboardingCompletedStore.data.first()
    scanner.scanAndAwait()
  }

  @After
  fun restoreLibrary() = runBlocking {
    bookContentRepo.setAllInactiveExcept(oldActive)
    gridModeStore.updateData { oldLayout }
    onboardingCompletedStore.updateData { oldOnboarding }
    Unit
  }

  @Test
  fun mainActivityReachesResumedState() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        activity.lifecycle.currentState shouldBe Lifecycle.State.RESUMED
      }
    }
  }

  @Test
  @OptIn(ExperimentalTestApi::class)
  fun bookOverviewListRestoresScrollPositionAfterPlaybackBack() {
    assertBookOverviewRestoresScrollPosition(GridMode.LIST)
  }

  @Test
  @OptIn(ExperimentalTestApi::class)
  fun bookOverviewGridRestoresScrollPositionAfterPlaybackBack() {
    assertBookOverviewRestoresScrollPosition(GridMode.GRID)
  }

  @Test
  @OptIn(ExperimentalTestApi::class)
  fun bookOverviewBooksRestoresScrollPositionAfterPlaybackBack() {
    assertBookOverviewRestoresScrollPosition(GridMode.BOOKS)
  }

  @Test
  @OptIn(ExperimentalTestApi::class)
  fun bookOverviewRestoresListPositionAfterSettingsBack() {
    val targetBookName = "Scroll Test Book 29"
    prepareScrollableLibrary(GridMode.LIST)

    ActivityScenario.launch(MainActivity::class.java).use {
      composeRule.waitUntilAtLeastOneExists(hasScrollToIndexAction(), 10_000)
      composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(targetBookName))
      composeRule.onNode(hasText(targetBookName) and hasClickAction()).assertIsDisplayed()
      composeRule.onNode(hasContentDescription("Library options")).performClick()
      composeRule.onNode(hasText("Settings")).performClick()
      composeRule.waitUntilAtLeastOneExists(hasContentDescription("Close"), 10_000)
      composeRule.onNode(hasContentDescription("Close")).performClick()

      composeRule.waitUntilAtLeastOneExists(
        matcher = hasText(targetBookName) and hasClickAction(),
        timeoutMillis = 10_000,
      )
      composeRule.onNode(hasText(targetBookName) and hasClickAction()).assertIsDisplayed()
    }
  }

  @Test
  @OptIn(ExperimentalTestApi::class)
  fun bookOverviewRestoresListPositionAfterActivityRecreation() {
    val targetBookName = "Scroll Test Book 29"
    prepareScrollableLibrary(GridMode.LIST)

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      composeRule.waitUntilAtLeastOneExists(hasScrollToIndexAction(), 10_000)
      composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(targetBookName))
      composeRule.onNode(hasText(targetBookName) and hasClickAction()).assertIsDisplayed()

      scenario.recreate()

      composeRule.waitUntilAtLeastOneExists(
        matcher = hasText(targetBookName) and hasClickAction(),
        timeoutMillis = 10_000,
      )
      composeRule.onNode(hasText(targetBookName) and hasClickAction()).assertIsDisplayed()
    }
  }

  @OptIn(ExperimentalTestApi::class)
  private fun assertBookOverviewRestoresScrollPosition(gridMode: GridMode) {
    val targetBookName = "Scroll Test Book 29"
    prepareScrollableLibrary(gridMode)

    ActivityScenario.launch(MainActivity::class.java).use {
      composeRule.waitUntilAtLeastOneExists(hasScrollToIndexAction(), 10_000)
      val library = composeRule.onNode(hasScrollToIndexAction())
      library.performScrollToNode(hasText(targetBookName))
      composeRule.onNode(hasText(targetBookName) and hasClickAction())
        .assertIsDisplayed()
        .performClick()

      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isEmpty()
      }
      composeRule.waitUntilAtLeastOneExists(
        matcher = hasContentDescription("Close"),
        timeoutMillis = 10_000,
      )
      composeRule.onNode(hasContentDescription("Close")).performClick()

      composeRule.waitUntilAtLeastOneExists(
        matcher = hasScrollToIndexAction(),
        timeoutMillis = 10_000,
      )
      composeRule.waitUntilAtLeastOneExists(
        matcher = hasText(targetBookName) and hasClickAction(),
        timeoutMillis = 10_000,
      )
      composeRule.onNode(hasText(targetBookName) and hasClickAction())
        .assertIsDisplayed()
    }
  }

  private fun prepareScrollableLibrary(gridMode: GridMode) = runBlocking {
    onboardingCompletedStore.updateData { true }
    gridModeStore.updateData { gridMode }
    prepareScrollableLibrary()
    bookContentRepo.setAllInactiveExcept((0 until 30).map { BookId("scroll-test-book-$it") })
  }

  private suspend fun prepareScrollableLibrary() {
    val chapterId = ChapterId("file:///scroll-position-test.mp3")
    chapterRepo.put(
      Chapter(
        id = chapterId,
        duration = 120_000L,
        name = "Scroll Test Chapter",
        fileLastModified = Instant.EPOCH,
        markData = emptyList(),
      ),
    )

    repeat(30) { index ->
      bookContentRepo.put(
        BookContent(
          id = BookId("scroll-test-book-$index"),
          playbackSpeed = 1f,
          skipSilence = false,
          isActive = true,
          lastPlayedAt = Instant.EPOCH.plusSeconds(index.toLong()),
          author = "Scroll Test Author",
          name = "Scroll Test Book $index",
          addedAt = Instant.EPOCH.plusSeconds(index.toLong()),
          chapters = listOf(chapterId),
          currentChapter = chapterId,
          positionInChapter = 0L,
          cover = null,
          gain = 0f,
          genre = null,
          narrator = null,
          series = null,
          part = null,
        ),
      )
    }
  }
}
