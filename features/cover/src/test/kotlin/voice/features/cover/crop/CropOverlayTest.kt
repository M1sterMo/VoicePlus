package voice.features.cover.crop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.size.Size
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CropOverlayTest {
  @Test
  fun `square and book crops fit portrait square and landscape sources`() {
    listOf(300 to 450, 400 to 400, 600 to 300).forEach { (width, height) ->
      val overlay = overlay(width, height)
      listOf(1f, 2f / 3f, 1f).forEach { ratio ->
        overlay.aspectRatio = ratio
        assertCrop(overlay.selectedRect, width, height, ratio)
      }
    }
  }

  @Test
  fun `resizing and dragging a book crop preserve ratio and bounds`() {
    val overlay = overlay(300, 450).apply { aspectRatio = 2f / 3f }
    touch(overlay, MotionEvent.ACTION_DOWN, 150f, 0f)
    touch(overlay, MotionEvent.ACTION_MOVE, 150f, 60f)
    touch(overlay, MotionEvent.ACTION_UP, 150f, 60f)
    assertTrue(overlay.selectedRect.width() < 300)
    assertCrop(overlay.selectedRect, 300, 450, 2f / 3f)
    val rect = overlay.selectedRect
    touch(overlay, MotionEvent.ACTION_DOWN, rect.exactCenterX(), rect.exactCenterY())
    touch(overlay, MotionEvent.ACTION_MOVE, 1_000f, 1_000f)
    touch(overlay, MotionEvent.ACTION_UP, 1_000f, 1_000f)
    assertCrop(overlay.selectedRect, 300, 450, 2f / 3f)
  }

  @Test
  fun `transform maps portrait selection to source pixels without stretching`() = runTest {
    val source = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
    source.eraseColor(Color.BLUE)
    source.setPixel(100, 50, Color.RED)
    val crop = CropTransformation(Rect(50, 25, 150, 175), 200, 200)
    val result = crop.transform(source, Size.ORIGINAL)
    result.width shouldBe 200
    result.height shouldBe 300
    result.getPixel(0, 0) shouldBe Color.RED
    result.getPixel(100, 100) shouldBe Color.BLUE
    assertTrue(crop.cacheKey != CropTransformation(Rect(0, 0, 200, 200), 200, 200).cacheKey)
  }

  private fun overlay(
    width: Int,
    height: Int,
  ) = CropOverlay(ApplicationProvider.getApplicationContext()).apply {
    selectionOn = true
    measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    layout(0, 0, width, height)
  }

  private fun touch(
    overlay: CropOverlay,
    action: Int,
    x: Float,
    y: Float,
  ) {
    val event = MotionEvent.obtain(0, 10, action, x, y, 0)
    overlay.onTouchEvent(event)
    event.recycle()
  }

  private fun assertCrop(
    rect: Rect,
    width: Int,
    height: Int,
    ratio: Float,
  ) {
    assertTrue(rect.width() > 0 && rect.height() > 0)
    assertTrue(rect.left >= 0 && rect.top >= 0 && rect.right <= width && rect.bottom <= height)
    assertTrue(abs(rect.width() - rect.height() * ratio) <= 1f)
  }
}
