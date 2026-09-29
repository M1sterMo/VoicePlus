package voice.features.bookOverview.shelves

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun rememberShelfDragState(
  grid: LazyGridState,
  onMove: (String, String) -> Unit,
): ShelfDragState {
  val scope = rememberCoroutineScope()
  val move by rememberUpdatedState(onMove)
  val haptic = LocalHapticFeedback.current
  val drag =
    remember(grid, scope, haptic) {
      ShelfDragState(grid, scope, { from, to -> move(from, to) }) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
  val edge = with(LocalDensity.current) { 48.dp.toPx() }
  LaunchedEffect(drag.dragged, edge) {
    if (drag.dragged != null) {
      var previous = withFrameMillis { it }
      while (true) {
        val now = withFrameMillis { it }
        drag.scroll(edge, (now - previous).coerceAtMost(32) / 1000f)
        previous = now
      }
    }
  }
  return drag
}

/** Draft-only grid drag. Persistence happens once, when Done is pressed. */
internal class ShelfDragState(
  private val grid: LazyGridState,
  private val scope: CoroutineScope,
  private val move: (String, String) -> Unit,
  private val haptic: () -> Unit,
) {
  var dragged: String? by mutableStateOf(null)
    private set
  var returning: String? by mutableStateOf(null)
    private set
  val settle = Animatable(Offset.Zero, Offset.VectorConverter)
  private var settleJob: Job? = null
  private var position by mutableStateOf(Offset.Zero)
  private var expectedIndex: Int? = null
  private val item get() = grid.layoutInfo.visibleItemsInfo.find { it.key == dragged }
  val offset: Offset get() = item?.let { position - Offset(it.offset.x.toFloat(), it.offset.y.toFloat()) } ?: Offset.Zero

  fun start(id: String) {
    val item = grid.layoutInfo.visibleItemsInfo.find { it.key == id } ?: return
    settleJob?.cancel()
    returning = null
    dragged = id
    position = Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
    expectedIndex = null
    haptic()
  }

  fun drag(delta: Offset) {
    position += delta
    val held = item ?: return
    if (expectedIndex != null && held.index != expectedIndex) return
    expectedIndex = null
    val centre = position + Offset(held.size.width / 2f, held.size.height / 2f)
    val target = grid.layoutInfo.visibleItemsInfo.find {
      it.key != held.key &&
        centre.x >= it.offset.x && centre.x < it.offset.x + it.size.width && centre.y >= it.offset.y &&
        centre.y < it.offset.y + it.size.height
    }
      ?: return
    grid.requestScrollToItem(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
    expectedIndex = target.index
    move(held.key as String, target.key as String)
    haptic()
  }

  suspend fun scroll(
    edge: Float,
    seconds: Float,
  ) {
    val held = item ?: return
    val top = grid.layoutInfo.viewportStartOffset + edge
    val bottom = grid.layoutInfo.viewportEndOffset - edge
    val speed = when {
      position.y < top -> -((top - position.y) / edge).coerceIn(0f, 1f)
      position.y + held.size.height > bottom -> ((position.y + held.size.height - bottom) / edge).coerceIn(0f, 1f)
      else -> 0f
    }
    if (speed != 0f) grid.scrollBy(speed * edge * 10 * seconds)
    drag(Offset.Zero)
  }

  fun stop() {
    val id = dragged ?: return
    val distance = offset
    returning = id
    dragged = null
    expectedIndex = null
    settleJob = scope.launch {
      settle.snapTo(distance)
      settle.animateTo(Offset.Zero, spring(dampingRatio = 1f, stiffness = 400f))
      returning = null
    }
  }
}
