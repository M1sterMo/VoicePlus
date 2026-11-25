package voice.core.scanner

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * An ID3v2 text frame carries its encoding in the first byte, and `0` means ISO-8859-1. Taggers that set that
 * byte correctly still wrote other bytes into the frame: Windows-1251 where the tagger's locale was Cyrillic,
 * Windows-1252 for Western punctuation, or plain UTF-8 from tools that ignore the encoding byte altogether.
 * Media3 decodes such a frame as ISO-8859-1, as the spec says, so "Валис" reaches the library as "Âàëèñ" and
 * stays that way.
 *
 * Repairing the decoded string instead of the file leaves the audio untouched, and loses nothing: every
 * character of an ISO-8859-1 decode fits into one byte, so [String.toByteArray] with ISO-8859-1 recovers
 * exactly the bytes the tagger wrote.
 *
 * Only repairs the bytes can prove are attempted. Latin-alphabet legacy encodings (Windows-1250, -1254, -1257)
 * and the double-byte Asian ones cannot be told apart from genuine Latin text in the few dozen bytes of a
 * title, so a guess there would corrupt correct names; those need the user to choose a charset.
 */
internal fun repairLegacyTagText(value: String): String {
  // Anything above U+00FF came out of a UTF-16 frame and is already decoded.
  if (value.isEmpty() || value.any { it.code > 0xFF }) return value
  val bytes = value.toByteArray(Charsets.ISO_8859_1)

  // UTF-8 written into a frame that declares ISO-8859-1. Independent of the script, so this covers languages
  // that have no single-byte legacy encoding at all.
  decodeStrict(bytes, Charsets.UTF_8)?.let { utf8 ->
    if (utf8.any { it.code > 0x7F }) return utf8
  }

  for (legacy in LEGACY_CHARSETS) {
    val candidate = decodeStrict(bytes, legacy.charset) ?: continue
    val letters = candidate.count { it.isLetter() }
    val script = candidate.count { character -> legacy.ranges.any { character.code in it } }
    // A repair turns a run of accented Latin letters into a run of script letters and leaves few ASCII ones
    // behind. Genuine accents do the opposite, so "Café" and "Müller" survive while "Âàëèñ" is fixed.
    if (script >= MIN_SCRIPT_LETTERS && script > letters - script) return candidate
  }

  // Windows-1252 keeps its punctuation in 0x80..0x9F, where ISO-8859-1 has control characters. Text that
  // arrived carrying those is Windows-1252 for certain: no title legitimately holds U+0080..U+009F.
  if (value.any { it.code in 0x80..0x9F }) {
    WINDOWS_1252?.let { decodeStrict(bytes, it) }?.let { return it }
  }
  return value
}

/**
 * A legacy charset, and the Unicode blocks where its letters land.
 */
private class LegacyCharset(
  val charset: Charset,
  val ranges: List<IntRange>,
)

/**
 * Windows-1251 is the only legacy charset this can separate from ISO-8859-1 on its own: Cyrillic letters sit in
 * 0xC0..0xFF, where ISO-8859-1 has accented Latin ones, so a decode that produces Cyrillic has evidence behind
 * it.
 *
 * Greek (windows-1253) and Hebrew (windows-1255) letters occupy the same band and decode just as cleanly from
 * the same bytes, which would turn a Cyrillic title into plausible-looking Greek or Hebrew and the other way
 * round. Adding one of them needs a discriminator beyond the byte ranges, such as letter frequency, or letting
 * the user name the charset.
 *
 * The charsets ship with Android and with the JVM; a platform that lacks one simply skips the repair.
 */
private val LEGACY_CHARSETS: List<LegacyCharset> = buildList {
  runCatching { Charset.forName("windows-1251") }.getOrNull()?.let { charset ->
    add(LegacyCharset(charset = charset, ranges = listOf(0x0400..0x04FF)))
  }
}

private val WINDOWS_1252: Charset? = runCatching { Charset.forName("windows-1252") }.getOrNull()

/**
 * Shortest run of script letters worth trusting. Two letters are a coincidence as often as a repair: "ÉTÉ" in
 * ISO-8859-1 is two bytes away from "ЙTЙ", and leaving a correct title alone costs nothing.
 */
private const val MIN_SCRIPT_LETTERS = 3

/**
 * Decodes with every error reported, so a byte sequence that does not fit the charset returns null instead of
 * being papered over with replacement characters.
 */
private fun decodeStrict(
  bytes: ByteArray,
  charset: Charset,
): String? {
  return try {
    charset.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(bytes))
      .toString()
  } catch (_: CharacterCodingException) {
    null
  }
}
