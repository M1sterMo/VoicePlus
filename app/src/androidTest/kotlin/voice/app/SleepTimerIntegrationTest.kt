package voice.app

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.zacsweers.metro.Inject
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import voice.core.common.rootGraphAs
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.LockscreenSliderMode
import voice.core.data.MarkData
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.FadeOutStore
import voice.core.data.store.LockscreenSliderModeStore
import voice.core.playback.PlayerController
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.session.PlaybackService
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerState
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SleepTimerIntegrationTest {

  @Inject
  lateinit var playerController: PlayerController

  @field:[Inject CurrentBookStore]
  lateinit var currentBookStore: DataStore<BookId?>

  @Inject
  lateinit var bookContentRepo: BookContentRepo

  @Inject
  lateinit var chapterRepo: ChapterRepo

  @Inject
  lateinit var sleepTimer: SleepTimer

  @Inject
  lateinit var playStateManager: PlayStateManager

  @field:[Inject FadeOutStore]
  lateinit var fadeOutStore: DataStore<Duration>

  @field:[Inject LockscreenSliderModeStore]
  lateinit var lockscreenSliderModeStore: DataStore<LockscreenSliderMode>

  @Test
  fun notificationPlayRequiresConfirmationButAppPlayRemainsImmediate() = runTest {
    rootGraphAs<TestGraph>().inject(this@SleepTimerIntegrationTest)
    val bookId = prepareTestBook()
    val oldFadeOut = fadeOutStore.data.first()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val connection = withContext(Dispatchers.Main) {
      MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
        .setConnectionHints(
          Bundle().apply { putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true) },
        )
        .buildAsync()
    }
    val notificationController = withContext(Dispatchers.IO) { connection.get(10, TimeUnit.SECONDS) }
    try {
      fadeOutStore.updateData { 1.seconds }
      playerController.play()
      playStateManager.flow.first { it == PlayStateManager.PlayState.Playing }
      sleepTimer.enable(SleepTimerMode.TimedWithDuration(3.seconds))
      sleepTimer.state.first { it == SleepTimerState.Disabled }
      playStateManager.flow.first { it == PlayStateManager.PlayState.Paused }

      val barrier = withContext(Dispatchers.Main) {
        notificationController.play()
        // An advertised command with no payload reaches the service but has no side effects.
        notificationController.sendCustomCommand(
          SessionCommand("voiceCommandAction", Bundle.EMPTY),
          Bundle.EMPTY,
        )
      }
      withContext(Dispatchers.IO) { barrier.get(10, TimeUnit.SECONDS) }
        .resultCode shouldBe SessionResult.RESULT_ERROR_NOT_SUPPORTED
      playStateManager.playState shouldBe PlayStateManager.PlayState.Paused

      withContext(Dispatchers.Main) { notificationController.play() }
      playStateManager.flow.first { it == PlayStateManager.PlayState.Playing }
      currentBookStore.data.first() shouldBe bookId

      playerController.pauseWithRewind(Duration.ZERO)
      playStateManager.flow.first { it == PlayStateManager.PlayState.Paused }
      playerController.play()
      playStateManager.flow.first { it == PlayStateManager.PlayState.Playing }
    } finally {
      sleepTimer.disable()
      withContext(Dispatchers.Main) {
        notificationController.pause()
        notificationController.release()
      }
      fadeOutStore.updateData { oldFadeOut }
    }
  }

  @Test
  fun testWithTimedMode() = runTest {
    rootGraphAs<TestGraph>().inject(this@SleepTimerIntegrationTest)

    val bookId = prepareTestBook()

    // speed up the tests by using shorter fade out and sleep times
    fadeOutStore.updateData { 1.seconds }

    // play the book and wait for it to start
    playerController.play()
    playStateManager.flow.first { it == PlayStateManager.PlayState.Playing }

    sleepTimer.enable(SleepTimerMode.TimedWithDuration(3.seconds))

    // wait for the sleep timer to trigger
    sleepTimer.state.first { it == SleepTimerState.Disabled }
    playStateManager.flow.first { it == PlayStateManager.PlayState.Paused }

    bookContentRepo.get(bookId)!!.positionInChapter.shouldBeGreaterThan(0)
  }

  @Test
  fun testWithEndOfChapterMode() = runTest {
    rootGraphAs<TestGraph>().inject(this@SleepTimerIntegrationTest)

    val bookId = prepareTestBook()

    // speed up the tests by using shorter fade out and sleep times
    fadeOutStore.updateData { 1.seconds }

    // play the book and wait for it to start
    playerController.play()
    playStateManager.flow.first { it == PlayStateManager.PlayState.Playing }

    sleepTimer.enable(SleepTimerMode.EndOfChapter())

    // wait for the sleep timer to trigger
    playStateManager.flow.first { it == PlayStateManager.PlayState.Paused }
    sleepTimer.state.first { it == SleepTimerState.Disabled }

    // suspend until the position is updated to the end of the chapter
    bookContentRepo.flow(bookId)
      .first { it!!.positionInChapter == 1000L }
  }

  @Test
  fun previousChapterPublishesFreshMetadataWhilePaused() = runTest {
    rootGraphAs<TestGraph>().inject(this@SleepTimerIntegrationTest)
    val bookId = prepareTestBook()
    val chapterId = bookContentRepo.get(bookId)!!.currentChapter
    val previousSliderMode = lockscreenSliderModeStore.data.first()
    lockscreenSliderModeStore.updateData { LockscreenSliderMode.CHAPTER }
    suspend fun awaitStage(
      stage: String,
      ready: suspend () -> Boolean,
    ) = withContext(Dispatchers.Main) {
      try {
        kotlinx.coroutines.withTimeout(10.seconds) {
          while (!ready()) kotlinx.coroutines.delay(25)
        }
      } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        throw AssertionError(
          "$stage: controller=${playerController.currentPlaybackSnapshot()}, live=${playerController.livePlaybackState(bookId)}",
          e,
        )
      }
    }
    try {
      playerController.play()
      // Another playback test can leave the shared play-state observer at Playing. Wait for
      // this fixture's actual controller, not an unrelated player's last published state.
      awaitStage("Playing fixture") { playerController.currentPlaybackSnapshot() == (bookId to true) }
      playerController.pauseWithRewind(Duration.ZERO)
      awaitStage("Paused fixture") { playerController.currentPlaybackSnapshot() == (bookId to false) }
      playerController.setPosition(1_000L, chapterId)
      awaitStage("Seek to second mark") { playerController.livePlaybackState(bookId)?.positionMs == 1_000L }
      playerController.previous()
      awaitStage("Previous mark while paused") { playerController.livePlaybackState(bookId)?.positionMs == 0L }
      withContext(Dispatchers.Main) {
        playerController.livePlaybackState(bookId)!!.positionMs shouldBe 0L
      }
    } finally {
      playerController.pauseWithRewind(Duration.ZERO)
      lockscreenSliderModeStore.updateData { previousSliderMode }
    }
  }

  private suspend fun prepareTestBook(): BookId {
    val audioFile = copyTestAudioFile()

    val bookId = BookId(UUID.randomUUID().toString())
    val chapterId = ChapterId(audioFile.toUri())

    val chapter = Chapter(
      id = chapterId,
      duration = 119210,
      name = "Test Chapter",
      fileLastModified = Instant.EPOCH,
      markData = listOf(
        MarkData(startMs = 0, name = "Mark 1"),
        MarkData(startMs = 1000, name = "Mark 2"),
      ),
    )

    val bookContent = BookContent(
      id = bookId,
      playbackSpeed = 1.0f,
      skipSilence = false,
      isActive = true,
      lastPlayedAt = Instant.now(),
      author = "Test Author",
      name = "Test Audio Book",
      addedAt = Instant.now(),
      chapters = listOf(chapterId),
      currentChapter = chapterId,
      positionInChapter = 0L,
      cover = null,
      gain = 0f,
      genre = null,
      narrator = null,
      series = null,
      part = null,
    )

    bookContentRepo.put(bookContent)
    chapterRepo.put(chapter)

    currentBookStore.updateData { bookId }

    return bookId
  }

  private fun copyTestAudioFile(): File {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val outputFile = File(context.filesDir, "auphonic_chapters_demo.m4a")
    InstrumentationRegistry.getInstrumentation().context.assets
      .open("auphonic_chapters_demo.m4a").use { inputStream ->
        outputFile.outputStream().use { outputStream ->
          inputStream.copyTo(outputStream)
        }
      }
    return outputFile
  }
}
