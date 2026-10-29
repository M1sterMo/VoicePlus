package voice.core.scanner.mp4.visitor

import androidx.media3.common.util.ParsableByteArray
import dev.zacsweers.metro.Inject
import voice.core.logging.api.Logger
import voice.core.scanner.mp4.Mp4ChpaterExtractorOutput

// https://developer.apple.com/documentation/quicktime-file-format/sample_size_atom
@Inject
internal class StszVisitor : AtomVisitor {

  override val path: List<String> = listOf("moov", "trak", "mdia", "minf", "stbl", "stsz")

  override fun consumes(
    parseOutput: Mp4ChpaterExtractorOutput,
    payloadSize: Long,
  ): Boolean {
    if (payloadSize !in 0..MAX_SAMPLE_SIZE_TABLE_BYTES) return false
    val handler = parseOutput.currentTrack()?.handler
    return handler != "soun" && handler != "vide"
  }

  override fun visit(
    buffer: ParsableByteArray,
    parseOutput: Mp4ChpaterExtractorOutput,
  ) {
    val track = parseOutput.currentTrack() ?: return
    val version = buffer.readUnsignedByte()
    if (version != 0) {
      Logger.w("Unexpected version $version in stsz atom, expected 0")
      return
    }
    buffer.skipBytes(3)
    val constantSampleSize = buffer.readUnsignedIntToInt()
    val sampleCount = buffer.readUnsignedIntToInt()
    if (constantSampleSize != 0) {
      track.constantSampleSize = constantSampleSize
      return
    }
    if (sampleCount > MAX_SAMPLES) {
      Logger.w("Refusing to store $sampleCount sample sizes for track ${track.id}")
      return
    }
    track.sampleSizes = List(sampleCount) { buffer.readUnsignedIntToInt() }
  }

  private companion object {
    const val MAX_SAMPLES = 100_000
    const val MAX_SAMPLE_SIZE_TABLE_BYTES = (MAX_SAMPLES * 4 + 32).toLong()
  }
}
