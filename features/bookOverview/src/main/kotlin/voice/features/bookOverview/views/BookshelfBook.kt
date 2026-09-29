package voice.features.bookOverview.views

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.ui.sharedBookCover
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
      BookshelfCover(book, coverModifier, sharedTransitionScope, onCoverReady, compact)
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

/** The same jacket, spine and page edge are used by individual books and series stacks. */
@Composable
internal fun BookshelfCover(
  book: BookOverviewItemViewState,
  modifier: Modifier = Modifier,
  sharedTransitionScope: SharedTransitionScope? = null,
  onCoverReady: () -> Unit = {},
  compact: Boolean = false,
) {
  val jacketShape = RoundedCornerShape(3.dp)
  val colors = MaterialTheme.colorScheme
  val dark = colors.surface.luminance() < 0.5f
  val paper = lerp(colors.surface, colors.onSurface, if (dark) 0.82f else 0.04f)
  val paperShade = lerp(paper, colors.outline, 0.25f)
  val depth = if (compact) 4.dp else 6.dp
  val drop = 2.dp
  val overhang = 2.dp
  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(2f / 3f),
  ) {
    // Inset paper between equally sized boards; all layers keep the same resting bounds.
    Box(
      Modifier
        .matchParentSize()
        .absolutePadding(left = depth, top = drop, right = overhang, bottom = overhang)
        .dropShadow(
          jacketShape,
          Shadow(radius = 5.dp, color = Color.Black.copy(alpha = if (dark) 0.4f else 0.18f), offset = DpOffset(1.dp, 3.dp)),
        )
        .background(lerp(colors.surfaceContainerHighest, Color.Black, 0.6f), jacketShape),
    )
    Box(
      Modifier.matchParentSize().drawWithCache {
        val front = size.width - (depth + overhang).toPx()
        val back = size.width - (overhang + 1.dp).toPx()
        val top = (drop + overhang).toPx()
        val bottom = size.height - (drop + overhang + 1.dp).toPx()
        val left = depth.toPx()
        val corner = CornerRadius(2.dp.toPx())
        val shading = Brush.horizontalGradient(listOf(paperShade, paper, paperShade), startX = front, endX = back)
        val grainStroke = Stroke(width = 0.35.dp.toPx())
        onDrawBehind {
          // A few curved leaves read as paper at thumbnail size, without a thick white stripe.
          repeat(3) { page ->
            val right = back - page * (back - front) / 3f
            val pageSize = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f))
            drawRoundRect(shading, Offset(left, top), pageSize, corner)
            drawRoundRect(paperShade, Offset(left, top), pageSize, corner, style = grainStroke)
          }
        }
      },
    )
    Box(
      modifier = Modifier
        .fillMaxSize()
        .absolutePadding(right = depth + overhang, bottom = drop + overhang)
        .dropShadow(jacketShape, Shadow(radius = 1.dp, color = Color.Black.copy(alpha = 0.35f), offset = DpOffset(1.dp, 1.dp)))
        .clip(jacketShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
      Icon(
        imageVector = Icons.Outlined.Book,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.Center).size(48.dp),
      )
      AsyncImage(
        model = book.cover?.file,
        contentDescription = null,
        onSuccess = { onCoverReady() },
        onError = { onCoverReady() },
        // Fill the portrait jacket with a centered crop, preserving the artwork's proportions.
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .sharedBookCover(book.id, sharedTransitionScope)
          .drawWithCache {
            val bindingWidth = (size.width * 0.09f).coerceIn(10.dp.toPx(), 16.dp.toPx())
            val spineWidth = bindingWidth * 0.7f
            val coverLayer = obtainGraphicsLayer()
            // Compress the artwork around the rounded spine, easing into the flat cover.
            val spineEdges = FloatArray(13) { index ->
              val t = index / 12f
              spineWidth * t * t * (2f - t)
            }
            val binding = Brush.horizontalGradient(
              0f to Color.Black.copy(alpha = 0.48f),
              0.15f to Color.Black.copy(alpha = 0.2f),
              0.35f to Color.White.copy(alpha = 0.12f),
              0.6f to Color.White.copy(alpha = 0.025f),
              0.72f to Color.Transparent,
              0.83f to Color.Black.copy(alpha = 0.26f),
              0.9f to Color.Black.copy(alpha = 0.12f),
              0.95f to Color.White.copy(alpha = 0.12f),
              1f to Color.Transparent,
              endX = bindingWidth,
            )
            onDrawWithContent {
              // Record once and reuse the native layer for the narrow curved strips.
              coverLayer.record { this@onDrawWithContent.drawContent() }
              clipRect(left = spineWidth) { drawLayer(coverLayer) }
              for (index in 0 until spineEdges.lastIndex) {
                val left = spineEdges[index]
                val right = spineEdges[index + 1]
                val scaleX = (right - left) / (spineWidth / 12f)
                clipRect(left = left, right = right) {
                  withTransform({
                    translate(left - index * spineWidth / 12f * scaleX, 0f)
                    scale(scaleX, 1f, pivot = Offset.Zero)
                  }) {
                    drawLayer(coverLayer)
                  }
                }
              }
              // A broad, soft shoulder and a narrow recessed hinge, lit from the left.
              drawRect(binding, size = Size(bindingWidth, size.height))
              drawLine(
                Color.Black.copy(alpha = 0.15f),
                Offset(size.width, 0f),
                Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
              )
            }
          },
      )
    }
  }
}
