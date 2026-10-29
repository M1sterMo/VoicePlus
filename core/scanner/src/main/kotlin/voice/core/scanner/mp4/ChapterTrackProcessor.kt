package voice.core.scanner.mp4

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import dev.zacsweers.metro.Inject
import voice.core.data.MarkData
import voice.core.logging.api.Logger

@Inject
internal class ChapterTrackProcessor {

  operator fun invoke(
    uri: Uri,
    dataSource: DataSource,
    trackId: Int,
    output: Mp4ChpaterExtractorOutput,
  ): List<MarkData> {
    val track = output.tracks[trackId]
    if (track == null) {
      Logger.w("No chapter track tables found for track ID $trackId")
      return emptyList()
    }
    val chunkOffsets = track.chunkOffsets
    if (chunkOffsets.isEmpty()) {
      Logger.w("No chunk offsets found for track ID $trackId")
      return emptyList()
    }
    val timeScale = track.timeScale
    if (timeScale == null) {
      Logger.w("No time scale found for track ID $trackId")
      return emptyList()
    }
    val durations = track.durations
    val stscEntries = track.stscEntries
    val hasSampleSizes = track.constantSampleSize > 0 || track.sampleSizes.isNotEmpty()

    var position = 0L
    var durationEntryIndex = 0
    var samplesConsumedInDurationEntry = 0L
    var sampleIndex = 0
    val chapters = mutableListOf<MarkData>()

    chunkOffsets.forEachIndexed { chunkIndex, chunkOffset ->
      val samplesInThisChunk = getSamplesPerChunk(chunkIndex, stscEntries)
      if (samplesInThisChunk > MAX_SAMPLES_PER_TRACK) {
        Logger.w("Refusing to expand $samplesInThisChunk samples in chapter track $trackId")
        return emptyList()
      }
      if (samplesInThisChunk > 1 && !hasSampleSizes) {
        Logger.w("Chapter track $trackId packs $samplesInThisChunk samples in one chunk without sample sizes")
        return emptyList()
      }
      var bytesIntoChunk = 0L
      repeat(samplesInThisChunk) {
        val sampleOffset = chunkOffset + bytesIntoChunk
        val sampleSize = track.sampleSize(sampleIndex) ?: 0
        bytesIntoChunk += sampleSize.toLong()
        sampleIndex++

        val consumedDuration = consumeDuration(
          sampleCount = 1,
          durations = durations,
          durationEntryIndex = durationEntryIndex,
          samplesConsumedInDurationEntry = samplesConsumedInDurationEntry,
        )
        durationEntryIndex = consumedDuration.durationEntryIndex
        samplesConsumedInDurationEntry = consumedDuration.samplesConsumedInDurationEntry
        if (!consumedDuration.hasEnoughDurations) {
          Logger.w("Not enough sample durations for chapter sample $sampleIndex")
        }

        chapters += MarkData(
          startMs = ticksToMilliseconds(position, timeScale),
          name = readSampleName(dataSource, uri, sampleOffset),
        )
        position = Math.addExact(position, consumedDuration.duration)
      }
    }
    return chapters.sorted()
  }

  private fun readSampleName(
    dataSource: DataSource,
    uri: Uri,
    offset: Long,
  ): String {
    dataSource.close()
    dataSource.open(
      DataSpec.Builder()
        .setUri(uri)
        .setPosition(offset)
        .build(),
    )
    val lengthBytes = ByteArray(2)
    if (!dataSource.readFully(lengthBytes)) return ""
    val textLength = ((lengthBytes[0].toInt() and 0xFF) shl 8) or (lengthBytes[1].toInt() and 0xFF)
    if (textLength == 0) return ""
    if (textLength > MAX_TITLE_BYTES) {
      Logger.w("Chapter title length $textLength is implausible")
      return ""
    }
    val text = ByteArray(textLength)
    if (!dataSource.readFully(text)) return ""
    return text.toString(Charsets.UTF_8)
  }

  private fun DataSource.readFully(target: ByteArray): Boolean {
    var offset = 0
    while (offset < target.size) {
      val read = read(target, offset, target.size - offset)
      if (read == C.RESULT_END_OF_INPUT || read <= 0) return false
      offset += read
    }
    return true
  }

  private fun consumeDuration(
    sampleCount: Int,
    durations: List<SttsEntry>,
    durationEntryIndex: Int,
    samplesConsumedInDurationEntry: Long,
  ): ConsumedDuration {
    var remainingSamples = sampleCount.toLong()
    var currentDurationEntryIndex = durationEntryIndex
    var currentSamplesConsumedInDurationEntry = samplesConsumedInDurationEntry
    var duration = 0L

    while (remainingSamples > 0 && currentDurationEntryIndex < durations.size) {
      val durationEntry = durations[currentDurationEntryIndex]
      val samplesLeftInEntry =
        durationEntry.sampleCount - currentSamplesConsumedInDurationEntry
      if (samplesLeftInEntry <= 0) {
        currentDurationEntryIndex++
        currentSamplesConsumedInDurationEntry = 0L
        continue
      }

      val samplesToConsume = minOf(remainingSamples, samplesLeftInEntry)
      duration += samplesToConsume * durationEntry.sampleDuration
      remainingSamples -= samplesToConsume
      currentSamplesConsumedInDurationEntry += samplesToConsume

      if (currentSamplesConsumedInDurationEntry == durationEntry.sampleCount) {
        currentDurationEntryIndex++
        currentSamplesConsumedInDurationEntry = 0L
      }
    }

    return ConsumedDuration(
      duration = duration,
      durationEntryIndex = currentDurationEntryIndex,
      samplesConsumedInDurationEntry = currentSamplesConsumedInDurationEntry,
      hasEnoughDurations = remainingSamples == 0L,
    )
  }

  private fun ticksToMilliseconds(
    ticks: Long,
    timeScale: Long,
  ): Long {
    val quotient = ticks / timeScale
    val remainder = ticks % timeScale
    return Math.addExact(
      Math.multiplyExact(quotient, 1000L),
      Math.multiplyExact(remainder, 1000L) / timeScale,
    )
  }

  private fun getSamplesPerChunk(
    chunkIndex: Int,
    stscEntries: List<StscEntry>,
  ): Int {
    stscEntries.forEachIndexed { index, entry ->
      val nextEntry = stscEntries.getOrNull(index + 1)
      if (chunkIndex + 1 >= entry.firstChunk) {
        if (nextEntry == null || chunkIndex + 1 < nextEntry.firstChunk) {
          return entry.samplesPerChunk
        }
      }
    }
    return 1
  }

  private data class ConsumedDuration(
    val duration: Long,
    val durationEntryIndex: Int,
    val samplesConsumedInDurationEntry: Long,
    val hasEnoughDurations: Boolean,
  )

  private companion object {
    const val MAX_TITLE_BYTES = 4_096
    const val MAX_SAMPLES_PER_TRACK = 100_000
  }
}
