package voice.core.playback.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackIntentHolderTest {

  private lateinit var holder: PlaybackIntentHolder

  @Before
  fun setUp() {
    holder = PlaybackIntentHolder(ApplicationProvider.getApplicationContext())
    holder.clearSleepResumeConfirmation()
  }

  @After
  fun tearDown() {
    holder.clearSleepResumeConfirmation()
  }

  @Test
  fun `external resume needs no confirmation before sleep timer stops playback`() {
    holder.confirmExternalResume(nowMs = 1_000L) shouldBe true
  }

  @Test
  fun `second external resume within window is confirmed`() {
    holder.requireSleepResumeConfirmation()

    holder.confirmExternalResume(nowMs = 1_000L) shouldBe false
    holder.confirmExternalResume(nowMs = 11_000L) shouldBe true
    holder.confirmExternalResume(nowMs = 11_001L) shouldBe true
  }

  @Test
  fun `expired external resume starts a fresh confirmation window`() {
    holder.requireSleepResumeConfirmation()

    holder.confirmExternalResume(nowMs = 1_000L) shouldBe false
    holder.confirmExternalResume(nowMs = 11_001L) shouldBe false
    holder.confirmExternalResume(nowMs = 21_001L) shouldBe true
  }

  @Test
  fun `sleep resume requirement survives a new holder instance`() {
    holder.requireSleepResumeConfirmation()

    holder = PlaybackIntentHolder(ApplicationProvider.getApplicationContext<Context>())

    holder.confirmExternalResume(nowMs = 1_000L) shouldBe false
  }
}
