package voice.features.playbackScreen.view

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.ui.BookshelfCover
import voice.core.ui.ImmutableFile
import voice.core.ui.sharedBookCover
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@Composable
internal fun Cover(
  bookId: BookId,
  sharedTransitionScope: SharedTransitionScope?,
  onDoubleClick: () -> Unit,
  cover: ImmutableFile?,
  bookJacket: Boolean,
) {
  val doubleTap = Modifier.pointerInput(Unit) {
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
      modifier = doubleTap,
      sharedTransitionScope = sharedTransitionScope,
    )
  } else {
    AsyncImage(
      modifier = Modifier
        .fillMaxSize()
        .sharedBookCover(bookId, sharedTransitionScope)
        .then(doubleTap)
        .clip(RoundedCornerShape(20.dp)),
      contentScale = ContentScale.Crop,
      model = cover?.file,
      placeholder = painterResource(id = UiR.drawable.album_art),
      error = painterResource(id = UiR.drawable.album_art),
      contentDescription = stringResource(id = StringsR.string.cover),
    )
  }
}
