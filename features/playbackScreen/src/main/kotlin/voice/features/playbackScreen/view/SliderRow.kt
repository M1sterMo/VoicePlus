package voice.features.playbackScreen.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import voice.core.ui.formatTime
import voice.features.playbackScreen.atSpeed
import kotlin.math.abs
import kotlin.time.Duration

@Composable
internal fun SliderRow(
  duration: Duration,
  playedTime: Duration,
  playbackSpeed: Float,
  onSeek: (Duration) -> Unit,
  showRemaining: Boolean,
  onTimeClick: () -> Unit,
  enabled: Boolean = true,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    var localValue by remember { mutableFloatStateOf(0F) }
    var dragging by remember { mutableStateOf(false) }
    // The reported position lags a seek (it is persisted asynchronously), so after release we keep
    // showing the value the user chose until the report catches up — otherwise the thumb snaps back
    // to the stale pre-drag position for a beat and then leaps forward, which reads as jitter.
    var heldAfterSeek by remember { mutableStateOf<Float?>(null) }

    val liveFraction = (playedTime / duration).toFloat().coerceIn(0F, 1F)
    heldAfterSeek?.let { held ->
      // Release the hold as soon as the reported position lands near the chosen target. Done in an
      // effect, not the composition body — writing state that this pass already read is the classic
      // backwards write and forces a guaranteed extra recomposition frame.
      val caughtUp = abs(liveFraction - held) * duration.inWholeMilliseconds <= CATCH_UP_TOLERANCE_MS
      LaunchedEffect(caughtUp) {
        if (caughtUp) heldAfterSeek = null
      }
      // Hard deadline keyed on the hold itself (not the ticking position), so a seek that never
      // reflects back — book switched mid-hold, seek rejected — can't freeze the thumb.
      LaunchedEffect(held) {
        delay(HOLD_TIMEOUT_MS)
        heldAfterSeek = null
      }
    }

    val displayedFraction = when {
      dragging -> localValue
      else -> heldAfterSeek ?: liveFraction
    }
    // Labels show listening time at the current speed; the slider itself stays on audio time.
    val listeningMs = duration.atSpeed(playbackSpeed).inWholeMilliseconds
    // The label lives in the same Row as the slider, so its width sets the track's width. Pin it
    // to the widest string it can show (the full duration) via an invisible template — otherwise
    // every digit change mid-drag resizes the track and the thumb hops under a steady finger.
    Box(contentAlignment = Alignment.CenterEnd) {
      Text(
        text = formatTime(timeMs = listeningMs, durationMs = listeningMs),
        modifier = Modifier.alpha(0F),
      )
      Text(
        text = formatTime(timeMs = (listeningMs * displayedFraction).toLong(), durationMs = listeningMs),
      )
    }
    Slider(
      modifier = Modifier
        .weight(1F)
        .padding(horizontal = 8.dp),
      enabled = enabled,
      value = displayedFraction,
      onValueChange = {
        dragging = true
        localValue = it
      },
      onValueChangeFinished = {
        onSeek(duration * localValue.toDouble())
        heldAfterSeek = localValue
        dragging = false
      },
    )
    // Tapping the length swaps it for a countdown of the chapter, and back, as upstream Voice does.
    Box(Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onTimeClick), contentAlignment = Alignment.CenterEnd) {
      Text(text = "−" + formatTime(timeMs = listeningMs, durationMs = listeningMs), modifier = Modifier.alpha(0F))
      Text(
        text = if (showRemaining) {
          "−" + formatTime(timeMs = (listeningMs * (1 - displayedFraction)).toLong(), durationMs = listeningMs)
        } else {
          formatTime(timeMs = listeningMs, durationMs = listeningMs)
        },
      )
    }
  }
}

// How close (in playback time) the reported position must get to the seek target before the
// slider trusts it again. Position persists at 1s granularity; 2.5s absorbs one write cycle
// plus the auto-rewind-free seek landing slightly off the exact target.
private const val CATCH_UP_TOLERANCE_MS = 2_500L

private const val HOLD_TIMEOUT_MS = 2_000L
