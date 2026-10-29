package voice.core.scanner.mp4

import androidx.media3.common.C
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.container.Mp4Box
import androidx.media3.extractor.ExtractorInput
import dev.zacsweers.metro.Inject
import voice.core.logging.api.Logger
import voice.core.scanner.mp4.visitor.ChapVisitor
import voice.core.scanner.mp4.visitor.ChplVisitor
import voice.core.scanner.mp4.visitor.Co64Visitor
import voice.core.scanner.mp4.visitor.HdlrVisitor
import voice.core.scanner.mp4.visitor.MdhdVisitor
import voice.core.scanner.mp4.visitor.StcoVisitor
import voice.core.scanner.mp4.visitor.StscVisitor
import voice.core.scanner.mp4.visitor.StszVisitor
import voice.core.scanner.mp4.visitor.SttsVisitor
import voice.core.scanner.mp4.visitor.TkhdVisitor
import kotlin.math.min

@Inject
internal class Mp4BoxParser(
  stscVisitor: StscVisitor,
  mdhdVisitor: MdhdVisitor,
  sttsVisitor: SttsVisitor,
  stcoVisitor: StcoVisitor,
  co64Visitor: Co64Visitor,
  stszVisitor: StszVisitor,
  chplVisitor: ChplVisitor,
  chapVisitor: ChapVisitor,
  tkhdVisitor: TkhdVisitor,
  hdlrVisitor: HdlrVisitor,
) {

  private val visitors = listOf(
    stscVisitor,
    mdhdVisitor,
    sttsVisitor,
    stcoVisitor,
    co64Visitor,
    stszVisitor,
    chplVisitor,
    chapVisitor,
    tkhdVisitor,
    hdlrVisitor,
  )
  private val visitorByPath = visitors.associateBy { it.path }

  operator fun invoke(input: ExtractorInput): Mp4ChpaterExtractorOutput {
    val scratch = ParsableByteArray(Mp4Box.LONG_HEADER_SIZE)
    val parseOutput = Mp4ChpaterExtractorOutput()
    parseBoxes(
      input = input,
      path = emptyList(),
      parentEnd = Long.MAX_VALUE,
      scratch = scratch,
      parseOutput = parseOutput,
    )
    return parseOutput
  }

  private fun parseBoxes(
    input: ExtractorInput,
    path: List<String>,
    parentEnd: Long,
    scratch: ParsableByteArray,
    parseOutput: Mp4ChpaterExtractorOutput,
  ) {
    while (input.position < parentEnd) {
      val headerStart = input.position
      scratch.reset(Mp4Box.HEADER_SIZE)
      if (!input.readFully(scratch.data, 0, Mp4Box.HEADER_SIZE, true)) {
        return
      }

      var atomSize = scratch.readUnsignedInt()
      val atomType = scratch.readString(4)
      var headerSize = Mp4Box.HEADER_SIZE

      if (atomSize == 1L) {
        if (!input.readFully(
            scratch.data,
            Mp4Box.HEADER_SIZE,
            Mp4Box.LONG_HEADER_SIZE - Mp4Box.HEADER_SIZE,
            true,
          )
        ) {
          return
        }
        scratch.setPosition(Mp4Box.HEADER_SIZE)
        atomSize = scratch.readUnsignedLongToLong()
        headerSize = Mp4Box.LONG_HEADER_SIZE
      } else if (atomSize == 0L) {
        val end = when {
          parentEnd != Long.MAX_VALUE -> parentEnd
          input.length != C.LENGTH_UNSET.toLong() -> input.length
          else -> {
            Logger.w("Box $atomType has size 0 and the input length is unknown")
            return
          }
        }
        if (end < headerStart) return
        atomSize = end - headerStart
      }

      if (atomSize < headerSize) {
        Logger.w("Box $atomType is smaller than its header")
        return
      }
      val payloadSize = atomSize - headerSize
      if (input.position > Long.MAX_VALUE - payloadSize) return
      val payloadEnd = input.position + payloadSize
      if (parentEnd != Long.MAX_VALUE && payloadEnd > parentEnd) {
        Logger.w("Box $atomType extends past its parent")
        return
      }
      val currentPath = path + atomType
      Logger.d("Current path: $currentPath, atomType: $atomType")

      val visitor = visitorByPath[currentPath]

      when {
        visitor != null -> {
          Logger.v("Found ${visitor.path.last()}!")
          if (visitor.consumes(parseOutput, payloadSize) && payloadSize <= Int.MAX_VALUE) {
            val size = payloadSize.toInt()
            scratch.reset(size)
            if (!input.readFully(scratch.data, 0, size, true)) {
              return
            }
            visitor.visit(scratch, parseOutput)
          } else if (!input.skipFully(payloadSize)) {
            return
          }

          if (parseOutput.chplChapters.isNotEmpty()) {
            return
          }
        }
        visitors.any { it.path.startsWith(currentPath) } -> {
          parseBoxes(
            input = input,
            path = currentPath,
            parentEnd = payloadEnd,
            scratch = scratch,
            parseOutput = parseOutput,
          )

          if (parseOutput.chplChapters.isNotEmpty()) {
            return
          }
        }
        else -> {
          if (!input.skipFully(payloadSize)) {
            return
          }
        }
      }

      if (input.position < payloadEnd) {
        if (!input.skipFully(payloadEnd - input.position)) {
          return
        }
      }
    }
  }

  private fun List<String>.startsWith(other: List<String>): Boolean {
    return take(other.size) == other
  }
}

internal fun ExtractorInput.skipFully(length: Long): Boolean {
  var remaining = length
  while (remaining > 0) {
    val chunk = min(remaining, Int.MAX_VALUE.toLong()).toInt()
    if (!skipFully(chunk, true)) return false
    remaining -= chunk
  }
  return true
}
