package voice.core.playback.session

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import voice.core.data.MediaButtonClickAction
import voice.core.playback.MemoryDataStore
import voice.core.playback.history.PlaybackIntentHolder
import voice.core.playback.player.VoicePlayer

@RunWith(RobolectricTestRunner::class)
class LibrarySessionCallbackTest {

  @After
  fun clearSleepResumeConfirmation() {
    newIntentHolder().clearSleepResumeConfirmation()
  }

  @Test
  fun `Android Auto next and previous ignore reversed headset actions`() = runTest {
    val player = mockk<VoicePlayer>(relaxed = true)
    val callback = mediaButtonCallback(player, this)
    val controller = mockk<MediaSession.ControllerInfo>()
    val session = mockk<MediaSession>(relaxed = true) {
      every { isAutoCompanionController(controller) } returns true
    }

    callback.onMediaButtonEvent(session, controller, mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)) shouldBe true
    advanceUntilIdle()
    verify(exactly = 1) { player.seekForward() }
    verify(exactly = 0) { player.seekBack() }

    callback.onMediaButtonEvent(session, controller, mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)) shouldBe true
    advanceUntilIdle()
    verify(exactly = 1) { player.seekBack() }
    verify(exactly = 1) { player.seekForward() }
  }

  @Test
  fun `Android Automotive next and previous ignore reversed headset actions`() = runTest {
    val player = mockk<VoicePlayer>(relaxed = true)
    val callback = mediaButtonCallback(player, this)
    val controller = mockk<MediaSession.ControllerInfo>()
    val session = mockk<MediaSession>(relaxed = true) {
      every { isAutomotiveController(controller) } returns true
    }

    callback.onMediaButtonEvent(session, controller, mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)) shouldBe true
    advanceUntilIdle()
    verify(exactly = 1) { player.seekForward() }
    verify(exactly = 0) { player.seekBack() }

    callback.onMediaButtonEvent(session, controller, mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)) shouldBe true
    advanceUntilIdle()
    verify(exactly = 1) { player.seekBack() }
    verify(exactly = 1) { player.seekForward() }
  }

  @Test
  fun `headset next and previous retain configured actions`() = runTest {
    val player = mockk<VoicePlayer>(relaxed = true)
    val callback = mediaButtonCallback(player, this)
    val session = mockk<MediaSession>(relaxed = true)
    val controller = mockk<MediaSession.ControllerInfo>()

    callback.onMediaButtonEvent(session, controller, mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)) shouldBe true
    advanceUntilIdle()
    verify(exactly = 1) { player.seekBack() }
    verify(exactly = 0) { player.seekForward() }

    callback.onMediaButtonEvent(session, controller, mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)) shouldBe true
    advanceUntilIdle()
    verify(exactly = 1) { player.seekForward() }
    verify(exactly = 1) { player.seekBack() }
  }

  @Test
  fun `car key up and repeat events are consumed without seeking`() = runTest {
    val player = mockk<VoicePlayer>(relaxed = true)
    val callback = mediaButtonCallback(player, this)
    val controller = mockk<MediaSession.ControllerInfo>()
    val session = mockk<MediaSession>(relaxed = true) {
      every { isAutoCompanionController(controller) } returns true
    }

    for (keyCode in listOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
      callback.onMediaButtonEvent(session, controller, mediaKey(keyCode, KeyEvent.ACTION_UP)) shouldBe true
      callback.onMediaButtonEvent(session, controller, mediaKey(keyCode, repeat = 1)) shouldBe true
    }
    advanceUntilIdle()
    verify(exactly = 0) { player.seekForward() }
    verify(exactly = 0) { player.seekBack() }
  }

  private fun mediaButtonCallback(
    player: VoicePlayer,
    scope: CoroutineScope,
    intentHolder: PlaybackIntentHolder = newIntentHolder(),
    context: Context = ApplicationProvider.getApplicationContext(),
  ) = LibrarySessionCallback(
    mediaItemProvider = mockk(),
    scope = scope,
    player = player,
    bookSearchParser = mockk(),
    bookSearchHandler = mockk(),
    currentBookStoreId = mockk(),
    bookRepository = mockk(),
    doubleClickHandlerStore = MemoryDataStore(MediaButtonClickAction.SKIP_BACKWARD),
    tripleClickHandlerStore = MemoryDataStore(MediaButtonClickAction.SKIP_FORWARD),
    bookmarkRepo = mockk(),
    intentHolder = intentHolder,
    positionUpdater = mockk(),
    context = context,
  )

  private fun newIntentHolder() = PlaybackIntentHolder(ApplicationProvider.getApplicationContext()).apply {
    clearSleepResumeConfirmation()
  }

  private fun mediaKey(
    keyCode: Int,
    action: Int = KeyEvent.ACTION_DOWN,
    repeat: Int = 0,
  ) = Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(
    Intent.EXTRA_KEY_EVENT,
    KeyEvent(0, 0, action, keyCode, repeat, 0, -1, 0),
  )

  @Test
  fun `lockscreen timed skip commands seek back and forward`() {
    val player = mockk<VoicePlayer>(relaxed = true)
    val callback = LibrarySessionCallback(
      mediaItemProvider = mockk(),
      scope = mockk(),
      player = player,
      bookSearchParser = mockk(),
      bookSearchHandler = mockk(),
      currentBookStoreId = mockk(),
      bookRepository = mockk(),
      doubleClickHandlerStore = mockk(),
      tripleClickHandlerStore = mockk(),
      bookmarkRepo = mockk(),
      intentHolder = mockk(),
      positionUpdater = mockk(),
      context = mockk(),
    )

    val result = callback.onCustomCommand(
      session = mockk<MediaSession>(),
      controller = mockk(),
      customCommand = CustomCommand.SeekBack.toSessionCommand(),
      args = Bundle.EMPTY,
    ).get()

    result.resultCode shouldBe SessionResult.RESULT_SUCCESS
    verify(exactly = 1) { player.seekBack() }

    val forwardResult = callback.onCustomCommand(
      session = mockk<MediaSession>(),
      controller = mockk(),
      customCommand = CustomCommand.SeekForward.toSessionCommand(),
      args = Bundle.EMPTY,
    ).get()

    forwardResult.resultCode shouldBe SessionResult.RESULT_SUCCESS
    verify(exactly = 1) { player.seekForward() }
  }

  @Test
  fun `only physical alphabetic devices bypass configured headset actions`() {
    fun device(
      isVirtual: Boolean,
      keyboardType: Int,
    ) = mockk<InputDevice> {
      every { this@mockk.isVirtual } returns isVirtual
      every { this@mockk.keyboardType } returns keyboardType
    }

    device(isVirtual = true, InputDevice.KEYBOARD_TYPE_ALPHABETIC)
      .isPhysicalAlphabeticKeyboard() shouldBe false
    device(isVirtual = false, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
      .isPhysicalAlphabeticKeyboard() shouldBe false
    device(isVirtual = false, InputDevice.KEYBOARD_TYPE_ALPHABETIC)
      .isPhysicalAlphabeticKeyboard() shouldBe true
  }

  @Test
  fun `pause with rewind marks sleep pauses then seeks the underlying position`() {
    val player = mockk<VoicePlayer>(relaxed = true)
    every { player.pause() } just Runs
    every { player.currentPosition } returns 50_000L
    every { player.seekTo(any<Long>()) } just Runs
    val intentHolder = newIntentHolder()

    pauseWithRewind(player, intentHolder, rewindMs = 7_500L)

    intentHolder.stoppedBySleepTimer shouldBe true
    intentHolder.confirmExternalResume(nowMs = 1_000L) shouldBe false
    verifySequence {
      player.pause()
      player.currentPosition
      player.seekTo(42_500L)
    }
  }

  @Test
  fun `headset play must be repeated after sleep timer stops playback`() = runTest {
    val player = mockk<VoicePlayer>(relaxed = true) {
      every { isPlaying } returns false
      every { currentMediaItem } returns mockk()
    }
    val intentHolder = newIntentHolder().apply { requireSleepResumeConfirmation() }
    val callback = mediaButtonCallback(player, this, intentHolder)
    val session = mockk<MediaSession>(relaxed = true)
    val controller = mockk<MediaSession.ControllerInfo>()

    callback.onMediaButtonEvent(session, controller, mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) shouldBe true
    advanceUntilIdle()
    verify(exactly = 0) { player.play() }

    callback.onMediaButtonEvent(session, controller, mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) shouldBe true
    advanceUntilIdle()
    verify(exactly = 1) { player.play() }
  }
}
