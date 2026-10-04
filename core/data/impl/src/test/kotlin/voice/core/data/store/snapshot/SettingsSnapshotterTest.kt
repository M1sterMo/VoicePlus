package voice.core.data.store.snapshot

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.data.GridMode
import voice.core.data.MediaButtonClickAction
import voice.core.data.PlaybackToolbarAction
import voice.core.data.repo.internals.MemoryDataStore
import voice.core.data.sleeptimer.SleepTimerPreference
import kotlin.time.Duration.Companion.seconds

/**
 * The one leg of the backup path whose silent failure mode is "every setting stops restoring":
 * Entry.apply swallows undecodable values by design, so nothing else would catch a serializer
 * change, key rename, or Json drift.
 */
class SettingsSnapshotterTest {

  private class Stores {
    val darkTheme = MemoryDataStore(false)
    val autoRewind = MemoryDataStore(2)
    val seekTime = MemoryDataStore(20)
    val fadeOut = MemoryDataStore(10.seconds)
    val sleepTimer = MemoryDataStore(SleepTimerPreference.Default)
    val gridMode = MemoryDataStore(GridMode.FOLLOW_DEVICE)
    val booksPerRow = MemoryDataStore(2)
    val mediaDoubleClick = MemoryDataStore(MediaButtonClickAction.NONE)
    val mediaTripleClick = MemoryDataStore(MediaButtonClickAction.NONE)
    val playbackToolbarActions = MemoryDataStore(PlaybackToolbarAction.DEFAULT)
    val globalVolumeGain = MemoryDataStore<Float?>(null)
    val experimentalPersistence = MemoryDataStore(false)
    val ignoreFileTags = MemoryDataStore(false)

    fun snapshotter() = SettingsSnapshotter(
      darkTheme = darkTheme,
      autoRewind = autoRewind,
      seekTime = seekTime,
      fadeOut = fadeOut,
      sleepTimer = sleepTimer,
      gridMode = gridMode,
      booksPerRow = booksPerRow,
      mediaDoubleClick = mediaDoubleClick,
      mediaTripleClick = mediaTripleClick,
      playbackToolbarActions = playbackToolbarActions,
      globalVolumeGain = globalVolumeGain,
      experimentalPersistence = experimentalPersistence,
      ignoreFileTags = ignoreFileTags,
      json = snapshotTestJson,
    )
  }

  @Test
  fun `captured settings survive apply onto fresh default stores`() = runTest {
    val source = Stores()
    source.darkTheme.updateData { true }
    source.seekTime.updateData { 45 }
    source.gridMode.updateData { GridMode.GRID }
    source.booksPerRow.updateData { 3 }
    source.mediaDoubleClick.updateData { MediaButtonClickAction.QUICK_BOOKMARK }
    source.ignoreFileTags.updateData { true }
    source.playbackToolbarActions.updateData { setOf(PlaybackToolbarAction.LISTENING_LOG) }
    source.globalVolumeGain.updateData { 6F }

    val captured = source.snapshotter().capture()
    val target = Stores()
    target.snapshotter().apply(captured)

    target.darkTheme.data.first() shouldBe true
    target.seekTime.data.first() shouldBe 45
    target.gridMode.data.first() shouldBe GridMode.GRID
    target.booksPerRow.data.first() shouldBe 3
    target.mediaDoubleClick.data.first() shouldBe MediaButtonClickAction.QUICK_BOOKMARK
    target.ignoreFileTags.data.first() shouldBe true
    target.playbackToolbarActions.data.first() shouldBe setOf(PlaybackToolbarAction.LISTENING_LOG)
    target.globalVolumeGain.data.first() shouldBe 6F
    // Untouched settings keep their defaults.
    target.autoRewind.data.first() shouldBe 2
  }

  @Test
  fun `disabled global volume boost restores over an enabled setting`() = runTest {
    val target = Stores()
    target.globalVolumeGain.updateData { 6F }
    target.snapshotter().apply(Stores().snapshotter().capture())
    target.globalVolumeGain.data.first() shouldBe null
  }

  @Test
  fun `all library layouts survive a settings backup and restore`() = runTest {
    GridMode.entries.forEach { mode ->
      val source = Stores()
      source.gridMode.updateData { mode }
      val target = Stores()
      target.snapshotter().apply(source.snapshotter().capture())
      target.gridMode.data.first() shouldBe mode
    }
  }

  @Test
  fun `an undecodable entry is skipped and the default kept`() = runTest {
    val target = Stores()
    target.snapshotter().apply(mapOf("seekTime" to "not-json", "darkTheme" to "true"))

    target.seekTime.data.first() shouldBe 20
    target.darkTheme.data.first() shouldBe true
    target.booksPerRow.data.first() shouldBe 2
  }

  /**
   * Found live in the emulator backup drill: a settings toggle right before "Back up now" was
   * missing from the manual save — settings changes never marked the snapshot dirty, so the
   * flushNow() in the manual-backup path had nothing to drain. changes() is that dirty signal.
   */
  @Test
  fun `changes emits once per store change and skips the initial replay`() = runTest {
    val stores = Stores()
    var emissions = 0
    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      stores.snapshotter().changes().collect { emissions++ }
    }
    runCurrent()
    emissions shouldBe 0 // initial replays of every store are dropped

    stores.gridMode.updateData { GridMode.GRID }
    runCurrent()
    emissions shouldBe 1

    stores.darkTheme.updateData { true }
    runCurrent()
    emissions shouldBe 2

    job.cancel()
  }

  @Test
  fun `applyScanAffecting applies ignoreFileTags and nothing else`() = runTest {
    val source = Stores()
    source.ignoreFileTags.updateData { true }
    source.darkTheme.updateData { true }
    val captured = source.snapshotter().capture()

    val target = Stores()
    target.snapshotter().applyScanAffecting(captured)

    target.ignoreFileTags.data.first() shouldBe true
    target.darkTheme.data.first() shouldBe false
  }
}
