package voice.features.playbackScreen.view

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BedtimeOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.data.PlaybackToolbarAction
import voice.core.strings.R
import voice.features.playbackScreen.BookPlayViewState

@Composable
internal fun BookPlayAppBar(
  viewState: BookPlayViewState,
  onSleepTimerClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onBookmarkLongClick: () -> Unit,
  onSpeedChangeClick: () -> Unit,
  onSkipSilenceClick: () -> Unit,
  onVolumeBoostClick: () -> Unit,
  onListeningLogClick: () -> Unit,
  onCharacterListClick: () -> Unit,
  onUpNextClick: () -> Unit,
  onAboutBookClick: () -> Unit,
  onCustomizeToolbarClick: () -> Unit,
  onCloseClick: () -> Unit,
  useLandscapeLayout: Boolean,
  onEditChapterNamesClick: (() -> Unit)? = null,
) {
  val (pinnedActions, overflowActions) = partitionToolbarActions(
    pinnedActions = viewState.toolbarActions,
    chapterFixAvailable = onEditChapterNamesClick != null,
  )
  val onActionClick: (PlaybackToolbarAction) -> Unit = { action ->
    when (action) {
      PlaybackToolbarAction.BOOKMARKS -> onBookmarkClick()
      PlaybackToolbarAction.CHARACTER_LIST -> onCharacterListClick()
      PlaybackToolbarAction.PLAYBACK_SPEED -> onSpeedChangeClick()
      PlaybackToolbarAction.CHAPTER_FIX -> onEditChapterNamesClick?.invoke()
      PlaybackToolbarAction.LISTENING_LOG -> onListeningLogClick()
      PlaybackToolbarAction.UP_NEXT -> onUpNextClick()
      PlaybackToolbarAction.ABOUT_BOOK -> onAboutBookClick()
    }
  }

  val appBarActions: @Composable RowScope.() -> Unit = {
    IconButton(onClick = onSleepTimerClick) {
      Icon(
        imageVector = if (viewState.sleepTimerState is BookPlayViewState.SleepTimerViewState.Disabled) {
          Icons.Outlined.Bedtime
        } else {
          Icons.Outlined.BedtimeOff
        },
        contentDescription = stringResource(id = R.string.action_sleep),
      )
    }
    pinnedActions.forEach { action ->
      when (action) {
        PlaybackToolbarAction.BOOKMARKS -> {
          // Not an IconButton: long-press adds a quick bookmark without leaving the screen.
          Box(
            modifier = Modifier
              .size(40.dp)
              .combinedClickable(
                onClick = onBookmarkClick,
                onLongClick = onBookmarkLongClick,
                indication = ripple(bounded = false, radius = 20.dp),
                interactionSource = remember { MutableInteractionSource() },
              ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = action.icon,
              contentDescription = stringResource(id = action.labelRes),
            )
          }
        }
        PlaybackToolbarAction.CHARACTER_LIST -> {
          IconButton(onClick = onCharacterListClick) {
            BadgedBox(
              badge = {
                if (viewState.characterCount > 0) {
                  Badge()
                }
              },
            ) {
              Icon(
                imageVector = action.icon,
                contentDescription = stringResource(id = action.labelRes),
              )
            }
          }
        }
        else -> {
          IconButton(onClick = { onActionClick(action) }) {
            Icon(
              imageVector = action.icon,
              contentDescription = stringResource(id = action.labelRes),
            )
          }
        }
      }
    }
    OverflowMenu(
      skipSilence = viewState.skipSilence,
      onSkipSilenceClick = onSkipSilenceClick,
      onVolumeBoostClick = onVolumeBoostClick,
      overflowActions = overflowActions,
      onActionClick = onActionClick,
      // Long-press on the pinned icon is the quick bookmark; with the icon gone it needs a menu entry.
      onQuickBookmarkClick = onBookmarkLongClick.takeIf { PlaybackToolbarAction.BOOKMARKS in overflowActions },
      onCustomizeToolbarClick = onCustomizeToolbarClick,
    )
  }
  if (useLandscapeLayout) {
    TopAppBar(
      navigationIcon = {
        CloseIcon(onCloseClick)
      },
      actions = appBarActions,
      title = {
        AppBarTitle(viewState, large = false)
      },
    )
  } else {
    LargeTopAppBar(
      navigationIcon = {
        CloseIcon(onCloseClick)
      },
      actions = appBarActions,
      title = {
        AppBarTitle(viewState, large = true)
      },
    )
  }
}
