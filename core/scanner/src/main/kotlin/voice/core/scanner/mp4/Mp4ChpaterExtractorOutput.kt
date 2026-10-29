package voice.core.scanner.mp4

import voice.core.data.MarkData

internal class Mp4ChpaterExtractorOutput {
  val tracks: MutableMap<Int, TrackTables> = mutableMapOf()
  var currentTrackId: Int? = null
  var chplChapters: List<MarkData> = emptyList()
  var chapterTrackId: Int? = null

  fun currentTrack(): TrackTables? {
    val id = currentTrackId ?: return null
    return tracks.getOrPut(id) { TrackTables(id) }
  }
}

internal class TrackTables(
  val id: Int,
) {
  var handler: String? = null
  var timeScale: Long? = null
  var durations: List<SttsEntry> = emptyList()
  var stscEntries: List<StscEntry> = emptyList()
  var chunkOffsets: List<Long> = emptyList()
  var sampleSizes: List<Int> = emptyList()
  var constantSampleSize: Int = 0

  fun sampleSize(sampleIndex: Int): Int? {
    if (constantSampleSize > 0) return constantSampleSize
    return sampleSizes.getOrNull(sampleIndex)
  }
}

internal data class StscEntry(
  val firstChunk: Long,
  val samplesPerChunk: Int,
)

internal data class SttsEntry(
  val sampleCount: Long,
  val sampleDuration: Long,
)
