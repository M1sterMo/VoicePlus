package voice.core.playback.session

import android.os.Bundle
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.playback.misc.Decibel

@RunWith(RobolectricTestRunner::class)
class CustomCommandTest {

  @Test
  fun `session command carries its action`() {
    val command = CustomCommand.ForceSeekToNext

    CustomCommand.parse(command.toSessionCommand(), Bundle.EMPTY) shouldBe command
  }

  @Test
  fun `gain command carries global mode and defaults to per book`() {
    for (remember in listOf(false, true)) {
      val command = CustomCommand.SetGain(Decibel(6F), remember)
      CustomCommand.parse(command.toSessionCommand(), Bundle.EMPTY) shouldBe command
    }
  }
}
