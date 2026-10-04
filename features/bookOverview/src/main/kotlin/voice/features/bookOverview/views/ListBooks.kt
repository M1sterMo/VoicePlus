package voice.features.bookOverview.views

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.ui.ImmutableFile
import voice.core.ui.sharedBookCover
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.shelves.LibraryEntryTile
import voice.features.bookOverview.shelves.LibrarySection
import voice.features.bookOverview.shelves.LibraryShelfDragState
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@Composable
internal fun ListBooks(
  sharedTransitionScope: SharedTransitionScope?,
  state: LazyListState,
  sections: List<LibrarySection>,
  onShelfToggle: (LibrarySection) -> Unit,
  onShelfMenu: (String) -> Unit,
  onShelfAdd: (String) -> Unit,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: ((BookId) -> Unit)?,
  showPermissionBugCard: Boolean,
  onPermissionBugCardClick: () -> Unit,
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
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp),
      state = state,
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 16.dp),
    ) {
      if (showPermissionBugCard) {
        item(key = "permission") {
          PermissionBugCard(onPermissionBugCardClick)
        }
      }
      sections.filter { (!selecting && !organising) || !it.current }.forEachIndexed { index, section ->
        val expanded = section.expanded
        item(
          key = "header:${section.id}",
          contentType = "header",
        ) {
          DisposableEffect(dragState, section.id) {
            onDispose { dragState?.unregisterShelf(section.id) }
          }
          ShelfHeader(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface)
              .testTag("shelf-header:${section.id}")
              .padding(top = if (index == 0) 0.dp else 8.dp, start = 8.dp)
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
            item(key = "empty:${section.id}") {
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
              layout = BookOverviewLayoutMode.List,
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
              dragState = dragState.takeIf { organising },
              onOrganise = if (organising) onOrganise else null,
            )
          }
        }
      }
      item {
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
      }
    }
  }
}

@Composable
internal fun ListBookRow(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: ((BookId) -> Unit)?,
  sharedTransitionScope: SharedTransitionScope?,
  modifier: Modifier = Modifier,
) {
  ElevatedCard(
    shape = MaterialTheme.shapes.extraLarge,
    modifier = modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = { onBookClick(book.id) },
        onLongClick = onBookLongClick?.let { { it(book.id) } },
      ),
  ) {
    Column(Modifier.padding()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CoverImage(
          bookId = book.id,
          cover = book.cover,
          sharedTransitionScope = sharedTransitionScope,
        )

        Column(
          Modifier
            .padding(start = 12.dp)
            .weight(1f),
        ) {
          if (book.author != null) {
            Text(
              text = book.author.toUpperCase(LocaleList.current),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }

          Text(
            text = book.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = book.statusLabel(),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )

            if (!book.finished && book.progress > 0f) {
              Text(
                text = "${(book.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
          }
        }

        if (book.finished) {
          CompletedMedal(
            size = 44.dp,
            modifier = Modifier.padding(end = 12.dp),
          )
        }
      }

      if (!book.finished && book.progress > 0.05f) {
        Spacer(Modifier.size(0.dp))
        LinearProgressIndicator(
          progress = { book.progress },
          modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .height(4.dp),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
      }
    }
  }
}

/** Remaining time while listening; the finish date, or plain "Completed", once done. */
@Composable
internal fun BookOverviewItemViewState.statusLabel(): String {
  return when {
    !finished -> remainingTime
    finishedOn != null -> stringResource(StringsR.string.book_finished_on, finishedOn)
    else -> stringResource(StringsR.string.book_header_completed)
  }
}

@Composable
private fun CoverImage(
  bookId: BookId,
  cover: ImmutableFile?,
  sharedTransitionScope: SharedTransitionScope?,
) {
  val startPadding = 16.dp
  val endPadding = 16.dp
  AsyncImage(
    modifier = Modifier
      .padding(top = 8.dp, start = 8.dp, bottom = 8.dp)
      .size(76.dp)
      .sharedBookCover(bookId, sharedTransitionScope)
      .clip(RoundedCornerShape(topStart = startPadding, bottomStart = startPadding, topEnd = endPadding, bottomEnd = endPadding)),
    model = cover?.file,
    placeholder = painterResource(id = UiR.drawable.album_art),
    error = painterResource(id = UiR.drawable.album_art),
    contentScale = ContentScale.Crop,
    contentDescription = null,
  )
}

@Composable
@Preview
private fun ListBookRowPreviewFinished() {
  ListBookRow(
    book = BookOverviewPreviewParameterProvider().book().copy(progress = 1f, finished = true, finishedOn = "12 Mar 2026"),
    onBookClick = {},
    onBookLongClick = {},
    sharedTransitionScope = null,
  )
}

@Composable
@Preview
private fun ListBookRowPreviewWithProgress() {
  ListBookRow(
    book = BookOverviewPreviewParameterProvider().book().copy(progress = 0.6f),
    onBookClick = {},
    onBookLongClick = {},
    sharedTransitionScope = null,
  )
}

@Composable
@Preview
private fun ListBookRowPreviewWithoutProgress() {
  ListBookRow(
    book = BookOverviewPreviewParameterProvider().book().copy(progress = 0f),
    onBookClick = {},
    onBookLongClick = {},
    sharedTransitionScope = null,
  )
}
