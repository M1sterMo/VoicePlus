package voice.core.playback.history

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.data.ListeningEventType
import voice.core.playback.di.PlaybackScope

@Inject
@SingleIn(PlaybackScope::class)
class PlaybackIntentHolder(context: Context) {
  // Main-looper-confined transient state (no locks needed — all access is on the playback looper).
  // Cleared by ListeningEventRecorder on the next discontinuity. If a tagged seek produces no
  // discontinuity (e.g. forceSeekToNext at the last chapter), the tag is left stale and will be
  // consumed by the next seek — a known best-effort labeling limitation.
  var pendingSeekIntent: ListeningEventType? = null
  var suppressNextSeek: Boolean = false // used by the transport-events task
  var pendingPauseEndPositionMs: Long? = null // true end position captured before pause auto-rewind
  var stoppedBySleepTimer: Boolean = false // pause originated from the sleep timer

  private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private var sleepResumeConfirmationRequired = preferences.getBoolean(KEY_SLEEP_RESUME_CONFIRMATION, false)
  private var sleepResumeConfirmationDeadlineMs = 0L

  fun requireSleepResumeConfirmation() {
    sleepResumeConfirmationRequired = true
    sleepResumeConfirmationDeadlineMs = 0L
    preferences.edit { putBoolean(KEY_SLEEP_RESUME_CONFIRMATION, true) }
  }

  fun confirmExternalResume(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
    if (!sleepResumeConfirmationRequired) return true
    if (sleepResumeConfirmationDeadlineMs > 0L && nowMs <= sleepResumeConfirmationDeadlineMs) {
      clearSleepResumeConfirmation()
      return true
    }
    sleepResumeConfirmationDeadlineMs = nowMs + SLEEP_RESUME_CONFIRMATION_WINDOW_MS
    return false
  }

  fun clearSleepResumeConfirmation() {
    sleepResumeConfirmationRequired = false
    sleepResumeConfirmationDeadlineMs = 0L
    preferences.edit { remove(KEY_SLEEP_RESUME_CONFIRMATION) }
  }

  internal companion object {
    const val SLEEP_RESUME_CONFIRMATION_WINDOW_MS = 10_000L
    private const val PREFERENCES_NAME = "playback_intent"
    private const val KEY_SLEEP_RESUME_CONFIRMATION = "sleep_resume_confirmation"
  }
}
