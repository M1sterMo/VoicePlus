package voice.features.bookOverview.series

import voice.features.bookOverview.overview.BookOverviewItemViewState

internal data class SeriesBookLabel(
  val title: String,
  val part: String?,
)

/** Shorten only an explicit series/volume prefix; never alter the stored title or guess its order. */
internal fun seriesBookLabel(
  book: BookOverviewItemViewState,
  seriesName: String,
): SeriesBookLabel {
  val match = listOfNotNull(book.importedSeries, seriesName).filter(String::isNotBlank).firstNotNullOfOrNull { name ->
    Regex("^${Regex.escape(name)}\\s*[:—–-]\\s*(?:Book\\s+)?(\\d+(?:\\.\\d+)?)(?:\\s*[:—–-]\\s*|\\s+)(.+)$", RegexOption.IGNORE_CASE)
      .matchEntire(book.name)
      ?.takeIf { it.groupValues[2].trim().trim(':', '—', '–', '-').isNotBlank() }
  }
  return if (match != null) {
    SeriesBookLabel(match.groupValues[2].trim(), match.groupValues[1])
  } else {
    SeriesBookLabel(book.name, book.seriesPart?.takeIf(String::isNotBlank))
  }
}
