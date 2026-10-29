package voice.core.scanner.mp4.visitor

import androidx.media3.common.util.ParsableByteArray
import dev.zacsweers.metro.Inject
import voice.core.logging.api.Logger
import voice.core.scanner.mp4.Mp4ChpaterExtractorOutput
import voice.core.scanner.mp4.TrackTables

// https://developer.apple.com/documentation/quicktime-file-format/track_header_atom
@Inject
internal class TkhdVisitor : AtomVisitor {

  override val path: List<String> = listOf("moov", "trak", "tkhd")

  override fun visit(
    buffer: ParsableByteArray,
    parseOutput: Mp4ChpaterExtractorOutput,
  ) {
    val version = buffer.readUnsignedByte()
    if (version != 0 && version != 1) {
      Logger.w("Unexpected version $version in tkhd atom, expected 0 or 1")
      return
    }
    val creationAndModificationBytes = if (version == 0) 8 else 16
    buffer.skipBytes(3 + creationAndModificationBytes)
    val trackId = buffer.readUnsignedIntToInt()
    parseOutput.currentTrackId = trackId
    if (parseOutput.tracks[trackId] == null) {
      parseOutput.tracks[trackId] = TrackTables(trackId)
    }
  }
}
