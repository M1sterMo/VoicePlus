package voice.features.bookOverview.search

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.data.BookId
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.views.LibraryGridBook
import voice.features.bookOverview.views.ListBookRow
import voice.features.bookOverview.views.booksColumnCount
import voice.core.strings.R as StringsR

@Composable
internal fun BookSearchContent(
  sharedTransitionScope: SharedTransitionScope?,
  viewState: BookSearchViewState,
  contentPadding: PaddingValues,
  onQueryChange: (String) -> Unit,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
) {
  when (viewState) {
    is BookSearchViewState.EmptySearch -> {
      LazyColumn(contentPadding = contentPadding) {
        item {
          Spacer(modifier = Modifier.size(16.dp))
        }
        items(viewState.recentQueries) { query ->
          ListItem(
            modifier = Modifier.clickable { onQueryChange(query) },
            headlineContent = { Text(query) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
              Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = stringResource(id = StringsR.string.cover_search_icon_recent),
              )
            },
          )
        }
        items(viewState.suggestedAuthors) { author ->
          ListItem(
            modifier = Modifier.clickable { onQueryChange(author) },
            headlineContent = { Text(author) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
              Icon(
                imageVector = Icons.Outlined.SentimentSatisfied,
                contentDescription = stringResource(id = StringsR.string.cover_search_author),
              )
            },
          )
        }
      }
    }
    is BookSearchViewState.SearchResults -> {
      when (viewState.layoutMode) {
        BookOverviewLayoutMode.List -> {
          LazyColumn(
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier
              .testTag("book-search-results")
              .padding(contentPadding)
              .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
              items(viewState.books) { book ->
                ListBookRow(
                  book = book,
                  onBookClick = onBookClick,
                  onBookLongClick = onBookLongClick,
                  sharedTransitionScope = sharedTransitionScope,
                )
              }
            },
          )
        }
        BookOverviewLayoutMode.Grid, BookOverviewLayoutMode.Books -> {
          val booksMode = viewState.layoutMode == BookOverviewLayoutMode.Books
          BoxWithConstraints(Modifier.padding(contentPadding)) {
            val columns = if (booksMode) {
              booksColumnCount(maxWidth, viewState.booksPerRow, LocalDensity.current.fontScale)
            } else {
              (maxWidth / 180.dp).toInt().coerceAtLeast(1)
            }
            LazyVerticalGrid(
              modifier = Modifier.testTag("book-search-results"),
              columns = GridCells.Fixed(columns),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = PaddingValues(horizontal = if (booksMode) 12.dp else 8.dp, vertical = 8.dp),
              content = {
                items(viewState.books, key = { it.id.value }) { book ->
                  LibraryGridBook(
                    booksMode = booksMode,
                    book = book,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    sharedTransitionScope = sharedTransitionScope,
                    compact = booksMode && columns >= 3 && maxWidth / columns < 144.dp,
                  )
                }
              },
            )
          }
        }
      }
    }
  }
}
