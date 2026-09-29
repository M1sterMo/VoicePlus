package voice.features.bookOverview.series

import androidx.compose.runtime.State
import voice.features.bookOverview.overview.BookOverviewItemViewState

internal data class LibraryEntry(
  val seriesName: String?,
  val books: List<State<BookOverviewItemViewState>>,
  val sectionId: String,
  val itemId: String,
  val seriesId: String? = null,
  val allBooks: List<State<BookOverviewItemViewState>> = books,
) {
  val key: String get() = "$sectionId:$itemId"
  val title: String get() = seriesName ?: books.first().value.name
}
