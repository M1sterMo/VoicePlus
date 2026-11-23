package voice.core.data

// A book keeps all of its chapter ids in one row, and Android reads a row through a 2 MiB cursor window.
// A row that doesn't fit can be written but never read back, so loading the library would crash on every start.
public const val MAX_CHAPTER_IDS_BYTES: Int = 2_000_000

public fun List<ChapterId>.fitIntoOneBook(): Boolean {
  // the size of the JSON array they are stored as
  val bytes = sumOf { it.value.encodeToByteArray().size + 3 } + 1
  return bytes <= MAX_CHAPTER_IDS_BYTES
}
