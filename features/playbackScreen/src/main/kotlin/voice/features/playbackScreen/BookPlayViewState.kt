package voice.features.playbackScreen

import androidx.compose.runtime.Immutable
import voice.core.data.PlaybackToolbarAction
import voice.core.playback.misc.Decibel
import voice.core.ui.ImmutableFile
import voice.features.sleepTimer.SleepTimerViewState
import kotlin.time.Duration

@Immutable
data class BookPlayViewState(
  val chapterName: String?,
  val showPreviousNextButtons: Boolean,
  val title: String,
  val sleepTimerState: SleepTimerViewState,
  val playedTime: Duration,
  val duration: Duration,
  val playbackSpeed: Float,
  /** Whole-book progress, 0–1. */
  val bookProgress: Float,
  /** Whole-book audio time left; the view converts it to listening time at [playbackSpeed]. */
  val bookTimeLeft: Duration,
  val playing: Boolean,
  val cover: ImmutableFile?,
  /** Draw the cover as a 2:3 book jacket, matching the library's Books layout. */
  val bookJacket: Boolean,
  val skipSilence: Boolean,
  val characterCount: Int,
  val editChapterNamesVisible: Boolean,
  val toolbarActions: Set<PlaybackToolbarAction>,
) {

  sealed interface SleepTimerViewState {
    data object Disabled : SleepTimerViewState

    sealed interface Enabled : SleepTimerViewState {
      @JvmInline
      value class WithEndOfChapter(val chaptersRemaining: Int) : Enabled

      @JvmInline
      value class WithDuration(val leftDuration: Duration) : Enabled
    }
  }

  init {
    require(duration > Duration.ZERO) {
      "Duration must be positive in $this"
    }
  }
}

/**
 * Wall-clock listening time for this much audio at [speed]: at 2x, an hour of audio takes half an hour.
 * Mirrors the library's speed-adjusted remaining time.
 */
internal fun Duration.atSpeed(speed: Float): Duration = if (speed > 0F) this / speed.toDouble() else this

internal sealed interface BookPlayDialogViewState {
  data class SpeedDialog(val speed: Float) : BookPlayDialogViewState {

    val maxSpeed: Float get() = if (speed < 2F) 2F else 3.5F
  }

  data class VolumeGainDialog(
    val gain: Decibel,
    val valueFormatted: String,
    val maxGain: Decibel,
    val remember: Boolean,
  ) : BookPlayDialogViewState

  data class SelectChapterDialog(val items: List<ItemViewState>) : BookPlayDialogViewState {

    data class ItemViewState(
      val number: Int,
      val name: String,
      val active: Boolean,
      val time: String,
    )
  }

  @JvmInline
  value class SleepTimer(val viewState: SleepTimerViewState) : BookPlayDialogViewState

  data object ToolbarActions : BookPlayDialogViewState
}
