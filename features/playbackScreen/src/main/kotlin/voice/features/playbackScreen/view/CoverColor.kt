package voice.features.playbackScreen.view

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import voice.core.ui.ImmutableFile

/**
 * A vibrant (else dominant) colour from the cover artwork: transparent while it is being picked,
 * null without artwork.
 */
@Composable
internal fun rememberCoverColor(cover: ImmutableFile?): Color? {
  val context = LocalContext.current
  return produceState<Color?>(Color.Transparent, cover) {
    value = cover?.let { sampleColor(context, it) }
  }.value
}

private suspend fun sampleColor(
  context: Context,
  cover: ImmutableFile,
): Color? {
  val request = ImageRequest.Builder(context)
    .data(cover.file)
    .size(SAMPLE_SIZE_PX)
    // Palette reads pixels, which hardware bitmaps don't allow.
    .allowHardware(false)
    .build()
  val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap() ?: return null
  return withContext(Dispatchers.Default) {
    val palette = Palette.from(bitmap).generate()
    (palette.vibrantSwatch ?: palette.dominantSwatch)?.let { Color(it.rgb) }
  }
}

private const val SAMPLE_SIZE_PX = 128
