package voice.features.settings

import voice.core.data.GridMode
import voice.core.data.LockscreenSecondaryTextMode
import voice.core.data.LockscreenSliderMode
import voice.core.data.MediaButtonClickAction
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class SettingsViewState(
  val useDarkTheme: Boolean,
  val showDarkThemePref: Boolean,
  val seekTimeInSeconds: Int,
  val autoRewindInSeconds: Int,
  val appVersion: String,
  val dialog: Dialog?,
  val gridMode: GridMode,
  val booksPerRow: Int,
  val autoSleepTimer: AutoSleepTimerViewState,
  val showFolderPickerEntry: Boolean,
  val mediaButtonDoubleClickAction: MediaButtonClickAction,
  val mediaButtonTripleClickAction: MediaButtonClickAction,
  val lockscreenSliderMode: LockscreenSliderMode,
  val lockscreenSecondaryTextMode: LockscreenSecondaryTextMode,
  val experimentalPlaybackPersistenceEnabled: Boolean,
  val sleepTimerAutoResetEnabled: Boolean,
  val ignoreFileTags: Boolean,
) {

  sealed interface Dialog {
    data object AutoRewindAmount : Dialog
    data object SeekTime : Dialog
    data object AutoSleepTimerDuration : Dialog
    data object MediaButtonDoubleClickAction : Dialog
    data object MediaButtonTripleAction : Dialog
    data object LockscreenSliderMode : Dialog
    data object LockscreenSecondaryTextMode : Dialog
    data object SleepTimerAutoResetInfo : Dialog
    data object ExperimentalPlaybackPersistenceInfo : Dialog
    data object IgnoreFileTagsInfo : Dialog
    data class IgnoreFileTagsConfirm(val newValue: Boolean) : Dialog
  }

  companion object {
    fun preview(): SettingsViewState {
      return SettingsViewState(
        useDarkTheme = false,
        showDarkThemePref = true,
        seekTimeInSeconds = 42,
        autoRewindInSeconds = 12,
        dialog = null,
        appVersion = "1.2.3",
        gridMode = GridMode.GRID,
        booksPerRow = 2,
        autoSleepTimer = AutoSleepTimerViewState.preview(),
        showFolderPickerEntry = false,
        mediaButtonDoubleClickAction = MediaButtonClickAction.SKIP_FORWARD,
        mediaButtonTripleClickAction = MediaButtonClickAction.SKIP_BACKWARD,
        lockscreenSliderMode = LockscreenSliderMode.CHAPTER,
        lockscreenSecondaryTextMode = LockscreenSecondaryTextMode.CHAPTER,
        experimentalPlaybackPersistenceEnabled = false,
        sleepTimerAutoResetEnabled = true,
        ignoreFileTags = false,
      )
    }
  }

  data class AutoSleepTimerViewState(
    val enabled: Boolean,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val duration: Duration,
  ) {
    companion object {
      fun preview(): AutoSleepTimerViewState {
        return AutoSleepTimerViewState(
          enabled = false,
          startTime = LocalTime.of(22, 0),
          endTime = LocalTime.of(6, 0),
          duration = 20.minutes,
        )
      }
    }
  }
}
