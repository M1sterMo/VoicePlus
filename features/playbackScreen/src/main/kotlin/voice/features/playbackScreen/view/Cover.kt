package voice.features.playbackScreen.view

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
  // Both are read only while drawing the shadow, so the slow spring doesn't recompose the cover.
  val glowStrength = animateFloatAsState(
    targetValue = when {
      !glow -> 0f
      playing -> 1f
      else -> 0.4f
    },
    animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
    label = "coverGlow",
  )
  // Fades in from transparent, so the artwork's colour doesn't replace a fallback as it arrives.
  val glowColor = animateColorAsState(
    targetValue = rememberCoverColor(cover.takeIf { glow }) ?: MaterialTheme.colorScheme.primary,
    label = "coverGlowColor",
  )
  // The jacket's corner radius matches BookshelfCover's.
  val shape = RoundedCornerShape(if (bookJacket) 3.dp else 20.dp)
  val glowAndDoubleTap = Modifier
    // A coloured drop shadow: the platform elevation shadow is too faint to carry a colour.
    .dropShadow(shape) {
      radius = 48.dp.toPx()
      spread = 6.dp.toPx()
      offset = Offset(0f, 16.dp.toPx())
      color = glowColor.value
      alpha = 0.85f * glowStrength.value
    }
    .pointerInput(Unit) {
      detectTapGestures(
        onDoubleTap = {
          onDoubleClick()
        },
      )
    }
  if (bookJacket) {
    val coverLabel = stringResource(id = StringsR.string.cover)
    BookshelfCover(
      bookId = bookId,
      cover = cover,
      // The whole jacket is the shared element, so the glow travels with it like the square cover's.
      modifier = Modifier
        .sharedBookCover(bookId, sharedTransitionScope)
        .then(glowAndDoubleTap)
        .semantics {
          contentDescription = coverLabel
          role = Role.Image
        },
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
