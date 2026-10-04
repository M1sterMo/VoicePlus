package voice.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.zacsweers.metro.Inject
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.common.rootGraphAs
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.GridMode
import voice.core.data.MarkData
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterNameOverrideRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.GridModeStore
import voice.core.data.store.OnboardingCompletedStore
import voice.core.scanner.BookEditDraftStore
import voice.core.scanner.MediaScanTrigger
import voice.navigation.Destination
import voice.navigation.Navigator
import java.io.File
import java.time.Instant

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FeatureRegressionTest {
  @get:Rule
  @Suppress("DEPRECATION") // The v2 test dispatcher disposes real MediaController listeners off the Android main thread.
  val compose = createEmptyComposeRule()

  @Inject lateinit var books: BookContentRepo

  @Inject lateinit var chapters: ChapterRepo

  @Inject lateinit var overrides: ChapterNameOverrideRepo

  @Inject lateinit var scanner: MediaScanTrigger

  @Inject lateinit var drafts: BookEditDraftStore

  @Inject lateinit var navigator: Navigator
  @field:[Inject GridModeStore] lateinit var layout: DataStore<GridMode>
  @field:[Inject OnboardingCompletedStore] lateinit var onboarding: DataStore<Boolean>
  @field:[Inject CurrentBookStore] lateinit var currentBook: DataStore<BookId?>

  private val bookId = BookId("v127-feature-regression")
  private val chapterId = ChapterId("file:///v127-feature-regression.m4a")
  private val title = "V127 Regression Fixture"
  private val searchResult = hasText(title) and hasClickAction() and hasAnyAncestor(hasTestTag("book-search-results"))

  @Test
  fun gridSearchLongPressOpensMenuAndTapOpensBook() = searchMenu(GridMode.GRID)

  @Test
  fun listSearchLongPressOpensMenuAndTapOpensBook() = searchMenu(GridMode.LIST)

  @Test
  fun booksSearchLongPressOpensMenuAndTapOpensBook() = searchMenu(GridMode.BOOKS)

  @Test
  fun bookEditorCancelAndSavePreservePlaybackAndSurviveRecreation() = withFixture(GridMode.BOOKS) {
    ActivityScenario.launch(MainActivity::class.java).use { activity ->
      search()
      compose.onNode(searchResult).performTouchInput { longClick() }
      compose.onNodeWithText("Edit book").performClick()
      val titleField = hasSetTextAction() and hasAnyAncestor(isDialog())
      compose.waitUntilAtLeastOneExists(titleField, 10_000)
      compose.onNodeWithText("Search covers").assertIsDisplayed()
      compose.onNodeWithText("Choose image").assertIsDisplayed()
      compose.onNodeWithText("Use embedded cover").assertIsDisplayed()
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val image = File(instrumentation.targetContext.cacheDir, "editor-cover-test.png")
      val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
      try {
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) shouldBe true }
      } finally {
        bitmap.recycle()
      }
      fun cropCover(): File {
        compose.runOnIdle { navigator.goTo(Destination.EditCover(bookId, image.toUri(), drafts.draft.value!!.token)) }
        compose.waitUntilAtLeastOneExists(hasText("Set") and isEnabled(), 10_000)
        compose.onNodeWithText("Set").performClick()
        compose.waitUntil(10_000) { drafts.draft.value?.coverChanged == true }
        return drafts.draft.value!!.cover!!
      }
      val cancelledCover = cropCover()
      runBlocking { books.get(bookId)!!.cover shouldBe null }
      compose.waitForIdle()
      UiDevice.getInstance(instrumentation).takeScreenshot(File(instrumentation.targetContext.filesDir, "book-editor.png"))
      compose.onNode(titleField).performTextReplacement("Unsaved title")
      compose.onNodeWithText("Cancel").performClick()
      compose.waitUntil(10_000) { !cancelledCover.exists() }
      runBlocking { books.get(bookId)!!.name shouldBe title }

      compose.onNode(searchResult).performTouchInput { longClick() }
      compose.onNodeWithText("Edit book").performClick()
      compose.waitUntilAtLeastOneExists(titleField, 10_000)
      val savedCover = cropCover()
      val savedCoverBytes = savedCover.readBytes()
      compose.onNode(titleField).performTextReplacement("$title — Edited")
      activity.recreate()
      compose.waitUntilAtLeastOneExists(titleField and hasText("$title — Edited"), 10_000)
      compose.onNodeWithText("Save").performClick()
      compose.waitUntil(10_000) { runBlocking { books.get(bookId)!!.name == "$title — Edited" } }
      val committedCover = runBlocking {
        books.get(bookId)!!.apply {
          positionInChapter shouldBe 65_000L
          chapters shouldBe listOf(chapterId)
          cover!!.readBytes() shouldBe savedCoverBytes
        }.cover!!
      }
      committedCover.parentFile!!.name shouldBe "bookCovers"
      activity.recreate()
      compose.waitUntilAtLeastOneExists(hasText("$title — Edited"), 10_000)
      committedCover.exists() shouldBe true
      savedCover.exists() shouldBe false
      image.delete()
    }
  }

  private fun searchMenu(mode: GridMode) = withFixture(mode) {
    ActivityScenario.launch(MainActivity::class.java).use {
      search()
      compose.onNode(searchResult).performTouchInput { longClick() }
      compose.waitUntilAtLeastOneExists(hasText("Edit book"), 10_000)
      compose.onNodeWithText("Edit book").assertIsDisplayed()
      compose.onNodeWithText("Delete Book").assertIsDisplayed()
      pressBack()
      compose.waitUntilAtLeastOneExists(searchResult, 10_000)
      compose.onNode(searchResult).performClick()
      compose.waitUntilAtLeastOneExists(hasText("Chapter 12"), 10_000)
      compose.onNodeWithText(title).assertIsDisplayed()
      pressBack()
      compose.waitUntilAtLeastOneExists(hasText(title) and hasClickAction() and !hasSetTextAction(), 10_000)
      // Returning from playback closes search; Current and the home shelf may both show this book.
      compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("current:book:${bookId.value}"))
      compose.onNodeWithTag("current:book:${bookId.value}").assertIsDisplayed()
    }
  }

  @Test
  fun chapterCorrectionPersistsAcrossReopenAndResetClearsNames() = withFixture(GridMode.GRID) {
    ActivityScenario.launch(MainActivity::class.java).use {
      search()
      compose.onNode(searchResult).performClick()
      compose.waitUntilAtLeastOneExists(hasText("Chapter 12"), 10_000)
      openEditor()
      repeat(2) { compose.onNodeWithContentDescription("Decrease chapter offset").performClick() }
      compose.waitUntilAtLeastOneExists(hasText("-2"), 10_000)
      compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chapter 8"))
      compose.onNodeWithText("Chapter 8").assertIsDisplayed()
      compose.onNodeWithText("Chapter 9").assertIsDisplayed()
      compose.onNodeWithContentDescription("Close").performClick()
      openEditor()
      compose.onNodeWithContentDescription("Decrease chapter offset").performClick()
      compose.waitUntilAtLeastOneExists(hasText("-3"), 10_000)
      compose.waitUntil(10_000) {
        runBlocking {
          books.get(bookId)!!.chapterNameOffset == -3 &&
            overrides.overridesForBook(bookId).first().size == 2
        }
      }
      runBlocking {
        books.get(bookId)!!.chapterNameOffset shouldBe -3
        overrides.overridesForBook(bookId).first().map { it.name } shouldBe listOf("Chapter 8", "Chapter 9")
      }
      compose.onNodeWithContentDescription("More").performClick()
      compose.onNodeWithText("Reset all to defaults").performClick()
      compose.onNodeWithText("Reset").performClick()
      compose.waitUntil(10_000) {
        runBlocking { books.get(bookId)!!.chapterNameOffset == 0 && overrides.overridesForBook(bookId).first().isEmpty() }
      }
      compose.onNodeWithContentDescription("Close").performClick()
      openEditor()
      compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Chapter 10"))
      compose.onNodeWithText("Chapter 10").assertIsDisplayed()
      compose.onNodeWithText("Chapter 11").assertIsDisplayed()
      compose.onNodeWithText("0").assertIsDisplayed()
    }
  }

  private fun openEditor() {
    compose.onNodeWithContentDescription("More").performClick()
    compose.waitUntilAtLeastOneExists(hasText("Chapter Fix"), 10_000)
    compose.onNodeWithText("Chapter Fix").performClick()
    compose.waitUntilAtLeastOneExists(hasText("Edit chapter names"), 10_000)
  }

  private fun search() {
    compose.waitUntilAtLeastOneExists(hasContentDescription("Search"), 10_000)
    compose.onNodeWithContentDescription("Search").performClick()
    compose.waitUntilAtLeastOneExists(hasSetTextAction(), 10_000)
    compose.onNode(hasSetTextAction()).performClick()
    // Opening search clears the previous query; wait for that state before entering new text.
    compose.waitUntilAtLeastOneExists(hasContentDescription("Close"), 10_000)
    // Clearing explicitly also tests returning to an existing search after closing the editor.
    compose.onNode(hasSetTextAction()).performTextReplacement(title)
    compose.waitUntilAtLeastOneExists(searchResult, 10_000)
  }

  private fun withFixture(
    mode: GridMode,
    block: () -> Unit,
  ) {
    rootGraphAs<TestGraph>().inject(this)
    val oldLayout = runBlocking { layout.data.first() }
    val oldOnboarding = runBlocking { onboarding.data.first() }
    val oldBook = runBlocking { currentBook.data.first() }
    runBlocking {
      // Synthetic books are outside watched folders: let reconciliation finish before seeding them.
      scanner.scanAndAwait()
      layout.updateData { mode }
      onboarding.updateData { true }
      chapters.put(
        Chapter(
          id = chapterId,
          name = "Regression fixture",
          duration = 120_000L,
          fileLastModified = Instant.EPOCH,
          markData = (0..3).map { MarkData(it * 30_000L, "Chapter ${it + 10}") },
        ),
      )
      overrides.deleteAll(bookId)
      books.put(
        BookContent(
          id = bookId, playbackSpeed = 1f, skipSilence = false, isActive = true,
          lastPlayedAt = Instant.now(), author = "Regression", name = title, addedAt = Instant.now(),
          chapters = listOf(chapterId), currentChapter = chapterId, positionInChapter = 65_000L,
          cover = null, gain = 0f, genre = null, narrator = null, series = null, part = null,
        ),
      )
    }
    try {
      block()
    } finally {
      runBlocking {
        books.get(bookId)?.let { books.put(it.copy(isActive = false)) }
        overrides.deleteAll(bookId)
        layout.updateData { oldLayout }
        onboarding.updateData { oldOnboarding }
        currentBook.updateData { oldBook }
      }
    }
  }
}
