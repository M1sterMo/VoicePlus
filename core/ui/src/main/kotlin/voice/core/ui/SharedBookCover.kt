package voice.core.ui

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import voice.core.data.BookId

private val emphasizedDecelerate = CubicBezierEasing(0.05F, 0.7F, 0.1F, 1F)
private val emphasizedAccelerate = CubicBezierEasing(0.3F, 0F, 0.8F, 0.15F)

private val bookCoverBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
  val initialArea = initialBounds.width * initialBounds.height
  val targetArea = targetBounds.width * targetBounds.height
  if (targetArea >= initialArea) {
    tween(durationMillis = 400, easing = emphasizedDecelerate)
  } else {
    tween(durationMillis = 250, easing = emphasizedAccelerate)
  }
}

/** The shared-element state of a book's cover, to see whether it is the one flying between screens. */
@Composable
fun SharedTransitionScope.rememberBookCoverState(bookId: BookId): SharedTransitionScope.SharedContentState =
  rememberSharedContentState(key = "book-cover:${bookId.value}")

@Composable
fun Modifier.sharedBookCover(
  bookId: BookId,
  sharedTransitionScope: SharedTransitionScope?,
  state: SharedTransitionScope.SharedContentState? = null,
): Modifier {
  sharedTransitionScope ?: return this
  val animatedVisibilityScope = LocalNavAnimatedContentScope.current
  return with(sharedTransitionScope) {
    sharedElement(
      sharedContentState = state ?: rememberBookCoverState(bookId),
      animatedVisibilityScope = animatedVisibilityScope,
      boundsTransform = bookCoverBoundsTransform,
    )
  }
}
