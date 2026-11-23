package voice.core.scanner

import android.app.Activity
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.folders.FolderType
import voice.core.documentfile.CachedDocumentFile
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class MediaScanTriggerTest {

  private val folders = FakeManagedFolders()
  private val firstFolder = "file:///first".toUri()
  private val secondFolder = "file:///second".toUri()

  // What each started file scan saw, and gates a test can hold a scan or the cover search on.
  private val scannedFolders = mutableListOf<Set<FolderType>>()
  private val forcedReParses = mutableListOf<Boolean>()
  private var failNextFileScan = false
  private var fileScanGate: CompletableDeferred<Unit>? = null
  private var coverGate: CompletableDeferred<Unit>? = null
  private var finishedCoverScans = 0

  private val scanner = mockk<MediaScanner> {
    coEvery { scan(any(), any()) } coAnswers {
      scannedFolders += firstArg<Map<FolderType, List<CachedDocumentFile>>>().filterValues { it.isNotEmpty() }.keys
      forcedReParses += secondArg<Boolean>()
      if (failNextFileScan) {
        failNextFileScan = false
        error("unreadable folder")
      }
      fileScanGate?.await()
    }
  }
  private val coverScanner = mockk<CoverScanner> {
    coEvery { scan(any()) } coAnswers {
      coverGate?.await()
      finishedCoverScans++
    }
  }

  private fun TestScope.trigger() = MediaScanTrigger(
    audiobookFolders = folders,
    scanner = scanner,
    coverScanner = coverScanner,
    bookRepo = mockk { coEvery { all() } returns emptyList() },
    documentFileFactory = mockk(relaxed = true),
    dispatcherProvider = DispatcherProvider(coroutineContext, coroutineContext, coroutineContext),
  )

  @Test
  fun `adding a folder while a scan runs scans again over the new folders`() = runTest {
    folders.add(firstFolder, FolderType.Root)
    val trigger = trigger()
    trigger.scanOnFolderChanges()
    fileScanGate = CompletableDeferred()
    trigger.scan()
    advanceUntilIdle()

    folders.add(secondFolder, FolderType.SingleFolder)
    advanceUntilIdle()
    fileScanGate!!.complete(Unit)
    advanceUntilIdle()

    scannedFolders shouldBe listOf(setOf(FolderType.Root), setOf(FolderType.Root, FolderType.SingleFolder))
  }

  @Test
  fun `a folder change scans even right after a scan armed the cooldown`() = runTest {
    val trigger = trigger()
    trigger.scanOnFolderChanges()
    trigger.scan()
    advanceUntilIdle()

    folders.add(firstFolder, FolderType.Root)
    advanceUntilIdle()

    scannedFolders shouldBe listOf(emptySet(), setOf(FolderType.Root))
  }

  @Test
  fun `a request while covers are looked for starts a new scan`() = runTest {
    val trigger = trigger()
    coverGate = CompletableDeferred()
    trigger.scan()
    advanceUntilIdle()
    scannedFolders.size shouldBe 1

    trigger.scan()
    advanceUntilIdle()

    scannedFolders.size shouldBe 2
  }

  @Test
  fun `a request skipped by the cooldown leaves the cover search running`() = runTest {
    val trigger = trigger()
    coverGate = CompletableDeferred()
    trigger.scan()
    advanceUntilIdle()

    trigger.scan(skipIfCompletedWithin = 5.minutes)
    advanceUntilIdle()
    coverGate!!.complete(Unit)
    advanceUntilIdle()

    scannedFolders.size shouldBe 1
    finishedCoverScans shouldBe 1
  }

  @Test
  fun `scanAndAwait waits for the scan that restarted it, covers included`() = runTest {
    folders.add(firstFolder, FolderType.Root)
    val trigger = trigger()
    trigger.scanOnFolderChanges()
    fileScanGate = CompletableDeferred()
    coverGate = CompletableDeferred()
    var awaited = false
    launch {
      trigger.scanAndAwait()
      awaited = true
    }
    advanceUntilIdle()

    folders.add(secondFolder, FolderType.SingleFolder)
    advanceUntilIdle()
    awaited shouldBe false

    fileScanGate!!.complete(Unit)
    advanceUntilIdle()
    awaited shouldBe false
    scannedFolders.last() shouldBe setOf(FolderType.Root, FolderType.SingleFolder)

    coverGate!!.complete(Unit)
    advanceUntilIdle()
    awaited shouldBe true
  }

  @Test
  fun `a forced re-parse survives a restart until one finishes`() = runTest {
    val trigger = trigger()
    trigger.scanOnFolderChanges()
    fileScanGate = CompletableDeferred()
    trigger.scan(restartIfScanning = true, forceReParse = true)
    advanceUntilIdle()

    folders.add(firstFolder, FolderType.Root)
    advanceUntilIdle()
    fileScanGate!!.complete(Unit)
    advanceUntilIdle()
    trigger.scan()
    advanceUntilIdle()

    forcedReParses shouldBe listOf(true, true, false)
  }

  @Test
  fun `a failed scan still looks for covers`() = runTest {
    val trigger = trigger()
    failNextFileScan = true
    trigger.scan()
    advanceUntilIdle()

    finishedCoverScans shouldBe 1
  }

  @Test
  fun `scans when the app comes back, not on its first start or a rotation`() {
    var scans = 0
    val callbacks = ScanOnReturnToForeground { scans++ }
    val activity = mockk<Activity> { every { isChangingConfigurations } returns false }
    val rotating = mockk<Activity> { every { isChangingConfigurations } returns true }

    callbacks.onActivityStarted(activity)
    scans shouldBe 0

    callbacks.onActivityStopped(rotating)
    callbacks.onActivityStarted(activity)
    scans shouldBe 0

    // Opening a second screen of the app isn't coming back.
    callbacks.onActivityStarted(activity)
    callbacks.onActivityStopped(activity)
    scans shouldBe 0

    callbacks.onActivityStopped(activity)
    callbacks.onActivityStarted(activity)
    scans shouldBe 1
  }
}
