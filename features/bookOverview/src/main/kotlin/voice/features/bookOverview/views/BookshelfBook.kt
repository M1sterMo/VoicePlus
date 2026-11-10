package voice.features.bookOverview.views

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
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
  title: String = book.name,
  overline: String? = null,
  onCoverReady: () -> Unit = {},
  compact: Boolean = false,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.small)
      .combinedClickable(
        onClick = { onBookClick(book.id) },
        onLongClick = onBookLongClick?.let { { it(book.id) } },
      )
      .padding(horizontal = 4.dp, vertical = 8.dp),
  ) {
    Box {
      BookshelfCover(book.id, book.cover, coverModifier, sharedTransitionScope, onCoverReady, compact)
      if (book.finished) {
        CompletedMedal(size = if (compact) 24.dp else 32.dp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
      }
    }
    Column(detailsModifier) {
      Spacer(Modifier.height(8.dp))
      if (overline != null) {
        Text(
          overline,
          style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = title,
        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(book.name) },
        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = book.statusLabel(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(6.dp))
      if (!book.finished && book.progress > 0f) {
        LinearProgressIndicator(
          progress = { book.progress },
          modifier = Modifier.fillMaxWidth().height(2.dp),
          drawStopIndicator = {},
        )
      } else {
        Spacer(Modifier.height(2.dp))
      }
    }
  }
}
