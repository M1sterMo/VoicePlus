package voice.core.data

import kotlinx.serialization.Serializable

/**
 * Actions the user can pin to the now-playing top app bar. Declaration order is the display
 * order in the toolbar; anything not pinned stays reachable from the overflow menu.
 */
@Serializable
public enum class PlaybackToolbarAction {
  BOOKMARKS,
  CHARACTER_LIST,
  PLAYBACK_SPEED,
  CHAPTER_FIX,
  LISTENING_LOG,
  UP_NEXT,
  ;

  public companion object {
    public val DEFAULT: Set<PlaybackToolbarAction> = setOf(BOOKMARKS, CHARACTER_LIST, PLAYBACK_SPEED)

    /** Sleep timer, close and overflow are always present; four more still fit a 360dp app bar. */
    public const val MAX_PINNED: Int = 4
  }
}
