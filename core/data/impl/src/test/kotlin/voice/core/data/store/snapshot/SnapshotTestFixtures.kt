package voice.core.data.store.snapshot

import kotlinx.serialization.json.Json
import voice.core.data.GridMode
import voice.core.data.MediaButtonClickAction
import voice.core.data.PlaybackToolbarAction
import voice.core.data.repo.internals.MemoryDataStore
import voice.core.data.sleeptimer.SleepTimerPreference
import kotlin.time.Duration.Companion.seconds

internal val snapshotTestJson: Json = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

internal fun backupFixture(name: String): String {
  return requireNotNull(LibrarySnapshot::class.java.getResource("/backups/$name")).readText()
}

/** A [SettingsSnapshotter] over in-memory stores at their production defaults. */
internal fun testSettingsSnapshotter(): SettingsSnapshotter = SettingsSnapshotter(
  darkTheme = MemoryDataStore(false),
  autoRewind = MemoryDataStore(2),
  seekTime = MemoryDataStore(20),
  fadeOut = MemoryDataStore(10.seconds),
  sleepTimer = MemoryDataStore(SleepTimerPreference.Default),
  gridMode = MemoryDataStore(GridMode.FOLLOW_DEVICE),
  booksPerRow = MemoryDataStore(2),
  mediaDoubleClick = MemoryDataStore(MediaButtonClickAction.NONE),
  mediaTripleClick = MemoryDataStore(MediaButtonClickAction.NONE),
  playbackToolbarActions = MemoryDataStore(PlaybackToolbarAction.DEFAULT),
  globalVolumeGain = MemoryDataStore(null),
  experimentalPersistence = MemoryDataStore(false),
  ignoreFileTags = MemoryDataStore(false),
  json = snapshotTestJson,
)
