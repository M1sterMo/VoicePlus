package voice.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Inject
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.common.rootGraphAs
import voice.core.data.MediaButtonClickAction
import voice.core.data.store.MediaButtonDoubleClickHandlerStore
import voice.core.data.store.MediaButtonTripleClickHandlerStore
import voice.core.data.store.SeekTimeStore
import voice.core.playback.di.PlaybackScope
import voice.core.playback.history.ListeningEventRecorder
import voice.core.playback.history.PlaybackIntentHolder
import voice.core.playback.playstate.PositionUpdater
import voice.core.playback.session.LibrarySessionCallback
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidAutoMediaButtonTest {

  @field:[Inject MediaButtonDoubleClickHandlerStore]
  lateinit var doubleClickStore: DataStore<MediaButtonClickAction>

  @field:[Inject MediaButtonTripleClickHandlerStore]
  lateinit var tripleClickStore: DataStore<MediaButtonClickAction>

  @field:[Inject SeekTimeStore]
  lateinit var seekTimeStore: DataStore<Int>

  @Test
  fun carKeysKeepTheirDirectionWhileHeadsetKeysRemainConfigurable() = runBlocking {
    val root = rootGraphAs<TestGraph>()
    root.inject(this@AndroidAutoMediaButtonTest)
    val previousDouble = doubleClickStore.data.first()
    val previousTriple = tripleClickStore.data.first()
    val previousSeek = seekTimeStore.data.first()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val audio = File.createTempFile("car-media-keys-", ".m4a", context.cacheDir)
    InstrumentationRegistry.getInstrumentation().context.assets.open("auphonic_chapters_demo.m4a").use { input ->
      audio.outputStream().use { input.copyTo(it) }
    }

    // Use production dependencies and real playback, but synthetic controller identities: this
    // covers the car callback boundary, not Android Auto's head-unit UI or controller attribution.
    val graph = withContext(Dispatchers.Main) { root.mediaButtonTestGraphFactory.create() }
    val intentHolder = graph.intentHolder
    var session: MediaSession? = null
    try {
      doubleClickStore.updateData { MediaButtonClickAction.SKIP_BACKWARD }
      tripleClickStore.updateData { MediaButtonClickAction.SKIP_FORWARD }
      seekTimeStore.updateData { 10 }
      withContext(Dispatchers.Main) {
        session = MediaSession.Builder(context, graph.player)
          .setId("car-media-buttons-test")
          .setCallback(graph.callback)
          .build()
        graph.player.setMediaItem(MediaItem.fromUri(audio.toURI().toString()))
        graph.player.prepare()
      }
      val activeSession = checkNotNull(session)
      withTimeout(10_000) {
        while (withContext(Dispatchers.Main) { graph.player.playbackState != Player.STATE_READY }) delay(20)
      }

      for (packageName in listOf(
        "com.google.android.projection.gearhead",
        "com.android.car.media",
        "com.android.car.carlauncher",
        "com.android.bluetooth",
      )) {
        val isCar = packageName != "com.android.bluetooth"
        val controller = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
          packageName,
          Process.myPid(),
          Process.myUid(),
          0,
          0,
          true,
          Bundle.EMPTY,
          true,
        )
        withContext(Dispatchers.Main) {
          (activeSession.isAutoCompanionController(controller) || activeSession.isAutomotiveController(controller)) shouldBe isCar
        }
        for (keyCode in listOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
          withContext(Dispatchers.Main) { graph.player.seekTo(60_000L) }
          awaitPosition(graph.player, 60_000L)
          val expected = if ((keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) == isCar) 70_000L else 50_000L
          withContext(Dispatchers.Main) {
            graph.callback.onMediaButtonEvent(activeSession, controller, mediaKey(keyCode)) shouldBe true
          }
          awaitPosition(graph.player, expected)

          withContext(Dispatchers.Main) {
            graph.callback.onMediaButtonEvent(activeSession, controller, mediaKey(keyCode, KeyEvent.ACTION_UP)) shouldBe true
            graph.callback.onMediaButtonEvent(activeSession, controller, mediaKey(keyCode, repeat = 1)) shouldBe true
          }
          delay(100)
          withContext(Dispatchers.Main) { graph.player.currentPosition shouldBe expected }
        }
      }

      val headsetController = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
        "com.android.bluetooth",
        Process.myPid(),
        Process.myUid(),
        0,
        0,
        true,
        Bundle.EMPTY,
        true,
      )
      withContext(Dispatchers.Main) {
        intentHolder.requireSleepResumeConfirmation()
        graph.callback.onMediaButtonEvent(
          activeSession,
          headsetController,
          mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        ) shouldBe true
      }
      delay(500)
      withContext(Dispatchers.Main) { graph.player.playWhenReady shouldBe false }

      withContext(Dispatchers.Main) {
        graph.callback.onMediaButtonEvent(
          activeSession,
          headsetController,
          mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        ) shouldBe true
      }
      delay(500)
      withContext(Dispatchers.Main) { graph.player.playWhenReady shouldBe true }
    } finally {
      withContext(Dispatchers.Main) {
        intentHolder.clearSleepResumeConfirmation()
        graph.positionUpdater.release()
        graph.listeningEventRecorder.release()
        session?.release()
        graph.player.release()
        graph.scope.cancel()
      }
      doubleClickStore.updateData { previousDouble }
      tripleClickStore.updateData { previousTriple }
      seekTimeStore.updateData { previousSeek }
      audio.delete()
    }
    Unit
  }

  private suspend fun awaitPosition(
    player: Player,
    expected: Long,
  ) = withTimeout(10_000) {
    while (withContext(Dispatchers.Main) { player.currentPosition != expected }) delay(20)
  }

  private fun mediaKey(
    keyCode: Int,
    action: Int = KeyEvent.ACTION_DOWN,
    repeat: Int = 0,
  ) = Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(
    Intent.EXTRA_KEY_EVENT,
    KeyEvent(0, 0, action, keyCode, repeat, 0, -1, 0),
  )
}

@GraphExtension(scope = PlaybackScope::class)
interface MediaButtonTestGraph {
  val player: Player
  val callback: LibrarySessionCallback
  val scope: CoroutineScope
  val positionUpdater: PositionUpdater
  val listeningEventRecorder: ListeningEventRecorder
  val intentHolder: PlaybackIntentHolder

  @GraphExtension.Factory
  interface Factory {
    fun create(): MediaButtonTestGraph
  }
}
