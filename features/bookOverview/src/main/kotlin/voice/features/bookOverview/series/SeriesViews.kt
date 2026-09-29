package voice.features.bookOverview.series

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.features.bookOverview.views.BookshelfBook
import voice.features.bookOverview.views.BookshelfCover
import voice.features.bookOverview.views.booksColumnCount
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

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
  var coverBounds by remember { mutableStateOf(Rect.Zero) }
  var wasExpanded by remember { mutableStateOf(false) }
  LaunchedEffect(expanded) {
    if (wasExpanded && !expanded) focusRequester.requestFocus()
    wasExpanded = expanded
  }
  Surface(
    modifier = modifier.testTag(
      entry.key,
    ).fillMaxWidth().focusRequester(focusRequester)
      .combinedClickable(onClick = { onClick(coverBounds) }, onLongClick = onLongClick, role = Role.Button)
      .graphicsLayer { alpha = if (expanded) 0f else 1f },
    shape = if (booksMode) MaterialTheme.shapes.small else MaterialTheme.shapes.extraLarge,
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Box {
      if (!booksMode) {
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
          SeriesCovers(entry, { coverBounds = it }, Modifier.size(76.dp), compact = true)
          SeriesLabel(entry, Modifier.weight(1f), listMode = true)
          Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        val padding = if (booksMode) 8.dp else 12.dp
        val horizontalPadding = if (booksMode) 4.dp else padding
        Column(
          Modifier.padding(start = horizontalPadding, end = horizontalPadding, top = padding, bottom = if (booksMode) 8.dp else 0.dp),
        ) {
          SeriesCovers(entry, { coverBounds = it }, Modifier.fillMaxWidth().aspectRatio(if (booksMode) 2f / 3f else 4f / 3f), compact)
          Spacer(Modifier.height(if (booksMode) 8.dp else 4.dp))
          SeriesLabel(entry, booksMode = booksMode, compact = compact)
          // Reserve the same status/progress space as an individual book.
          Spacer(Modifier.height(if (booksMode) 8.dp else 12.dp))
        }
      }
    }
  }
}

@Composable
private fun SeriesLabel(
  entry: LibraryEntry,
  modifier: Modifier = Modifier,
  booksMode: Boolean = false,
  listMode: Boolean = false,
  compact: Boolean = false,
) {
  Column(modifier) {
    Text(
      entry.seriesName.orEmpty(),
      style = when {
        booksMode && compact -> MaterialTheme.typography.bodySmall
        booksMode -> MaterialTheme.typography.bodyMedium
        listMode -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.titleMedium
      },
      minLines = if (listMode || booksMode) 1 else 2,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    if (booksMode) Spacer(Modifier.height(4.dp))
    Text(
      pluralStringResource(StringsR.plurals.series_book_count, entry.books.size, entry.books.size),
      style = if (booksMode) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun stackAngle(index: Int): Float = when (index) {
  0 -> 0f
  1 -> -3f
  else -> 3f
}

/** A full-size front book, with two angled jackets and page edges visible behind it. */
@Composable
private fun SeriesCovers(
  entry: LibraryEntry,
  onCoverBounds: (Rect) -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
    val coverWidth = minOf(maxWidth, maxHeight * (2f / 3f))
    entry.books.take(3).withIndex().reversed().forEach { (index, book) ->
      Box(
        Modifier
          .width(coverWidth)
          .onGloballyPositioned { if (index == 0) onCoverBounds(Rect(it.positionInWindow(), it.size.toSize())) }
          .graphicsLayer {
            transformOrigin = TransformOrigin.Center
            scaleX = if (index == 0) 1f else 0.96f
            scaleY = scaleX
            rotationZ = stackAngle(index)
            translationY = -index * 3.dp.toPx()
          }.testTag("${entry.key}:cover:$index"),
      ) {
        BookshelfCover(book.value, compact = compact)
      }
    }
  }
}

@Composable
internal fun SeriesDetails(
  entry: LibraryEntry,
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
      // Keep the source stack visible until every initially visible cover has loaded (or failed).
      snapshotFlow {
        val visible = gridState.layoutInfo.visibleItemsInfo
        visible.isNotEmpty() && visible.all { readyCovers[it.key] == true }
      }.first { it }
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
      Modifier.matchParentSize().graphicsLayer { alpha = if (prepared) reveal.value * (1f - dragState.requireOffset() / maxDrag) else 0f }
        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
        .clearAndSetSemantics {}.focusProperties { canFocus = false }.clickable(onClick = dismiss),
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
                Modifier.matchParentSize().graphicsLayer { alpha = reveal.value }
                  .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraLarge),
              )
              Column {
                Column(
                  Modifier.fillMaxWidth().testTag("series-drag-region").graphicsLayer { alpha = reveal.value },
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                  ) {
                    itemsIndexed(entry.books, key = { _, book -> book.value.id.value }) { index, book ->
                      val label = seriesBookLabel(book.value, entry.seriesName.orEmpty())
                      var bounds by remember { mutableStateOf(Rect.Zero) }
                      // Measure the stationary slot, not the animated child, to avoid layout feedback.
                      Box(Modifier.onGloballyPositioned { bounds = Rect(it.positionInWindow(), it.size.toSize()) }) {
                        BookshelfBook(
                          book.value,
                          onBookClick = { if (prepared && !closing) onBookClick(it) },
                          onBookLongClick = { if (prepared && !closing) onBookLongClick(it) },
                          sharedTransitionScope = null,
                          modifier = Modifier.renderInSharedTransitionScopeOverlay(
                            zIndexInOverlay = (entry.books.size - index).toFloat(),
                            renderInOverlay = { reveal.value != 1f },
                          ).graphicsLayer {
                            val remaining = 1f - reveal.value
                            val padding = 8.dp.toPx()
                            val coverWidth = size.width - 8.dp.toPx()
                            val coverCenterY = padding + coverWidth * 0.75f
                            if (!bounds.isEmpty && !origin.isEmpty && coverWidth > 0f) {
                              transformOrigin = TransformOrigin(0.5f, coverCenterY / size.height)
                              val layer = index.coerceAtMost(2)
                              val stackScale = if (layer == 0) 1f else 0.96f
                              val scale = 1f + (origin.width * stackScale / coverWidth - 1f) * remaining
                              scaleX = scale
                              scaleY = scale
                              rotationZ = stackAngle(layer) * remaining
                              translationX = (origin.center.x - bounds.center.x) * remaining
                              val stackCenterY = origin.center.y - layer * 3.dp.toPx()
                              translationY = (stackCenterY - bounds.top - coverCenterY) * remaining
                            }
                          }.drawWithContent {
                            // Preserve the visible part of edge rows when lifting them out of the scroll clip.
                            clipRect(
                              top = (gridBounds.top - bounds.top).coerceAtLeast(0f),
                              bottom = (gridBounds.bottom - bounds.top).coerceIn(0f, size.height),
                            ) { this@drawWithContent.drawContent() }
                          }.testTag("series-book:${book.value.id.value}"),
                          coverModifier = Modifier.testTag("book-cover:${book.value.id.value}"),
                          detailsModifier = Modifier.graphicsLayer { alpha = reveal.value },
                          title = label.title,
                          overline = label.part?.let { stringResource(StringsR.string.series_volume, it) }.orEmpty(),
                          onCoverReady = { readyCovers[book.value.id.value] = true },
                          compact = compact,
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

private fun <T> seriesMotion() = spring<T>(dampingRatio = 1f, stiffness = 400f)
