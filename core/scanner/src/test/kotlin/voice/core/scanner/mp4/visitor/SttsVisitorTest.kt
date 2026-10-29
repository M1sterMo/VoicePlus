package voice.core.scanner.mp4.visitor

import androidx.media3.common.util.ParsableByteArray
import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.scanner.mp4.Mp4ChpaterExtractorOutput
import voice.core.scanner.mp4.SttsEntry

internal class SttsVisitorTest {

  @Test
  fun `keeps stts entries compact`() {
    val buffer = ParsableByteArray(
      byteArrayOf(
        0, 0, 0, 0, // version and flags
        0, 0, 0, 1, // entry count
        0xB2.toByte(), 0xD0.toByte(), 0x5E, 0, // sample count
        0, 0, 0x04, 0, // sample duration
      ),
    )
    val output = Mp4ChpaterExtractorOutput().apply { currentTrackId = 1 }

    SttsVisitor().visit(buffer, output)

    output.tracks.getValue(1).durations shouldBe listOf(
      SttsEntry(sampleCount = 3_000_000_000L, sampleDuration = 1024L),
    )
  }
}
