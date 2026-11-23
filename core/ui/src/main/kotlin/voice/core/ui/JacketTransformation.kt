package voice.core.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import coil.size.Size
import coil.size.pxOrElse
import coil.transform.Transformation

/**
 * Draws a book jacket around the cover art once, when the image loads: boards, page edges, and the
 * artwork eased around a rounded spine. The library then shows one cached bitmap per book instead of
 * drawing a dozen layers every frame, which is what made budget phones stutter (GitHub issue #43).
 *
 * All lengths are pixels at the size being drawn; colours are ARGB. The soft drop shadow outside the
 * jacket stays in Compose, where it is blurred once and reused.
 */
internal class JacketTransformation(
  private val depth: Float,
  private val drop: Float,
  private val overhang: Float,
  private val corner: Float,
  private val pageCorner: Float,
  private val grain: Float,
  private val hairline: Float,
  private val bindingMin: Float,
  private val bindingMax: Float,
  private val board: Int,
  private val paper: Int,
  private val paperShade: Int,
  /** Behind the artwork, so transparent covers and books without art still get a solid front. */
  private val artBackground: Int,
) : Transformation {

  override val cacheKey: String =
    "jacket-$depth-$drop-$overhang-$corner-$pageCorner-$grain-$hairline-$bindingMin-$bindingMax-$board-$paper-$paperShade-$artBackground"

  override suspend fun transform(
    input: Bitmap,
    size: Size,
  ): Bitmap {
    val width = size.width.pxOrElse { input.width }
    val height = size.height.pxOrElse { width * 3 / 2 }
    val output = createBitmap(width, height)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Inset paper between equally sized boards.
    paint.color = board
    canvas.drawRoundRect(RectF(depth, drop, width - overhang, height - overhang), corner, corner, paint)

    // A few curved leaves read as paper at thumbnail size, without a thick white stripe.
    val front = width - (depth + overhang)
    val back = width - (overhang + hairline)
    val top = drop + overhang
    val bottom = height - (drop + overhang + hairline)
    val shading = LinearGradient(front, 0f, back, 0f, intArrayOf(paperShade, paper, paperShade), null, Shader.TileMode.CLAMP)
    repeat(3) { page ->
      val leaf = RectF(depth, top, back - page * (back - front) / 3f, bottom)
      paint.shader = shading
      paint.style = Paint.Style.FILL
      canvas.drawRoundRect(leaf, pageCorner, pageCorner, paint)
      paint.shader = null
      paint.color = paperShade
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = grain
      canvas.drawRoundRect(leaf, pageCorner, pageCorner, paint)
    }
    paint.style = Paint.Style.FILL

    // The front board, with a tight shadow onto the pages.
    val art = RectF(0f, 0f, width - (depth + overhang), height - (drop + overhang))
    paint.color = 0x59000000
    paint.maskFilter = BlurMaskFilter(hairline, BlurMaskFilter.Blur.NORMAL)
    canvas.drawRoundRect(RectF(art).apply { offset(hairline, hairline) }, corner, corner, paint)
    paint.maskFilter = null

    canvas.withClip(Path().apply { addRoundRect(art, corner, corner, Path.Direction.CW) }) {
      paint.color = artBackground
      drawRect(art, paint)
      drawArtwork(this, input, art)
    }
    // Upload to the GPU here, on the loader's thread, rather than on the first frame that shows it.
    return output.copy(Bitmap.Config.HARDWARE, false)?.also { output.recycle() } ?: output
  }

  /** The cover, centre-cropped, compressed around the rounded spine and lit from the left. */
  private fun drawArtwork(
    canvas: Canvas,
    input: Bitmap,
    art: RectF,
  ) {
    // No anti-aliasing on the image itself: neighbouring strips meet on exact pixel edges, without seams.
    val image = Paint(Paint.FILTER_BITMAP_FLAG)
    val scale = maxOf(art.width() / input.width, art.height() / input.height)
    val cropLeft = (art.width() - input.width * scale) / 2f
    val cropTop = (art.height() - input.height * scale) / 2f

    // Draws the whole cover, centre-cropped into the artwork's box, through whatever clip is set.
    fun drawCover() {
      canvas.withTranslation(cropLeft, cropTop) {
        scale(scale, scale)
        drawBitmap(input, 0f, 0f, image)
      }
    }

    val bindingWidth = (art.width() * 0.09f).coerceIn(bindingMin, bindingMax)
    val spineWidth = bindingWidth * 0.7f
    canvas.withClip(spineWidth, 0f, art.width(), art.height()) { drawCover() }
    // Twelve strips of the spine's artwork, each squeezed into a narrower band towards the fold.
    val strip = spineWidth / 12f
    var left = 0f
    for (index in 1..12) {
      val t = index / 12f
      val right = spineWidth * t * t * (2f - t)
      val squeeze = (right - left) / strip
      canvas.withClip(left, 0f, right, art.height()) {
        translate(left - (index - 1) * strip * squeeze, 0f)
        scale(squeeze, 1f)
        drawCover()
      }
      left = right
    }

    // A broad, soft shoulder and a narrow recessed hinge, lit from the left.
    val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    shade.shader = LinearGradient(
      0f,
      0f,
      bindingWidth,
      0f,
      intArrayOf(0x7A000000, 0x33000000, 0x1FFFFFFF, 0x06FFFFFF, 0x00000000, 0x42000000, 0x1F000000, 0x1FFFFFFF, 0x00000000),
      floatArrayOf(0f, 0.15f, 0.35f, 0.6f, 0.72f, 0.83f, 0.9f, 0.95f, 1f),
      Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, bindingWidth, art.height(), shade)
    // The edge line straddled the clip before, so only half of it ever showed.
    shade.shader = null
    shade.color = 0x26000000
    canvas.drawRect(art.width() - hairline / 2f, 0f, art.width(), art.height(), shade)
  }
}
