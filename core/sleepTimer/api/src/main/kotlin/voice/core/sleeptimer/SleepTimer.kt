package voice.core.sleeptimer

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

interface SleepTimer {
  val state: StateFlow<SleepTimerState>
  fun enable(mode: SleepTimerMode)
  fun disable()
  fun reset()

  /**
   * @param boundaryId identifies the chapter boundary that was crossed. Boundaries fire again when
   * playback re-crosses them, so the timer counts each one only once per armed timer — otherwise
   * skipping back over a boundary you already passed would burn a second chapter. It names the
   * book too, since a timer can run on into the next book in Up next.
   */
  fun onChapterBoundaryReached(boundaryId: String)
}

sealed interface SleepTimerState {

  data object Disabled : SleepTimerState
  sealed interface Enabled : SleepTimerState {
    data class WithEndOfChapter(val chaptersRemaining: Int) : Enabled

    @JvmInline
    value class WithDuration(val leftDuration: Duration) : Enabled
  }

  val enabled: Boolean
    get() = when (this) {
      Disabled -> false
      is Enabled -> true
    }
}

sealed interface SleepTimerMode {
  data class TimedWithDuration(val duration: Duration) : SleepTimerMode
  data object TimedWithDefault : SleepTimerMode
  data class EndOfChapter(val chapters: Int = 1) : SleepTimerMode
}
