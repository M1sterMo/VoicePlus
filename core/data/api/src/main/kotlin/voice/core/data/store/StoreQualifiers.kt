package voice.core.data.store

import dev.zacsweers.metro.Qualifier

@Qualifier
public annotation class OnboardingCompletedStore

@Qualifier
public annotation class CurrentBookStore

/** The books queued to play after the current one, in order. */
@Qualifier
public annotation class UpNextStore

@Qualifier
public annotation class AutoRewindAmountStore

@Qualifier
public annotation class SeekTimeStore

@Qualifier
public annotation class SleepTimerPreferenceStore

@Qualifier
public annotation class GridModeStore

@Qualifier
public annotation class BooksPerRowStore

/** The Android 9 dark-theme switch; only read to migrate into [ThemeModeStore]. */
@Qualifier
public annotation class DarkThemeStore

@Qualifier
public annotation class ThemeModeStore

@Qualifier
public annotation class ThemeColorStore

@Qualifier
public annotation class CoverGlowStore

/** Whether the player shows the chapter's time left instead of its length. */
@Qualifier
public annotation class ChapterCountdownStore

@Qualifier
public annotation class FadeOutStore

@Qualifier
public annotation class AmountOfBatteryOptimizationRequestedStore

@Qualifier
public annotation class ReviewDialogShownStore

@Qualifier
public annotation class ExcludedBooksStore

/** Books whose first files were read and hold no embedded cover, keyed by book and those files' state. */
@Qualifier
public annotation class NoEmbeddedCoverStore

@Qualifier
public annotation class NotStartedExpandedStore

@Qualifier
public annotation class FinishedExpandedStore

@Qualifier
public annotation class MediaButtonDoubleClickHandlerStore

@Qualifier
public annotation class MediaButtonTripleClickHandlerStore

@Qualifier
public annotation class LockscreenSliderModeStore

@Qualifier
public annotation class LockscreenSecondaryTextModeStore

@Qualifier
public annotation class ExperimentalPlaybackPersistenceStore

@Qualifier
public annotation class IgnoreFileTagsStore

@Qualifier
public annotation class OpenListeningSessionStore

@Qualifier
public annotation class PlaybackToolbarActionsStore

@Qualifier
public annotation class GlobalVolumeGainStore
