package voice.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.swipeUp
import androidx.datastore.core.DataStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.zacsweers.metro.Inject
import io.kotest.matchers.shouldBe
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
import voice.core.data.LibraryShelf
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.repo.LibraryOrganisationRepo
import voice.core.data.store.BooksPerRowStore
import voice.core.data.store.GridModeStore
import voice.core.data.store.OnboardingCompletedStore
import voice.core.scanner.MediaScanTrigger
import java.io.File
import java.time.Instant

class ShelvesLibraryTest {
  @get:Rule
  @Suppress("DEPRECATION")
  val compose = createEmptyComposeRule()

  @Inject lateinit var books: BookContentRepo

  @Inject lateinit var chapters: ChapterRepo

  @Inject lateinit var organisation: LibraryOrganisationRepo

  @Inject lateinit var scanner: MediaScanTrigger
  @field:[Inject GridModeStore] lateinit var layout: DataStore<GridMode>
  @field:[Inject BooksPerRowStore] lateinit var columns: DataStore<Int>
  @field:[Inject OnboardingCompletedStore] lateinit var onboarding: DataStore<Boolean>

  @Test fun largeLibraryDraggingAccessibleOrderingAndDisplayVariants() {
    rootGraphAs<TestGraph>().inject(this)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val directory = File(instrumentation.targetContext.filesDir, "books-demo").apply { mkdirs() }
    val oldBooks = runBlocking { books.all() }
    val oldLayout = runBlocking { layout.data.first() }
    val oldColumns = runBlocking { columns.data.first() }
    val oldOnboarding = runBlocking { onboarding.data.first() }
    val ids = (0 until 240).map { BookId("shelves-large-$it") }
    val oldFont = device.executeShellCommand("settings get system font_scale").trim()
    val oldNight = device.executeShellCommand("cmd uimode night").substringAfter(":").trim()
    val oldLocales = instrumentation.targetContext.getSystemService(
      android.app.LocaleManager::class.java,
    ).applicationLocales.toLanguageTags()
    val shelves = mutableListOf<String>()
    val chapter = ChapterId("file:///shelves-large.m4a")
    fun capture(name: String) {
      compose.waitForIdle()
      device.takeScreenshot(File(directory, "shelves-$name.png"))
    }
    try {
      runBlocking {
        scanner.scanAndAwait()
        onboarding.updateData { true }
        layout.updateData { GridMode.BOOKS }
        columns.updateData { 3 }
        chapters.put(Chapter(chapter, "Chapter 1", 120_000, Instant.EPOCH, emptyList()))
        ids.forEachIndexed { index, id ->
          books.put(
            BookContent(
              id, 1f, false, true, Instant.EPOCH, "Author", "Book ${index + 1} — A Long Adventure on My Bookshelf",
              Instant.EPOCH.plusSeconds(index.toLong()), listOf(chapter), chapter, 0,
              File(directory, "chrysalis-${index % 3 + 2}.jpg").takeIf(File::exists), 0f, null, null, null, null,
            ),
          )
        }
        books.setAllInactiveExcept(ids)
        shelves += organisation.createShelf("Reading room").after.shelves.single { it.name == "Reading room" }.id
        shelves += organisation.createShelf("Later").after.shelves.single { it.name == "Later" }.id
        organisation.move(ids.toSet(), shelves[0])
        // Fixture IDs are reused between runs; preserve real user order but reset this test's own order.
        organisation.reorder(shelves[0], ids.map { "book:${it.value}" })
      }
      ActivityScenario.launch(MainActivity::class.java).use { activity ->
        compose.waitUntil(15_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        capture("large-library")
        device.executeShellCommand("dumpsys gfxinfo ${instrumentation.targetContext.packageName} reset")
        val window = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        repeat(8) {
          device.swipe(window.center.x.toInt(), (window.bottom - 150).toInt(), window.center.x.toInt(), (window.top + 150).toInt(), 50)
          compose.waitForIdle()
        }
        File(
          directory,
          "shelves-frame-stats.txt",
        ).writeText(device.executeShellCommand("dumpsys gfxinfo ${instrumentation.targetContext.packageName}"))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Organise Reading room").performClick()
        val rearrange = hasText("Rearrange") or hasText("Show all to rearrange")
        compose.waitUntil(10_000) { compose.onAllNodes(rearrange).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(rearrange).performClick()
        val firstTag = "reorder:book:${ids[0].value}"
        val secondTag = "reorder:book:${ids[1].value}"
        val first = compose.onNodeWithTag(firstTag).fetchSemanticsNode().boundsInRoot.center
        val second = compose.onNodeWithTag(secondTag).fetchSemanticsNode().boundsInRoot.center
        val root = compose.onNode(isRoot() and hasAnyDescendant(hasTestTag("shelf-reorder-grid")))
        compose.mainClock.autoAdvance = false
        root.performTouchInput {
          down(first)
          advanceEventTime(650)
        }
        compose.mainClock.advanceTimeBy(650)
        repeat(12) { step ->
          root.performTouchInput { moveTo(first + (second - first) * ((step + 1) / 12f)) }
          compose.mainClock.advanceTimeByFrame()
        }
        root.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        val moved = compose.onNodeWithTag(firstTag).fetchSemanticsNode().boundsInRoot.center
        capture("drag-after")
        check(moved.x > first.x) { "Long-press drag did not change the draft order: $first -> $moved; target=$second" }
        runBlocking { organisation.get().itemOrder(shelves[0]).first() } shouldBe "book:${ids[0].value}"
        // The accessibility action shares the same draft path.
        val actions = compose.onNodeWithTag(firstTag).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.single { it.label == "Move earlier" }.action() shouldBe true }
        // Holding at the viewport edge must advance beyond the initially visible rows.
        val start = compose.onNodeWithTag(firstTag).fetchSemanticsNode().boundsInRoot.center
        val bounds = compose.onNodeWithTag("shelf-reorder-grid").fetchSemanticsNode().boundsInRoot
        val edge = Offset(start.x, bounds.bottom - 24f)
        compose.mainClock.autoAdvance = false
        root.performTouchInput {
          down(start)
          advanceEventTime(650)
        }
        compose.mainClock.advanceTimeBy(650)
        repeat(24) { step ->
          root.performTouchInput { moveTo(start + (edge - start) * ((step + 1) / 24f)) }
          compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(2_000)
        root.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(10_000) { runBlocking { organisation.get().itemOrder(shelves[0]).indexOf("book:${ids[0].value}") > 8 } }
        compose.onNodeWithContentDescription("Library options").performClick()
        compose.onNodeWithText("Organise library").performClick()
        compose.onNodeWithContentDescription("Library options").performClick()
        compose.onNodeWithText("Reorder shelves").performClick()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithText("Move earlier").performClick()
        activity.recreate()
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(10_000) { runBlocking { organisation.get().shelves.sortedBy { it.position }.first().id == shelves[1] } }
        compose.onNodeWithText("Done").performClick()
        listOf(GridMode.LIST, GridMode.GRID, GridMode.BOOKS).forEach { mode ->
          runBlocking { layout.updateData { mode } }
          compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
          capture("large-${mode.name.lowercase()}")
        }
        device.setOrientationLeft()
        android.os.SystemClock.sleep(1_200)
        device.waitForIdle()
        compose.waitForIdle()
        capture("landscape")
        device.setOrientationNatural()
        android.os.SystemClock.sleep(1_200)
        device.executeShellCommand("cmd uimode night no")
        android.os.SystemClock.sleep(700)
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Reading room")).fetchSemanticsNodes().isNotEmpty() }
        capture("light-dynamic")
        device.executeShellCommand("cmd uimode night yes")
        device.executeShellCommand("settings put system font_scale 1.6")
        android.os.SystemClock.sleep(700)
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Reading room")).fetchSemanticsNodes().isNotEmpty() }
        capture("large-font")
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("cmd locale set-app-locales ${instrumentation.targetContext.packageName} --locales ar")
        android.os.SystemClock.sleep(700)
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Reading room")).fetchSemanticsNodes().isNotEmpty() }
        capture("rtl")
      }
    } catch (failure: Throwable) {
      compose.mainClock.autoAdvance = true
      capture("large-failure")
      throw failure
    } finally {
      device.unfreezeRotation()
      device.executeShellCommand("settings put system font_scale $oldFont")
      device.executeShellCommand("cmd uimode night $oldNight")
      device.executeShellCommand("cmd locale set-app-locales ${instrumentation.targetContext.packageName} --locales \"$oldLocales\"")
      runBlocking {
        shelves.forEach { organisation.removeShelf(it) }
        ids.forEach { id -> books.get(id)?.let { books.put(it.copy(isActive = false)) } }
        books.setAllInactiveExcept(oldBooks.filter { it.isActive }.map { it.id })
        layout.updateData { oldLayout }
        columns.updateData { oldColumns }
        onboarding.updateData { oldOnboarding }
      }
    }
  }

  @Test fun shelvesSelectionOrderUndoAndWholeSeriesSurviveRecreation() {
    rootGraphAs<TestGraph>().inject(this)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val directory = File(instrumentation.targetContext.filesDir, "books-demo").apply { mkdirs() }
    val oldBooks = runBlocking { books.all() }
    val oldLayout = runBlocking { layout.data.first() }
    val oldColumns = runBlocking { columns.data.first() }
    val oldOnboarding = runBlocking { onboarding.data.first() }
    val chapter = ChapterId("file:///shelves-fixture.m4a")
    val titles =
      listOf(
        "Chrysalis: Book 2 — Upping the Ante",
        "Chrysalis: Book 3 — Antelligent Design",
        "Chrysalis: Book 4 — Between a Rock and a Carapace",
        "The Dungeon Anarchist’s Cookbook, Dungeon Crawler Carl, Book 3",
        "A Very Long Audiobook Title Without Artwork — An Unabridged Adventure",
        "Project Hail Mary",
      )
    val ids = titles.indices.map { BookId("shelves-fixture-$it") }
    val created = mutableListOf<String>()
    runBlocking {
      scanner.scanAndAwait()
      val oldOrganisation = organisation.get()
      oldOrganisation.series.filter { group ->
        oldOrganisation.placements.filter { it.seriesId == group.id }.all {
          it.bookId in
            ids.map(BookId::value)
        }
      }
        .forEach { organisation.saveSeries(it.id, it.name, emptyList()) }
      onboarding.updateData { true }
      layout.updateData { GridMode.FOLLOW_DEVICE }
      columns.updateData { 3 }
      chapters.put(Chapter(chapter, "Chapter 1", 120_000, Instant.EPOCH, emptyList()))
      titles.forEachIndexed { index, title ->
        books.put(
          BookContent(
            ids[index], 1f, false, true, Instant.EPOCH, "Demo author", title, Instant.EPOCH,
            listOf(chapter), chapter,
            if (index == 1 || index == 5) {
              30_000
            } else if (index == 0) {
              120_000
            } else {
              0
            },
            if (index <
              3
            ) {
              File(directory, "chrysalis-${index + 2}.jpg").takeIf(File::exists)
            } else {
              null
            },
            0f, null, null, null, index.toString(),
          ),
        )
      }
      books.setAllInactiveExcept(ids)
      organisation.saveSeries(null, "Chrysalis", ids.take(3))
    }
    fun option(label: String) {
      compose.onNodeWithContentDescription("Library options").performClick()
      compose.onNodeWithText(label).performClick()
    }
    fun scrollTo(tag: String) {
      compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
    }
    fun capture(name: String) {
      compose.waitForIdle()
      device.waitForIdle()
      device.takeScreenshot(File(directory, "shelves-$name.png"))
    }
    try {
      ActivityScenario.launch(MainActivity::class.java).use { activity ->
        try {
          compose.waitUntil(15_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
          scrollTo("current:book:${ids[1].value}")
          compose.onNodeWithTag("current:book:${ids[1].value}").assertIsDisplayed()
          option("Organise library")
          option("New shelf")
          compose.onNodeWithText("Shelf name").performTextInput("Fantasy")
          compose.onNodeWithText("Create").performClick()
          compose.waitUntil(10_000) { runBlocking { organisation.get().shelves.any { it.name == "Fantasy" } } }
          compose.onNodeWithText("Done").performClick()
          val fantasy = runBlocking { organisation.get().shelves.single { it.name == "Fantasy" }.id }.also(created::add)
          // Organise mode supports a direct long-press drag from a book to a shelf heading.
          option("Organise library")
          val draggedBook = "other:book:${ids[3].value}"
          scrollTo(draggedBook)
          val from = compose.onNodeWithTag(draggedBook).fetchSemanticsNode().boundsInRoot.center
          val destination = compose.onNodeWithTag("shelf-header:$fantasy").fetchSemanticsNode().boundsInRoot.center
          compose.mainClock.autoAdvance = false
          compose.onRoot().performTouchInput {
            down(from)
            advanceEventTime(650)
          }
          compose.mainClock.advanceTimeBy(650)
          repeat(12) { step ->
            compose.onRoot().performTouchInput { moveTo(from + (destination - from) * ((step + 1) / 12f)) }
            compose.mainClock.advanceTimeByFrame()
          }
          compose.onRoot().performTouchInput { up() }
          compose.mainClock.autoAdvance = true
          compose.waitUntil(10_000) {
            runBlocking { organisation.get().placements.single { it.bookId == ids[3].value }.shelfId == fantasy }
          }
          compose.onNodeWithText("Done").performClick()
          // Rename/empty shelf changes don't require a book write to survive recreation.
          activity.recreate()
          compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Fantasy"))
          compose.onNodeWithContentDescription("Organise Fantasy").performClick()
          compose.onNodeWithText("Add books").performClick()
          val seriesId = runBlocking { organisation.get().series.single { it.name == "Chrysalis" }.id }
          val stack = "other:series:$seriesId"
          scrollTo(stack)
          compose.onNodeWithTag(stack).performClick()
          compose.onNodeWithText("3 selected").assertIsDisplayed()
          compose.onNodeWithText("Done").performClick()
          compose.waitUntil(10_000) {
            runBlocking {
              organisation.get().placements.filter { it.bookId in ids.take(3).map { id -> id.value } }.all {
                it.shelfId ==
                  fantasy
              }
            }
          }
          scrollTo("$fantasy:series:$seriesId")
          capture("home-three")
          compose.onNodeWithTag("$fantasy:series:$seriesId").performClick()
          compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-revealed")).fetchSemanticsNodes().isNotEmpty() }
          compose.onNodeWithTag("series-grid").performScrollToNode(hasText(titles[0]))
          capture("whole-series")
          compose.onNodeWithContentDescription("Close").performClick()
          compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-details")).fetchSemanticsNodes().isEmpty() }
          compose.onNodeWithContentDescription("Filter shelves").performClick()
          compose.onNodeWithText("Completed").performClick()
          scrollTo("$fantasy:series:$seriesId")
          compose.onNodeWithTag("$fantasy:series:$seriesId").performClick()
          compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-revealed")).fetchSemanticsNodes().isNotEmpty() }
          compose.onNodeWithText("Show entire series").performClick()
          compose.onNodeWithText("3 books").assertIsDisplayed()
          compose.onNodeWithContentDescription("Close").performClick()
          compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-details")).fetchSemanticsNodes().isEmpty() }
          compose.onNodeWithText("Reset").performClick()
          // Rearranging is an isolated draft; Back discards it and Done persists it.
          compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Other books"))
          // Bring the heading above the playback FAB before injecting an actual touch.
          compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
          compose.onNodeWithContentDescription("Organise Other books").performClick()
          compose.onNodeWithText("Rearrange").performClick()
          val before = runBlocking { organisation.get().itemOrder(LibraryShelf.OTHER) }
          val firstVisible = before.first { it.startsWith("book:shelves-fixture") }
          compose.onNodeWithTag("other:$firstVisible").performTouchInput { click() }
          compose.onNodeWithText("Move later").performClick()
          compose.onNodeWithText("Cancel").performClick()
          runBlocking { organisation.get().itemOrder(LibraryShelf.OTHER) } shouldBe before
          compose.onNodeWithContentDescription("Organise Other books").performClick()
          compose.onNodeWithText("Rearrange").performClick()
          compose.onNodeWithTag("other:$firstVisible").performTouchInput { click() }
          compose.onNodeWithText("Move later").performClick()
          activity.recreate()
          compose.onNodeWithText("Done").performClick()
          compose.waitUntil(10_000) { runBlocking { organisation.get().itemOrder(LibraryShelf.OTHER) != before } }
          // Removing a shelf never removes a book, and Undo restores the full series home.
          compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Fantasy"))
          compose.onNodeWithContentDescription("Organise Fantasy").performClick()
          compose.onNodeWithText("Remove shelf").performClick()
          compose.onNodeWithText("Remove shelf").performClick()
          compose.waitUntil(10_000) { runBlocking { organisation.get().shelves.none { it.id == fantasy } } }
          compose.onNodeWithText("Undo").performClick()
          compose.waitUntil(10_000) { runBlocking { organisation.get().shelves.any { it.id == fantasy } } }
          listOf(2, 3).forEach { count ->
            runBlocking { columns.updateData { count } }
            scrollTo("$fantasy:series:$seriesId")
            capture("books-$count")
          }
          // The same standalone book can open from Current or its home without shared-cover collisions.
          listOf("current:book:${ids[5].value}", "other:book:${ids[5].value}").forEach { origin ->
            scrollTo(origin)
            compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(origin))).performClick()
            compose.waitUntil(10_000) {
              compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Close")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(titles[5]).assertIsDisplayed()
            device.pressBack()
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(origin)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(origin).assertIsDisplayed()
          }
        } catch (failure: Throwable) {
          capture("failure")
          compose.onRoot().printToLog("ShelfFailure")
          throw failure
        }
      }
    } finally {
      runBlocking {
        val state = organisation.get()
        state.series.filter { group -> state.placements.filter { it.seriesId == group.id }.all { it.bookId in ids.map(BookId::value) } }
          .forEach { organisation.saveSeries(it.id, it.name, emptyList()) }
        created.forEach { organisation.removeShelf(it) }
        ids.forEach { id -> books.get(id)?.let { books.put(it.copy(isActive = false)) } }
        books.setAllInactiveExcept(oldBooks.filter { it.isActive }.map { it.id })
        layout.updateData { oldLayout }
        columns.updateData { oldColumns }
        onboarding.updateData { oldOnboarding }
      }
    }
  }
}
