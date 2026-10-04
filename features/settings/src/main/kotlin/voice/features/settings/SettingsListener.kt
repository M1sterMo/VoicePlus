package voice.features.settings

import voice.core.data.GridMode
import voice.core.data.LockscreenSecondaryTextMode
import voice.core.data.LockscreenSliderMode
import voice.core.data.MediaButtonClickAction
import java.time.LocalTime

interface SettingsListener {
  fun close()
  fun toggleDarkTheme()
  fun setLibraryView(mode: GridMode)
  fun setBooksPerRow(count: Int)
  fun seekAmountChanged(seconds: Int)
  fun onSeekAmountRowClick()
  fun autoRewindAmountChang(seconds: Int)
  fun onAutoRewindRowClick()
  fun onAutoSleepTimerDurationClick()
  fun dismissDialog()
  fun setAutoSleepTimer(checked: Boolean)
  fun setAutoSleepTimerStart(time: LocalTime)
  fun setAutoSleepTimerEnd(time: LocalTime)
  fun setAutoSleepTimerDuration(minutes: Int)
  fun openFolderPicker()
  fun onAppVersionClick()

  fun openListeningStats()
  fun openHiddenBooks()
  fun openBackup()
  fun openLicenses()
  fun openPrivacyPolicy()
  fun suggestIdea()
  fun reportProblem()

  fun onMediaButtonDoubleClickRowClick()
  fun onMediaButtonTripleClickRowClick()
  fun setMediaButtonDoubleClickAction(action: MediaButtonClickAction)
  fun setMediaButtonTripleClickAction(action: MediaButtonClickAction)

  fun onLockscreenSliderRowClick()
  fun setLockscreenSliderMode(mode: LockscreenSliderMode)
  fun onLockscreenSecondaryTextRowClick()
  fun setLockscreenSecondaryTextMode(mode: LockscreenSecondaryTextMode)

  fun setExperimentalPlaybackPersistence(enabled: Boolean)
  fun onExperimentalPlaybackPersistenceInfoClick()

  fun setSleepTimerAutoReset(enabled: Boolean)
  fun onSleepTimerAutoResetInfoClick()

  fun setIgnoreFileTags(enabled: Boolean)
  fun onIgnoreFileTagsInfoClick()
  fun confirmIgnoreFileTagsChange()

  companion object {
    fun noop() = object : SettingsListener {
      override fun close() {}
      override fun toggleDarkTheme() {}
      override fun setLibraryView(mode: GridMode) {}
      override fun setBooksPerRow(count: Int) {}
      override fun seekAmountChanged(seconds: Int) {}
      override fun onSeekAmountRowClick() {}
      override fun autoRewindAmountChang(seconds: Int) {}
      override fun onAutoRewindRowClick() {}
      override fun onAutoSleepTimerDurationClick() {}
      override fun dismissDialog() {}
      override fun setAutoSleepTimer(checked: Boolean) {}
      override fun setAutoSleepTimerStart(time: LocalTime) {}
      override fun setAutoSleepTimerEnd(time: LocalTime) {}
      override fun setAutoSleepTimerDuration(minutes: Int) {}
      override fun openFolderPicker() {}
      override fun onAppVersionClick() {}
      override fun openListeningStats() {}
      override fun openHiddenBooks() {}
      override fun openBackup() {}
      override fun openLicenses() {}
      override fun openPrivacyPolicy() {}
      override fun suggestIdea() {}
      override fun reportProblem() {}

      override fun onMediaButtonDoubleClickRowClick() {}
      override fun onMediaButtonTripleClickRowClick() {}
      override fun setMediaButtonDoubleClickAction(action: MediaButtonClickAction) {}
      override fun setMediaButtonTripleClickAction(action: MediaButtonClickAction) {}

      override fun onLockscreenSliderRowClick() {}
      override fun setLockscreenSliderMode(mode: LockscreenSliderMode) {}
      override fun onLockscreenSecondaryTextRowClick() {}
      override fun setLockscreenSecondaryTextMode(mode: LockscreenSecondaryTextMode) {}

      override fun setExperimentalPlaybackPersistence(enabled: Boolean) {}
      override fun onExperimentalPlaybackPersistenceInfoClick() {}

      override fun setSleepTimerAutoReset(enabled: Boolean) {}
      override fun onSleepTimerAutoResetInfoClick() {}

      override fun setIgnoreFileTags(enabled: Boolean) {}
      override fun onIgnoreFileTagsInfoClick() {}
      override fun confirmIgnoreFileTagsChange() {}
    }
  }
}
