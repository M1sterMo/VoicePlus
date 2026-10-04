package voice.app

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.swipe
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
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.store.BooksPerRowStore
import voice.core.data.store.GridModeStore
import voice.core.data.store.OnboardingCompletedStore
import voice.core.scanner.MediaScanTrigger
import java.io.File
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

class SeriesGroupingTest {
  @get:Rule
  @Suppress("DEPRECATION")
  val compose = createEmptyComposeRule()

  @Inject lateinit var books: BookContentRepo

  @Inject lateinit var organisation: voice.core.data.repo.LibraryOrganisationRepo

  @Inject lateinit var chapters: ChapterRepo

  @Inject lateinit var scanner: MediaScanTrigger
  @field:[Inject GridModeStore] lateinit var layout: DataStore<GridMode>
  @field:[Inject BooksPerRowStore] lateinit var booksPerRow: DataStore<Int>
  @field:[Inject OnboardingCompletedStore] lateinit var onboarding: DataStore<Boolean>

  @Test
  fun createOpenRenameRemoveAndUngroupAcrossLibraryLayouts() {
    rootGraphAs<TestGraph>().inject(this)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Suppress("DEPRECATION")
    instrumentation.setInTouchMode(true)
    val directory = File(instrumentation.targetContext.filesDir, "books-demo").apply { mkdirs() }
    val titles =
      listOf(
        "Chrysalis: Book 2 — Upping the Ante",
        "Chrysalis: Book 3 — Antelligent Design",
        "Chrysalis: Book 4 — Between a Rock and a Carapace",
        "Project Hail Mary",
        "Chrysalis: Book 5 — The Antventure Continues",
        "Chrysalis: Book 6 — A Very Long Title for a Small Book Cover",
        "Chrysalis: Book 7 — Beneath the Surface",
        "Chrysalis: Book 8 — The Long Road Home",
        "Chrysalis: Book 9 — A New Beginning",
        "Chrysalis: Book 10 — Beyond the Colony",
        "Chrysalis: Book 11 — The Long Way Forward",
      )
    val covers =
      listOf(
        "chrysalis-2", "chrysalis-3", "chrysalis-4", "project-hail-mary", "chrysalis-2", "chrysalis-3",
        "chrysalis-4", "chrysalis-2", "chrysalis-3", "chrysalis-4", "chrysalis-2",
      )
    val chapter = ChapterId("file:///series-fixture.m4a")
    val ids = titles.indices.map { BookId("series-fixture-$it") }
    val oldLayout = runBlocking { layout.data.first() }
    val oldCount = runBlocking { booksPerRow.data.first() }
    val oldOnboarding = runBlocking { onboarding.data.first() }
    val previousActive = runBlocking { books.all().filter { it.isActive }.map { it.id } }
    val originals = titles.mapIndexed { index, title ->
      BookContent(
        id = ids[index], name = title, author = "Demo author", playbackSpeed = 1.5f,
        skipSilence = false, isActive = true, lastPlayedAt = Instant.now().minusSeconds(index.toLong()),
        addedAt = Instant.EPOCH, chapters = listOf(chapter), currentChapter = chapter,
        positionInChapter = when (index) {
          0 -> 120_000L
          1 -> 30_000L
          else -> 0L
        },
        cover = File(directory, "${covers[index]}.jpg").takeIf(File::exists), gain = 0f,
        genre = null, narrator = null, series = if (index != 3) "Chrysalis" else null,
        part = Regex("Book (\\d+)").find(title)?.groupValues?.get(1),
      )
    }
    runBlocking {
      scanner.scanAndAwait()
      val state = organisation.get()
      state.series.filter { group -> state.placements.filter { it.seriesId == group.id }.all { it.bookId in ids.map(BookId::value) } }
        .forEach { organisation.saveSeries(it.id, it.name, emptyList()) }
      onboarding.updateData { true }
      layout.updateData { GridMode.BOOKS }
      booksPerRow.updateData { 3 }
      chapters.put(Chapter(chapter, "Chapter", 120_000L, Instant.EPOCH, emptyList()))
      originals.forEach { books.put(it) }
      books.setAllInactiveExcept(ids)
    }
    fun screenshot(name: String) {
      compose.waitForIdle()
      val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
      File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    fun waitForGroup(name: String?) {
      compose.waitUntil(10_000) {
        runBlocking {
          val state = organisation.get()
          state.series.find {
            it.id ==
              state.placements.find { it.bookId == ids[1].value }?.seriesId
          }?.name ==
            name
        }
      }
      compose.waitForIdle()
      compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-books")).fetchSemanticsNodes().isEmpty() }
    }
    fun stackKey(name: String = "Chrysalis"): String = runBlocking {
      "other:series:${organisation.get().series.single { it.name == name }.id}"
    }
    fun showStack(
      category: String,
      name: String = "Chrysalis",
    ) {
      compose.onNodeWithContentDescription("Filter shelves").performClick()
      compose.onNodeWithText(if (category == "FINISHED") "Completed" else "Not started").performClick()
      compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(stackKey(name)))
      compose.onNodeWithTag(stackKey(name)).assertIsDisplayed()
    }
    fun waitForPrepared() {
      compose.waitUntil(10_000) {
        if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame()
        compose.onAllNodes(hasTestTag("series-revealed")).fetchSemanticsNodes().isNotEmpty()
      }
    }
    fun editStack(name: String = "Chrysalis") {
      showStack("NOT_STARTED", name)
      compose.onNodeWithTag(stackKey(name)).performTouchInput { longClick() }
      compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-books")).fetchSemanticsNodes().isNotEmpty() }
    }
    try {
      ActivityScenario.launch(MainActivity::class.java).use { activity ->
        compose.waitUntil(15_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("current:book:${ids[1].value}"))
        compose.onNodeWithTag("current:book:${ids[1].value}").performTouchInput { longClick() }
        compose.onNodeWithText("Group into series").performClick()
        compose.onNode(hasSetTextAction() and hasText("Series name")).assertIsDisplayed()
        compose.onNodeWithText("Add books").performClick()
        (listOf(0, 2) + (4 until titles.size)).forEach { index ->
          compose.onNodeWithTag("series-picker").performScrollToNode(hasText(titles[index]))
          compose.onNode(hasText(titles[index]) and isToggleable()).performClick()
        }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("series-order:${ids[0].value}").assertIsDisplayed()
        screenshot("series-editor")
        compose.onNodeWithText("Save series").performClick()
        waitForGroup("Chrysalis")
        compose.onNodeWithText(titles[1]).assertIsDisplayed()
        // Covers and title starts align; short Books captions need not reserve a second line.
        listOf(GridMode.BOOKS to 2, GridMode.GRID to 2, GridMode.LIST to 2, GridMode.BOOKS to 3).forEach { (mode, count) ->
          runBlocking {
            layout.updateData { mode }
            booksPerRow.updateData { count }
          }
          compose.waitForIdle()
          showStack("NOT_STARTED")
          val stack = compose.onNodeWithTag(stackKey()).getUnclippedBoundsInRoot()
          val book = compose.onNode(hasText(titles[3]) and hasClickAction()).getUnclippedBoundsInRoot()
          screenshot("series-sizing-${mode.name.lowercase()}-$count")
          compose.onNodeWithTag(stackKey()).assertWidthIsEqualTo(book.right - book.left)
          // Columns can differ by one physical pixel; a 2:3 cover magnifies that rounding in height.
          val heightDifference = abs((stack.bottom - stack.top).value - (book.bottom - book.top).value)
          if (mode != GridMode.BOOKS) {
            check(heightDifference <= 1f) { "$mode ($count columns) has unequal card heights: $stack vs $book" }
          }
          if (mode != GridMode.LIST) {
            val stackTitle = compose.onNode(
              hasText("Chrysalis") and hasAnyAncestor(hasTestTag(stackKey())),
              useUnmergedTree = true,
            ).getUnclippedBoundsInRoot()
            val bookTitle = compose.onNodeWithText(titles[3], useUnmergedTree = true).getUnclippedBoundsInRoot()
            val titleDifference = abs((stackTitle.top - stack.top).value - (bookTitle.top - book.top).value)
            check(titleDifference <= 1f) { "$mode ($count columns) titles are not aligned within their cards" }
            if (mode == GridMode.BOOKS) {
              val front = compose.onNodeWithTag("${stackKey()}:cover:0", useUnmergedTree = true).getUnclippedBoundsInRoot()
              val single = compose.onNodeWithTag("library-book-cover:${ids[3].value}", useUnmergedTree = true).getUnclippedBoundsInRoot()
              check(abs((front.right - front.left - (single.right - single.left)).value) <= 1f)
              check(abs((front.bottom - front.top - (single.bottom - single.top)).value) <= 1f)
              // Semantic bounds include rotation; unclipped layout bounds above measure the unrotated front.
              val frontPixels = compose.onNodeWithTag("${stackKey()}:cover:0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
              (1..2).forEach { layer ->
                val rear = compose.onNodeWithTag("${stackKey()}:cover:$layer", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                check(rear.left < frontPixels.left && rear.right > frontPixels.right) {
                  "Rear books must fan beyond the front cover: $rear vs $frontPixels"
                }
                check(rear.bottom < frontPixels.bottom) { "Rear books must not intrude into the caption" }
              }
              check(abs((stackTitle.top - front.bottom).value - 8f) <= 1f) { "Series cover/caption gap must be 8dp" }
              val labelHeightDifference = abs((stackTitle.bottom - stackTitle.top - (bookTitle.bottom - bookTitle.top)).value)
              check(abs(heightDifference - labelHeightDifference) <= 1f) { "Only title wrapping should change Books tile height" }
              val countBounds = compose.onNode(
                hasText("8 books") and hasAnyAncestor(hasTestTag(stackKey())),
                useUnmergedTree = true,
              ).getUnclippedBoundsInRoot()
              check(abs((countBounds.top - stackTitle.bottom).value - 4f) <= 1f) { "Series title/count gap must be 4dp" }
            }
          }
        }
        editStack()
        fun assertPosition(
          id: BookId,
          position: Int,
        ) {
          compose.onNodeWithTag("series-order:${id.value}")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Position $position of 10"))
        }
        assertPosition(ids[0], 1)
        // A handle drags immediately; holding near the edge must keep scrolling without new input.
        val orderHandle = compose.onNodeWithTag("series-reorder:${ids[0].value}").fetchSemanticsNode().boundsInRoot.center
        val listBounds = compose.onNodeWithTag("series-books").fetchSemanticsNode().boundsInRoot
        val bottom = Offset(orderHandle.x, listBounds.bottom - 20f)
        compose.mainClock.autoAdvance = false
        val editorRoot = compose.onNode(isRoot() and hasAnyDescendant(hasTestTag("series-books")))
        editorRoot.performTouchInput { down(orderHandle) }
        repeat(24) { step ->
          editorRoot.performTouchInput { moveTo(orderHandle + (bottom - orderHandle) * ((step + 1) / 24f)) }
          compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(2_000)
        editorRoot.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        assertPosition(ids[0], 10)
        screenshot("series-editor-reordered")
        compose.onNodeWithText("Save series").performClick()
        waitForGroup("Chrysalis")
        runBlocking { organisation.get().placements.single { it.bookId == ids[0].value }.seriesPosition } shouldBe 9
        activity.recreate()
        editStack()
        compose.onNodeWithTag("series-books").performScrollToNode(hasTestTag("series-order:${ids[0].value}"))
        assertPosition(ids[0], 10)
        // Tap-based ordering is an alternative to dragging. Closing discards that draft.
        compose.onNodeWithTag("series-reorder:${ids[0].value}").performClick()
        compose.onNodeWithText("Move up").performClick()
        assertPosition(ids[0], 9)
        compose.onNodeWithContentDescription("Close").performClick()
        runBlocking { organisation.get().placements.single { it.bookId == ids[0].value }.seriesPosition } shouldBe 9
        editStack()
        compose.onNodeWithTag("series-books").performScrollToNode(hasTestTag("series-order:${ids[0].value}"))
        assertPosition(ids[0], 10)
        // Reverse direction: the same edge-scrolling gesture returns the last book to the top.
        val lastHandle = compose.onNodeWithTag("series-reorder:${ids[0].value}").fetchSemanticsNode().boundsInRoot.center
        val top = Offset(lastHandle.x, compose.onNodeWithTag("series-books").fetchSemanticsNode().boundsInRoot.top + 20f)
        compose.mainClock.autoAdvance = false
        editorRoot.performTouchInput { down(lastHandle) }
        repeat(24) { step ->
          editorRoot.performTouchInput { moveTo(lastHandle + (top - lastHandle) * ((step + 1) / 24f)) }
          compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(2_000)
        editorRoot.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        assertPosition(ids[0], 1)
        // Accessibility actions share the same reorder path in both directions.
        listOf("Move down", "Move up").forEach { label ->
          val actions = compose.onNodeWithTag("series-order:${ids[0].value}").fetchSemanticsNode().config[SemanticsActions.CustomActions]
          compose.runOnIdle { actions.single { it.label == label }.action() shouldBe true }
        }
        compose.onNodeWithTag("series-books").performScrollToIndex(0)
        assertPosition(ids[0], 1)
        compose.onNodeWithText("Add books").performClick()
        compose.onNode(hasSetTextAction() and hasText("Find books")).performTextReplacement("no-matching-title")
        compose.onNodeWithText("No matching books").assertIsDisplayed()
        UiDevice.getInstance(instrumentation).pressBack()
        compose.onNodeWithContentDescription("Back to series editor").performClick()
        assertPosition(ids[0], 1)
        compose.onNodeWithText("Save series").performClick()
        waitForGroup("Chrysalis")
        runBlocking {
          val ordered = originals.filter { it.series != null }.sortedBy { it.part!!.toInt() }
          ordered.forEachIndexed { index, book ->
            organisation.get().placements.single { it.bookId == book.id.value }.seriesPosition shouldBe
              index
          }
        }
        showStack("NOT_STARTED")
        screenshot("series-books-three")
        activity.recreate()
        showStack("NOT_STARTED")
        val sourceCover = compose.onNodeWithTag("${stackKey()}:cover:0", useUnmergedTree = true)
          .fetchSemanticsNode().boundsInRoot
        val sourcePixel = compose.onRoot().captureToImage().toPixelMap()[
          sourceCover.center.x.roundToInt(), sourceCover.center.y.roundToInt(),
        ]
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(stackKey()).performClick()
        waitForPrepared()
        compose.mainClock.advanceTimeBy(160)
        val movingIds = (listOf(2) + (4 until titles.size)).map { ids[it] }
        fun coverBounds(id: BookId) = compose.onNode(
          hasTestTag("book-cover:${id.value}") and hasAnyAncestor(hasTestTag("series-grid")),
          useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val earlyBounds = movingIds.associateWith { coverBounds(it) }
        screenshot("series-separating")
        compose.mainClock.advanceTimeBy(80)
        screenshot("series-opening-240")
        compose.mainClock.advanceTimeBy(80)
        val lateBounds = movingIds.associateWith { coverBounds(it) }
        screenshot("series-opening-320")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        fun distance(
          a: Color,
          b: Color,
        ) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
        // Every visible row must travel, not just fade in at its final location.
        movingIds.forEach { id ->
          val early = earlyBounds.getValue(id).center
          val late = lateBounds.getValue(id).center
          val settled = coverBounds(id).center
          check((early - late).getDistance() > 8f) { "$id did not travel with the stack" }
          check((late - settled).getDistance() < (early - settled).getDistance()) { "$id moved away from its destination" }
        }
        compose.onNodeWithText(titles[0]).assertDoesNotExist()
        compose.onNodeWithText(titles[1]).assertDoesNotExist()
        listOf(2, 4, 5).forEach { index ->
          compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(titles[index]))
          compose.onNodeWithText(titles[index]).assertIsDisplayed()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        screenshot("series-open")
        val panelTop = compose.onNodeWithTag("series-drag-region").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("series-grid").performTouchInput {
          swipe(start = center.copy(y = height * 0.8f), end = center.copy(y = height * 0.2f), durationMillis = 300)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("series-drag-region").fetchSemanticsNode().boundsInRoot.top shouldBe panelTop
        compose.onNodeWithText(titles.last()).assertIsDisplayed()
        compose.onNodeWithTag("series-grid").performScrollToIndex(0)
        compose.onNode(hasText(titles[4]) and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-details")).fetchSemanticsNodes().isEmpty() }
        UiDevice.getInstance(instrumentation).pressBack()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-details")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("series-details").assertIsDisplayed()
        compose.onNodeWithTag("series-drag-region").performTouchInput {
          swipe(
            start = center,
            end = center.copy(y = center.y + 40f),
            durationMillis = 300,
          )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("series-details").assertIsDisplayed()
        check(abs(compose.onNodeWithTag("series-drag-region").fetchSemanticsNode().boundsInRoot.top - panelTop) < 1f) {
          "A short swipe did not return the panel to its resting position"
        }
        compose.mainClock.autoAdvance = false
        val handle = compose.onNodeWithTag("series-drag-region").fetchSemanticsNode().boundsInRoot
        val restingTop = handle.top
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val handleInRoot = handle.center - rootBounds.topLeft
        // Inject in stationary window coordinates: the handle itself follows the finger.
        compose.onRoot().performTouchInput { down(handleInRoot) }
        repeat(10) { step ->
          compose.onRoot().performTouchInput {
            moveTo(handleInRoot.copy(y = handleInRoot.y + (step + 1) * 40f), delayMillis = 32)
          }
          // Real drags redraw between input events; batching them can hide coordinate feedback.
          compose.mainClock.advanceTimeBy(32)
          compose.waitForIdle()
        }
        compose.mainClock.advanceTimeByFrame()
        val heldTop = compose.onNodeWithTag("series-drag-region").fetchSemanticsNode().boundsInRoot.top
        val heldCovers = movingIds.take(3).associateWith { coverBounds(it) }
        check(heldTop > restingTop + 300f) { "Panel did not follow the finger: $restingTop -> $heldTop, root=$rootBounds, handle=$handle" }
        screenshot("series-swipe-held")
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
        val releasedTop = compose.onNodeWithTag("series-drag-region").fetchSemanticsNode().boundsInRoot.top
        screenshot("series-swipe-released")
        check(abs(releasedTop - heldTop) < 1f) { "Swipe release moved the panel instead of returning books to the stack" }
        compose.mainClock.advanceTimeBy(64)
        val settlingTop = compose.onNodeWithTag("series-drag-region").fetchSemanticsNode().boundsInRoot.top
        check(abs(settlingTop - heldTop) < 1f) { "A separate slide-off animation ran before the return-to-stack animation" }
        heldCovers.forEach { (id, held) ->
          val gathering = coverBounds(id)
          check((gathering.center - sourceCover.center).getDistance() < (held.center - sourceCover.center).getDistance()) {
            "$id did not start returning to its stack immediately after swipe release"
          }
        }
        // The first jacket stays upright; rotated jackets can briefly have wider axis-aligned bounds as they fan in.
        val frontId = heldCovers.keys.first()
        check(
          abs(coverBounds(frontId).width - sourceCover.width) < abs(heldCovers.getValue(frontId).width - sourceCover.width),
        ) {
          "The front cover did not shrink back to stack size"
        }
        screenshot("series-swipe-settling")
        // Bound the entire handoff, not just eventual dismissal: no invisible off-screen spring may delay the source.
        compose.mainClock.advanceTimeBy(416)
        compose.onNodeWithTag("series-details").assertDoesNotExist()
        val restoredPixel = compose.onRoot().captureToImage().toPixelMap()[
          sourceCover.center.x.roundToInt(), sourceCover.center.y.roundToInt(),
        ]
        check(distance(sourcePixel, restoredPixel) < 0.1f) { "The source stack was not drawn when the closing animation ended" }
        screenshot("series-swipe-restored")
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-details")).fetchSemanticsNodes().isEmpty() }
        showStack("NOT_STARTED")
        compose.onNodeWithTag(stackKey()).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Close").performClick()
        compose.mainClock.advanceTimeBy(160)
        screenshot("series-gathering")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        showStack("NOT_STARTED")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(stackKey()).performClick()
        compose.mainClock.advanceTimeBy(64)
        // Exercise rapid state reversal without coordinate taps targeting moving content.
        compose.onNodeWithContentDescription("Close").performSemanticsAction(SemanticsActions.OnClick) { it() shouldBe true }
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("series-details").assertDoesNotExist()
        compose.onNodeWithTag(stackKey()).performClick()
        screenshot("series-reopened")
        compose.onNodeWithTag("series-details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        showStack("FINISHED")
        compose.onNodeWithTag(stackKey()).performClick()
        compose.onNodeWithText(titles[0]).assertIsDisplayed()
        compose.onNodeWithText(titles[2]).assertDoesNotExist()
        UiDevice.getInstance(instrumentation).pressBack()
        // Reproduce the reported case: three rows of completed books, including their badges.
        val completedIds = (listOf(0, 2) + (4 until titles.size - 1)).map { ids[it] }
        runBlocking {
          completedIds.forEach { id -> books.put(books.get(id)!!.copy(positionInChapter = 120_000L)) }
        }
        showStack("FINISHED")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(stackKey()).performClick()
        waitForPrepared()
        compose.mainClock.advanceTimeBy(64)
        val completedEarly = completedIds.associateWith { coverBounds(it) }
        compose.mainClock.advanceTimeBy(96)
        screenshot("series-completed-160")
        compose.mainClock.advanceTimeBy(80)
        val completedImage = compose.onRoot().captureToImage().toPixelMap()
        val movingBadgeBounds = completedIds.associateWith { id ->
          compose.onNode(
            hasContentDescription("Completed") and hasAnyAncestor(hasTestTag("series-book:${id.value}")),
            useUnmergedTree = true,
          ).fetchSemanticsNode().boundsInRoot
        }
        screenshot("series-completed-240")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val completedSettled = compose.onRoot().captureToImage().toPixelMap()
        completedIds.forEach { id ->
          val early = completedEarly.getValue(id)
          val settledCover = coverBounds(id)
          val movement = (early.center - settledCover.center).getDistance() + abs(early.width - settledCover.width)
          check(movement > 8f) { "$id completed cover did not move or expand" }
          val moving = movingBadgeBounds.getValue(id)
          val settledBadge = compose.onNode(
            hasContentDescription("Completed") and hasAnyAncestor(hasTestTag("series-book:${id.value}")),
            useUnmergedTree = true,
          ).fetchSemanticsNode().boundsInRoot
          // Sample inside the badge, away from its scalloped edge and check-mark antialiasing.
          val actual = completedImage[moving.center.x.roundToInt(), (moving.top + moving.height * 0.3f).roundToInt()]
          val expected = completedSettled[settledBadge.center.x.roundToInt(), (settledBadge.top + settledBadge.height * 0.3f).roundToInt()]
          check(distance(actual, expected) < 0.2f) { "$id completion badge was not drawn with its travelling cover" }
        }
        screenshot("series-completed-open")
        compose.onNodeWithContentDescription("Close").performClick()
        runBlocking {
          completedIds.forEach { id ->
            books.put(books.get(id)!!.copy(positionInChapter = originals.single { it.id == id }.positionInChapter))
          }
        }
        showStack("NOT_STARTED")
        compose.onNodeWithTag(stackKey()).performClick()
        activity.recreate()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-details")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("series-details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit entire series").performClick()
        compose.onNodeWithText("Book order").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Series name")).performClick()
        compose.onNode(hasSetTextAction() and hasText("Series name")).performTextReplacement("Chrysalis collection")
        compose.onNodeWithText("Save series").assertIsDisplayed()
        screenshot("series-keyboard")
        UiDevice.getInstance(instrumentation).pressBack()
        compose.onNodeWithTag("series-books").performScrollToNode(hasText(titles[2]))
        compose.onNodeWithTag("series-reorder:${ids[2].value}").performClick()
        compose.onNodeWithText("Remove from series").performClick()
        compose.onNodeWithText("Save series").performClick()
        waitForGroup("Chrysalis collection")
        runBlocking { organisation.get().placements.single { it.bookId == ids[2].value }.seriesId } shouldBe null
        listOf(GridMode.LIST, GridMode.GRID, GridMode.BOOKS).forEach { mode ->
          runBlocking {
            layout.updateData { mode }
            booksPerRow.updateData { 2 }
          }
          compose.waitForIdle()
          showStack("NOT_STARTED", "Chrysalis collection")
          screenshot("series-${mode.name.lowercase()}")
          compose.onNodeWithTag(stackKey("Chrysalis collection")).performClick()
          try {
            waitForPrepared()
          } catch (failure: Throwable) {
            screenshot("series-open-failure")
            compose.onRoot().printToLog("SeriesOpenFailure")
            throw failure
          }
          compose.onNodeWithText(titles[4]).assertIsDisplayed()
          compose.onNodeWithText(titles[1]).assertDoesNotExist()
          compose.onNodeWithContentDescription("Close").performClick()
          compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("series-details")).fetchSemanticsNodes().isEmpty() }
        }
        editStack("Chrysalis collection")
        compose.onNodeWithText("Ungroup books").performClick()
        compose.onNode(
          hasText("Ungroup books") and hasClickAction() and hasAnyAncestor(hasTestTag("series-ungroup-confirmation")),
        ).performClick()
        waitForGroup(null)
        runBlocking {
          originals.forEach { original -> books.get(original.id) shouldBe original }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(titles[1]))
        compose.onNodeWithText(titles[1]).assertIsDisplayed()
        // Keep real keyboard events after all virtual-clock touch input: their time bases differ.
        runBlocking {
          organisation.saveSeries(null, "Chrysalis", originals.filter { it.series != null }.map { it.id })
        }
        showStack("NOT_STARTED")
        compose.onNodeWithTag(stackKey()).performClick()
        repeat(8) {
          UiDevice.getInstance(instrumentation).pressKeyCode(KeyEvent.KEYCODE_TAB)
          compose.waitForIdle()
          compose.onNode(isFocused(), useUnmergedTree = true).assert(hasAnyAncestor(hasTestTag("series-details")))
        }
        compose.onNodeWithContentDescription("Close").performSemanticsAction(SemanticsActions.OnClick) { it() shouldBe true }
        compose.onNodeWithTag(stackKey()).assert(isFocused())
      }
    } finally {
      compose.mainClock.autoAdvance = true
      @Suppress("DEPRECATION")
      instrumentation.setInTouchMode(true)
      runBlocking {
        val state = organisation.get()
        state.series.filter { group -> state.placements.filter { it.seriesId == group.id }.all { it.bookId in ids.map(BookId::value) } }
          .forEach { organisation.saveSeries(it.id, it.name, emptyList()) }
        ids.forEach { id -> books.get(id)?.let { books.put(it.copy(isActive = false)) } }
        books.setAllInactiveExcept(previousActive)
        layout.updateData { oldLayout }
        booksPerRow.updateData { oldCount }
        onboarding.updateData { oldOnboarding }
      }
    }
  }
}
