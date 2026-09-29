package voice.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.zacsweers.metro.Inject
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import voice.core.common.rootGraphAs
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.GridMode
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.store.BooksPerRowStore
import voice.core.data.store.GridModeStore
import voice.core.data.store.OnboardingCompletedStore
import voice.core.scanner.MediaScanTrigger
import voice.navigation.Destination
import voice.navigation.Navigator
import java.io.File
import java.time.Instant
import kotlin.math.abs

/** Real navigation/settings with long titles. Optional local artwork is never bundled in the app. */
@OptIn(ExperimentalTestApi::class)
class BooksLibraryTest {
  @get:Rule
  @Suppress("DEPRECATION")
  val compose = createEmptyComposeRule()

  @Inject lateinit var books: BookContentRepo

  @Inject lateinit var chapters: ChapterRepo

  @Inject lateinit var scanner: MediaScanTrigger

  @Inject lateinit var navigator: Navigator

  @field:[Inject GridModeStore] lateinit var layout: DataStore<GridMode>
  @field:[Inject BooksPerRowStore] lateinit var booksPerRow: DataStore<Int>
  @field:[Inject OnboardingCompletedStore] lateinit var onboarding: DataStore<Boolean>

  @Test
  fun longTitlesAndLibrarySelectionSurviveRecreation() {
    rootGraphAs<TestGraph>().inject(this)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = File(instrumentation.targetContext.filesDir, "books-demo").apply { mkdirs() }
    val titles = listOf(
      "Chrysalis: Book 4 — Between a Rock and a Carapace",
      "The Dungeon Anarchist’s Cookbook, Dungeon Crawler Carl, Book 3",
      "Project Hail Mary",
      "The Hobbit",
      "A Very Long Audiobook Title Without Any Cover Art — An Unabridged Adventure, Book Twenty-Seven",
    )
    val covers = listOf("chrysalis-4", "dungeon-anarchists-cookbook", "project-hail-mary", "the-hobbit")
    val oldLayout = runBlocking { layout.data.first() }
    val oldBooksPerRow = runBlocking { booksPerRow.data.first() }
    val oldOnboarding = runBlocking { onboarding.data.first() }
    val previousActive = runBlocking { books.all().filter { it.isActive }.map { it.id } }
    val audio = File(directory, "fixture.m4a")
    instrumentation.context.assets.open("auphonic_chapters_demo.m4a").use { input ->
      audio.outputStream().use(input::copyTo)
    }
    val chapterId = ChapterId(audio.toURI().toString())
    val missingCoverChapterId = ChapterId("file:///books-layout-missing-cover.m4a")
    val ids = titles.indices.map { BookId("books-layout-fixture-$it") }
    runBlocking {
      // Don't let the entry scan reconcile these synthetic books out of the library during the test.
      scanner.scanAndAwait()
      onboarding.updateData { true }
      layout.updateData { GridMode.BOOKS }
      booksPerRow.updateData { 2 }
      chapters.put(Chapter(chapterId, "Chapter 1", 120_000L, Instant.EPOCH, emptyList()))
      chapters.put(Chapter(missingCoverChapterId, "Chapter 1", 120_000L, Instant.EPOCH, emptyList()))
      titles.forEachIndexed { index, title ->
        val fixtureChapterId = if (index == titles.lastIndex) missingCoverChapterId else chapterId
        books.put(
          BookContent(
            id = ids[index], name = title, author = "Demo author", playbackSpeed = 1f,
            skipSilence = false, isActive = true, lastPlayedAt = Instant.now().minusSeconds(index.toLong()),
            addedAt = Instant.now(), chapters = listOf(fixtureChapterId), currentChapter = fixtureChapterId,
            positionInChapter = 30_000L,
            cover = covers.getOrNull(index)?.let {
              File(directory, "$it.jpg").takeIf(File::exists)
            },
            gain = 0f, genre = null, narrator = null, series = null, part = null,
          ),
        )
      }
      books.setAllInactiveExcept(ids)
    }
    try {
      ActivityScenario.launch(MainActivity::class.java).use { activity ->
        compose.waitUntil(15_000) {
          compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("shelf-header:current"))
        val headingText = compose.onAllNodes(
          hasAnyAncestor(hasTestTag("shelf-header:current")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
          useUnmergedTree = true,
        ).fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.Text].map { text -> text.text } }
        headingText shouldBe listOf("Current")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("current:book:${ids.first().value}"))
        compose.onNode(hasText(titles.first()) and hasAnyAncestor(hasTestTag("current:book:${ids.first().value}"))).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("current:book:${ids[1].value}"))
        compose.onNode(hasText(titles[1]) and hasAnyAncestor(hasTestTag("current:book:${ids[1].value}"))).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("current:book:${ids.first().value}"))
        val twoColumnTitleHeight = compose.onNode(
          hasText(titles.first()) and hasAnyAncestor(hasTestTag("current:book:${ids.first().value}")),
        ).fetchSemanticsNode().boundsInRoot.height
        // Capture the actual library after Coil has drawn the optional local cover images.
        compose.waitForIdle()
        UiDevice.getInstance(instrumentation).waitForIdle()
        UiDevice.getInstance(instrumentation).takeScreenshot(File(directory, "library.png"))

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("current:book:${ids.last().value}"))
        compose.onNode(hasText(titles.last()) and hasAnyAncestor(hasTestTag("current:book:${ids.last().value}"))).assertIsDisplayed()

        compose.onNodeWithContentDescription("Library options").performClick()
        compose.onNodeWithText("Settings").performClick()
        // Appearance stays compact on the main settings page and opens its focused editor.
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Books · 2 per row").assertIsDisplayed()
        compose.onNode(hasText("Appearance") and hasClickAction()).performClick()
        compose.onNodeWithText("Library view").assertIsDisplayed()
        listOf("List", "Grid", "Books").forEach { label ->
          compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
        }
        val choiceHeights = listOf("List", "Grid", "Books").map { label ->
          compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot.height
        }
        choiceHeights.distinct().size shouldBe 1
        compose.onNode(hasText("Books") and hasClickAction()).assertIsSelected()
        UiDevice.getInstance(instrumentation).waitForIdle()
        UiDevice.getInstance(instrumentation).takeScreenshot(File(directory, "library-settings.png"))
        compose.onNode(hasText("List") and hasClickAction()).performClick()
        compose.waitUntil { runBlocking { layout.data.first() == GridMode.LIST } }
        compose.onNode(hasText("Grid") and hasClickAction()).performClick()
        compose.waitUntil { runBlocking { layout.data.first() == GridMode.GRID } }
        compose.onNode(hasText("Books") and hasClickAction()).performClick()
        compose.waitUntil { runBlocking { layout.data.first() == GridMode.BOOKS } }
        compose.onNodeWithText("Books per row").assertIsDisplayed()
        compose.onNodeWithContentDescription("2 per row").assertIsSelected()
        compose.onNodeWithContentDescription("3 per row").performClick()
        compose.waitUntil { runBlocking { booksPerRow.data.first() == 3 } }
        // Finish Compose state updates before ActivityScenario waits for idle.
        compose.waitForIdle()
        activity.recreate()
        runBlocking { layout.data.first() } shouldBe GridMode.BOOKS
        runBlocking { booksPerRow.data.first() } shouldBe 3
        compose.onNodeWithText("Books per row").assertIsDisplayed()
        compose.onNodeWithContentDescription("3 per row").assertIsSelected()
        UiDevice.getInstance(instrumentation).takeScreenshot(File(directory, "three-columns-settings.png"))
        UiDevice.getInstance(instrumentation).pressBack()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("current:book:${ids.first().value}"))
        compose.onNode(hasText(titles.first()) and hasAnyAncestor(hasTestTag("current:book:${ids.first().value}"))).assertIsDisplayed()
        val firstRow = titles.take(3).mapIndexed { index, title ->
          compose.onNode(
            hasText(title) and hasAnyAncestor(hasTestTag("current:book:${ids[index].value}")),
          ).fetchSemanticsNode().boundsInRoot
        }
        firstRow[0].top shouldBe firstRow[1].top
        firstRow[1].top shouldBe firstRow[2].top
        firstRow[0].height shouldBe firstRow[1].height
        assertTrue("Short titles should not reserve an empty second line", firstRow[2].height < firstRow[0].height)
        assertTrue("Compact titles should use the smaller type scale", firstRow.first().height < twoColumnTitleHeight)
        compose.waitForIdle()
        UiDevice.getInstance(instrumentation).takeScreenshot(File(directory, "library-three-columns.png"))

        val coverSource = File(directory, "chrysalis-4.jpg").takeIf(File::exists) ?: File(directory, "crop-fixture.png").also { file ->
          val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
          bitmap.eraseColor(android.graphics.Color.BLUE)
          file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
          bitmap.recycle()
        }
        val oldCover = runBlocking { books.get(ids.last())!!.cover }
        compose.runOnIdle { navigator.goTo(Destination.EditCover(ids.last(), coverSource.toUri())) }
        compose.onNodeWithText("Book (2:3)").assertIsSelected()
        compose.onNodeWithText("Square (1:1)").performClick()
        compose.onNodeWithText("Square (1:1)").assertIsSelected()
        compose.onNodeWithText("Book (2:3)").performClick()
        compose.waitForIdle()
        UiDevice.getInstance(instrumentation).takeScreenshot(File(directory, "book-crop.png"))
        compose.onNodeWithText(instrumentation.targetContext.getString(voice.core.strings.R.string.dialog_confirm)).performClick()
        compose.waitUntil(10_000) { runBlocking { books.get(ids.last())!!.cover != oldCover } }
        val saved = runBlocking { books.get(ids.last())!!.cover!! }
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(saved.path, size)
        assertTrue("Saved cover must decode to a valid image", size.outWidth > 0 && size.outHeight > 0)
        assertTrue(abs(size.outWidth - size.outHeight * 2f / 3f) <= 2f)
      }
    } finally {
      // A manual emulator run can retain its demo library for interactive visual review.
      if (InstrumentationRegistry.getArguments().getString("keepBooksDemo") != "true") {
        runBlocking {
          books.setAllInactiveExcept(previousActive)
          layout.updateData { oldLayout }
          booksPerRow.updateData { oldBooksPerRow }
          onboarding.updateData { oldOnboarding }
        }
      }
    }
  }
}
