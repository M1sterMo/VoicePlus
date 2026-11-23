package voice.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.core.DataStore
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import voice.core.common.rootGraphAs
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.GridMode
import voice.core.data.ListeningSession
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.repo.ListeningSessionRepo
import voice.core.data.store.GridModeStore
import voice.core.data.store.OnboardingCompletedStore
import java.io.File
import java.time.Instant

/** The About sheet, rating from it and from the end-of-book card, and the rating in the library. */
class AboutBookTest {
  @get:Rule
  @Suppress("DEPRECATION")
  val compose = createEmptyComposeRule()

  @Inject lateinit var books: BookContentRepo

  @Inject lateinit var chapters: ChapterRepo

  @Inject lateinit var sessions: ListeningSessionRepo
  @field:[Inject GridModeStore] lateinit var layout: DataStore<GridMode>
  @field:[Inject OnboardingCompletedStore] lateinit var onboarding: DataStore<Boolean>

  @Test
  fun aboutSheetRatesAndEndOfBookAsksOnce() {
    rootGraphAs<TestGraph>().inject(this)
    val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "about-demo").apply { mkdirs() }
    fun screenshot(name: String) {
      compose.waitForIdle()
      val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
      File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    val chapter = ChapterId("file:///about-fixture.m4a")
    val listening = BookId("about-fixture-listening")
    val finished = BookId("about-fixture-finished")
    fun book(
      id: BookId,
      name: String,
      position: Long,
    ) = BookContent(
      id = id, name = name, author = "Andy Weir", playbackSpeed = 1f, skipSilence = false, isActive = true,
      lastPlayedAt = Instant.now(), addedAt = Instant.EPOCH, chapters = listOf(chapter), currentChapter = chapter,
      positionInChapter = position, cover = null, gain = 0f, genre = "Science fiction", narrator = "Ray Porter",
      series = null, part = null,
      synopsis = "Ryland Grace is the sole survivor on a desperate, last-chance mission.\n\n" +
        "If he fails, humanity and the earth itself will perish. ".repeat(12),
    )
    val oldLayout = runBlocking { layout.data.first() }
    val oldOnboarding = runBlocking { onboarding.data.first() }
    val previousActive = runBlocking { books.all().filter { it.isActive }.map { it.id } }
    runBlocking {
      chapters.put(Chapter(chapter, "Chapter", 120_000L, Instant.EPOCH, emptyList()))
      books.put(book(listening, "Project Hail Mary", 30_000L))
      books.put(book(finished, "The Martian", 119_000L))
      books.setAllInactiveExcept(listOf(listening, finished))
      onboarding.updateData { true }
      layout.updateData { GridMode.LIST }
    }
    try {
      ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitUntil(15_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Project Hail Mary"))
        compose.onAllNodes(hasText("Project Hail Mary") and hasClickAction()).onFirst().performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("About this book")).fetchSemanticsNodes().isNotEmpty() }
        screenshot("menu")
        compose.onNodeWithText("About this book").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Narrated by Ray Porter")).fetchSemanticsNodes().isNotEmpty() }
        screenshot("about")
        compose.onNodeWithContentDescription("Rate 4 of 5").performClick()
        compose.waitUntil(5_000) { runBlocking { books.get(listening)?.rating == 4 } }
        // Tapping the current rating clears it.
        compose.onNodeWithContentDescription("Rate 4 of 5").performClick()
        compose.waitUntil(5_000) { runBlocking { books.get(listening)?.rating == null } }
        compose.onNodeWithContentDescription("Rate 4 of 5").performClick()
        compose.waitUntil(5_000) { runBlocking { books.get(listening)?.rating == 4 } }
        screenshot("about-rated")
        compose.onNodeWithText("More").performClick()
        screenshot("about-expanded")
        compose.onNodeWithContentDescription("Edit book").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Synopsis")).fetchSemanticsNodes().isNotEmpty() }
        screenshot("edit-book")
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("4 out of 5 stars")).fetchSemanticsNodes().isNotEmpty() }
        screenshot("library-rated")

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("The Martian"))
        compose.onAllNodes(hasText("The Martian") and hasClickAction()).onFirst().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("You finished The Martian")).fetchSemanticsNodes().isNotEmpty() }
        screenshot("rate-prompt")
        compose.onNodeWithText("Not now").performClick()
        compose.waitUntil(5_000) { runBlocking { books.get(finished)?.ratingPrompted == true } }
        check(runBlocking { books.get(finished)?.rating } == null)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("You finished The Martian")).fetchSemanticsNodes().isEmpty() }
        screenshot("player-after-prompt")
      }
    } finally {
      runBlocking {
        listOf(listening, finished).forEach { id -> books.get(id)?.let { books.put(it.copy(isActive = false)) } }
        books.setAllInactiveExcept(previousActive)
        layout.updateData { oldLayout }
        onboarding.updateData { oldOnboarding }
      }
    }
  }

  /** Sheets open fully: their last rows show without a swipe. */
  @Test
  fun appearanceAndFinishedBookSheetsOpenFully() {
    rootGraphAs<TestGraph>().inject(this)
    val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "about-demo").apply { mkdirs() }
    fun screenshot(name: String) {
      compose.waitForIdle()
      val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
      File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    val chapter = ChapterId("file:///about-sheets-fixture.m4a")
    val finished = BookId("about-sheets-finished")
    val oldLayout = runBlocking { layout.data.first() }
    val oldOnboarding = runBlocking { onboarding.data.first() }
    val previousActive = runBlocking { books.all().filter { it.isActive }.map { it.id } }
    runBlocking {
      chapters.put(Chapter(chapter, "Chapter", 7_200_000L, Instant.EPOCH, emptyList()))
      books.put(
        BookContent(
          id = finished, name = "Sheet Fixture", author = "Andy Weir", playbackSpeed = 1.5f, skipSilence = false,
          isActive = true, lastPlayedAt = Instant.now(), addedAt = Instant.EPOCH, chapters = listOf(chapter),
          currentChapter = chapter, positionInChapter = 7_199_000L, cover = coverArt(directory), gain = 0f, genre = null,
          narrator = null, series = null, part = null, rating = 5, ratingPrompted = true,
        ),
      )
      val end = Instant.now().minusSeconds(3_600)
      sessions.addSession(
        ListeningSession(
          bookId = finished,
          chapterId = chapter,
          startedAt = end.minusSeconds(4_800),
          endedAt = end,
          durationMs = 4_800_000L,
          startPositionMs = 0L,
          endPositionMs = 7_199_000L,
        ),
      )
      books.setAllInactiveExcept(listOf(finished))
      onboarding.updateData { true }
      layout.updateData { GridMode.BOOKS }
    }
    try {
      ActivityScenario.launch(MainActivity::class.java).use {
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("Settings")).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(1_000)
        screenshot("library-jacket")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Listening stats")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("Appearance") and hasClickAction()).onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Books per row")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Books per row").assertIsDisplayed()
        screenshot("sheet-appearance")
        Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Books per row")).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithText("Listening stats").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Sheet Fixture")).fetchSemanticsNodes().isNotEmpty() }
        // Covers follow the library's look: jackets in Books, square covers in Grid.
        fun awaitCover(form: String) {
          compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("finished-cover:$form"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
          }
          // Room for the cover to load and the jacket to be drawn.
          Thread.sleep(1_000)
        }
        awaitCover("jacket")
        screenshot("stats-page")
        runBlocking { layout.updateData { GridMode.GRID } }
        awaitCover("square")
        screenshot("stats-page-grid")
        runBlocking { layout.updateData { GridMode.BOOKS } }
        awaitCover("jacket")
        compose.onAllNodes(hasText("Sheet Fixture") and hasClickAction()).onFirst().performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Book length")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Book length").assertIsDisplayed()
        compose.onNodeWithText("2h 0m").assertIsDisplayed()
        screenshot("sheet-finished-book")
      }
    } finally {
      runBlocking {
        sessions.deleteAllForBook(finished)
        books.get(finished)?.let { books.put(it.copy(isActive = false)) }
        books.setAllInactiveExcept(previousActive)
        layout.updateData { oldLayout }
        onboarding.updateData { oldOnboarding }
      }
    }
  }

  /** Cover art with detail near the spine, so the drawn jacket's spine and binding show in screenshots. */
  private fun coverArt(directory: File): File = File(directory, "sheet-cover.jpg").also { file ->
    val bitmap = android.graphics.Bitmap.createBitmap(600, 900, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(196, 64, 48))
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    for (x in 0 until 600 step 40) canvas.drawRect(x.toFloat(), 0f, x + 12f, 900f, paint)
    paint.textSize = 96f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    canvas.drawText("SHEET", 300f, 420f, paint)
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    bitmap.recycle()
  }
}
