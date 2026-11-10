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
      val next = upNextStore.data.first()
        .firstNotNullOfOrNull { id -> bookRepository.get(id).takeIf { id != current } } ?: return@launch
      // Queueing a finished book means listening again: resumed at its end it would finish at once.
      // Same "finished" rule as the library's Completed books.
      if (next.position >= next.duration - FINISHED_WITHIN_MS) {
        bookRepository.updateBook(next.id) { it.copy(currentChapter = next.chapters.first().id, positionInChapter = 0) }
      }
      playerController.playPauseForBook(next.id)
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

private const val FINISHED_WITHIN_MS = 5_000L
