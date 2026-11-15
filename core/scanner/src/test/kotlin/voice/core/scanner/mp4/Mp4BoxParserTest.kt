package voice.core.scanner.mp4

import androidx.media3.common.C
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.ExtractorInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
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
import java.io.EOFException

@RunWith(AndroidJUnit4::class)
internal class Mp4BoxParserTest {

  @Test
  fun `co64 offsets above 4GiB are stored on the track id`() {
    val moov = moovWithCo64(trackId = 2, offset = 0x1_0000_0001)
    val output = parser().invoke(input(moov))

    output.tracks.getValue(2).chunkOffsets shouldBe listOf(0x1_0000_0001)
  }

  @Test
  fun `mdat larger than 2GiB does not hide a following moov`() {
    val moov = moovWithCo64(trackId = 3, offset = 99)
    val mdatSize = 3_000_000_000L
    val header = ByteArray(8)
    writeU32(header, 0, mdatSize)
    "mdat".toByteArray(Charsets.US_ASCII).copyInto(header, 4)
    val file = SparseExtractorInput(
      regions = listOf(
        0L to header,
        mdatSize to moov,
      ),
      fileLength = mdatSize + moov.size,
    )

    val output = parser().invoke(file)

    output.tracks.getValue(3).chunkOffsets shouldBe listOf(99L)
  }

  @Test
  fun `audio sample sizes are not loaded`() {
    val output = Mp4ChpaterExtractorOutput().apply {
      currentTrackId = 1
      currentTrack()?.handler = "soun"
    }

    StszVisitor().consumes(output, payloadSize = 5_000_000) shouldBe false
  }

  @Test
  fun `a trak without tkhd does not inherit the previous track id`() {
    val moov = box(
      "moov",
      trak(trackId = 1, offset = 11) + box("trak", mdiaWith(box("co64", co64(offset = 22)))),
    )

    val output = parser().invoke(input(moov))

    output.tracks.keys shouldBe setOf(1)
    output.tracks.getValue(1).chunkOffsets shouldBe listOf(11L)
  }

  @Test
  fun `a co64 declaring more entries than it holds is ignored`() {
    val moov = box(
      "moov",
      box("trak", box("tkhd", tkhd(trackId = 2)) + mdiaWith(box("co64", co64(offset = 99, declaredEntries = 1_000)))),
    )

    val output = parser().invoke(input(moov))

    output.tracks[2]?.chunkOffsets.orEmpty() shouldBe emptyList()
  }

  private fun parser(): Mp4BoxParser {
    return Mp4BoxParser(
      stscVisitor = StscVisitor(),
      mdhdVisitor = MdhdVisitor(),
      sttsVisitor = SttsVisitor(),
      stcoVisitor = StcoVisitor(),
      co64Visitor = Co64Visitor(),
      stszVisitor = StszVisitor(),
      chplVisitor = ChplVisitor(),
      chapVisitor = ChapVisitor(),
      tkhdVisitor = TkhdVisitor(),
      hdlrVisitor = HdlrVisitor(),
    )
  }

  private fun input(bytes: ByteArray): ExtractorInput {
    val dataSource = ByteArrayDataSource(bytes)
    dataSource.open(DataSpec(android.net.Uri.EMPTY))
    return DefaultExtractorInput(dataSource, 0, bytes.size.toLong())
  }

  private fun moovWithCo64(
    trackId: Int,
    offset: Long,
  ): ByteArray = box("moov", trak(trackId, offset))

  private fun trak(
    trackId: Int,
    offset: Long,
  ): ByteArray = box("trak", box("tkhd", tkhd(trackId)) + mdiaWith(box("co64", co64(offset))))

  private fun tkhd(trackId: Int): ByteArray = ByteArray(16).also { writeU32(it, 12, trackId.toLong()) }

  private fun co64(
    offset: Long,
    declaredEntries: Int = 1,
  ): ByteArray = ByteArray(16).also {
    writeU32(it, 4, declaredEntries.toLong())
    writeU64(it, 8, offset)
  }

  private fun mdiaWith(table: ByteArray): ByteArray = box("mdia", box("minf", box("stbl", table)))
}

private class SparseExtractorInput(
  regions: List<Pair<Long, ByteArray>>,
  private val fileLength: Long,
) : ExtractorInput {
  private val regions = regions.sortedBy { it.first }
  private var position = 0L
  private var peekPosition = 0L

  override fun read(
    target: ByteArray,
    offset: Int,
    length: Int,
  ): Int {
    val read = readAt(::position, target, offset, length)
    peekPosition = position
    return read
  }

  override fun readFully(
    target: ByteArray,
    offset: Int,
    length: Int,
    allowEndOfInput: Boolean,
  ): Boolean {
    return readFullyAt(::position, target, offset, length, allowEndOfInput)
  }

  override fun readFully(
    target: ByteArray,
    offset: Int,
    length: Int,
  ) {
    if (!readFully(target, offset, length, false)) throw EOFException()
  }

  override fun skip(length: Int): Int = skipAt(::position, length)

  override fun skipFully(
    length: Int,
    allowEndOfInput: Boolean,
  ): Boolean {
    if (position + length > fileLength) {
      if (allowEndOfInput) return false
      throw EOFException()
    }
    position += length
    peekPosition = position
    return true
  }

  override fun skipFully(length: Int) {
    if (!skipFully(length, false)) throw EOFException()
  }

  override fun peek(
    target: ByteArray,
    offset: Int,
    length: Int,
  ): Int = readAt(::peekPosition, target, offset, length)

  override fun peekFully(
    target: ByteArray,
    offset: Int,
    length: Int,
    allowEndOfInput: Boolean,
  ): Boolean {
    return readFullyAt(::peekPosition, target, offset, length, allowEndOfInput)
  }

  override fun peekFully(
    target: ByteArray,
    offset: Int,
    length: Int,
  ) {
    if (!peekFully(target, offset, length, false)) throw EOFException()
  }

  override fun advancePeekPosition(
    length: Int,
    allowEndOfInput: Boolean,
  ): Boolean {
    if (peekPosition + length > fileLength) {
      if (allowEndOfInput) return false
      throw EOFException()
    }
    peekPosition += length
    return true
  }

  override fun advancePeekPosition(length: Int) {
    if (!advancePeekPosition(length, false)) throw EOFException()
  }

  override fun resetPeekPosition() {
    peekPosition = position
  }

  override fun getPeekPosition(): Long = peekPosition

  override fun getPosition(): Long = position

  override fun getLength(): Long = fileLength

  override fun <E : Throwable> setRetryPosition(
    position: Long,
    e: E,
  ): Unit = throw e

  private fun readFullyAt(
    cursor: kotlin.reflect.KMutableProperty0<Long>,
    target: ByteArray,
    offset: Int,
    length: Int,
    allowEndOfInput: Boolean,
  ): Boolean {
    var filled = 0
    while (filled < length) {
      val read = readAt(cursor, target, offset + filled, length - filled)
      if (read <= 0) {
        if (allowEndOfInput && filled == 0) return false
        throw EOFException()
      }
      filled += read
    }
    return true
  }

  private fun readAt(
    cursor: kotlin.reflect.KMutableProperty0<Long>,
    target: ByteArray,
    offset: Int,
    length: Int,
  ): Int {
    if (length == 0) return 0
    val pos = cursor.get()
    if (pos >= fileLength) return C.RESULT_END_OF_INPUT
    val region = regions.firstOrNull { pos >= it.first && pos < it.first + it.second.size }
      ?: return C.RESULT_END_OF_INPUT
    val local = (pos - region.first).toInt()
    val available = region.second.size - local
    val count = minOf(length, available)
    region.second.copyInto(target, offset, local, local + count)
    cursor.set(pos + count)
    return count
  }

  private fun skipAt(
    cursor: kotlin.reflect.KMutableProperty0<Long>,
    length: Int,
  ): Int {
    val pos = cursor.get()
    val count = minOf(length.toLong(), (fileLength - pos).coerceAtLeast(0)).toInt()
    cursor.set(pos + count)
    return count
  }
}

private fun box(
  type: String,
  payload: ByteArray,
): ByteArray {
  val out = ByteArray(8 + payload.size)
  writeU32(out, 0, out.size.toLong())
  type.toByteArray(Charsets.US_ASCII).copyInto(out, 4)
  payload.copyInto(out, 8)
  return out
}

private fun writeU32(
  target: ByteArray,
  offset: Int,
  value: Long,
) {
  target[offset] = (value ushr 24).toByte()
  target[offset + 1] = (value ushr 16).toByte()
  target[offset + 2] = (value ushr 8).toByte()
  target[offset + 3] = value.toByte()
}

private fun writeU64(
  target: ByteArray,
  offset: Int,
  value: Long,
) {
  writeU32(target, offset, value ushr 32)
  writeU32(target, offset + 4, value and 0xFFFF_FFFFL)
}
