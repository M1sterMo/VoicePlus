package voice.core.data.store.snapshot

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import voice.core.data.GridMode
import voice.core.data.MediaButtonClickAction
import voice.core.data.PlaybackToolbarAction
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.data.store.AutoRewindAmountStore
import voice.core.data.store.BooksPerRowStore
import voice.core.data.store.DarkThemeStore
import voice.core.data.store.ExperimentalPlaybackPersistenceStore
import voice.core.data.store.FadeOutStore
import voice.core.data.store.GlobalVolumeGainStore
import voice.core.data.store.GridModeStore
import voice.core.data.store.IgnoreFileTagsStore
import voice.core.data.store.MediaButtonDoubleClickHandlerStore
import voice.core.data.store.MediaButtonTripleClickHandlerStore
import voice.core.data.store.PlaybackToolbarActionsStore
import voice.core.data.store.SeekTimeStore
import voice.core.data.store.SleepTimerPreferenceStore
import kotlin.time.Duration

/**
 * Captures the settings worth carrying across a wipe into the snapshot's `settings` map, and
 * applies them back on restore. Each value is JSON-encoded with its own serializer, keyed by a
 * stable name — unknown keys in an old bundle are simply skipped, missing keys keep defaults.
 *
 * Database-only restore does not include DataStore preferences; capture them separately here.
 */
@SingleIn(AppScope::class)
@Inject
internal class SettingsSnapshotter(
  @DarkThemeStore darkTheme: DataStore<Boolean>,
  @AutoRewindAmountStore autoRewind: DataStore<Int>,
  @SeekTimeStore seekTime: DataStore<Int>,
  @FadeOutStore fadeOut: DataStore<Duration>,
  @SleepTimerPreferenceStore sleepTimer: DataStore<SleepTimerPreference>,
  @GridModeStore gridMode: DataStore<GridMode>,
  @BooksPerRowStore booksPerRow: DataStore<Int>,
  @MediaButtonDoubleClickHandlerStore mediaDoubleClick: DataStore<MediaButtonClickAction>,
  @MediaButtonTripleClickHandlerStore mediaTripleClick: DataStore<MediaButtonClickAction>,
  @PlaybackToolbarActionsStore playbackToolbarActions: DataStore<Set<PlaybackToolbarAction>>,
  @GlobalVolumeGainStore globalVolumeGain: DataStore<Float?>,
  @ExperimentalPlaybackPersistenceStore experimentalPersistence: DataStore<Boolean>,
  @IgnoreFileTagsStore ignoreFileTags: DataStore<Boolean>,
  @SnapshotJson private val json: Json,
) {

  private inner class Entry<T>(
    val key: String,
    val store: DataStore<T>,
    val serializer: KSerializer<T>,
  ) {
    suspend fun capture(): Pair<String, String> = key to json.encodeToString(serializer, store.data.first())

    suspend fun apply(encoded: String) {
      val value = runCatching { json.decodeFromString(serializer, encoded) }.getOrElse { return }
      store.updateData { value }
    }
  }

  private val entries: List<Entry<*>> = listOf(
    Entry("darkTheme", darkTheme, Boolean.serializer()),
    Entry("autoRewind", autoRewind, Int.serializer()),
    Entry("seekTime", seekTime, Int.serializer()),
    Entry("fadeOut", fadeOut, Duration.serializer()),
    Entry("sleepTimerPreference", sleepTimer, SleepTimerPreference.serializer()),
    Entry("gridMode", gridMode, GridMode.serializer()),
    Entry("booksPerRow", booksPerRow, Int.serializer()),
    Entry("mediaButtonDoubleClick", mediaDoubleClick, MediaButtonClickAction.serializer()),
    Entry("mediaButtonTripleClick", mediaTripleClick, MediaButtonClickAction.serializer()),
    Entry("playbackToolbarActions", playbackToolbarActions, SetSerializer(PlaybackToolbarAction.serializer())),
    Entry("globalVolumeGain", globalVolumeGain, Float.serializer().nullable),
    Entry("experimentalPlaybackPersistence", experimentalPersistence, Boolean.serializer()),
    Entry("ignoreFileTags", ignoreFileTags, Boolean.serializer()),
  )

  suspend fun capture(): Map<String, String> = entries.associate { it.capture() }

  /**
   * Emits on every change to any captured store (initial replays dropped). This is the snapshot
   * writer's dirty signal for settings — without it a settings-only change never reaches the
   * ring, and "Back up now" exports a stale settings map.
   */
  fun changes(): Flow<Unit> = merge(*entries.map { entry -> entry.store.data.drop(1).map { } }.toTypedArray())

  suspend fun apply(settings: Map<String, String>) {
    entries.forEach { entry ->
      settings[entry.key]?.let { entry.apply(it) }
    }
  }

  /**
   * Apply ONLY the settings that change how a library scan derives names (ignoreFileTags).
   * The OS-wipe restore needs these before its scan; everything else is applied after the
   * restore succeeds, so a failed restore doesn't leave half-replaced settings behind.
   */
  suspend fun applyScanAffecting(settings: Map<String, String>) {
    entries.filter { it.key == "ignoreFileTags" }.forEach { entry ->
      settings[entry.key]?.let { entry.apply(it) }
    }
  }
}
