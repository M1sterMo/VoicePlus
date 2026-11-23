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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.ui.RatingLabel
import voice.core.ui.shadeFilter
import voice.core.ui.sharedBookCover
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.shelves.LibraryEntryTile
import voice.features.bookOverview.shelves.LibrarySection
import voice.features.bookOverview.shelves.LibraryShelfDragState
import voice.features.bookOverview.shelves.boundsInRootOrNull
import java.text.NumberFormat
import kotlin.math.floor
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
  onUpNextClick: () -> Unit = {},
) {
  BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    // Books and Grid both follow the chosen books per row; wide screens centre the shelf at a readable width.
    val contentWidth = (booksPerRow.coerceIn(2, 3) * if (booksMode) 240 else 280).dp
    val columns = booksColumnCount(maxWidth, booksPerRow, LocalDensity.current.fontScale)
    val compactBooks = columns >= 3 && maxWidth.coerceAtMost(contentWidth) / columns < 144.dp
    LazyVerticalGrid(
      modifier = Modifier.widthIn(max = contentWidth),
      state = state,
      columns = GridCells.Fixed(columns),
      verticalArrangement = Arrangement.spacedBy(if (booksMode) 0.dp else 20.dp),
      horizontalArrangement = Arrangement.spacedBy(if (booksMode) 8.dp else 12.dp),
      contentPadding = PaddingValues(
        start = if (booksMode) 12.dp else 16.dp,
        end = if (booksMode) 12.dp else 16.dp,
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
      sections.filter { (!selecting && !organising) || !it.projection }.forEachIndexed { index, section ->
        val expanded = section.expanded
        item(
          span = { GridItemSpan(maxLineSpan) },
          key = "header:${section.id}",
          contentType = "header",
        ) {
          // Where the heading is, read only while something is being dragged.
          val header = remember { arrayOfNulls<LayoutCoordinates>(1) }
          val headerBounds = remember { { header[0]?.boundsInRootOrNull() } }
          DisposableEffect(dragState, section.id) {
            onDispose { dragState?.unregisterShelf(section.id) }
          }
          ShelfHeader(
            modifier = Modifier.testTag("shelf-header:${section.id}")
              .padding(top = if (index == 0) 0.dp else 8.dp, start = if (booksMode) 4.dp else 12.dp)
              .onPlaced {
                header[0] = it
                dragState?.registerShelf(section.id, headerBounds)
              },
            title = section.title(),
            count = section.count.takeUnless { section.projection && !section.upNext },
            expanded = expanded,
            onToggle = if (section.projection) null else ({ onShelfToggle(section) }),
            onMenu = if (section.projection || selecting) null else ({ onShelfMenu(section.id) }),
            dropActive = dragState?.hoveredShelf == section.id,
            onOpen = onUpNextClick.takeIf { section.upNext },
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
              selecting = selecting && !section.projection,
              selected = selected,
              onSelect = onSelect,
              modifier = Modifier.animateItem(),
              compactBooks = compactBooks,
              dragState = dragState,
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
    GridBook(book, onBookClick, onBookLongClick, sharedTransitionScope, compact)
  }
}

@Composable
internal fun GridBook(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: ((BookId) -> Unit)?,
  sharedTransitionScope: SharedTransitionScope?,
  compact: Boolean = false,
) {
  GridBookBody(
    book,
    // No clip on the tile: its rounded corners would cut into the author line. The cover rounds itself.
    Modifier.combinedClickable(
      onClick = { onBookClick(book.id) },
      onLongClick = onBookLongClick?.let { { it(book.id) } },
    ),
    sharedTransitionScope = sharedTransitionScope,
    compact = compact,
  )
}

/**
 * A Grid tile: the whole cover as a square with the book's time on it, then a thin progress bar, one
 * line of title and the author. Every tile has the same rows, series stacks included, so rows stay even.
 */
@Composable
internal fun GridBookBody(
  book: BookOverviewItemViewState,
  modifier: Modifier = Modifier,
  coverModifier: Modifier = Modifier,
  detailsModifier: Modifier = Modifier,
  chipModifier: Modifier = Modifier,
  title: String = book.name,
  overline: String? = null,
  onCoverReady: () -> Unit = {},
  sharedTransitionScope: SharedTransitionScope? = null,
  compact: Boolean = false,
  /** A series stack's look, kept by a card while it flies out of the stack: see [stackShade]. */
  coverShade: Float = 0f,
  /**
   * How strongly the cover is raised on a stack's shadow, from 0 to 1, read while drawing: a card flying
   * out of a stack lets it go as it lands. See [stackLift].
   */
  coverLift: (() -> Float)? = null,
) {
  Column(modifier.fillMaxWidth()) {
    Box {
      // Behind the cover, so the cover's clip doesn't cut it. The same cached blur as the stack's, faded
      // as it draws: no new blur and no recomposition while the card flies.
      coverLift?.let { lift ->
        Box(
          Modifier.matchParentSize()
            .graphicsLayer {
              compositingStrategy = CompositingStrategy.ModulateAlpha
              alpha = lift()
            }
            .dropShadow(MaterialTheme.shapes.medium, stackLift()),
        )
      }
      Box(
        modifier = coverModifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(MaterialTheme.shapes.medium)
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        AsyncImage(
          modifier = Modifier
            .fillMaxSize()
            .sharedBookCover(book.id, sharedTransitionScope)
            .clip(MaterialTheme.shapes.medium),
          contentScale = ContentScale.Crop,
          model = book.cover?.file,
          placeholder = painterResource(id = UiR.drawable.album_art),
          error = painterResource(id = UiR.drawable.album_art),
          onSuccess = { onCoverReady() },
          onError = { onCoverReady() },
          colorFilter = shadeFilter(coverShade),
          contentDescription = null,
        )
        if (book.finished) {
          CompletedMedal(
            size = if (compact) 28.dp else 36.dp,
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp),
          )
        }
        TileTimeChip(book, compact, chipModifier.align(Alignment.BottomStart).padding(6.dp))
      }
    }
    TileDetails(book, title, overline, compact, detailsModifier)
  }
}

/**
 * What sits under a library tile's cover, in Grid and Books alike: a bar on every book, empty until
 * started and full once finished, one line of title, and the author with the listener's rating, or how
 * far in. Every tile has the same rows, series stacks included, so a row of tiles lines up.
 */
@Composable
internal fun TileDetails(
  book: BookOverviewItemViewState,
  title: String,
  overline: String?,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Spacer(Modifier.height(GridTileSpacing))
    // Silent: the time and percentage say the same, and the bar would add a stop of its own to every tile.
    Box(Modifier.clearAndSetSemantics {}) {
      LinearProgressIndicator(
        progress = { if (book.finished) 1f else book.progress },
        modifier = Modifier.fillMaxWidth().height(GridBarHeight),
        gapSize = 0.dp,
        drawStopIndicator = {},
      )
    }
    Spacer(Modifier.height(GridTileSpacing))
    if (overline != null) {
      Text(
        overline,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = title,
      modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(book.name) },
      style = gridTitleStyle(compact),
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(TileTextGap))
    val percent = if (!book.finished && book.progress > 0f) {
      remember(book.progress) { NumberFormat.getPercentInstance().format(floor(book.progress * 100) / 100) }
    } else {
      null
    }
    val rated = book.rating?.let { pluralStringResource(StringsR.plurals.rating_label, it, it) }
    // The time on the cover is read here, after the title and author, and in full.
    val spoken = listOfNotNull(book.author, book.tileTime(compact = false).ifEmpty { null }, rated ?: percent).joinToString(", ")
    Row(
      modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = book.author.orEmpty(),
        modifier = Modifier.weight(1f),
        style = TileSubtitleStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      when {
        book.rating != null -> RatingLabel(book.rating, style = TileSubtitleStyle)
        percent != null -> Text(percent, style = TileSubtitleStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

/** The book's time as a chip on its cover, in Grid and Books. */
@Composable
internal fun TileTimeChip(
  book: BookOverviewItemViewState,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  val time = book.tileTime(compact)
  if (time.isNotEmpty()) CoverChip(time, compact, modifier.clearAndSetSemantics {})
}

/** The time on a tile's cover. Three to a row leaves no room for "left"; the bar and percentage say it. */
@Composable
private fun BookOverviewItemViewState.tileTime(compact: Boolean): String = statusLabel(tile = true, sayLeft = !compact)

/** A dark label on a cover, legible on any artwork: a book's time, a series' book count. */
@Composable
internal fun CoverChip(
  text: String,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier
      .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
      .background(Color.Black.copy(alpha = 0.65f), CircleShape)
      .padding(horizontal = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text,
      style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
      color = Color.White,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Material's depth cues for a series stack, both cheap. The book behind sits a tone darker, more so
 * in a dark theme, where shadows barely show; see [stackLift] for the one in front.
 */
@Composable
internal fun stackShade(): Float = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.35f else 0.2f

/**
 * The soft shadow that raises the front cover of a Grid stack off the book behind: a low Material
 * elevation, blurred once and reused while scrolling. Jackets carry a shadow of their own.
 */
@Composable
internal fun stackLift(): Shadow {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  return Shadow(radius = 8.dp, color = Color.Black.copy(alpha = if (dark) 0.6f else 0.3f), offset = DpOffset(0.dp, 2.dp))
}

/** How far a stack's front cover's shadow reaches past it: its blur radius and offset. */
internal val StackLiftReach = 10.dp

internal val GridTileSpacing = 6.dp
internal val GridBarHeight = 3.dp

/** A little air between a tile's title and its author line. */
internal val TileTextGap = 2.dp

/** The quieter line under a tile's title: regular weight, like the original Voice app's author line. */
internal val TileSubtitleStyle: TextStyle
  @Composable get() = MaterialTheme.typography.bodySmall

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

@Composable
internal fun gridTitleStyle(compact: Boolean): TextStyle =
  // Regular weight, as in the original Voice app: easier on the eyes in a dense grid than medium.
  if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
