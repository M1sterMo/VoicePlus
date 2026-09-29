package voice.features.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.GridMode
import voice.core.data.LockscreenSecondaryTextMode
import voice.core.data.LockscreenSliderMode
import voice.core.ui.VoiceTheme
import voice.features.settings.SettingsListener
import voice.features.settings.SettingsViewEffect
import voice.features.settings.SettingsViewModel
import voice.features.settings.SettingsViewState
import voice.features.settings.views.sleeptimer.AutoSleepTimerDurationDialog
import voice.features.settings.views.sleeptimer.SleepTimerCard
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@Composable
@Preview
private fun SettingsPreview() {
  VoiceTheme {
    Settings(
      SettingsViewState.preview(),
      SettingsListener.noop(),
    )
  }
}

@Composable
private fun Settings(
  viewState: SettingsViewState,
  listener: SettingsListener,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  var showAppearanceSettings by rememberSaveable { mutableStateOf(false) }
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState)
    },
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
          Text(stringResource(StringsR.string.action_settings))
        },
        navigationIcon = {
          IconButton(
            onClick = {
              listener.close()
            },
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = stringResource(StringsR.string.close),
            )
          }
        },
      )
    },
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier.padding(contentPadding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(key = "appearance") {
        SettingsGroup {
          AppearanceSettingsRow(viewState) {
            showAppearanceSettings = true
          }
        }
      }

      item(key = "library") {
        SettingsSection(stringResource(StringsR.string.settings_section_library)) {
          SettingsGroup {
            ListItem(
              modifier = Modifier.clickable { listener.openListeningStats() },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              leadingContent = {
                Icon(Icons.Outlined.BarChart, contentDescription = null)
              },
              headlineContent = {
                Text(stringResource(StringsR.string.listening_stats))
              },
              supportingContent = {
                Text(stringResource(StringsR.string.settings_listening_stats_summary))
              },
              trailingContent = {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
              },
            )
            SettingsDivider()
            ListItem(
              modifier = Modifier.clickable { listener.openBackup() },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              leadingContent = {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
              },
              headlineContent = {
                Text(stringResource(StringsR.string.backup_title))
              },
              supportingContent = {
                Text(stringResource(StringsR.string.settings_backup_summary))
              },
              trailingContent = {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
              },
            )
            SettingsDivider()
            if (viewState.showFolderPickerEntry) {
              ListItem(
                modifier = Modifier.clickable { listener.openFolderPicker() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                  Icon(Icons.Outlined.Book, contentDescription = null)
                },
                headlineContent = {
                  Text(stringResource(StringsR.string.audiobook_folders_title))
                },
                supportingContent = {
                  Text(stringResource(StringsR.string.pref_audiobook_folders_explanation))
                },
                trailingContent = {
                  Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                },
              )
              SettingsDivider()
            }
            ListItem(
              modifier = Modifier.clickable { listener.openHiddenBooks() },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              leadingContent = {
                Icon(Icons.Outlined.VisibilityOff, contentDescription = null)
              },
              headlineContent = {
                Text(stringResource(StringsR.string.hidden_books))
              },
              trailingContent = {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
              },
            )
            SettingsDivider()
            ListItem(
              modifier = Modifier.clickable { listener.setIgnoreFileTags(!viewState.ignoreFileTags) },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              leadingContent = {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
              },
              headlineContent = {
                Text(stringResource(StringsR.string.pref_ignore_file_tags))
              },
              supportingContent = {
                Text(
                  text = stringResource(StringsR.string.experimental),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              },
              trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = listener::onIgnoreFileTagsInfoClick) {
                    Icon(
                      imageVector = Icons.Outlined.Info,
                      contentDescription = stringResource(StringsR.string.pref_ignore_file_tags_more_info),
                    )
                  }
                  Switch(
                    checked = viewState.ignoreFileTags,
                    onCheckedChange = listener::setIgnoreFileTags,
                  )
                }
              },
            )
          }
        }
      }

      item(key = "playback") {
        SettingsSection(stringResource(StringsR.string.settings_section_playback)) {
          SettingsGroup {
            SeekTimeRow(viewState.seekTimeInSeconds, listener::onSeekAmountRowClick)
            SettingsDivider()
            AutoRewindRow(viewState.autoRewindInSeconds, listener::onAutoRewindRowClick)
            SettingsDivider()
            MediaButtonActionRow(
              title = stringResource(StringsR.string.pref_media_button_double_click),
              currentAction = viewState.mediaButtonDoubleClickAction,
              onClick = listener::onMediaButtonDoubleClickRowClick,
            )
            SettingsDivider()
            MediaButtonActionRow(
              title = stringResource(StringsR.string.pref_media_button_triple_click),
              currentAction = viewState.mediaButtonTripleClickAction,
              onClick = listener::onMediaButtonTripleClickRowClick,
            )
          }
        }
      }

      item(key = "sleep_timer") {
        SettingsSection(stringResource(StringsR.string.settings_section_sleep_timer)) {
          SettingsGroup {
            SleepTimerCard(
              autoSleepTimer = viewState.autoSleepTimer,
              autoResetEnabled = viewState.sleepTimerAutoResetEnabled,
              listener = listener,
            )
          }
        }
      }

      item(key = "lockscreen") {
        SettingsSection(stringResource(StringsR.string.settings_section_lockscreen)) {
          SettingsGroup {
            LockscreenSliderRow(
              currentMode = viewState.lockscreenSliderMode,
              onClick = listener::onLockscreenSliderRowClick,
            )
            SettingsDivider()
            LockscreenSecondaryTextRow(
              currentMode = viewState.lockscreenSecondaryTextMode,
              onClick = listener::onLockscreenSecondaryTextRowClick,
            )
          }
        }
      }

      item(key = "advanced") {
        SettingsSection(stringResource(StringsR.string.settings_section_advanced)) {
          SettingsGroup {
            ListItem(
              modifier = Modifier.clickable {
                listener.setExperimentalPlaybackPersistence(!viewState.experimentalPlaybackPersistenceEnabled)
              },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              leadingContent = {
                Icon(Icons.Outlined.Science, contentDescription = null)
              },
              headlineContent = {
                Text(stringResource(StringsR.string.pref_experimental_playback_persistence))
              },
              supportingContent = {
                Text(
                  text = stringResource(StringsR.string.experimental),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              },
              trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = listener::onExperimentalPlaybackPersistenceInfoClick) {
                    Icon(
                      imageVector = Icons.Outlined.Info,
                      contentDescription = stringResource(
                        StringsR.string.pref_experimental_playback_persistence_more_info,
                      ),
                    )
                  }
                  Switch(
                    checked = viewState.experimentalPlaybackPersistenceEnabled,
                    onCheckedChange = listener::setExperimentalPlaybackPersistence,
                  )
                }
              },
            )
          }
        }
      }

      item(key = "help_feedback") {
        SettingsSection(stringResource(StringsR.string.settings_section_help_feedback)) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrioritySettingsItem(
              title = stringResource(StringsR.string.pref_suggest_idea),
              supportingText = stringResource(StringsR.string.settings_suggest_idea_summary),
              icon = Icons.Outlined.Lightbulb,
              containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
              onClick = listener::suggestIdea,
            )
            PrioritySettingsItem(
              title = stringResource(StringsR.string.pref_report_issue),
              supportingText = stringResource(StringsR.string.settings_report_issue_summary),
              icon = Icons.Outlined.BugReport,
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
              onClick = listener::reportProblem,
            )
          }
        }
      }

      item(key = "about") {
        SettingsSection(stringResource(StringsR.string.settings_section_about)) {
          SettingsGroup {
            ListItem(
              modifier = Modifier.clickable { listener.openPrivacyPolicy() },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              leadingContent = {
                Icon(Icons.Outlined.Lock, contentDescription = null)
              },
              headlineContent = {
                Text(stringResource(StringsR.string.privacy_policy))
              },
              trailingContent = {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
              },
            )
            SettingsDivider()
            ListItem(
              modifier = Modifier.clickable { listener.openLicenses() },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              leadingContent = {
                Icon(Icons.Outlined.Gavel, contentDescription = null)
              },
              headlineContent = {
                Text(stringResource(StringsR.string.open_source_licenses))
              },
              trailingContent = {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
              },
            )
            SettingsDivider()
            AppVersion(
              appVersion = viewState.appVersion,
              onClick = listener::onAppVersionClick,
            )
          }
        }
      }
    }
    Dialog(viewState, listener)
  }
  if (showAppearanceSettings) {
    AppearanceSettingsSheet(
      state = viewState,
      listener = listener,
      onDismiss = { showAppearanceSettings = false },
    )
  }
}

@ContributesTo(AppScope::class)
interface SettingsGraph {
  val settingsViewModel: SettingsViewModel
}

@ContributesTo(AppScope::class)
interface SettingsProvider {

  @Provides
  @IntoSet
  fun settingsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Settings> { key ->
    NavEntry(key) {
      Settings()
    }
  }
}

@Composable
fun Settings() {
  val viewModel = retain<SettingsViewModel> { rootGraphAs<SettingsGraph>().settingsViewModel }
  val snackbarHostState = remember { SnackbarHostState() }
  val viewState = viewModel.viewState()
  val currentDeveloperMenuUnlockedMessage = rememberUpdatedState("Developer Menu unlocked")
  LaunchedEffect(viewModel) {
    viewModel.viewEffects.collect { viewEffect ->
      when (viewEffect) {
        SettingsViewEffect.DeveloperMenuUnlocked -> {
          snackbarHostState.showSnackbar(currentDeveloperMenuUnlockedMessage.value)
        }
      }
    }
  }
  Settings(viewState, viewModel, snackbarHostState)
}

@Composable
private fun Dialog(
  viewState: SettingsViewState,
  listener: SettingsListener,
) {
  val dialog = viewState.dialog ?: return
  when (dialog) {
    SettingsViewState.Dialog.AutoRewindAmount -> {
      AutoRewindAmountDialog(
        currentSeconds = viewState.autoRewindInSeconds,
        onSecondsConfirm = listener::autoRewindAmountChang,
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.SeekTime -> {
      SeekAmountDialog(
        currentSeconds = viewState.seekTimeInSeconds,
        onSecondsConfirm = listener::seekAmountChanged,
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.AutoSleepTimerDuration -> {
      AutoSleepTimerDurationDialog(
        initialDurationMinutes = viewState.autoSleepTimer.duration.inWholeMinutes.toInt(),
        onConfirm = { minutes ->
          listener.setAutoSleepTimerDuration(minutes)
          listener.dismissDialog()
        },
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.MediaButtonDoubleClickAction -> {
      MediaButtonActionDialog(
        title = stringResource(StringsR.string.pref_media_button_double_click),
        currentAction = viewState.mediaButtonDoubleClickAction,
        onActionConfirm = {
          listener.setMediaButtonDoubleClickAction(it)
          listener.dismissDialog()
        },
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.MediaButtonTripleAction -> {
      MediaButtonActionDialog(
        title = stringResource(StringsR.string.pref_media_button_triple_click),
        currentAction = viewState.mediaButtonTripleClickAction,
        onActionConfirm = {
          listener.setMediaButtonTripleClickAction(it)
          listener.dismissDialog()
        },
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.LockscreenSliderMode -> {
      LockscreenSliderDialog(
        currentMode = viewState.lockscreenSliderMode,
        onModeSelect = {
          listener.setLockscreenSliderMode(it)
          listener.dismissDialog()
        },
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.LockscreenSecondaryTextMode -> {
      LockscreenSecondaryTextDialog(
        currentMode = viewState.lockscreenSecondaryTextMode,
        onModeSelect = {
          listener.setLockscreenSecondaryTextMode(it)
          listener.dismissDialog()
        },
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.SleepTimerAutoResetInfo -> {
      AlertDialog(
        onDismissRequest = listener::dismissDialog,
        title = { Text(stringResource(StringsR.string.pref_sleep_timer_auto_reset)) },
        text = { Text(stringResource(StringsR.string.pref_sleep_timer_auto_reset_info)) },
        confirmButton = {
          TextButton(onClick = listener::dismissDialog) {
            Text(stringResource(StringsR.string.close))
          }
        },
      )
    }
    SettingsViewState.Dialog.ExperimentalPlaybackPersistenceInfo -> {
      AlertDialog(
        onDismissRequest = listener::dismissDialog,
        title = { Text(stringResource(StringsR.string.pref_experimental_playback_persistence)) },
        text = { Text(stringResource(StringsR.string.pref_experimental_playback_persistence_info)) },
        confirmButton = {
          TextButton(onClick = listener::dismissDialog) {
            Text(stringResource(StringsR.string.close))
          }
        },
      )
    }
    SettingsViewState.Dialog.IgnoreFileTagsInfo -> {
      AlertDialog(
        onDismissRequest = listener::dismissDialog,
        title = { Text(stringResource(StringsR.string.pref_ignore_file_tags)) },
        text = { Text(stringResource(StringsR.string.pref_ignore_file_tags_info)) },
        confirmButton = {
          TextButton(onClick = listener::dismissDialog) {
            Text(stringResource(StringsR.string.close))
          }
        },
      )
    }
    is SettingsViewState.Dialog.IgnoreFileTagsConfirm -> {
      val sourceLabel = if (dialog.newValue) {
        stringResource(StringsR.string.file_tags)
      } else {
        stringResource(StringsR.string.folder_names)
      }
      AlertDialog(
        onDismissRequest = listener::dismissDialog,
        title = { Text(stringResource(StringsR.string.ignore_file_tags_confirm_title)) },
        text = { Text(stringResource(StringsR.string.ignore_file_tags_confirm_message, sourceLabel)) },
        confirmButton = {
          Button(onClick = listener::confirmIgnoreFileTagsChange) {
            Text(stringResource(StringsR.string.rescan))
          }
        },
        dismissButton = {
          TextButton(onClick = listener::dismissDialog) {
            Text(stringResource(StringsR.string.dialog_cancel))
          }
        },
      )
    }
  }
}

@Composable
private fun LockscreenSliderRow(
  currentMode: LockscreenSliderMode,
  onClick: () -> Unit,
) {
  val title = stringResource(StringsR.string.pref_lockscreen_slider)
  ListItem(
    modifier = Modifier
      .clickable(onClick = onClick)
      .fillMaxWidth(),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    leadingContent = {
      Icon(Icons.Outlined.Lock, contentDescription = null)
    },
    headlineContent = {
      Text(title)
    },
    trailingContent = {
      Text(stringResource(currentMode.toLabelRes()))
    },
  )
}

@Composable
private fun LockscreenSliderDialog(
  currentMode: LockscreenSliderMode,
  onModeSelect: (LockscreenSliderMode) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(StringsR.string.pref_lockscreen_slider)) },
    text = {
      Column(Modifier.selectableGroup()) {
        LockscreenSliderMode.entries.forEach { mode ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .selectable(
                selected = mode == currentMode,
                onClick = { onModeSelect(mode) },
                role = Role.RadioButton,
              ),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = mode == currentMode,
              onClick = null,
            )
            Text(stringResource(mode.toLabelRes()))
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.close))
      }
    },
  )
}

@Composable
private fun LockscreenSecondaryTextRow(
  currentMode: LockscreenSecondaryTextMode,
  onClick: () -> Unit,
) {
  val title = stringResource(StringsR.string.pref_lockscreen_secondary_text)
  ListItem(
    modifier = Modifier
      .clickable(onClick = onClick)
      .fillMaxWidth(),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    leadingContent = {
      Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = null)
    },
    headlineContent = {
      Text(title)
    },
    trailingContent = {
      Text(stringResource(currentMode.toLabelRes()))
    },
  )
}

@Composable
private fun LockscreenSecondaryTextDialog(
  currentMode: LockscreenSecondaryTextMode,
  onModeSelect: (LockscreenSecondaryTextMode) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(StringsR.string.pref_lockscreen_secondary_text)) },
    text = {
      Column(Modifier.selectableGroup()) {
        LockscreenSecondaryTextMode.entries.forEach { mode ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .selectable(
                selected = mode == currentMode,
                onClick = { onModeSelect(mode) },
                role = Role.RadioButton,
              ),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = mode == currentMode,
              onClick = null,
            )
            Text(stringResource(mode.toLabelRes()))
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.close))
      }
    },
  )
}

internal fun GridMode.toLabelRes(): Int = when (this) {
  GridMode.LIST -> StringsR.string.library_view_list
  GridMode.BOOKS -> StringsR.string.library_view_books
  GridMode.GRID, GridMode.FOLLOW_DEVICE -> StringsR.string.library_view_grid
}

private fun LockscreenSliderMode.toLabelRes(): Int = when (this) {
  LockscreenSliderMode.AUDIOBOOK -> StringsR.string.lockscreen_slider_audiobook
  LockscreenSliderMode.CHAPTER -> StringsR.string.lockscreen_slider_chapter
  LockscreenSliderMode.DISABLED -> StringsR.string.lockscreen_slider_disabled
}

private fun LockscreenSecondaryTextMode.toLabelRes(): Int = when (this) {
  LockscreenSecondaryTextMode.AUTHOR -> StringsR.string.lockscreen_secondary_text_author
  LockscreenSecondaryTextMode.CHAPTER -> StringsR.string.lockscreen_secondary_text_chapter
}
