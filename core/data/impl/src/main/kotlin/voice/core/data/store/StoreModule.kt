package voice.core.data.store

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import voice.core.data.BookId
import voice.core.data.GridMode
import voice.core.data.LockscreenSecondaryTextMode
import voice.core.data.LockscreenSliderMode
import voice.core.data.MediaButtonClickAction
import voice.core.data.OpenSessionCheckpoint
import voice.core.data.PlaybackToolbarAction
import voice.core.data.sleeptimer.SleepTimerPreference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ContributesTo(AppScope::class)
public interface StoreModule {

  @Provides
  @SingleIn(AppScope::class)
  private fun sharedPreferences(context: Application): SharedPreferences {
    return context.getSharedPreferences(
      "${context.packageName}_preferences",
      Context.MODE_PRIVATE,
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @DarkThemeStore
  private fun darkTheme(
    factory: VoiceDataStoreFactory,
    sharedPreferences: SharedPreferences,
  ): DataStore<Boolean> {
    return factory.boolean(
      fileName = "darkTheme",
      defaultValue = false,
      migrations = listOf(
        booleanPrefsDataMigration(sharedPreferences, "darkTheme"),
      ),
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @AutoRewindAmountStore
  private fun autoRewindAmount(
    factory: VoiceDataStoreFactory,
    sharedPreferences: SharedPreferences,
  ): DataStore<Int> {
    return factory.int(
      fileName = "autoRewind",
      defaultValue = 2,
      migrations = listOf(intPrefsDataMigration(sharedPreferences, "AUTO_REWIND")),
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @FadeOutStore
  private fun fadeOut(factory: VoiceDataStoreFactory): DataStore<Duration> {
    return factory.create(
      fileName = "fadeOut",
      defaultValue = 10.seconds,
      serializer = Duration.serializer(),
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @SeekTimeStore
  private fun seekTime(
    factory: VoiceDataStoreFactory,
    sharedPreferences: SharedPreferences,
  ): DataStore<Int> {
    return factory.int(
      fileName = "seekTime",
      defaultValue = 20,
      migrations = listOf(intPrefsDataMigration(sharedPreferences, "SEEK_TIME")),
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @SleepTimerPreferenceStore
  private fun sleepTimerPreference(factory: VoiceDataStoreFactory): DataStore<SleepTimerPreference> {
    return factory.create(
      serializer = SleepTimerPreference.Companion.serializer(),
      fileName = "sleepTime3",
      defaultValue = SleepTimerPreference.Default,
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @BooksPerRowStore
  private fun booksPerRow(factory: VoiceDataStoreFactory): DataStore<Int> = factory.int(fileName = "booksPerRow", defaultValue = 2)

  @Provides
  @SingleIn(AppScope::class)
  @GridModeStore
  private fun gridMode(
    factory: VoiceDataStoreFactory,
    sharedPreferences: SharedPreferences,
  ): DataStore<GridMode> {
    return factory.create(
      GridMode.serializer(),
      GridMode.FOLLOW_DEVICE,
      "gridMode",
      migrations = listOf(
        PrefsDataMigration(
          sharedPreferences,
          key = "gridView",
          getFromSharedPreferences = {
            when (sharedPreferences.getString("gridView", null)) {
              "LIST" -> GridMode.LIST
              "GRID" -> GridMode.GRID
              else -> GridMode.FOLLOW_DEVICE
            }
          },
        ),
      ),
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @OnboardingCompletedStore
  private fun onboardingCompleted(factory: VoiceDataStoreFactory): DataStore<Boolean> {
    return factory.boolean("onboardingCompleted", defaultValue = false)
  }

  @Provides
  @SingleIn(AppScope::class)
  @CurrentBookStore
  private fun currentBook(factory: VoiceDataStoreFactory): DataStore<BookId?> {
    return factory.create(
      serializer = BookId.serializer().nullable,
      fileName = "currentBook",
      defaultValue = null,
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @AmountOfBatteryOptimizationRequestedStore
  private fun amountOfBatteryOptimizationsRequestedStore(factory: VoiceDataStoreFactory): DataStore<Int> {
    return factory.int("amountOfBatteryOptimizationsRequestedStore", 0)
  }

  @Provides
  @SingleIn(AppScope::class)
  @ReviewDialogShownStore
  private fun reviewDialogShown(factory: VoiceDataStoreFactory): DataStore<Boolean> {
    return factory.create(Boolean.serializer(), false, "reviewDialogShown")
  }

  @Provides
  @SingleIn(AppScope::class)
  @ExcludedBooksStore
  private fun excludedBooks(factory: VoiceDataStoreFactory): DataStore<Set<String>> {
    return factory.create(
      serializer = SetSerializer(String.serializer()),
      defaultValue = emptySet(),
      fileName = "excludedBooks",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @NotStartedExpandedStore
  private fun notStartedExpanded(factory: VoiceDataStoreFactory): DataStore<Boolean> {
    return factory.boolean("notStartedExpanded", defaultValue = true)
  }

  @Provides
  @SingleIn(AppScope::class)
  @FinishedExpandedStore
  private fun finishedExpanded(factory: VoiceDataStoreFactory): DataStore<Boolean> {
    return factory.boolean("finishedExpanded", defaultValue = true)
  }

  @Provides
  @SingleIn(AppScope::class)
  @MediaButtonDoubleClickHandlerStore
  private fun mediaButtonDoubleClickHandlerStore(factory: VoiceDataStoreFactory): DataStore<MediaButtonClickAction> {
    return factory.create(
      serializer = MediaButtonClickAction.serializer(),
      defaultValue = MediaButtonClickAction.SKIP_FORWARD,
      fileName = "mediaButtonDoubleClickHandlerStore",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @MediaButtonTripleClickHandlerStore
  private fun mediaButtonTripleClickHandlerStore(factory: VoiceDataStoreFactory): DataStore<MediaButtonClickAction> {
    return factory.create(
      serializer = MediaButtonClickAction.serializer(),
      defaultValue = MediaButtonClickAction.SKIP_BACKWARD,
      fileName = "mediaButtonTripleClickHandlerStore",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @PlaybackToolbarActionsStore
  private fun playbackToolbarActionsStore(factory: VoiceDataStoreFactory): DataStore<Set<PlaybackToolbarAction>> {
    return factory.create(
      serializer = SetSerializer(PlaybackToolbarAction.serializer()),
      defaultValue = PlaybackToolbarAction.DEFAULT,
      fileName = "playbackToolbarActions",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @GlobalVolumeGainStore
  private fun globalVolumeGainStore(factory: VoiceDataStoreFactory): DataStore<Float?> {
    return factory.create(
      serializer = Float.serializer().nullable,
      defaultValue = null,
      fileName = "globalVolumeGain",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @LockscreenSliderModeStore
  private fun lockscreenSliderModeStore(factory: VoiceDataStoreFactory): DataStore<LockscreenSliderMode> {
    return factory.create(
      serializer = LockscreenSliderMode.serializer(),
      defaultValue = LockscreenSliderMode.CHAPTER,
      fileName = "lockscreenSliderMode",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @LockscreenSecondaryTextModeStore
  private fun lockscreenSecondaryTextModeStore(factory: VoiceDataStoreFactory): DataStore<LockscreenSecondaryTextMode> {
    return factory.create(
      serializer = LockscreenSecondaryTextMode.serializer(),
      defaultValue = LockscreenSecondaryTextMode.CHAPTER,
      fileName = "lockscreenSecondaryTextMode",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @ExperimentalPlaybackPersistenceStore
  private fun experimentalPlaybackPersistenceStore(factory: VoiceDataStoreFactory): DataStore<Boolean> {
    return factory.boolean("experimentalPlaybackPersistence", defaultValue = false)
  }

  @Provides
  @SingleIn(AppScope::class)
  @IgnoreFileTagsStore
  private fun ignoreFileTagsStore(factory: VoiceDataStoreFactory): DataStore<Boolean> {
    return factory.boolean("ignoreFileTags", defaultValue = false)
  }

  @Provides
  @SingleIn(AppScope::class)
  @OpenListeningSessionStore
  private fun openListeningSessionStore(factory: VoiceDataStoreFactory): DataStore<OpenSessionCheckpoint?> {
    return factory.create(
      serializer = OpenSessionCheckpoint.serializer().nullable,
      defaultValue = null,
      fileName = "openListeningSession",
      replaceCorruptedWithDefault = true,
    )
  }
}
