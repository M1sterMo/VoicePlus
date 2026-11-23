package voice.core.scanner

import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookContentRepoImpl
import voice.core.data.repo.BookRepositoryImpl
import voice.core.data.repo.ChapterRepoImpl
import voice.core.data.repo.internals.AppDb
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.FileBasedDocumentFactory
import voice.core.documentfile.FileBasedDocumentFile
import java.io.Closeable
import java.io.File
import java.nio.file.Files

/**
 * Guards the "remove an audiobook folder while a scan is still in flight" race against regression.
 *
 * WHY THIS LIVES IN :core:scanner (not :core:data:impl): the bug spans three real collaborators —
 * [MediaScanTrigger] (captures the folder set once at scan start), [voice.core.data.folders.AudiobookFolders]
 * (whose remove() deactivates the removed folder's books) and [voice.core.data.repo.BookContentRepo]. Only
 * :core:scanner can see all three (it has testImplementation(projects.core.data.impl)); the dependency
 * direction is scanner -> data, so :core:data:impl structurally cannot host this test. Do not "tidy" it back
 * into an isolated per-component test — that silently reopens the gap.
 *
 * DETERMINISM: a single test scheduler drives everything. [MediaScanTrigger]'s scope is built from an injected
 * [DispatcherProvider] whose io == the test's coroutineContext, so the scan job runs on the same scheduler the
 * test steps with runCurrent()/advanceUntilIdle() — no Thread.sleep, no real dispatcher, no wall-clock race.
 */
@RunWith(AndroidJUnit4::class)
class ScanFolderRemovalRaceTest {

  /**
   * The exact device repro reduced to its essence at the [MediaScanner] seam: a scan whose folder set was
   * snapshotted BEFORE the folder was removed must not re-activate that folder's books AFTER the removal
   * already deactivated them. Pre-fix (isActive forced true) this leaves zombies and FAILS; post-fix the
   * activation gate consults the live (now-empty) folder set and the books stay inactive.
   */
  @Test
  fun `a stale scan must not re-activate books whose folder was removed mid-scan`() = test {
    gate.complete(Unit) // this test drives scanner.scan directly; no mid-flight parking needed.
    val root = folder("audiobooks")
    val book1 = File(root, "book1").also { audioFile(it, "1.mp3") }
    val book2 = File(root, "book2").also { audioFile(it, "1.mp3") }

    // 1. Folder configured + initial scan -> both books active.
    folders.set(FolderType.Root, listOf(root.toUri()))
    scanner.scan(staleSnapshot(root))
    activeBookPaths() shouldBe setOf(book1.absolutePath, book2.absolutePath)

    // 2. A scan S has ALREADY snapshotted the folder set (captured here, before removal).
    val staleFolders = staleSnapshot(root)

    // 3. User removes the folder: drop it from the live set + run the deactivation (mirrors
    //    AudiobookFoldersImpl.remove). The two books are now inactive and no folder is configured.
    folders.remove(root.toUri(), FolderType.Root)
    deactivateBooksUnder(root)
    activeBookPaths().shouldBeEmpty()

    // 4. The stale in-flight scan finally runs against its pre-removal snapshot (files still on disk).
    scanner.scan(staleFolders)

    // 5. INVARIANT: no book is re-activated — active set ⊆ currently-configured folders (which is empty).
    activeBookPaths().shouldBeEmpty()
  }

  /**
   * The fully-wired version through [MediaScanTrigger]. The scan job is parked mid-walk on a gate, the folder
   * is removed and its deactivation completes while the scan is frozen, then the scan resumes against its stale
   * folder snapshot. Asserts the resumed scan leaves no active book under the removed folder, and a subsequent
   * zero-folder scan does not resurrect them (covers the empty-scan reconcile-guard interaction).
   */
  @Test
  fun `trigger - scan parked mid-walk, folder removed, scan resumes leaves no zombies`() = test {
    val root = folder("audiobooks")
    val book1 = File(root, "book1").also { audioFile(it, "1.mp3") }
    val book2 = File(root, "book2").also { audioFile(it, "1.mp3") }
    folders.set(FolderType.Root, listOf(root.toUri()))

    // Seed the device state: a completed scan with both books ACTIVE (the re-add that precedes the racy remove).
    gate.complete(Unit)
    scanner.scan(staleSnapshot(root))
    activeBookPaths() shouldBe setOf(book1.absolutePath, book2.absolutePath)

    // Now launch a fresh scan through the trigger that will be frozen mid-flight by a new gate.
    // forceReParse = true so the per-book walk actually re-analyzes (and so awaits the gate); without it the
    // second scan would hit the chapter/content cache, skip analyze entirely, and run to completion — never
    // parking, never opening the race window (that was a tautology before this fix).
    resetGate()
    trigger.scan(restartIfScanning = true, forceReParse = true)
    runCurrent() // the scan job is launched and suspended inside analyze() on the gate.

    // While that scan is parked, remove the folder and let its deactivation run to completion.
    folders.remove(root.toUri(), FolderType.Root)
    deactivateBooksUnder(root)
    runCurrent()
    activeBookPaths().shouldBeEmpty()

    // Resume the parked scan; it runs against its stale (pre-removal) folder snapshot.
    gate.complete(Unit)
    advanceUntilIdle()
    activeBookPaths().shouldBeEmpty()

    // A subsequent scan with zero folders configured must not resurrect them either (empty-scan-guard hole).
    trigger.scan(restartIfScanning = true)
    advanceUntilIdle()
    activeBookPaths().shouldBeEmpty()
  }

  private fun test(body: suspend Env.() -> Unit) = runTest {
    Env(this).use { it.body() }
  }

  private inner class Env(private val testScope: TestScope) : Closeable {

    private val testContext = testScope.coroutineContext

    /** Step the single test scheduler — delegating to the enclosing [TestScope] so a test body (whose receiver
     *  is this Env) can drive execution without losing the scheduler receiver. */
    fun runCurrent() = testScope.runCurrent()

    fun advanceUntilIdle() = testScope.advanceUntilIdle()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java)
      .allowMainThreadQueries()
      .build()
    val bookContentRepo = BookContentRepoImpl(db.bookContentDao())
    private val chapterRepo = ChapterRepoImpl(db.chapterDao())
    private val bookRepo = BookRepositoryImpl(chapterRepo, bookContentRepo)
    private val ignoreFileTags = MutableStateFlow(false)
    val folders = FakeManagedFolders()

    // A gate the analyzer awaits, letting the trigger test freeze the scan in flight (inside the per-chapter
    // analyze) and resume it deterministically after the folder removal has completed. Reassignable so a test
    // can install a fresh, un-completed gate before the racy scan (a CompletableDeferred completes only once).
    var gate = CompletableDeferred<Unit>()
      private set

    fun resetGate() {
      gate = CompletableDeferred()
    }

    private val mediaAnalyzer = mockk<MediaAnalyzer>().also {
      coEvery { it.analyze(any()) } coAnswers {
        gate.await()
        Metadata(
          duration = 1000L,
          artist = "Author",
          album = "Book Name",
          fileName = "Chapter",
          chapters = emptyList(),
          title = "Title",
          genre = "Genre",
          narrator = "Narrator",
          series = "Series",
          part = "Part",
        )
      }
    }

    val scanner = MediaScanner(
      contentRepo = bookContentRepo,
      chapterParser = ChapterParser(
        chapterRepo = chapterRepo,
        mediaAnalyzer = mediaAnalyzer,
        ignoreFileTagsStore = mockk { every { data } returns ignoreFileTags },
      ),
      bookParser = BookParser(
        context = ApplicationProvider.getApplicationContext(),
        contentRepo = bookContentRepo,
        mediaAnalyzer = mediaAnalyzer,
        fileFactory = FileBasedDocumentFactory,
        ignoreFileTagsStore = mockk { every { data } returns ignoreFileTags },
      ),
      deviceHasPermissionBug = mockk(relaxed = true),
      audiobookFolders = folders,
      excludedBooksStore = mockk { every { data } returns MutableStateFlow(emptySet()) },
      chapterRepo = chapterRepo,
      progressCarryOver = ProgressCarryOver(bookContentRepo, RecordingBookMover()),
    )

    private val documentFileFactory = object : CachedDocumentFileFactory {
      override fun create(uri: Uri): CachedDocumentFile = FileBasedDocumentFile(uri.toFile())
    }

    val trigger = MediaScanTrigger(
      audiobookFolders = folders,
      scanner = scanner,
      coverScanner = mockk(relaxed = true),
      bookRepo = bookRepo,
      documentFileFactory = documentFileFactory,
      dispatcherProvider = DispatcherProvider(testContext, testContext, testContext),
    )

    private val rootDir: File = Files.createTempDirectory(this::class.java.canonicalName!!).toFile()

    fun folder(name: String): File = File(rootDir, name).also { it.mkdirs() }

    @IgnorableReturnValue
    fun audioFile(
      parent: File,
      name: String,
    ): File {
      return File(parent, name).also {
        it.parentFile?.mkdirs()
        check(it.createNewFile())
      }
    }

    fun staleSnapshot(vararg roots: File): Map<FolderType, List<CachedDocumentFile>> =
      mapOf(FolderType.Root to roots.map(::FileBasedDocumentFile))

    suspend fun activeBookPaths(): Set<String> =
      bookContentRepo.all().filter { it.isActive }.map { it.id.toUri().toFile().absolutePath }.toSet()

    /** Mirrors AudiobookFoldersImpl.remove's deactivation: inactivate every active book under [removed]. */
    suspend fun deactivateBooksUnder(removed: File) {
      val removedAbs = removed.absoluteFile
      bookContentRepo.all()
        .filter { it.isActive && it.id.toUri().toFile().absoluteFile.parentFile == removedAbs }
        .forEach { bookContentRepo.put(it.copy(isActive = false)) }
    }

    override fun close() {
      rootDir.deleteRecursively()
      db.close()
    }
  }
}
