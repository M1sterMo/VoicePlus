package voice.core.scanner.mp4.visitor

import androidx.media3.common.util.ParsableByteArray
import voice.core.scanner.mp4.Mp4ChpaterExtractorOutput

internal interface AtomVisitor {
  val path: List<String>

  fun visit(
    buffer: ParsableByteArray,
    parseOutput: Mp4ChpaterExtractorOutput,
  )

  /**
   * False skips the payload without reading it. Audio sample-size tables are millions of
   * entries and are not needed to locate chapter titles.
   */
  fun consumes(
    parseOutput: Mp4ChpaterExtractorOutput,
    payloadSize: Long,
  ): Boolean = payloadSize in 0..Int.MAX_VALUE.toLong()
}
