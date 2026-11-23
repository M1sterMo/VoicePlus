package voice.core.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import voice.core.data.BookId
import kotlin.math.roundToInt

/**
 * A 2:3 book jacket with spine and page edge, used by the library's Books layout and the player.
 * The jacket is drawn into the cover image once as it loads (see [JacketTransformation]), so on
 * screen it costs one cached image and one shadow, like a plain cover.
 */
@Composable
fun BookshelfCover(
  bookId: BookId,
  cover: ImmutableFile?,
  modifier: Modifier = Modifier,
  sharedTransitionScope: SharedTransitionScope? = null,
  onCoverReady: () -> Unit = {},
  compact: Boolean = false,
  /** Darkens the jacket, from 0 (as it is) to 1 (black): a book lower in a stack sits in shade. */
  shade: Float = 0f,
) {
  val jacketShape = RoundedCornerShape(3.dp)
  val colors = MaterialTheme.colorScheme
  val dark = colors.surface.luminance() < 0.5f
  val paper = lerp(colors.surface, colors.onSurface, if (dark) 0.82f else 0.04f)
  val paperShade = lerp(paper, colors.outline, 0.25f)
  val board = lerp(colors.surfaceContainerHighest, Color.Black, 0.6f)
  val artBackground = colors.surfaceContainerHighest
  val depth = if (compact) 4.dp else 6.dp
  val drop = 2.dp
  val overhang = 2.dp
  val density = LocalDensity.current
  val context = LocalContext.current
  // Drawn at the size the jacket settles at, and drawn again when that changes, so at rest it is never stretched.
  // A shared-element transition resizes it on every frame, like the player's cover flying in from the
  // library: its lookahead pass gives the size it lands at from the first frame, so it is drawn at that
  // size once and scaled while it flies, never redrawn as it lands.
  var size by remember { mutableStateOf(IntSize.Zero) }
  val lookahead = remember { booleanArrayOf(false) }
  val palette = listOf(board, paper, paperShade, artBackground).joinToString { it.toArgb().toString() }
  val request = remember(cover, compact, board, paper, paperShade, artBackground, density, size) {
    if (size == IntSize.Zero) return@remember null
    val jacket = with(density) {
      JacketTransformation(
        depth = depth.toPx(),
        drop = drop.toPx(),
        overhang = overhang.toPx(),
        corner = 3.dp.toPx(),
        pageCorner = 2.dp.toPx(),
        grain = 0.35.dp.toPx(),
        hairline = 1.dp.toPx(),
        bindingMin = minOf(10.dp.toPx(), size.width * 0.15f),
        bindingMax = 16.dp.toPx(),
        board = board.toArgb(),
        paper = paper.toArgb(),
        paperShade = paperShade.toArgb(),
        artBackground = artBackground.toArgb(),
      )
    }
    ImageRequest.Builder(context)
      // A book without cover art still gets a jacket, drawn around a plain front, and all of them share it.
      .data(cover?.file ?: artBackground.toArgb().toDrawable())
      .apply { if (cover == null) memoryCacheKey("${jacket.cacheKey}-blank-${size.width}x${size.height}") }
      .size(size.width, size.height)
      .transformations(jacket)
      // While a new size or theme draws, the book's last jacket stands in, scaled.
      .placeholderMemoryCacheKey(LastJacket[bookId])
      .build()
  }
  // Until a jacket can be shown (a finished one, or the book's last one still in memory), a plain
  // drawn jacket stands in, so a tile is never left empty.
  var loaded by remember(bookId) {
    mutableStateOf(LastJacket[bookId]?.let { context.imageLoader.memoryCache?.get(it) } != null)
  }
  val coverState = sharedTransitionScope?.run { rememberBookCoverState(bookId) }
  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(2f / 3f)
      .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        if (isLookingAhead) lookahead[0] = true
        if (isLookingAhead || !lookahead[0]) size = IntSize(placeable.width, placeable.height)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
      },
  ) {
    val boards = Modifier.matchParentSize().absolutePadding(left = depth, top = drop, right = overhang, bottom = overhang)
    // The soft shadow spreads past the jacket. Compose blurs it once and reuses the bitmap.
    Box(
      boards.dropShadow(
        jacketShape,
        Shadow(radius = 5.dp, color = Color.Black.copy(alpha = if (dark) 0.4f else 0.18f), offset = DpOffset(1.dp, 3.dp)),
      ),
    )
    if (!loaded || request == null) {
      // In the same shade as the jacket it stands in for.
      val shadow = Color.Black.copy(alpha = shade)
      Box(boards.background(shadow.compositeOver(paper), jacketShape))
      Box(
        Modifier
          .fillMaxSize()
          .absolutePadding(right = depth + overhang, bottom = drop + overhang)
          .background(shadow.compositeOver(artBackground), jacketShape),
      )
    }
    // Nothing to load until the jacket has been measured; then it is drawn at exactly that size.
    if (request != null) {
      AsyncImage(
        model = request,
        contentDescription = null,
        onSuccess = { state ->
          state.result.memoryCacheKey?.let {
            LastJacket[bookId] = it
            WidestJacket.offer(bookId, WidestJacket.Entry(it, state.painter.intrinsicSize.width, palette))
          }
          loaded = true
          onCoverReady()
        },
        onError = {
          loaded = false
          onCoverReady()
        },
        contentScale = ContentScale.FillBounds,
        // Applied as the cached jacket is drawn: no new image, no extra pass.
        colorFilter = shadeFilter(shade),
        modifier = Modifier
          .fillMaxSize()
          .sharedBookCover(bookId, sharedTransitionScope, coverState)
          .drawWithContent {
            // Flying home from the player, the jacket starts at the player's size: drawn from the player's
            // larger jacket, not this one stretched with page edges several times too thick. It lands as
            // this one again.
            val flying = sharedTransitionScope?.isTransitionActive == true && coverState?.isMatchFound == true
            val wider = if (flying) WidestJacket[bookId]?.takeIf { it.width > size.width.toFloat() && it.palette == palette } else null
            val bitmap = wider?.let { context.imageLoader.memoryCache?.get(it.key)?.bitmap }
            if (bitmap == null) {
              drawContent()
            } else {
              drawImage(
                bitmap.asImageBitmap(),
                dstSize = this.size.let { IntSize(it.width.roundToInt(), it.height.roundToInt()) },
                colorFilter = shadeFilter(shade),
              )
            }
          },
      )
    }
    if (cover == null) {
      Icon(
        imageVector = Icons.Outlined.Book,
        contentDescription = null,
        tint = colors.onSurfaceVariant,
        modifier = Modifier.align(Alignment.Center).absolutePadding(right = depth + overhang, bottom = drop + overhang).size(48.dp),
      )
    }
  }
}

/**
 * The memory-cache key of each book's most recent jacket, whatever its size or theme. A new size (the
 * player, another books-per-row) or a theme change shows it scaled while the right one is drawn.
 */
private object LastJacket {
  private const val MAX = 300
  private val keys = object : LinkedHashMap<BookId, MemoryCache.Key>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BookId, MemoryCache.Key>?) = size > MAX
  }

  @Synchronized
  operator fun get(id: BookId): MemoryCache.Key? = keys[id]

  @Synchronized
  operator fun set(
    id: BookId,
    key: MemoryCache.Key,
  ) {
    keys[id] = key
  }
}

/**
 * Each book's widest jacket drawn in the current colours, like the player's: a library tile flying home from
 * the player draws from it. A jacket in other colours always replaces it, so it never stays in old ones.
 */
private object WidestJacket {
  class Entry(
    val key: MemoryCache.Key,
    val width: Float,
    val palette: String,
  )

  private const val MAX = 300
  private val entries = object : LinkedHashMap<BookId, Entry>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BookId, Entry>?) = size > MAX
  }

  @Synchronized
  operator fun get(id: BookId): Entry? = entries[id]

  @Synchronized
  fun offer(
    id: BookId,
    entry: Entry,
  ) {
    val current = entries[id]
    if (current == null || current.palette != entry.palette || entry.width >= current.width) entries[id] = entry
  }
}

/** A darker tone over a cover, or none: from 0 (as it is) to 1 (black). */
fun shadeFilter(shade: Float): ColorFilter? = if (shade > 0f) ColorFilter.tint(Color.Black.copy(alpha = shade), BlendMode.SrcAtop) else null
