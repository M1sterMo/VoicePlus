package voice.core.scanner.mp4

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.data.MarkData

@RunWith(AndroidJUnit4::class)
internal class ChapterTrackProcessorTest {

  @Test
  fun `contiguous track ids still resolve one sample per chunk`() {
    val chapters = invoke(
      trackId = 2,
      names = listOf("one", "two", "three"),
      samplesPerChunk = listOf(1, 1, 1),
      durations = listOf(SttsEntry(sampleCount = 3, sampleDuration = 100)),
      alsoStoreTracks = listOf(1),
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 100, name = "two"),
      MarkData(startMs = 200, name = "three"),
    )
  }

  @Test
  fun `track id 3 resolves when only two traks were stored`() {
    val chapters = invoke(
      trackId = 3,
      names = listOf("Opening Credits", "1349"),
      samplesPerChunk = listOf(1, 1),
      durations = listOf(
        SttsEntry(sampleCount = 1, sampleDuration = 26_540),
        SttsEntry(sampleCount = 1, sampleDuration = 1_610_489),
      ),
      alsoStoreTracks = listOf(1),
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "Opening Credits"),
      MarkData(startMs = 26_540, name = "1349"),
    )
  }

  @Test
  fun `one chunk of sized samples yields one chapter per sample`() {
    val chapters = invoke(
      trackId = 3,
      names = listOf("Opening Credits", "1349", "The Devil's Bargain"),
      samplesPerChunk = listOf(3),
      durations = listOf(
        SttsEntry(sampleCount = 1, sampleDuration = 26_540),
        SttsEntry(sampleCount = 1, sampleDuration = 1_610_489),
        SttsEntry(sampleCount = 1, sampleDuration = 1_712_053),
      ),
      trailingBytes = 12,
      alsoStoreTracks = listOf(1),
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "Opening Credits"),
      MarkData(startMs = 26_540, name = "1349"),
      MarkData(startMs = 1_637_029, name = "The Devil's Bargain"),
    )
  }

  @Test
  fun `a 64-bit chunk offset still locates the title`() {
    val name = "Past 4GiB"
    val text = name.toByteArray(Charsets.UTF_8)
    val sample = ByteArray(2 + text.size)
    sample[0] = (text.size ushr 8).toByte()
    sample[1] = text.size.toByte()
    text.copyInto(sample, destinationOffset = 2)
    val offset = 0x1_0000_0001L
    val output = Mp4ChpaterExtractorOutput().apply {
      tracks[2] = TrackTables(2).apply {
        chunkOffsets = listOf(offset)
        durations = listOf(SttsEntry(sampleCount = 1, sampleDuration = 100))
        timeScale = 1000
        stscEntries = listOf(StscEntry(firstChunk = 1, samplesPerChunk = 1))
      }
    }

    val chapters = ChapterTrackProcessor()(
      uri = Uri.EMPTY,
      dataSource = WindowDataSource(offset, sample),
      trackId = 2,
      output = output,
    )

    chapters shouldBe listOf(MarkData(startMs = 0, name = name))
  }

  @Test
  fun `zero-count stts entry is skipped`() {
    val chapters = invoke(
      trackId = 1,
      names = listOf("one", "two"),
      samplesPerChunk = listOf(1, 1),
      durations = listOf(
        SttsEntry(sampleCount = 0, sampleDuration = 100),
        SttsEntry(sampleCount = 2, sampleDuration = 100),
      ),
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 100, name = "two"),
    )
  }

  @Test
  fun `insufficient duration data still returns chapters`() {
    val chapters = invoke(
      trackId = 1,
      names = listOf("one", "two", "three"),
      samplesPerChunk = listOf(1, 1, 1),
      durations = listOf(SttsEntry(sampleCount = 2, sampleDuration = 100)),
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 100, name = "two"),
      MarkData(startMs = 200, name = "three"),
    )
  }

  private fun invoke(
    trackId: Int,
    names: List<String>,
    samplesPerChunk: List<Int>,
    durations: List<SttsEntry>,
    timeScale: Long = 1000L,
    trailingBytes: Int = 0,
    alsoStoreTracks: List<Int> = emptyList(),
  ): List<MarkData> {
    val samples = names.map { name ->
      val text = name.toByteArray(Charsets.UTF_8)
      val sample = ByteArray(2 + text.size + trailingBytes)
      sample[0] = (text.size ushr 8).toByte()
      sample[1] = text.size.toByte()
      text.copyInto(sample, destinationOffset = 2)
      sample
    }
    val data = ByteArray(samples.sumOf { it.size })
    val chunkOffsets = mutableListOf<Long>()
    var cursor = 0
    var sampleIndex = 0
    samplesPerChunk.forEach { count ->
      chunkOffsets += cursor.toLong()
      repeat(count) {
        val sample = samples[sampleIndex++]
        sample.copyInto(data, destinationOffset = cursor)
        cursor += sample.size
      }
    }

    val track = TrackTables(trackId).apply {
      this.chunkOffsets = chunkOffsets
      this.durations = durations
      this.timeScale = timeScale
      this.stscEntries = samplesPerChunk.mapIndexed { index, count ->
        StscEntry(firstChunk = index + 1L, samplesPerChunk = count)
      }
      if (trailingBytes > 0 || samplesPerChunk.any { it > 1 }) {
        sampleSizes = samples.map { it.size }
      }
    }
    val output = Mp4ChpaterExtractorOutput().apply {
      alsoStoreTracks.forEach { id -> tracks[id] = TrackTables(id) }
      tracks[trackId] = track
    }

    return ChapterTrackProcessor()(
      uri = Uri.EMPTY,
      dataSource = ByteArrayDataSource(data),
      trackId = trackId,
      output = output,
    )
  }
}

private class WindowDataSource(
  private val base: Long,
  private val bytes: ByteArray,
) : DataSource {
  private var position = 0

  override fun addTransferListener(transferListener: TransferListener) = Unit

  override fun open(dataSpec: DataSpec): Long {
    position = (dataSpec.position - base).toInt()
    return (bytes.size - position).toLong()
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (position >= bytes.size) return C.RESULT_END_OF_INPUT
    val count = minOf(length, bytes.size - position)
    bytes.copyInto(buffer, destinationOffset = offset, startIndex = position, endIndex = position + count)
    position += count
    return count
  }

  override fun getUri(): Uri = Uri.EMPTY

  override fun close() = Unit
}
