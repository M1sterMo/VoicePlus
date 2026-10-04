package voice.core.playback.session

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import voice.core.data.LockscreenSecondaryTextMode
import voice.core.data.LockscreenSliderMode
import voice.core.data.durationMs
import voice.core.data.store.LockscreenSecondaryTextModeStore
import voice.core.data.store.LockscreenSliderModeStore
import voice.core.playback.ChapterMarkChangeNotifier
import voice.core.playback.history.PlaybackIntentHolder
import voice.core.playback.player.VoicePlayer
import voice.core.strings.R as StringsR

@Inject
class LockscreenPlayer(
  private val voicePlayer: VoicePlayer,
  @LockscreenSliderModeStore modeStore: DataStore<LockscreenSliderMode>,
  @LockscreenSecondaryTextModeStore secondaryTextModeStore: DataStore<LockscreenSecondaryTextMode>,
  chapterMarkChangeNotifier: ChapterMarkChangeNotifier,
  private val intentHolder: PlaybackIntentHolder,
  private val context: Context,
  scope: CoroutineScope,
) : ForwardingSimpleBasePlayer(voicePlayer) {

  private var mediaSession: MediaSession? = null
  private var mode = LockscreenSliderMode.CHAPTER
  private var secondaryTextMode = LockscreenSecondaryTextMode.CHAPTER

  init {
    scope.launch {
      modeStore.data.collect {
        mode = it
        invalidateState()
      }
    }
    scope.launch {
      secondaryTextModeStore.data.collect {
        secondaryTextMode = it
        invalidateState()
      }
    }
    scope.launch {
      chapterMarkChangeNotifier.flow.collect {
        invalidateState()
      }
    }
  }

  internal fun attachTo(mediaSession: MediaSession) {
    this.mediaSession = mediaSession
  }

  override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    if (!playWhenReady) return super.handleSetPlayWhenReady(false)

    val controller = mediaSession?.getControllerForCurrentRequest()
    // Media3's notification controller also uses our package, including relayed System UI requests.
    if (controller == null ||
      (controller.packageName == context.packageName && mediaSession?.isMediaNotificationController(controller) != true)
    ) {
      intentHolder.clearSleepResumeConfirmation()
      return super.handleSetPlayWhenReady(true)
    }
    if (intentHolder.confirmExternalResume()) {
      return super.handleSetPlayWhenReady(true)
    }

    Toast.makeText(context, StringsR.string.sleep_resume_confirmation, Toast.LENGTH_SHORT).show()
    return Futures.immediateVoidFuture()
  }

  override fun getState(): State {
    val state = super.getState()
    val sliderState = when (mode) {
      LockscreenSliderMode.AUDIOBOOK -> state.forWholeAudiobook()
      LockscreenSliderMode.CHAPTER -> state.forCurrentChapter()
      LockscreenSliderMode.DISABLED -> state.withSeekingDisabled()
    }
    return sliderState.withSecondaryText()
  }

  override fun handleSeek(
    mediaItemIndex: Int,
    positionMs: Long,
    seekCommand: Int,
  ): ListenableFuture<*> {
    if (seekCommand == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) {
      when (mode) {
        LockscreenSliderMode.AUDIOBOOK -> {
          val mapping = super.getState().audiobookMapping()
          val target = mapping?.let { audiobookSeekTarget(positionMs, it.durations) }
          if (target != null) {
            voicePlayer.seekTo(target.first, target.second)
            return Futures.immediateVoidFuture()
          }
        }
        LockscreenSliderMode.CHAPTER -> {
          val mark = voicePlayer.currentChapterMark()
            ?: return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
          voicePlayer.seekTo(
            mediaItemIndex,
            mark.startMs + positionMs.coerceIn(0L, mark.durationMs),
          )
          return Futures.immediateVoidFuture()
        }
        LockscreenSliderMode.DISABLED -> Unit
      }
    }
    return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
  }

  private fun State.forWholeAudiobook(): State {
    val mapping = audiobookMapping() ?: return this
    val aggregateDurationUs = mapping.aggregateDurationMs * 1_000L
    val aggregateItem = mapping.currentItem
      .withDisplayToSourcePositionOffset(-mapping.previousDurationMs)
      .buildUpon()
      .setDurationUs(aggregateDurationUs)
      .setPositionInFirstPeriodUs(0L)
      .setPeriods(
        listOf(
          mapping.currentItem.periods.single()
            .buildUpon()
            .setDurationUs(aggregateDurationUs)
            .build(),
        ),
      )
      .build()
    val aggregatePlaylist = mapping.playlist.toMutableList().apply {
      this[mapping.currentIndex] = aggregateItem
    }

    fun aggregatePosition(positionMs: Long): Long {
      if (positionMs == C.TIME_UNSET) return C.TIME_UNSET
      return (mapping.previousDurationMs + positionMs.coerceIn(0L, mapping.currentDurationMs))
        .coerceIn(0L, mapping.aggregateDurationMs)
    }

    val builder = buildUpon()
      .setPlaylist(aggregatePlaylist)
      .setContentPositionMs(PositionSupplier { aggregatePosition(voicePlayer.currentPosition) })
      .setContentBufferedPositionMs(
        PositionSupplier { aggregatePosition(voicePlayer.contentBufferedPosition) },
      )
      .setTotalBufferedDurationMs(
        PositionSupplier {
          val position = aggregatePosition(voicePlayer.currentPosition)
          val bufferedPosition = aggregatePosition(voicePlayer.contentBufferedPosition)
          if (position == C.TIME_UNSET || bufferedPosition == C.TIME_UNSET) {
            0L
          } else {
            (bufferedPosition - position).coerceAtLeast(0L)
          }
        },
      )
    if (hasPositionDiscontinuity) {
      builder.setPositionDiscontinuity(
        positionDiscontinuityReason,
        aggregatePosition(discontinuityPositionMs),
      )
    }
    return builder.build()
  }

  private fun State.audiobookMapping(): AudiobookMapping? {
    val durations = voicePlayer.currentBookChapterDurations()
    val playlist = getPlaylist()
    if (durations.isEmpty() || durations.size != playlist.size || durations.any { it <= 0L }) {
      return null
    }
    val currentIndex = currentMediaItemIndex.takeIf { it in durations.indices } ?: return null
    val currentItem = playlist.getOrNull(currentIndex) ?: return null
    if (currentItem.periods.size != 1) return null

    var aggregateDurationMs = 0L
    for (duration in durations) {
      if (duration > Long.MAX_VALUE - aggregateDurationMs) return null
      aggregateDurationMs += duration
    }
    if (aggregateDurationMs <= 0L || aggregateDurationMs > Long.MAX_VALUE / 1_000L) {
      return null
    }

    var previousDurationMs = 0L
    repeat(currentIndex) { previousDurationMs += durations[it] }
    return AudiobookMapping(
      durations = durations,
      playlist = playlist,
      currentIndex = currentIndex,
      currentItem = currentItem,
      aggregateDurationMs = aggregateDurationMs,
      previousDurationMs = previousDurationMs,
      currentDurationMs = durations[currentIndex],
    )
  }

  private fun State.forCurrentChapter(): State {
    val markInfo = voicePlayer.currentChapterMarkInfo() ?: return this
    val mark = markInfo.mark
    val playlist = getPlaylist()
    val currentItem = playlist.getOrNull(currentMediaItemIndex) ?: return this
    if (currentItem.periods.size != 1) return this

    val durationMs = mark.durationMs
    val durationUs = durationMs * 1_000
    val period = currentItem.periods.single().buildUpon()
      .setDurationUs(durationUs)
      .build()
    val chapterItem = currentItem
      .withDisplayToSourcePositionOffset(
        offsetMs = mark.startMs,
        trackNumber = markInfo.number,
        totalTrackCount = markInfo.total,
      )
      .buildUpon()
      .setDurationUs(durationUs)
      .setPositionInFirstPeriodUs(0L)
      .setPeriods(listOf(period))
      .build()
    val chapterPlaylist = playlist.toMutableList().apply {
      this[currentMediaItemIndex] = chapterItem
    }

    fun chapterPosition(positionMs: Long): Long {
      if (positionMs == C.TIME_UNSET) return C.TIME_UNSET
      return (positionMs - mark.startMs).coerceIn(0L, durationMs)
    }

    val builder = buildUpon()
      .setPlaylist(chapterPlaylist)
      .setContentPositionMs(PositionSupplier { chapterPosition(voicePlayer.currentPosition) })
      .setContentBufferedPositionMs(PositionSupplier { chapterPosition(voicePlayer.contentBufferedPosition) })
      .setTotalBufferedDurationMs(
        PositionSupplier {
          val position = chapterPosition(voicePlayer.currentPosition)
          val bufferedPosition = chapterPosition(voicePlayer.contentBufferedPosition)
          if (position == C.TIME_UNSET || bufferedPosition == C.TIME_UNSET) {
            0L
          } else {
            (bufferedPosition - position).coerceAtLeast(0L)
          }
        },
      )
    if (hasPositionDiscontinuity) {
      builder.setPositionDiscontinuity(
        positionDiscontinuityReason,
        chapterPosition(discontinuityPositionMs),
      )
    }
    return builder.build()
  }

  private fun State.withSeekingDisabled(): State {
    val playlist = getPlaylist()
    val currentItem = playlist.getOrNull(currentMediaItemIndex) ?: return this
    val disabledItem = currentItem
      .withDisplayToSourcePositionOffset(0L)
      .buildUpon()
      .setIsSeekable(false)
      .build()
    val disabledPlaylist = playlist.toMutableList().apply {
      this[currentMediaItemIndex] = disabledItem
    }
    return buildUpon()
      .setPlaylist(disabledPlaylist)
      .setAvailableCommands(
        availableCommands.buildUpon()
          .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
          .build(),
      )
      .build()
  }

  private fun State.withSecondaryText(): State {
    if (secondaryTextMode == LockscreenSecondaryTextMode.AUTHOR) return this
    val chapterName = voicePlayer.currentChapterMarkInfo()?.name?.takeIf { it.isNotBlank() } ?: return this
    val playlist = getPlaylist()
    val currentItem = playlist.getOrNull(currentMediaItemIndex) ?: return this
    val currentMetadata = currentItem.mediaMetadata ?: currentItem.mediaItem.mediaMetadata
    val metadata = currentMetadata.buildUpon()
      .setArtist(chapterName)
      .build()
    val chapterNameItem = currentItem.buildUpon()
      .setMediaItem(currentItem.mediaItem.buildUpon().setMediaMetadata(metadata).build())
      .setMediaMetadata(metadata)
      .build()
    val chapterNamePlaylist = playlist.toMutableList().apply {
      this[currentMediaItemIndex] = chapterNameItem
    }
    return buildUpon()
      .setPlaylist(chapterNamePlaylist)
      .build()
  }

  private fun MediaItemData.withDisplayToSourcePositionOffset(
    offsetMs: Long,
    trackNumber: Int? = null,
    totalTrackCount: Int? = null,
  ): MediaItemData {
    val currentMetadata = mediaMetadata ?: mediaItem.mediaMetadata
    val metadataBuilder = currentMetadata.buildUpon()
      .setExtras(
        Bundle(currentMetadata.extras ?: Bundle()).apply {
          putLong(DISPLAY_TO_SOURCE_POSITION_OFFSET_MS, offsetMs)
        },
      )
    if (trackNumber != null && totalTrackCount != null) {
      metadataBuilder
        .setTrackNumber(trackNumber)
        .setTotalTrackCount(totalTrackCount)
    }
    val metadata = metadataBuilder.build()
    return buildUpon()
      .setMediaItem(mediaItem.buildUpon().setMediaMetadata(metadata).build())
      .setMediaMetadata(metadata)
      .build()
  }

  private data class AudiobookMapping(
    val durations: List<Long>,
    val playlist: List<MediaItemData>,
    val currentIndex: Int,
    val currentItem: MediaItemData,
    val aggregateDurationMs: Long,
    val previousDurationMs: Long,
    val currentDurationMs: Long,
  )
}

private fun audiobookSeekTarget(
  positionMs: Long,
  durations: List<Long>,
): Pair<Int, Long>? {
  if (positionMs == C.TIME_UNSET || durations.isEmpty() || durations.any { it <= 0L }) return null

  var totalDurationMs = 0L
  for (duration in durations) {
    if (duration > Long.MAX_VALUE - totalDurationMs) return null
    totalDurationMs += duration
  }
  if (totalDurationMs <= 0L) return null

  val targetPositionMs = positionMs.coerceIn(0L, totalDurationMs)
  var itemStartMs = 0L
  durations.forEachIndexed { index, durationMs ->
    val itemEndMs = itemStartMs + durationMs
    if (targetPositionMs < itemEndMs || index == durations.lastIndex) {
      return index to (targetPositionMs - itemStartMs).coerceIn(0L, durationMs)
    }
    itemStartMs = itemEndMs
  }
  return null
}

internal const val DISPLAY_TO_SOURCE_POSITION_OFFSET_MS =
  "voice.lockscreenDisplayToSourcePositionOffsetMs"
