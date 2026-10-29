package voice.core.scanner.mp4.visitor

import androidx.media3.common.util.ParsableByteArray
import dev.zacsweers.metro.Inject
import voice.core.logging.api.Logger
import voice.core.scanner.mp4.Mp4ChpaterExtractorOutput

// https://developer.apple.com/documentation/quicktime-file-format/chunk_offset_atom_64
@Inject
internal class Co64Visitor : AtomVisitor {

  override val path: List<String> = listOf("moov", "trak", "mdia", "minf", "stbl", "co64")

  override fun visit(
    buffer: ParsableByteArray,
    parseOutput: Mp4ChpaterExtractorOutput,
  ) {
    val track = parseOutput.currentTrack() ?: return
    val version = buffer.readUnsignedByte()
    if (version != 0) {
      Logger.w("Unexpected version $version in co64 atom, expected 0")
      return
    }
    buffer.skipBytes(3)
    val numberOfEntries = buffer.readUnsignedIntToInt()
    track.chunkOffsets = List(numberOfEntries) { buffer.readUnsignedLongToLong() }
  }
}
