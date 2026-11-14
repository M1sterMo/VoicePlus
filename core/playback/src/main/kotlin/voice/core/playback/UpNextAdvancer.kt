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
 * Runs the Up next queue. A queued book leaves it once it starts playing, however it was started;
 * opening it alone keeps its place. When a book plays to its end, the first queued one starts. Only
 * the player's own end counts: every sleep-timer stop is a pause, which never reaches [Player.STATE_ENDED].
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

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    // The loaded book, not the current one: a headset can resume a book while another is open.
    val playing = player?.takeIf { isPlaying }?.currentBookId() ?: return
    scope.launch { upNextStore.updateData { it - playing } }
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState != Player.STATE_ENDED || player?.playWhenReady != true) return
    scope.launch {
      val ended = currentBookStore.data.first() ?: return@launch
      if (endsChapterSleepTimer(ended)) return@launch
      val queued = upNextStore.data.first().filter { it != ended }
        .associateWith { id -> bookRepository.get(id)?.takeIf { it.content.isActive } }
      // A book that left the library is gone from Up next too, where it could no longer be removed.
      val gone = queued.filterValues { it == null }.keys
      if (gone.isNotEmpty()) upNextStore.updateData { it - gone }
      val next = queued.values.firstNotNullOfOrNull { it } ?: return@launch
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
  private fun endsChapterSleepTimer(book: BookId): Boolean {
    if (sleepTimer.state.value !is SleepTimerState.Enabled.WithEndOfChapter) return false
    sleepTimer.onChapterBoundaryReached(boundaryId = "${book.value}:end")
    return sleepTimer.state.value == SleepTimerState.Disabled
  }
}

private const val FINISHED_WITHIN_MS = 5_000L
