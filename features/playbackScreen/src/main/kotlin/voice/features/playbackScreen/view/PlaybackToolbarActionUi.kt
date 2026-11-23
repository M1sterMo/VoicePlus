package voice.features.playbackScreen.view

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import voice.core.data.PlaybackToolbarAction
import voice.core.strings.R as StringsR

internal val PlaybackToolbarAction.icon: ImageVector
  get() = when (this) {
    PlaybackToolbarAction.BOOKMARKS -> Icons.Outlined.CollectionsBookmark
    PlaybackToolbarAction.CHARACTER_LIST -> Icons.Outlined.Group
    PlaybackToolbarAction.PLAYBACK_SPEED -> Icons.Outlined.Speed
    PlaybackToolbarAction.CHAPTER_FIX -> Icons.Outlined.EditNote
    PlaybackToolbarAction.LISTENING_LOG -> Icons.Outlined.History
    PlaybackToolbarAction.UP_NEXT -> Icons.AutoMirrored.Outlined.QueueMusic
    PlaybackToolbarAction.ABOUT_BOOK -> Icons.Outlined.Info
  }

internal val PlaybackToolbarAction.labelRes: Int
  get() = when (this) {
    PlaybackToolbarAction.BOOKMARKS -> StringsR.string.bookmark
    PlaybackToolbarAction.CHARACTER_LIST -> StringsR.string.character_list
    PlaybackToolbarAction.PLAYBACK_SPEED -> StringsR.string.playback_speed
    PlaybackToolbarAction.CHAPTER_FIX -> StringsR.string.chapter_fix_menu
    PlaybackToolbarAction.LISTENING_LOG -> StringsR.string.listening_log
    PlaybackToolbarAction.UP_NEXT -> StringsR.string.up_next
    PlaybackToolbarAction.ABOUT_BOOK -> StringsR.string.about_book
  }
