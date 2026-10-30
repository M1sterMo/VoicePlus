package voice.core.scanner.mp4.visitor

import androidx.media3.common.util.ParsableByteArray
import voice.core.logging.api.Logger
import voice.core.scanner.mp4.Mp4ChpaterExtractorOutput

internal interface AtomVisitor {
  val path: List<String>

  fun visit(
    buffer: ParsableByteArray,
    parseOutput: Mp4ChpaterExtractorOutput,
  )

  /**
   * Whether the payload is worth reading into memory. False skips it: audio sample-size tables
   * are millions of entries and are not needed to locate chapter titles.
   */
  fun consumes(
    parseOutput: Mp4ChpaterExtractorOutput,
    payloadSize: Long,
  ): Boolean = true
}

/** True when [count] table entries of [entrySize] bytes fit in the remaining payload of [atom]. */
internal fun ParsableByteArray.fitsEntries(
  atom: String,
  count: Int,
  entrySize: Int,
): Boolean {
  if (bytesLeft() >= count.toLong() * entrySize) return true
  Logger.w("$atom declares $count entries but only ${bytesLeft()} bytes remain")
  return false
}
