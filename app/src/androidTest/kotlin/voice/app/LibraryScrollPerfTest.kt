package voice.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import voice.core.common.rootGraphAs
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.GridMode
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.repo.LibraryOrganisationRepo
import voice.core.data.store.GridModeStore
import voice.core.data.store.OnboardingCompletedStore
import java.io.File
import java.time.Instant
import java.util.regex.Pattern

/**
 * Scrolls a large library with real cover images, and opens and closes a series, reporting frame
 * times from `dumpsys gfxinfo`: as many single books as series stacks, in Books and Grid view.
 * A measuring tool for GitHub issue #43, not a pass/fail check. Results go to logcat (tag ScrollPerf)
 * and to files/scroll-perf.txt.
 *
 * Gradle's connected tests switch animations off, which hides animation costs. Install the app and
 * test APKs, then run it with `adb shell am instrument -w -e class voice.app.LibraryScrollPerfTest
 * <package>.test/voice.app.VoiceJUnitRunner -e perf true`. An emulator started with `-gpu swiftshader_indirect`
 * stands in for a phone with a weak GPU.
 */
class LibraryScrollPerfTest {
  // No Compose test rule: it would drive animations from a virtual clock. UiAutomator keeps every
  // frame on the real one, as on a phone.

  @Inject lateinit var books: BookContentRepo

  @Inject lateinit var chapters: ChapterRepo

  @Inject lateinit var organisation: LibraryOrganisationRepo
  @field:[Inject GridModeStore] lateinit var layout: DataStore<GridMode>
  @field:[Inject OnboardingCompletedStore] lateinit var onboarding: DataStore<Boolean>

  @Test
  fun scrollSeriesAndSingleBooks() {
    // Minutes long and only meaningful with animations on, so it runs only when asked for.
    assumeTrue(InstrumentationRegistry.getArguments().getString("perf") != null)
    rootGraphAs<TestGraph>().inject(this)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val packageName = instrumentation.targetContext.packageName
    val directory = File(instrumentation.targetContext.filesDir, "perf-covers").apply { mkdirs() }
    val chapter = ChapterId("file:///perf-fixture.m4a")
    val ids = (0 until BOOKS).map { BookId("perf-fixture-$it") }
    val oldLayout = runBlocking { layout.data.first() }
    val oldOnboarding = runBlocking { onboarding.data.first() }
    val previousActive = runBlocking { books.all().filter { it.isActive }.map { it.id } }
    runBlocking {
      chapters.put(Chapter(chapter, "Chapter", 3_600_000L, Instant.EPOCH, emptyList()))
      ids.forEachIndexed { index, id ->
        books.put(
          BookContent(
            id = id, name = "Perf book ${index + 1}", author = "Author ${index / 4}", playbackSpeed = 1f,
            skipSilence = false, isActive = true, lastPlayedAt = Instant.EPOCH, addedAt = Instant.EPOCH,
            chapters = listOf(chapter), currentChapter = chapter, positionInChapter = 0L,
            cover = cover(directory, index), gain = 0f, genre = null, narrator = null, series = null, part = null,
          ),
        )
      }
      books.setAllInactiveExcept(ids)
      onboarding.updateData { true }
    }
    fun scroll(label: String) {
      ActivityScenario.launch(MainActivity::class.java).use {
        check(device.wait(Until.hasObject(By.textStartsWith("Perf ")), 15_000)) { "The library did not show" }
        device.waitForIdle()
        // Warm up once, so decoding the first screen of covers is not counted.
        swipe(device, up = true)
        swipe(device, up = false)
        Thread.sleep(500)
        device.executeShellCommand("dumpsys gfxinfo $packageName reset")
        repeat(SWIPES) {
          swipe(device, up = true)
          Thread.sleep(250)
        }
        repeat(SWIPES) {
          swipe(device, up = false)
          Thread.sleep(250)
        }
        report(device, packageName, label)
        if (label.endsWith("series")) {
          // The open and close animation of a stack, three times over.
          // The tile merges its texts, so match the series name at the start of its label.
          val stack = By.text(Pattern.compile("Perf series 1(\\D.*)?", Pattern.DOTALL))
          repeat(SWIPES) { if (!device.hasObject(stack)) swipe(device, up = false) }
          Thread.sleep(500)
          device.executeShellCommand("dumpsys gfxinfo $packageName reset")
          repeat(3) {
            device.findObject(stack).click()
            check(device.wait(Until.hasObject(By.desc("Close")), 10_000)) { "The series did not open" }
            Thread.sleep(1_000)
            device.pressBack()
            check(device.wait(Until.gone(By.desc("Close")), 10_000)) { "The series did not close" }
            Thread.sleep(1_000)
          }
          report(device, packageName, "$label open/close")
        }
      }
    }
    try {
      listOf(GridMode.BOOKS, GridMode.GRID).forEach { mode ->
        // The same number of tiles both times, so the lists are equally long: TILES single books, then
        // TILES series of SERIES_SIZE books each.
        runBlocking {
          layout.updateData { mode }
          books.setAllInactiveExcept(ids.take(TILES))
        }
        ungroup(ids)
        scroll("$mode singles")
        runBlocking {
          books.setAllInactiveExcept(ids)
          ids.chunked(SERIES_SIZE).forEachIndexed { index, members -> organisation.saveSeries(null, "Perf series ${index + 1}", members) }
        }
        scroll("$mode series")
        ungroup(ids)
      }
    } finally {
      runBlocking {
        ungroup(ids)
        ids.forEach { id -> books.get(id)?.let { books.put(it.copy(isActive = false)) } }
        books.setAllInactiveExcept(previousActive)
        layout.updateData { oldLayout }
        onboarding.updateData { oldOnboarding }
      }
    }
  }

  private fun report(
    device: UiDevice,
    packageName: String,
    label: String,
  ) {
    val summary = device.executeShellCommand("dumpsys gfxinfo $packageName").lines()
      .filter { line -> SUMMARY_KEYS.any { line.trim().startsWith(it) } }
      .distinct().joinToString(" | ") { it.trim() }
    // CPU time per frame on the UI thread (input, animation, layout, recording) and on the render
    // thread, which a fast GPU does not hide the way it hides drawing costs.
    val stats = device.executeShellCommand("dumpsys gfxinfo $packageName framestats").lines()
    val header = stats.firstOrNull { it.startsWith("Flags,") }?.split(",")
    val cpu = if (header == null) {
      ""
    } else {
      val column = header.withIndex().associate { (index, name) -> name to index }
      val frames = stats.map { it.split(",") }.filter { it.size >= header.size - 1 && it[0] == "0" }
      fun value(
        row: List<String>,
        name: String,
      ): Long? = column[name]?.let { row.getOrNull(it)?.toLongOrNull() }
      fun percentiles(span: (List<String>) -> Long?): String {
        val ms = frames.mapNotNull { row -> span(row)?.takeIf { it > 0 }?.let { it / 1_000_000.0 } }.sorted()
        if (ms.isEmpty()) return "-"
        fun at(p: Double) = "%.1f".format(java.util.Locale.ROOT, ms[((ms.size - 1) * p).toInt()])
        return "${at(0.5)}/${at(0.9)}/${at(0.99)}ms"
      }
      fun between(
        from: String,
        to: String,
      ): (List<String>) -> Long? = { row -> value(row, from)?.let { start -> value(row, to)?.minus(start) } }
      // Render-thread work excludes waiting for a buffer; GPU time is from handing the frame over to the GPU finishing it.
      val render: (List<String>) -> Long? = { row ->
        between("SyncStart", "SwapBuffers")(row)?.minus(value(row, "DequeueBufferDuration") ?: 0)
      }
      " | UI thread p50/p90/p99 ${percentiles(between("HandleInputStart", "SyncQueued"))}" +
        " | render thread ${percentiles(render)}" +
        " | GPU ${percentiles(between("SwapBuffers", "GpuCompleted"))}"
    }
    Log.i("ScrollPerf", "$label: $summary$cpu")
    File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "scroll-perf.txt").appendText("$label: $summary$cpu\n")
  }

  private fun ungroup(ids: List<BookId>) = runBlocking {
    val state = organisation.get()
    state.series.filter { group -> state.placements.any { it.seriesId == group.id && it.bookId.startsWith("perf-fixture-") } }
      .forEach { organisation.saveSeries(it.id, it.name, emptyList()) }
    check(organisation.get().placements.none { it.bookId in ids.map(BookId::value) && it.seriesId != null })
  }

  private fun swipe(
    device: UiDevice,
    up: Boolean,
  ) {
    val x = device.displayWidth / 2
    val low = device.displayHeight * 4 / 5
    val high = device.displayHeight / 5
    if (up) device.swipe(x, low, x, high, 12) else device.swipe(x, high, x, low, 12)
  }

  /** A full-size cover, so decoding costs what a real library's covers cost. */
  private fun cover(
    directory: File,
    index: Int,
  ): File = File(directory, "cover-$index.jpg").also { file ->
    if (file.exists()) return@also
    val bitmap = Bitmap.createBitmap(1000, 1500, Bitmap.Config.ARGB_8888)
    val hue = (index * 37 % 360).toFloat()
    val start = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.6f, 0.8f))
    val end = android.graphics.Color.HSVToColor(floatArrayOf((hue + 90) % 360, 0.7f, 0.3f))
    Canvas(bitmap).drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 1000f, 1500f, start, end, Shader.TileMode.CLAMP) })
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    bitmap.recycle()
  }

  private companion object {
    const val TILES = 32
    const val SERIES_SIZE = 3
    const val BOOKS = TILES * SERIES_SIZE
    const val SWIPES = 8
    val SUMMARY_KEYS =
      listOf("Total frames rendered", "Janky frames:", "50th percentile", "90th percentile", "95th percentile", "99th percentile")
  }
}
