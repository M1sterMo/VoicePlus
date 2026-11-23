package voice.features.bookOverview.series

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import voice.core.data.BookId
import voice.core.ui.BookshelfCover
import voice.core.ui.shadeFilter
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.views.BooksTileVerticalPadding
import voice.features.bookOverview.views.BookshelfBook
import voice.features.bookOverview.views.CoverChip
import voice.features.bookOverview.views.GridBarHeight
import voice.features.bookOverview.views.GridBookBody
import voice.features.bookOverview.views.GridTileSpacing
import voice.features.bookOverview.views.StackLiftReach
import voice.features.bookOverview.views.TileSubtitleStyle
import voice.features.bookOverview.views.TileTextGap
import voice.features.bookOverview.views.booksColumnCount
import voice.features.bookOverview.views.gridTitleStyle
import voice.features.bookOverview.views.stackLift
import voice.features.bookOverview.views.stackShade
import java.text.NumberFormat
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@Composable
internal fun SeriesCard(
  entry: LibraryEntry,
  onClick: (Rect) -> Unit,
  onLongClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  listMode: Boolean = false,
  booksMode: Boolean = false,
  expanded: Boolean = false,
  compact: Boolean = false,
) {
  val focusRequester = remember { FocusRequester() }
  // The front cover's layout, measured only when the stack is tapped and the series flies open.
  val cover = remember { arrayOfNulls<LayoutCoordinates>(1) }
  var wasExpanded by remember { mutableStateOf(false) }
  LaunchedEffect(expanded) {
    if (wasExpanded && !expanded) focusRequester.requestFocus()
    wasExpanded = expanded
  }
  // Grid tiles are not clipped: their text runs to the corner, where a rounded clip would cut it.
  val tileShape = when {
    booksMode -> MaterialTheme.shapes.small
    listMode -> MaterialTheme.shapes.extraLarge
    else -> RectangleShape
  }
  // A Box rather than a Surface, which always clips, even a Grid tile, cutting its front cover's shadow.
  Box(
    modifier = modifier.testTag(
      entry.key,
    ).fillMaxWidth().focusRequester(focusRequester)
      // List and Books tiles keep their text inset; a Grid tile's text runs to its edge, so it is not clipped.
      .then(if (listMode || booksMode) Modifier.clip(tileShape) else Modifier)
      // No ripple: the stack flies open at once, which answers the tap, and a ripple would play unseen
      // behind the opening on the render thread, costing every other frame of it.
      .combinedClickable(
        interactionSource = null,
        indication = null,
        onClick = { onClick(cover[0]?.takeIf { it.isAttached }?.let { Rect(it.positionInWindow(), it.size.toSize()) } ?: Rect.Zero) },
        onLongClick = onLongClick,
        role = Role.Button,
      )
      .graphicsLayer { alpha = if (expanded) 0f else 1f },
  ) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
      if (listMode) {
        Box(
          Modifier.matchParentSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraLarge),
        )
      }
      if (listMode) {
        Row(
          Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SeriesCovers(entry, { cover[0] = it }, jackets = true, modifier = Modifier.size(76.dp), compact = true)
          SeriesLabel(entry, Modifier.weight(1f), listMode = true)
          Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        // The same rows as a single book's tile, with one bar segment per book where its progress bar sits.
        // Books jackets get room around them; Grid covers fill the tile. One padding whose values change,
        // rather than a padding that comes and goes, so switching views moves the content with it.
        Column(
          Modifier.padding(horizontal = if (booksMode) 4.dp else 0.dp, vertical = if (booksMode) BooksTileVerticalPadding else 0.dp),
        ) {
          SeriesCovers(
            entry,
            { cover[0] = it },
            jackets = booksMode,
            modifier = Modifier.fillMaxWidth().aspectRatio(if (booksMode) 2f / 3f else 1f),
            compact = compact,
            badge = true,
          )
          Spacer(Modifier.height(GridTileSpacing))
          // The label shows the author, not the finished count, so the bar says it.
          SeriesProgress(entry, Modifier.height(GridBarHeight), announce = true)
          Spacer(Modifier.height(GridTileSpacing))
          SeriesLabel(entry, compact = compact)
        }
      }
    }
  }
}

@Composable
private fun SeriesLabel(
  entry: LibraryEntry,
  modifier: Modifier = Modifier,
  listMode: Boolean = false,
  compact: Boolean = false,
) {
  val count = entry.books.size
  val finished = entry.books.count { it.value.finished }
  Column(modifier) {
    Text(
      entry.seriesName.orEmpty(),
      style = if (listMode) MaterialTheme.typography.titleSmall else gridTitleStyle(compact),
      // One line on a tile, like a single book's title, so every tile in a row has the same rows.
      maxLines = if (listMode) 2 else 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (!listMode) Spacer(Modifier.height(TileTextGap))
    // A tile shows the count on its cover badge, which frees this line for the author.
    val author = entry.books.firstNotNullOfOrNull { it.value.author }.takeIf { !listMode }
    Text(
      when {
        author != null -> author
        // A List row has no badge or segmented bar, so it says how far through the series is.
        finished > 0 && listMode -> pluralStringResource(StringsR.plurals.series_finished_count, count, finished, count)
        else -> pluralStringResource(StringsR.plurals.series_book_count, count, count)
      },
      style = if (listMode) MaterialTheme.typography.labelMedium else TileSubtitleStyle,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** One segment per book, filled as far as that book has been heard; one shared bar when segments would be too thin. */
@Composable
private fun SeriesProgress(
  entry: LibraryEntry,
  modifier: Modifier = Modifier,
  announce: Boolean = false,
) {
  val progress = entry.books.map { if (it.value.finished) 1f else it.value.progress }
  val finished = entry.books.count { it.value.finished }
  val description = pluralStringResource(StringsR.plurals.series_finished_count, progress.size, finished, progress.size)
  val track = ProgressIndicatorDefaults.linearTrackColor
  val fill = ProgressIndicatorDefaults.linearColor
  Canvas(modifier.fillMaxWidth().then(if (announce) Modifier.semantics { contentDescription = description } else Modifier)) {
    val radius = CornerRadius(size.height / 2)
    val rtl = layoutDirection == LayoutDirection.Rtl
    fun bar(
      left: Float,
      width: Float,
      value: Float,
    ) {
      drawRoundRect(track, Offset(left, 0f), Size(width, size.height), radius)
      if (value > 0f) {
        val filled = width * value
        drawRoundRect(fill, Offset(if (rtl) left + width - filled else left, 0f), Size(filled, size.height), radius)
      }
    }
    val gap = (if (progress.size > 12) 1.dp else 3.dp).toPx()
    val width = (size.width - gap * (progress.size - 1)) / progress.size
    if (width < 2.dp.toPx()) {
      bar(0f, size.width, progress.average().toFloat())
      return@Canvas
    }
    progress.forEachIndexed { index, value ->
      val start = index * (width + gap)
      bar(if (rtl) size.width - start - width else start, width, value)
    }
  }
}

/** How far each book behind steps out, up and to the left, as a share of the front book's width. */
private const val JACKET_STEP = 0.09f

/**
 * Where a back book's top-left sits relative to the front book's, in absolute (not mirrored) pixels.
 * Every book in a stack has the front book's size, as jackets and as Grid covers alike.
 */
private fun stackOffset(
  layer: Int,
  front: Size,
): Offset = Offset(-layer * JACKET_STEP * front.width, -layer * JACKET_STEP * front.width)

/** A stack shows the series' first book in front and the next one stepping out behind it. */
private const val STACK_DEPTH = 2

/**
 * The series' first two books, stacked, the one behind stepping up and to the left: 2:3 jackets in
 * Books and List, square covers in Grid. The book behind sits in shade and the front one is raised
 * (see [stackShade] and [stackLift]), so the two read as separate books. The stack keeps its direction
 * in right-to-left layouts, like the book art itself.
 */
@Composable
private fun SeriesCovers(
  entry: LibraryEntry,
  onFrontPlace: (LayoutCoordinates) -> Unit,
  jackets: Boolean,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  badge: Boolean = false,
) {
  val books = entry.books.take(STACK_DEPTH)
  val shade = stackShade()
  // Jackets carry a shadow of their own; a Grid stack's front cover is raised on this one.
  val lift = if (jackets || books.size < 2) null else stackLift()
  val back = books.size - 1
  val count = remember(entry.books.size) { NumberFormat.getIntegerInstance().format(entry.books.size) }
  // A plain layout rather than BoxWithConstraints: one measure pass, no subcomposition.
  Layout(
    modifier = modifier,
    content = {
      books.withIndex().reversed().forEach { (index, book) ->
        Box(
          Modifier
            .onPlaced { if (index == 0) onFrontPlace(it) }
            .testTag("${entry.key}:cover:$index"),
        ) {
          if (jackets) {
            BookshelfCover(book.value.id, book.value.cover, compact = compact, shade = if (index > 0) shade else 0f)
          } else {
            AsyncImage(
              model = book.value.cover?.file,
              placeholder = painterResource(UiR.drawable.album_art),
              error = painterResource(UiR.drawable.album_art),
              contentScale = ContentScale.Crop,
              contentDescription = null,
              colorFilter = shadeFilter(if (index > 0) shade else 0f),
              modifier = Modifier.fillMaxSize()
                .then(if (index == 0 && lift != null) Modifier.dropShadow(MaterialTheme.shapes.medium, lift) else Modifier)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            )
          }
          if (index == 0 && badge) {
            CoverChip(
              count,
              compact,
              // Clear of the jacket's page edges, away from the book behind.
              Modifier.align(
                AbsoluteAlignment.BottomRight,
              ).padding(bottom = if (jackets) 10.dp else 6.dp).absolutePadding(right = if (jackets) 10.dp else 6.dp)
                .clearAndSetSemantics {},
            )
          }
        }
      }
    },
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    // Every book in the stack has the front book's size; the back ones only move, in their layers.
    // 2:3 jackets, or square covers so the stack fills the same square as a single book's cover.
    val aspect = if (jackets) 1.5f else 1f
    val bookWidth = minOf(width / (1f + back * JACKET_STEP), height / (aspect + back * JACKET_STEP))
    val bookHeight = bookWidth * aspect
    val spread = bookWidth * back * JACKET_STEP
    val fixed = Constraints.fixed(bookWidth.roundToInt(), bookHeight.roundToInt())
    val placeables = measurables.map { it.measure(fixed) }
    // Centre the whole stack, with the front book standing on the tile's bottom edge and room on the
    // left for the books behind. place(), not placeRelative(): the stack keeps its direction in every
    // layout direction.
    val x = ((width - bookWidth - spread) / 2 + spread).roundToInt()
    val y = height - fixed.maxHeight
    val front = Size(bookWidth, bookHeight)
    // The books behind are placed where they show, rather than each moved in a layer of its own.
    layout(width, height) {
      placeables.forEachIndexed { drawOrder, placeable ->
        val offset = stackOffset(books.size - 1 - drawOrder, front)
        placeable.place(x + offset.x.roundToInt(), y + offset.y.roundToInt())
      }
    }
  }
}

@Composable
internal fun SeriesDetails(
  entry: LibraryEntry,
  layout: BookOverviewLayoutMode,
  booksPerRow: Int,
  origin: Rect,
  onPrepare: () -> Unit,
  onDismiss: () -> Unit,
  onEdit: () -> Unit,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  onShowAll: (() -> Unit)? = null,
) {
  // One clock moves complete book cards; no separate first-row, badge or label timelines.
  var opened by rememberSaveable { mutableStateOf(false) }
  var closing by remember { mutableStateOf(false) }
  var prepared by remember { mutableStateOf(false) }
  val gridState = rememberLazyGridState()
  val readyCovers = remember { mutableStateMapOf<String, Boolean>() }
  val reveal = remember { Animatable(if (opened) 1f else 0f) }
  val prepare by rememberUpdatedState(onPrepare)
  val dismissCallback by rememberUpdatedState(onDismiss)
  LaunchedEffect(closing) {
    if (!closing) {
      // Keep the source stack in place until the covers it shows have loaded (or failed), and give the
      // others, behind them as the cards set off, a moment to load before they fan out.
      suspend fun ready(books: (List<LazyGridItemInfo>) -> List<LazyGridItemInfo>) {
        snapshotFlow {
          val visible = gridState.layoutInfo.visibleItemsInfo
          visible.isNotEmpty() && books(visible).all { readyCovers[it.key] == true }
        }.first { it }
      }
      ready { visible -> visible.filter { it.index < STACK_DEPTH } }
      withTimeoutOrNull(150) { ready { it } }
      prepared = true
      prepare()
    }
    reveal.animateTo(if (closing) 0f else 1f, seriesMotion())
    if (closing) dismissCallback() else opened = true
  }
  val dismiss = { closing = true }
  BackHandler(onBack = dismiss)
  val closeFocus = remember { FocusRequester() }
  LaunchedEffect(LocalInputModeManager.current.inputMode) { closeFocus.requestFocus() }
  BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val panelHeight = maxHeight * 0.86f
    val dismissDistance = with(LocalDensity.current) { 72.dp.toPx() }
    val dismissVelocity = with(LocalDensity.current) { 125.dp.toPx() }
    val maxDrag = constraints.maxHeight.toFloat()
    val dragState = remember {
      AnchoredDraggableState(
        false,
        DraggableAnchors {
          false at 0f
          true at maxDrag
        },
      )
    }
    SideEffect {
      dragState.updateAnchors(
        DraggableAnchors {
          false at 0f
          true at maxDrag
        },
      )
    }
    val fling = remember(dragState, dismissDistance, dismissVelocity) {
      object : FlingBehavior {
        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
          if (initialVelocity >= dismissVelocity || (initialVelocity > -dismissVelocity && dragState.requireOffset() >= dismissDistance)) {
            // Continue from the held position using the same return-to-stack animation as X and Back.
            closing = true
          } else {
            animate(dragState.requireOffset(), 0f, initialVelocity, seriesMotion()) { value, _ ->
              scrollBy(value - dragState.requireOffset())
            }
          }
          return 0f
        }
      }
    }
    Box(
      Modifier.matchParentSize().graphicsLayer {
        // A fading colour or caption needs no off-screen buffer; modulating alpha keeps every frame cheap.
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = if (prepared) reveal.value * (1f - dragState.requireOffset() / maxDrag) else 0f
      }
        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
        .clearAndSetSemantics {}.focusProperties { canFocus = false }
        // No ripple across the screen while the series closes.
        .clickable(interactionSource = null, indication = null, onClick = dismiss),
    )
    // Keep travelling cards in this panel's overlay, so dragging moves covers, badges and text together.
    Box(
      Modifier.fillMaxSize().anchoredDraggable(
        state = dragState,
        orientation = Orientation.Vertical,
        enabled = prepared && !closing && reveal.value == 1f,
        flingBehavior = fling,
      ),
    ) {
      SharedTransitionLayout(
        Modifier.fillMaxSize().offset { IntOffset(0, dragState.requireOffset().roundToInt()) }
          .graphicsLayer { alpha = if (prepared) 1f else 0f }
          .testTag(if (prepared) "series-revealed" else "series-preparing"),
      ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Surface(
            modifier = Modifier.safeDrawingPadding().padding(16.dp).widthIn(max = 720.dp).fillMaxWidth().heightIn(max = panelHeight)
              .testTag("series-details").semantics {
                paneTitle = entry.seriesName.orEmpty()
                dialog()
              }.focusProperties {
                onExit = { if (!closing) cancelFocusChange() }
              }.focusGroup(),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ) {
            Box {
              Box(
                Modifier.matchParentSize().graphicsLayer {
                  compositingStrategy = CompositingStrategy.ModulateAlpha
                  alpha = reveal.value
                }
                  .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraLarge),
              )
              Column {
                Column(
                  Modifier.fillMaxWidth().testTag("series-drag-region").graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = reveal.value
                  },
                ) {
                  Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                  }
                  Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    IconButton(onClick = dismiss, enabled = !closing, modifier = Modifier.focusRequester(closeFocus)) {
                      Icon(Icons.Outlined.Close, stringResource(StringsR.string.close))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                      Text(
                        entry.seriesName.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        pluralStringResource(StringsR.plurals.series_book_count, entry.books.size, entry.books.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    IconButton(onClick = onEdit, enabled = prepared && !closing) {
                      Icon(Icons.Outlined.Edit, stringResource(StringsR.string.series_manage_all))
                    }
                  }
                  if (onShowAll != null) {
                    androidx.compose.material3.TextButton(onClick = onShowAll, enabled = prepared && !closing) {
                      Text(stringResource(StringsR.string.library_show_entire_series))
                    }
                  }
                }
                BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                  // The series opens in the library's own style: cropped Grid tiles, or jackets for Books and List.
                  val grid = layout == BookOverviewLayoutMode.Grid
                  // Respect the chosen density, but leave room for readable titles at larger text sizes.
                  val columns = booksColumnCount(maxWidth, booksPerRow, LocalDensity.current.fontScale)
                  val compact = columns >= 3 && maxWidth / columns < 144.dp
                  var gridBounds by remember { mutableStateOf(Rect.Zero) }
                  LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth().testTag("series-grid")
                      .onGloballyPositioned { gridBounds = Rect(it.positionInWindow(), it.size.toSize()) },
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    // 20dp between rows, as in the library: Grid items add 4dp each side, Books tiles their own padding.
                    verticalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 0.dp),
                  ) {
                    itemsIndexed(entry.books, key = { _, book -> book.value.id.value }) { index, book ->
                      val label = seriesBookLabel(book.value, entry.seriesName.orEmpty())
                      var bounds by remember { mutableStateOf(Rect.Zero) }
                      // Measure the stationary slot, not the animated child, to avoid layout feedback.
                      Box(Modifier.onGloballyPositioned { bounds = Rect(it.positionInWindow(), it.size.toSize()) }) {
                        val itemModifier = Modifier.renderInSharedTransitionScopeOverlay(
                          zIndexInOverlay = (entry.books.size - index).toFloat(),
                          renderInOverlay = { reveal.value != 1f },
                        ).graphicsLayer {
                          // Each card starts on its book in the library's stack and travels to its slot.
                          val remaining = 1f - reveal.value
                          // At rest nothing moves: skip reading the positions, which change on every scroll frame.
                          if (remaining == 0f) {
                            scaleX = 1f
                            scaleY = 1f
                            translationX = 0f
                            translationY = 0f
                            return@graphicsLayer
                          }
                          // Both items inset their cover 4dp at the sides; jackets sit a Books tile's padding from the top, Grid covers 4dp.
                          val top = (if (grid) 4.dp else BooksTileVerticalPadding).toPx()
                          val coverWidth = size.width - 8.dp.toPx()
                          val coverCenterY = top + coverWidth * (if (grid) 1f / 2f else 3f / 4f)
                          if (!bounds.isEmpty && !origin.isEmpty && coverWidth > 0f) {
                            transformOrigin = TransformOrigin(0.5f, coverCenterY / size.height)
                            val layer = index.coerceAtMost(STACK_DEPTH - 1)
                            val scale = 1f + (origin.width / coverWidth - 1f) * remaining
                            scaleX = scale
                            scaleY = scale
                            val target = origin.center + stackOffset(layer, origin.size)
                            translationX = (target.x - bounds.center.x) * remaining
                            translationY = (target.y - bounds.top - coverCenterY) * remaining
                          }
                        }.drawWithContent {
                          // At rest the items are inside the scroll clip already; only the animation needs this.
                          if (reveal.value == 1f) {
                            drawContent()
                            return@drawWithContent
                          }
                          // Preserve the visible part of edge rows when lifting them out of the scroll clip, with
                          // room around the card for the shadow it carries out of the stack.
                          val reach = StackLiftReach.toPx()
                          clipRect(
                            left = -reach,
                            top = (gridBounds.top - bounds.top).coerceAtLeast(-reach),
                            right = size.width + reach,
                            bottom = (gridBounds.bottom - bounds.top).coerceIn(0f, size.height + reach),
                          ) { this@drawWithContent.drawContent() }
                        }.testTag("series-book:${book.value.id.value}")
                        val overline = label.part?.let { stringResource(StringsR.string.series_volume, it) }.orEmpty()
                        // The details and the time on the cover fade in as each card lands.
                        val fade = Modifier.graphicsLayer {
                          compositingStrategy = CompositingStrategy.ModulateAlpha
                          alpha = reveal.value
                        }
                        // The two cards that start as the stack keep its look as they leave it: the one behind in
                        // shade, a Grid front raised on its shadow. Only the shade reads the animation here, for one
                        // card; the shadow fades while drawing.
                        val shade = if (index == 1) stackShade() * (1f - reveal.value) else 0f
                        val lift = if (index == 0 && grid && entry.books.size > 1) ({ 1f - reveal.value }) else null
                        val click: (BookId) -> Unit = { if (prepared && !closing) onBookClick(it) }
                        val longClick: (BookId) -> Unit = { if (prepared && !closing) onBookLongClick(it) }
                        if (grid) {
                          GridBookBody(
                            book.value,
                            // Unclipped, so the author line is never cut by a rounded corner.
                            modifier = itemModifier
                              .combinedClickable(onClick = { click(book.value.id) }, onLongClick = { longClick(book.value.id) })
                              .padding(4.dp),
                            coverModifier = Modifier.testTag("book-cover:${book.value.id.value}"),
                            detailsModifier = fade,
                            chipModifier = fade,
                            title = label.title,
                            overline = overline,
                            onCoverReady = { readyCovers[book.value.id.value] = true },
                            compact = compact,
                            coverShade = shade,
                            coverLift = lift,
                          )
                        } else {
                          BookshelfBook(
                            book.value,
                            onBookClick = click,
                            onBookLongClick = longClick,
                            sharedTransitionScope = null,
                            modifier = itemModifier,
                            coverModifier = Modifier.testTag("book-cover:${book.value.id.value}"),
                            detailsModifier = fade,
                            chipModifier = fade,
                            title = label.title,
                            overline = overline,
                            onCoverReady = { readyCovers[book.value.id.value] = true },
                            compact = compact,
                            coverShade = shade,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun <T> seriesMotion() = spring<T>(dampingRatio = 1f, stiffness = 400f)
