package voice.core.playback

import androidx.datastore.core.DataStore
import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.UpNextStore
import voice.core.playback.di.PlaybackScope
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerState

/**
 * Starts the first queued book once the current one plays to its end. Only the player's own end of
 * the book counts: every sleep-timer stop is a pause, which never reaches [Player.STATE_ENDED].
 * Starting the book makes it the current one, and that takes it off the queue.
 */
@Inject
@SingleIn(PlaybackScope::class)
class UpNextAdvancer(
  @UpNextStore
  private val upNextStore: DataStore<List<BookId>>,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  private val bookRepository: BookRepository,
  private val playerController: PlayerController,
  private val sleepTimer: SleepTimer,
  private val scope: CoroutineScope,
) : Player.Listener {

  private var player: Player? = null

  fun attachTo(player: Player) {
    this.player = player
    player.addListener(this)
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState != Player.STATE_ENDED || player?.playWhenReady != true || endsChapterSleepTimer()) return
    scope.launch {
      val current = currentBookStore.data.first()
      val next = upNextStore.data.first().firstOrNull { it != current && bookRepository.get(it) != null } ?: return@launch
      playerController.playPauseForBook(next)
    }
  }

  /**
   * A chapter timer pauses at the start of the next chapter, which the last chapter doesn't have,
   * so the end of the book is its last boundary. If that was the final chapter the timer counted,
   * the listener asked to stop here.
   */
  private fun endsChapterSleepTimer(): Boolean {
    if (sleepTimer.state.value !is SleepTimerState.Enabled.WithEndOfChapter) return false
    sleepTimer.onChapterBoundaryReached(boundaryId = "end")
    return sleepTimer.state.value == SleepTimerState.Disabled
  }
}
