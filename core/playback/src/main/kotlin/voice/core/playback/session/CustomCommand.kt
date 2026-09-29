package voice.core.playback.session

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import voice.core.playback.misc.Decibel

@Serializable
internal sealed interface CustomCommand {

  @Serializable
  data object ForceSeekToNext : CustomCommand

  @Serializable
  data object ForceSeekToPrevious : CustomCommand

  @Serializable
  data object SeekBack : CustomCommand

  @Serializable
  data object SeekForward : CustomCommand

  @Serializable
  data class SetSkipSilence(val skipSilence: Boolean) : CustomCommand

  @Serializable
  data class SetGain(
    val gain: Decibel,
    val remember: Boolean = false,
  ) : CustomCommand

  @Serializable
  data class PauseWithRewind(val rewindMs: Long) : CustomCommand

  // Label the next seek in the listening log (stable ListeningEventType id). Sent immediately
  // before the seek on the same session binder, so ordering is guaranteed like PauseWithRewind.
  @Serializable
  data class TagNextSeek(val typeId: Int) : CustomCommand

  companion object {

    const val CUSTOM_COMMAND_ACTION = "voiceCommandAction"
    internal const val CUSTOM_COMMAND_EXTRA = "voiceCommandExtra"
    internal fun parse(
      command: SessionCommand,
      args: Bundle,
    ): CustomCommand? {
      if (command.customAction != CUSTOM_COMMAND_ACTION) {
        return null
      }
      val json = args.getString(CUSTOM_COMMAND_EXTRA)
        ?: command.customExtras.getString(CUSTOM_COMMAND_EXTRA)
        ?: return null
      return Json.decodeFromString(serializer(), json)
    }
  }
}

internal fun CustomCommand.toSessionCommand(): SessionCommand {
  val json = Json.encodeToString(CustomCommand.serializer(), this)
  return SessionCommand(
    CustomCommand.CUSTOM_COMMAND_ACTION,
    Bundle().apply {
      putString(CustomCommand.CUSTOM_COMMAND_EXTRA, json)
    },
  )
}

@IgnorableReturnValue
internal fun MediaController.sendCustomCommand(command: CustomCommand): ListenableFuture<SessionResult> {
  val json = Json.encodeToString(CustomCommand.serializer(), command)
  return sendCustomCommand(
    SessionCommand(CustomCommand.CUSTOM_COMMAND_ACTION, Bundle.EMPTY),
    Bundle().apply {
      putString(CustomCommand.CUSTOM_COMMAND_EXTRA, json)
    },
  )
}
