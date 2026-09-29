package voice.features.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.common.AppInfoProvider
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.common.RetainedViewModel
import voice.core.data.GridMode
import voice.core.data.LockscreenSecondaryTextMode
import voice.core.data.LockscreenSliderMode
import voice.core.data.MediaButtonClickAction
import voice.core.data.sleeptimer.SleepTimerPreference
import voice.core.data.store.AutoRewindAmountStore
import voice.core.data.store.BooksPerRowStore
import voice.core.data.store.DarkThemeStore
import voice.core.data.store.ExperimentalPlaybackPersistenceStore
import voice.core.data.store.GridModeStore
import voice.core.data.store.IgnoreFileTagsStore
import voice.core.data.store.LockscreenSecondaryTextModeStore
import voice.core.data.store.LockscreenSliderModeStore
import voice.core.data.store.MediaButtonDoubleClickHandlerStore
import voice.core.data.store.MediaButtonTripleClickHandlerStore
import voice.core.data.store.SeekTimeStore
import voice.core.data.store.SleepTimerPreferenceStore
import voice.core.featureflag.FeatureFlag
import voice.core.featureflag.FolderPickerInSettingsFeatureFlagQualifier
import voice.core.scanner.MediaScanTrigger
import voice.core.ui.DARK_THEME_SETTABLE
import voice.navigation.Destination
import voice.navigation.Navigator
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes

@Inject
class SettingsViewModel(
  @DarkThemeStore
  private val useDarkThemeStore: DataStore<Boolean>,
  @AutoRewindAmountStore
  private val autoRewindAmountStore: DataStore<Int>,
  @SeekTimeStore
  private val seekTimeStore: DataStore<Int>,
  private val navigator: Navigator,
  private val appInfoProvider: AppInfoProvider,
  @GridModeStore
  private val gridModeStore: DataStore<GridMode>,
  @BooksPerRowStore
  private val booksPerRowStore: DataStore<Int>,
  @SleepTimerPreferenceStore
  private val sleepTimerPreferenceStore: DataStore<SleepTimerPreference>,
  @FolderPickerInSettingsFeatureFlagQualifier
  private val folderPickerInSettingsFeatureFlag: FeatureFlag<Boolean>,
  @MediaButtonDoubleClickHandlerStore
  private val mediaButtonDoubleClickHandlerStore: DataStore<MediaButtonClickAction>,
  @MediaButtonTripleClickHandlerStore
  private val mediaButtonTripleClickHandlerStore: DataStore<MediaButtonClickAction>,
  @LockscreenSliderModeStore
  private val lockscreenSliderModeStore: DataStore<LockscreenSliderMode>,
  @LockscreenSecondaryTextModeStore
  private val lockscreenSecondaryTextModeStore: DataStore<LockscreenSecondaryTextMode>,
  @ExperimentalPlaybackPersistenceStore
  private val experimentalPlaybackPersistenceStore: DataStore<Boolean>,
  @IgnoreFileTagsStore
  private val ignoreFileTagsStore: DataStore<Boolean>,
  private val mediaScanTrigger: MediaScanTrigger,
  dispatcherProvider: DispatcherProvider,
) : RetainedViewModel(MainScope(dispatcherProvider)),
  SettingsListener {
  private val _viewEffects = MutableSharedFlow<SettingsViewEffect>(extraBufferCapacity = 1)
  internal val viewEffects: SharedFlow<SettingsViewEffect> = _viewEffects.asSharedFlow()
  private val dialog = mutableStateOf<SettingsViewState.Dialog?>(null)

  @Composable
  fun viewState(): SettingsViewState {
    val useDarkTheme by remember { useDarkThemeStore.data }.collectAsState(initial = false)
    val autoRewindAmount by remember { autoRewindAmountStore.data }.collectAsState(initial = 0)
    val seekTime by remember { seekTimeStore.data }.collectAsState(initial = 0)
    val gridMode by remember { gridModeStore.data }.collectAsState(initial = GridMode.GRID)
    val booksPerRow by remember { booksPerRowStore.data }.collectAsState(initial = 2)
    val autoSleepTimer by remember { sleepTimerPreferenceStore.data }.collectAsState(
      initial = SleepTimerPreference.Default,
    )
    val showFolderPickerEntry = remember {
      folderPickerInSettingsFeatureFlag.get()
    }
    val mediaButtonDoubleClickAction by remember { mediaButtonDoubleClickHandlerStore.data }.collectAsState(
      initial = MediaButtonClickAction.SKIP_FORWARD,
    )
    val mediaButtonTripleClickAction by remember { mediaButtonTripleClickHandlerStore.data }.collectAsState(
      initial = MediaButtonClickAction.SKIP_BACKWARD,
    )
    val lockscreenSliderMode by remember { lockscreenSliderModeStore.data }.collectAsState(
      initial = LockscreenSliderMode.CHAPTER,
    )
    val lockscreenSecondaryTextMode by remember { lockscreenSecondaryTextModeStore.data }.collectAsState(
      initial = LockscreenSecondaryTextMode.CHAPTER,
    )
    val experimentalPlaybackPersistenceEnabled by remember { experimentalPlaybackPersistenceStore.data }.collectAsState(
      initial = false,
    )
    val ignoreFileTags by remember { ignoreFileTagsStore.data }.collectAsState(initial = false)
    return SettingsViewState(
      useDarkTheme = useDarkTheme,
      showDarkThemePref = DARK_THEME_SETTABLE,
      seekTimeInSeconds = seekTime,
      autoRewindInSeconds = autoRewindAmount,
      dialog = dialog.value,
      appVersion = appInfoProvider.versionName,
      booksPerRow = booksPerRow.coerceIn(2, 3),
      gridMode = when (gridMode) {
        GridMode.FOLLOW_DEVICE -> GridMode.BOOKS
        else -> gridMode
      },
      autoSleepTimer = SettingsViewState.AutoSleepTimerViewState(
        enabled = autoSleepTimer.autoSleepTimerEnabled,
        startTime = autoSleepTimer.autoSleepStartTime,
        endTime = autoSleepTimer.autoSleepEndTime,
        duration = autoSleepTimer.duration,
      ),
      showFolderPickerEntry = showFolderPickerEntry,
      mediaButtonDoubleClickAction = mediaButtonDoubleClickAction,
      mediaButtonTripleClickAction = mediaButtonTripleClickAction,
      lockscreenSliderMode = lockscreenSliderMode,
      lockscreenSecondaryTextMode = lockscreenSecondaryTextMode,
      experimentalPlaybackPersistenceEnabled = experimentalPlaybackPersistenceEnabled,
      sleepTimerAutoResetEnabled = autoSleepTimer.autoResetEnabled,
      ignoreFileTags = ignoreFileTags,
    )
  }

  override fun close() {
    navigator.goBack()
  }

  override fun toggleDarkTheme() {
    scope.launch {
      useDarkThemeStore.updateData { !it }
    }
  }

  override fun setLibraryView(mode: GridMode) {
    scope.launch {
      gridModeStore.updateData { mode }
    }
  }

  override fun setBooksPerRow(count: Int) {
    require(count in 2..3)
    scope.launch { booksPerRowStore.updateData { count } }
  }

  override fun seekAmountChanged(seconds: Int) {
    scope.launch {
      seekTimeStore.updateData { seconds }
    }
  }

  override fun onSeekAmountRowClick() {
    dialog.value = SettingsViewState.Dialog.SeekTime
  }

  override fun autoRewindAmountChang(seconds: Int) {
    scope.launch {
      autoRewindAmountStore.updateData { seconds }
    }
  }

  override fun onAutoRewindRowClick() {
    dialog.value = SettingsViewState.Dialog.AutoRewindAmount
  }

  override fun onAutoSleepTimerDurationClick() {
    dialog.value = SettingsViewState.Dialog.AutoSleepTimerDuration
  }

  override fun dismissDialog() {
    dialog.value = null
  }

  override fun openFolderPicker() {
    navigator.goTo(Destination.FolderPicker)
  }

  override fun setAutoSleepTimer(checked: Boolean) {
    scope.launch {
      sleepTimerPreferenceStore.updateData { currentPrefs ->
        currentPrefs.copy(autoSleepTimerEnabled = checked)
      }
    }
  }

  override fun setAutoSleepTimerStart(time: LocalTime) {
    scope.launch {
      sleepTimerPreferenceStore.updateData { currentPrefs ->
        currentPrefs.copy(autoSleepStartTime = time)
      }
    }
  }

  override fun setAutoSleepTimerEnd(time: LocalTime) {
    scope.launch {
      sleepTimerPreferenceStore.updateData { currentPrefs ->
        currentPrefs.copy(autoSleepEndTime = time)
      }
    }
  }

  override fun setAutoSleepTimerDuration(minutes: Int) {
    scope.launch {
      sleepTimerPreferenceStore.updateData { currentPrefs ->
        currentPrefs.copy(duration = minutes.minutes)
      }
    }
  }

  override fun onAppVersionClick() {}

  override fun openListeningStats() {
    navigator.goTo(Destination.ListeningStatistics)
  }

  override fun openHiddenBooks() {
    navigator.goTo(Destination.HiddenBooks)
  }

  override fun openBackup() {
    navigator.goTo(Destination.BackupRestore)
  }

  override fun openLicenses() {
    navigator.goTo(Destination.OpenSourceLicenses)
  }

  override fun openPrivacyPolicy() {
    navigator.goTo(Destination.Website(PRIVACY_POLICY_URL))
  }

  override fun suggestIdea() {
    navigator.goTo(Destination.Website(IDEAS_URL))
  }

  override fun reportProblem() {
    val query = listOf(
      "template" to "bug.yml",
      "version" to appInfoProvider.versionName,
      "androidversion" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
      "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
    ).joinToString("&") { (name, value) ->
      "${name.urlEncoded()}=${value.urlEncoded()}"
    }
    val url = "$BUG_REPORT_URL?$query"
    navigator.goTo(Destination.Website(url))
  }

  override fun onMediaButtonDoubleClickRowClick() {
    dialog.value = SettingsViewState.Dialog.MediaButtonDoubleClickAction
  }

  override fun onMediaButtonTripleClickRowClick() {
    dialog.value = SettingsViewState.Dialog.MediaButtonTripleAction
  }

  override fun setMediaButtonDoubleClickAction(action: MediaButtonClickAction) {
    scope.launch {
      mediaButtonDoubleClickHandlerStore.updateData { action }
    }
  }

  override fun setMediaButtonTripleClickAction(action: MediaButtonClickAction) {
    scope.launch {
      mediaButtonTripleClickHandlerStore.updateData { action }
    }
  }

  override fun onLockscreenSliderRowClick() {
    dialog.value = SettingsViewState.Dialog.LockscreenSliderMode
  }

  override fun setLockscreenSliderMode(mode: LockscreenSliderMode) {
    scope.launch {
      lockscreenSliderModeStore.updateData { mode }
    }
  }

  override fun onLockscreenSecondaryTextRowClick() {
    dialog.value = SettingsViewState.Dialog.LockscreenSecondaryTextMode
  }

  override fun setLockscreenSecondaryTextMode(mode: LockscreenSecondaryTextMode) {
    scope.launch {
      lockscreenSecondaryTextModeStore.updateData { mode }
    }
  }

  override fun setExperimentalPlaybackPersistence(enabled: Boolean) {
    scope.launch {
      experimentalPlaybackPersistenceStore.updateData { enabled }
    }
  }

  override fun onExperimentalPlaybackPersistenceInfoClick() {
    dialog.value = SettingsViewState.Dialog.ExperimentalPlaybackPersistenceInfo
  }

  override fun setSleepTimerAutoReset(enabled: Boolean) {
    scope.launch {
      sleepTimerPreferenceStore.updateData { it.copy(autoResetEnabled = enabled) }
    }
  }

  override fun onSleepTimerAutoResetInfoClick() {
    dialog.value = SettingsViewState.Dialog.SleepTimerAutoResetInfo
  }

  override fun setIgnoreFileTags(enabled: Boolean) {
    dialog.value = SettingsViewState.Dialog.IgnoreFileTagsConfirm(enabled)
  }

  override fun onIgnoreFileTagsInfoClick() {
    dialog.value = SettingsViewState.Dialog.IgnoreFileTagsInfo
  }

  override fun confirmIgnoreFileTagsChange() {
    val newValue = (dialog.value as? SettingsViewState.Dialog.IgnoreFileTagsConfirm)?.newValue ?: return
    dismissDialog()
    scope.launch {
      ignoreFileTagsStore.updateData { newValue }
      // forceReParse re-derives chapter names per book during the scan; no global chapter wipe,
      // so a scan that can't read files (e.g. a dropped permission) won't blank the library.
      mediaScanTrigger.scan(restartIfScanning = true, forceReParse = true)
    }
  }
}

private fun String.urlEncoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private const val IDEAS_URL = "https://github.com/mistermo-vibecode/VoicePlus/discussions/categories/ideas"
private const val BUG_REPORT_URL = "https://github.com/mistermo-vibecode/VoicePlus/issues/new"
private const val PRIVACY_POLICY_URL = "https://github.com/mistermo-vibecode/VoicePlus/blob/main/PRIVACY.md"
