package voice.features.bookOverview.views

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import voice.core.data.BookId
import voice.core.ui.BookshelfCover
import voice.features.bookOverview.overview.BookOverviewItemViewState

@Composable
internal fun BookshelfBook(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: ((BookId) -> Unit)?,
  sharedTransitionScope: SharedTransitionScope?,
  modifier: Modifier = Modifier,
  coverModifier: Modifier = Modifier,
  detailsModifier: Modifier = Modifier,
  chipModifier: Modifier = Modifier,
  title: String = book.name,
  overline: String? = null,
  onCoverReady: () -> Unit = {},
  compact: Boolean = false,
  /** A series stack's shade, kept by the jacket behind while it flies out of the stack: see [stackShade]. */
  coverShade: Float = 0f,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.small)
      .combinedClickable(
        onClick = { onBookClick(book.id) },
        onLongClick = onBookLongClick?.let { { it(book.id) } },
      )
      .padding(horizontal = 4.dp, vertical = BooksTileVerticalPadding),
  ) {
    Box {
      BookshelfCover(
        book.id,
        book.cover,
        coverModifier,
        sharedTransitionScope,
        onCoverReady,
        compact,
        shade = coverShade,
      )
      if (book.finished) {
        CompletedMedal(size = if (compact) 24.dp else 32.dp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
      }
      // On the jacket's front: past the binding, which is up to 16dp wide, and short of the page edges.
      TileTimeChip(book, compact, chipModifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 10.dp, bottom = 10.dp))
    }
    TileDetails(book, title, overline, compact, detailsModifier)
  }
}

/** Above and below a Books tile; two of them make the gap between rows, as Grid's row spacing does. */
internal val BooksTileVerticalPadding = 10.dp
