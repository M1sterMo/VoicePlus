package voice.core.playback.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import voice.core.featureflag.FeatureFlag
import voice.core.featureflag.Media3AudioOffloadFeatureFlagQualifier
import voice.core.playback.history.ListeningEventRecorder
import voice.core.playback.misc.VolumeGain
import voice.core.playback.notification.MainActivityIntentProvider
import voice.core.playback.player.DurationInconsistenciesUpdater
import voice.core.playback.player.OnlyAudioRenderersFactory
import voice.core.playback.player.VoicePlayer
import voice.core.playback.player.onAudioSessionIdChanged
import voice.core.playback.playstate.PlayStateDelegatingListener
import voice.core.playback.playstate.PositionUpdater
import voice.core.playback.session.CustomCommand
import voice.core.playback.session.LibrarySessionCallback
import voice.core.playback.session.LockscreenPlayer
import voice.core.playback.session.PlaybackService
import voice.core.playback.session.toSessionCommand
import voice.core.strings.R as StringsR

@ContributesTo(PlaybackScope::class)
interface PlaybackModule {

  @Provides
  @SingleIn(PlaybackScope::class)
  fun mediaSourceFactory(context: Context): MediaSource.Factory {
    val dataSourceFactory = DefaultDataSource.Factory(context)
    val extractorsFactory = DefaultExtractorsFactory()
      .setConstantBitrateSeekingEnabled(true)
    return ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
  }

  @Provides
  @SingleIn(PlaybackScope::class)
  fun player(
    context: Context,
    onlyAudioRenderersFactory: OnlyAudioRenderersFactory,
    mediaSourceFactory: MediaSource.Factory,
    playStateDelegatingListener: PlayStateDelegatingListener,
    positionUpdater: PositionUpdater,
    listeningEventRecorder: ListeningEventRecorder,
    volumeGain: VolumeGain,
    durationInconsistenciesUpdater: DurationInconsistenciesUpdater,
    @Media3AudioOffloadFeatureFlagQualifier media3AudioOffloadFeatureFlag: FeatureFlag<Boolean>,
  ): Player {
    val audioAttributes = AudioAttributes.Builder()
      .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
      .setUsage(C.USAGE_MEDIA)
      .build()

    return ExoPlayer.Builder(context, onlyAudioRenderersFactory, mediaSourceFactory)
      .setAudioAttributes(audioAttributes, true)
      .setHandleAudioBecomingNoisy(true)
      .setWakeMode(C.WAKE_MODE_LOCAL)
      .build()
      .also { player ->
        if (media3AudioOffloadFeatureFlag.get()) {
          player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
              TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                .setIsGaplessSupportRequired(true)
                .setIsSpeedChangeSupportRequired(true)
                .build(),
            )
            .build()
        }
        playStateDelegatingListener.attachTo(player)
        positionUpdater.attachTo(player)
        listeningEventRecorder.attachTo(player)
        durationInconsistenciesUpdater.attachTo(player)
        player.onAudioSessionIdChanged {
          volumeGain.audioSessionId = it
        }
      }
  }

  @Provides
  @SingleIn(PlaybackScope::class)
  fun scope(): CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

  @Provides
  @SingleIn(PlaybackScope::class)
  fun session(
    service: PlaybackService,
    player: LockscreenPlayer,
    callback: LibrarySessionCallback,
    mainActivityIntentProvider: MainActivityIntentProvider,
    context: Context,
  ): MediaLibraryService.MediaLibrarySession {
    return MediaLibraryService.MediaLibrarySession.Builder(service, player, callback)
      .setSessionActivity(mainActivityIntentProvider.toCurrentBook())
      // Timed skips stay in the legacy custom layout so System UI retains the native
      // previous/next actions used for chapter navigation.
      .setCustomLayout(
        listOf(
          CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
            .setDisplayName(context.getString(StringsR.string.rewind))
            .setSessionCommand(CustomCommand.SeekBack.toSessionCommand())
            .build(),
          CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
            .setDisplayName(context.getString(StringsR.string.fast_forward))
            .setSessionCommand(CustomCommand.SeekForward.toSessionCommand())
            .build(),
        ),
      )
      .build()
      .also(player::attachTo)
  }
}
