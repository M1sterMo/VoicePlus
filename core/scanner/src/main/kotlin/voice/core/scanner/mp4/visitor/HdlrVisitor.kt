package voice.core.scanner.mp4.visitor

import androidx.media3.common.util.ParsableByteArray
import dev.zacsweers.metro.Inject
import voice.core.scanner.mp4.Mp4ChpaterExtractorOutput

// https://developer.apple.com/documentation/quicktime-file-format/handler_reference_atom
@Inject
internal class HdlrVisitor : AtomVisitor {

  override val path: List<String> = listOf("moov", "trak", "mdia", "hdlr")

  override fun visit(
    buffer: ParsableByteArray,
    parseOutput: Mp4ChpaterExtractorOutput,
  ) {
    buffer.skipBytes(8) // version, flags, and pre_defined
    parseOutput.currentTrack()?.handler = buffer.readString(4)
  }
}
