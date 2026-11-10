package voice.features.playbackScreen.view

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.ui.BookshelfCover
import voice.core.ui.ImmutableFile
import voice.core.ui.sharedBookCover
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

/**
 * @param glow a soft halo behind the cover in a colour picked from the artwork (the theme's primary
 * without artwork), rising while playing.
 */
@Composable
internal fun Cover(
  bookId: BookId,
  sharedTransitionScope: SharedTransitionScope?,
  onDoubleClick: () -> Unit,
  cover: ImmutableFile?,
  bookJacket: Boolean,
  playing: Boolean,
  glow: Boolean = true,
) {
  val glowStrength by animateFloatAsState(
    targetValue = when {
      !glow -> 0f
      playing -> 1f
      else -> 0.4f
    },
    animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
    label = "coverGlow",
  )
  val glowColor = rememberCoverColor(cover.takeIf { glow }) ?: MaterialTheme.colorScheme.primary
  // The jacket's corner radius matches BookshelfCover's.
  val shape = RoundedCornerShape(if (bookJacket) 3.dp else 20.dp)
  val glowAndDoubleTap = Modifier
    // A coloured drop shadow: the platform elevation shadow is too faint to carry a colour.
    .dropShadow(
      shape,
      Shadow(radius = 48.dp, spread = 6.dp, color = glowColor.copy(alpha = 0.85f * glowStrength), offset = DpOffset(0.dp, 16.dp)),
    )
    .pointerInput(Unit) {
      detectTapGestures(
        onDoubleTap = {
          onDoubleClick()
        },
      )
    }
  if (bookJacket) {
    BookshelfCover(
      bookId = bookId,
      cover = cover,
      modifier = glowAndDoubleTap,
      sharedTransitionScope = sharedTransitionScope,
    )
  } else {
    AsyncImage(
      modifier = Modifier
        .fillMaxSize()
        // After the shared bounds, so the glow travels with the cover between screens.
        .sharedBookCover(bookId, sharedTransitionScope)
        .then(glowAndDoubleTap)
        .clip(shape),
      contentScale = ContentScale.Crop,
      model = cover?.file,
      placeholder = painterResource(id = UiR.drawable.album_art),
      error = painterResource(id = UiR.drawable.album_art),
      contentDescription = stringResource(id = StringsR.string.cover),
    )
  }
}
