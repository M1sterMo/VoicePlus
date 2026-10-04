package voice.features.bookOverview.views

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.ui.sharedBookCover
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.shelves.LibraryEntryTile
import voice.features.bookOverview.shelves.LibrarySection
import voice.features.bookOverview.shelves.LibraryShelfDragState
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@Composable
internal fun GridBooks(
  sharedTransitionScope: SharedTransitionScope?,
  state: LazyGridState,
  sections: List<LibrarySection>,
  onShelfToggle: (LibrarySection) -> Unit,
  onShelfMenu: (String) -> Unit,
  onShelfAdd: (String) -> Unit,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: ((BookId) -> Unit)?,
  showPermissionBugCard: Boolean,
  onPermissionBugCardClick: () -> Unit,
  booksMode: Boolean = false,
  booksPerRow: Int = 2,
  onSeriesClick: (String, Rect) -> Unit = { _, _ -> },
  onSeriesLongClick: (BookId) -> Unit = {},
  openSeriesKey: String? = null,
  onCoverOrigin: (String) -> Unit = {},
  activeCoverKey: String? = null,
  selecting: Boolean = false,
  selected: Set<BookId> = emptySet(),
  onSelect: (Set<BookId>) -> Unit = {},
  organising: Boolean = false,
  dragState: LibraryShelfDragState? = null,
  onOrganise: (Set<BookId>) -> Unit = {},
) {
  BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    val contentWidth = if (booksMode) (booksPerRow.coerceIn(2, 3) * 240).dp else 840.dp
    val columns = if (booksMode) {
      booksColumnCount(maxWidth, booksPerRow, LocalDensity.current.fontScale)
    } else {
      (maxWidth.coerceAtMost(840.dp) / 180.dp).toInt().coerceAtLeast(1)
    }
    val compactBooks = booksMode && columns >= 3 && maxWidth.coerceAtMost(contentWidth) / columns < 144.dp
    LazyVerticalGrid(
      modifier = Modifier.widthIn(max = contentWidth),
      state = state,
      columns = GridCells.Fixed(columns),
      verticalArrangement = Arrangement.spacedBy(if (booksMode) 0.dp else 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(
        start = if (booksMode) 12.dp else 8.dp,
        end = if (booksMode) 12.dp else 8.dp,
        top = 8.dp,
        bottom = if (booksMode) 80.dp else 4.dp,
      ),
    ) {
      if (showPermissionBugCard) {
        item(
          span = { GridItemSpan(maxLineSpan) },
          key = "permission",
        ) {
          PermissionBugCard(onPermissionBugCardClick)
        }
      }
      sections.filter { (!selecting && !organising) || !it.current }.forEachIndexed { index, section ->
        val expanded = section.expanded
        item(
          span = { GridItemSpan(maxLineSpan) },
          key = "header:${section.id}",
          contentType = "header",
        ) {
          DisposableEffect(dragState, section.id) {
            onDispose { dragState?.unregisterShelf(section.id) }
          }
          ShelfHeader(
            modifier = Modifier.testTag("shelf-header:${section.id}")
              .padding(top = if (index == 0) 0.dp else 8.dp, start = if (booksMode) 4.dp else 12.dp)
              .onGloballyPositioned { dragState?.registerShelf(section.id, it.boundsInRoot()) },
            title = section.title(),
            count = section.count.takeUnless { section.current },
            expanded = expanded,
            onToggle = if (section.current) null else ({ onShelfToggle(section) }),
            onMenu = if (section.current || selecting) null else ({ onShelfMenu(section.id) }),
            dropActive = dragState?.hoveredShelf == section.id,
          )
        }
        if (expanded) {
          if (section.entries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty:${section.id}") {
              if (section.totalCount > 0 || selecting) {
                Text(
                  stringResource(if (selecting) StringsR.string.library_empty_shelf else StringsR.string.library_no_matching_books),
                  Modifier.padding(16.dp),
                )
              } else {
                TextButton(onClick = { onShelfAdd(section.id) }) { Text(stringResource(StringsR.string.series_add_books)) }
              }
            }
          }
          items(
            items = section.entries,
            key = { it.key },
            contentType = { "item" },
          ) { entry ->
            LibraryEntryTile(
              entry = entry,
              layout = if (booksMode) BookOverviewLayoutMode.Books else BookOverviewLayoutMode.Grid,
              onBookClick = onBookClick,
              onBookLongClick = onBookLongClick,
              onSeriesClick = onSeriesClick,
              onSeriesLongClick = onSeriesLongClick,
              onCoverOrigin = onCoverOrigin,
              activeCoverKey = activeCoverKey,
              sharedTransitionScope = sharedTransitionScope,
              openSeriesKey = openSeriesKey,
              selecting = selecting && !section.current,
              selected = selected,
              onSelect = onSelect,
              modifier = Modifier.animateItem(),
              compactBooks = compactBooks,
              dragState = dragState.takeIf { organising },
              onOrganise = if (organising) onOrganise else null,
            )
          }
        }
      }
      item(span = { GridItemSpan(maxLineSpan) }) {
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
      }
    }
  }
}

@Composable
internal fun LibraryGridBook(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: ((BookId) -> Unit)?,
  sharedTransitionScope: SharedTransitionScope?,
  booksMode: Boolean,
  compact: Boolean = false,
) {
  if (booksMode) {
    BookshelfBook(
      book,
      onBookClick,
      onBookLongClick,
      sharedTransitionScope,
      coverModifier = Modifier.testTag("library-book-cover:${book.id.value}"),
      compact = compact,
    )
  } else {
    GridBook(book, onBookClick, onBookLongClick, sharedTransitionScope)
  }
}

@Composable
internal fun GridBook(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: ((BookId) -> Unit)?,
  sharedTransitionScope: SharedTransitionScope?,
) {
  ElevatedCard(
    shape = MaterialTheme.shapes.extraLarge,
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = { onBookClick(book.id) },
        onLongClick = onBookLongClick?.let { { it(book.id) } },
      ),
  ) {
    Column(
      modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(4f / 3f)
          .clip(MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        AsyncImage(
          modifier = Modifier
            .fillMaxSize()
            .sharedBookCover(book.id, sharedTransitionScope)
            .clip(MaterialTheme.shapes.large),
          contentScale = ContentScale.Crop,
          model = book.cover?.file,
          placeholder = painterResource(id = UiR.drawable.album_art),
          error = painterResource(id = UiR.drawable.album_art),
          contentDescription = null,
        )
        if (book.finished) {
          CompletedMedal(
            size = 36.dp,
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp),
          )
        }
      }

      Spacer(Modifier.height(4.dp))

      Text(
        text = book.name,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = book.statusLabel(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!book.finished && book.progress > 0f) {
          Text(
            text = "${(book.progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(Modifier.height(8.dp))
      if (!book.finished && book.progress > 0.05f) {
        LinearProgressIndicator(
          progress = { book.progress },
          modifier = Modifier.height(4.dp),
        )
      } else {
        Spacer(Modifier.height(4.dp))
      }
    }
  }
}

internal fun booksColumnCount(
  width: Dp,
  booksPerRow: Int,
  fontScale: Float,
): Int = booksPerRow.coerceIn(2, 3).coerceAtMost((width / (100.dp * fontScale)).toInt().coerceAtLeast(1))

@Composable
@Preview(widthDp = 200)
private fun GridBookPreviewWithProgress() {
  GridBook(
    book = BookOverviewPreviewParameterProvider().book().copy(progress = 0.66f),
    onBookClick = {},
    onBookLongClick = {},
    sharedTransitionScope = null,
  )
}

@Composable
@Preview(widthDp = 200)
private fun GridBookPreviewWithoutProgress() {
  GridBook(
    book = BookOverviewPreviewParameterProvider().book().copy(progress = 0f),
    onBookClick = {},
    onBookLongClick = {},
    sharedTransitionScope = null,
  )
}
