package voice.features.playbackScreen.view

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Test
import voice.core.data.PlaybackToolbarAction
import voice.core.data.PlaybackToolbarAction.BOOKMARKS
import voice.core.data.PlaybackToolbarAction.CHAPTER_FIX
import voice.core.data.PlaybackToolbarAction.CHARACTER_LIST
import voice.core.data.PlaybackToolbarAction.LISTENING_LOG
import voice.core.data.PlaybackToolbarAction.PLAYBACK_SPEED
import voice.core.data.PlaybackToolbarAction.UP_NEXT

class ToolbarActionsTest {

  @Test
  fun `defaults pin the three original icons and overflow the rest`() {
    val (pinned, overflow) = partitionToolbarActions(PlaybackToolbarAction.DEFAULT, chapterFixAvailable = true)
    pinned shouldContainExactly listOf(BOOKMARKS, CHARACTER_LIST, PLAYBACK_SPEED)
    overflow shouldContainExactly listOf(CHAPTER_FIX, LISTENING_LOG, UP_NEXT)
  }

  @Test
  fun `display order follows the enum regardless of pin order`() {
    val (pinned, _) = partitionToolbarActions(linkedSetOf(LISTENING_LOG, BOOKMARKS), chapterFixAvailable = true)
    pinned shouldContainExactly listOf(BOOKMARKS, LISTENING_LOG)
  }

  @Test
  fun `chapter fix is dropped everywhere when the book has nothing to fix`() {
    val (pinned, overflow) = partitionToolbarActions(setOf(CHAPTER_FIX, BOOKMARKS), chapterFixAvailable = false)
    pinned shouldContainExactly listOf(BOOKMARKS)
    overflow shouldContainExactly listOf(CHARACTER_LIST, PLAYBACK_SPEED, LISTENING_LOG, UP_NEXT)
  }

  @Test
  fun `pins beyond the cap fall back to the overflow`() {
    val (pinned, overflow) = partitionToolbarActions(PlaybackToolbarAction.entries.toSet(), chapterFixAvailable = true)
    pinned shouldContainExactly listOf(BOOKMARKS, CHARACTER_LIST, PLAYBACK_SPEED, CHAPTER_FIX)
    overflow shouldContainExactly listOf(LISTENING_LOG, UP_NEXT)
  }
}
