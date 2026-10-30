package voice.core.scanner.mp4

import android.net.Uri
import androidx.media3.common.C
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
    if (track.durations.isEmpty()) {
      Logger.w("No durations found for track ID $trackId")
      return emptyList()
    }
    val samplesPerChunk = chunkOffsets.indices.map { getSamplesPerChunk(it, track.stscEntries) }
    if (samplesPerChunk.sumOf { it.toLong() } > MAX_SAMPLES_PER_TRACK) {
      Logger.w("Refusing to expand more than $MAX_SAMPLES_PER_TRACK samples in chapter track $trackId")
      return emptyList()
    }
    val hasSampleSizes = track.constantSampleSize > 0 || track.sampleSizes.isNotEmpty()
    if (!hasSampleSizes && samplesPerChunk.any { it > 1 }) {
      Logger.w("Chapter track $trackId packs several samples in one chunk without sample sizes")
      return emptyList()
    }

    val durations = SampleDurations(track.durations)
    var position = 0L
    var sampleIndex = 0
    val chapters = mutableListOf<MarkData>()
    chunkOffsets.forEachIndexed { chunkIndex, chunkOffset ->
      var sampleOffset = chunkOffset
      repeat(samplesPerChunk[chunkIndex]) {
        chapters += MarkData(
          startMs = ticksToMilliseconds(position, timeScale),
          name = readSampleName(dataSource, uri, sampleOffset),
        )
        val duration = durations.next()
        if (duration == null) {
          Logger.w("Not enough sample durations for chapter sample ${sampleIndex + 1}")
        }
        position = Math.addExact(position, duration ?: 0L)
        sampleOffset += track.sampleSize(sampleIndex) ?: 0
        sampleIndex++
      }
    }
    return chapters.sorted()
  }

  /** A chapter title sample is a big-endian 16-bit length followed by that many UTF-8 bytes. */
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
    val lengthBytes = dataSource.readOrNull(2) ?: return truncatedTitle(offset)
    val textLength = ((lengthBytes[0].toInt() and 0xFF) shl 8) or (lengthBytes[1].toInt() and 0xFF)
    if (textLength > MAX_TITLE_BYTES) {
      Logger.w("Chapter title length $textLength at offset $offset is implausible")
      return ""
    }
    val text = dataSource.readOrNull(textLength) ?: return truncatedTitle(offset)
    return text.toString(Charsets.UTF_8)
  }

  private fun truncatedTitle(offset: Long): String {
    Logger.w("Chapter title at offset $offset is truncated")
    return ""
  }

  /** Reads exactly [length] bytes, or null when the input ends first. */
  private fun DataSource.readOrNull(length: Int): ByteArray? {
    val target = ByteArray(length)
    var filled = 0
    while (filled < length) {
      val read = read(target, filled, length - filled)
      if (read == C.RESULT_END_OF_INPUT || read == 0) return null
      filled += read
    }
    return target
  }

  /** Walks the time-to-sample table one sample at a time. */
  private class SampleDurations(private val entries: List<SttsEntry>) {
    private var entryIndex = 0
    private var consumedInEntry = 0L

    /** The next sample's duration in ticks, or null once the table is exhausted. */
    fun next(): Long? {
      while (entryIndex < entries.size && consumedInEntry >= entries[entryIndex].sampleCount) {
        entryIndex++
        consumedInEntry = 0L
      }
      val entry = entries.getOrNull(entryIndex) ?: return null
      consumedInEntry++
      return entry.sampleDuration
    }
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

  private companion object {
    const val MAX_TITLE_BYTES = 4_096
    const val MAX_SAMPLES_PER_TRACK = 100_000
  }
}
