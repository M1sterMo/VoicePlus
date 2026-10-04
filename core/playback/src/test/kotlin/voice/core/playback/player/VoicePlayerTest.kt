package voice.core.playback.player

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ChapterMark
import voice.core.data.LockscreenSecondaryTextMode
import voice.core.data.LockscreenSliderMode
import voice.core.data.MarkData
import voice.core.data.markForPosition
import voice.core.data.repo.BookRepository
import voice.core.logging.api.LogWriter
import voice.core.logging.api.Logger
import voice.core.playback.ChapterMarkChangeNotifier
import voice.core.playback.LivePlaybackState
import voice.core.playback.MemoryDataStore
import voice.core.playback.history.PlaybackIntentHolder
import voice.core.playback.misc.Decibel
import voice.core.playback.misc.VolumeGain
import voice.core.playback.session.LockscreenPlayer
import voice.core.playback.session.MediaId
import voice.core.playback.session.MediaItemProvider
import voice.core.playback.session.search.book
import voice.core.playback.session.toMediaIdOrNull
import voice.core.playback.toLivePlaybackState
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VoicePlayerTest {

  init {
    Logger.install(
      object : LogWriter {
        override fun log(
          severity: Logger.Severity,
          message: String,
          throwable: Throwable?,
        ) {
          println("$severity: $message")
          throwable?.printStackTrace()
        }
      },
    )
  }

  @Test
  fun `accepted gain saves survive playback scope cancellation`() = scope.runTest {
    setMediaItems(listOf(chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null))))
    player.setGain(Decibel(6F), remember = true)
    val releaseBookWrite = CompletableDeferred<Unit>()
    coEvery { bookRepository.updateBook(bookId, any()) } coAnswers {
      releaseBookWrite.await()
      currentBook = currentBook.copy(content = secondArg<(BookContent) -> BookContent>()(currentBook.content))
      bookFlow.value = currentBook
    }
    val save = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { player.setGain(Decibel(3F)) }
    runCurrent()
    save.cancel()
    runCurrent()
    globalVolumeGainStore.data.first() shouldBe 6F
    releaseBookWrite.complete(Unit)
    save.join()
    runCurrent()
    globalVolumeGainStore.data.first() shouldBe null
    currentBook.content.gain shouldBe 3F
  }

  private val seekTimeStore = MemoryDataStore(2)
  private val globalVolumeGainStore = MemoryDataStore<Float?>(null)
  private val volumeGain = VolumeGain(mockk(relaxed = true))
  private val bookFlow = MutableStateFlow<Book?>(null)
  private var periodCount = 1

  private val internalPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
    .setMediaSourceFactory(
      mockk {
        every { createMediaSource(any()) } answers {
          val mediaItem = arg<MediaItem>(0)
          val mediaId = mediaItem.mediaId
          val chapter = currentBook.chapters.single {
            it.id == (mediaId.toMediaIdOrNull()!! as MediaId.Chapter).chapterId
          }
          FakeMediaSource(
            FakeTimeline(
              FakeTimeline.TimelineWindowDefinition.Builder()
                .setPeriodCount(periodCount)
                .setSeekable(true)
                .setDurationUs(TimeUnit.MILLISECONDS.toMicros(chapter.duration))
                .setMediaItem(mediaItem)
                .build(),
            ),
          )
        }
      },
    )
    .build()

  private val scope = TestScope()
  private val mediaItemProvider = MediaItemProvider(
    mockk(),
    mockk(),
    mockk(),
    mockk(),
    mockk { every { overridesForBook(any()) } returns flowOf(emptyList()) },
    mockk(),
    mockk(),
  )
  private val bookId = BookId(UUID.randomUUID().toString())
  private val currentBookStoreId = MemoryDataStore<BookId?>(bookId)
  private lateinit var currentBook: Book
  private val bookRepository = mockk<BookRepository> {
    every { flow(any()) } returns bookFlow
    coEvery { get(bookId) } answers { currentBook }
    coEvery { updateBook(any(), any()) } just Runs
  }
  private val chapterMarkChangeNotifier = ChapterMarkChangeNotifier()
  private val playbackIntentHolder = PlaybackIntentHolder(ApplicationProvider.getApplicationContext())
  private val player = VoicePlayer(
    player = internalPlayer,
    repo = bookRepository,
    currentBookStoreId = currentBookStoreId,
    seekTimeStore = seekTimeStore,
    autoRewindAmountStore = MemoryDataStore(0),
    scope = CoroutineScope(scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler)),
    chapterRepo = mockk {
      coEvery { this@mockk.get(any()) } answers {
        currentBook.chapters.single { it.id == firstArg() }
      }
    },
    mediaItemProvider = mediaItemProvider,
    volumeGain = volumeGain,
    globalVolumeGainStore = globalVolumeGainStore,
    sleepTimer = mockk(relaxed = true),
    intentHolder = playbackIntentHolder,
    listeningEventRecorder = mockk(relaxed = true),
    chapterMarkChangeNotifier = chapterMarkChangeNotifier,
  )

  @After
  fun tearDown() {
    internalPlayer.release()
    playbackIntentHolder.clearSleepResumeConfirmation()
  }

  @Test
  fun `loading books uses remembered boost including zero or falls back to per book gain`() = scope.runTest {
    val chapters = listOf(chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null)))
    currentBook = book(chapters, bookId).let { it.copy(content = it.content.copy(gain = 2F)) }
    bookFlow.value = currentBook
    player.setMediaItem(mediaItemProvider.mediaItem(currentBook))
    volumeGain.gain shouldBe Decibel(2F)

    globalVolumeGainStore.updateData { 6F }
    setMediaItems(chapters)
    volumeGain.gain shouldBe Decibel(6F)

    val otherBookId = BookId(UUID.randomUUID().toString())
    currentBook = currentBook.copy(content = currentBook.content.copy(id = otherBookId, gain = 4F))
    bookFlow.value = currentBook
    coEvery { bookRepository.get(otherBookId) } answers { currentBook }
    currentBookStoreId.updateData { otherBookId }
    player.setMediaItem(mediaItemProvider.mediaItem(currentBook))
    volumeGain.gain shouldBe Decibel(6F)

    globalVolumeGainStore.updateData { 0F }
    player.setMediaItem(mediaItemProvider.mediaItem(currentBook))
    volumeGain.gain shouldBe Decibel(0F)

    globalVolumeGainStore.updateData { null }
    player.setMediaItem(mediaItemProvider.mediaItem(currentBook))
    volumeGain.gain shouldBe Decibel(4F)
  }

  @Test
  fun `global edits preserve book gains and restored settings refresh loaded audio`() = scope.runTest {
    setMediaItems(listOf(chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null))))
    currentBook = currentBook.copy(content = currentBook.content.copy(gain = 4F))
    bookFlow.value = currentBook
    runCurrent()
    volumeGain.gain shouldBe Decibel(4F)

    player.setGain(Decibel(6F), remember = true)
    volumeGain.gain shouldBe Decibel(6F)
    runCurrent()
    globalVolumeGainStore.data.first() shouldBe 6F
    coVerify(exactly = 0) { bookRepository.updateBook(any(), match { it(currentBook.content).gain == 6F }) }

    currentBook = currentBook.copy(content = currentBook.content.copy(gain = 2F))
    bookFlow.value = currentBook
    for (restored in listOf(null, 0F, 6F, null)) {
      globalVolumeGainStore.updateData { restored }
      runCurrent()
      volumeGain.gain shouldBe Decibel(restored ?: 2F)
      player.playWhenReady shouldBe false
      player.currentMediaItemIndex shouldBe 0
    }

    coEvery { bookRepository.updateBook(bookId, any()) } coAnswers {
      currentBook = currentBook.copy(content = secondArg<(BookContent) -> BookContent>()(currentBook.content))
      bookFlow.value = currentBook
    }
    player.setGain(Decibel(3F))
    volumeGain.gain shouldBe Decibel(3F)
    runCurrent()
    coVerify { bookRepository.updateBook(bookId, match { it(currentBook.content).gain == 3F }) }
    globalVolumeGainStore.data.first() shouldBe null
    currentBook.content.gain shouldBe 3F
  }

  @Test
  fun `slow book writes cannot undo newer global mode or audio previews`() = scope.runTest {
    setMediaItems(listOf(chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null))))
    player.setGain(Decibel(6F), remember = true)
    val releaseBookWrite = CompletableDeferred<Unit>()
    coEvery { bookRepository.updateBook(bookId, any()) } coAnswers {
      releaseBookWrite.await()
      currentBook = currentBook.copy(content = secondArg<(BookContent) -> BookContent>()(currentBook.content))
      bookFlow.value = currentBook
    }

    val writes = listOf(3F to false, 8F to true, 9F to true).map { (gain, remember) ->
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { player.setGain(Decibel(gain), remember) }
    }
    runCurrent()
    volumeGain.gain shouldBe Decibel(9F)
    globalVolumeGainStore.data.first() shouldBe 6F

    val nextBook = book(currentBook.chapters, BookId(UUID.randomUUID().toString()))
      .let { it.copy(content = it.content.copy(gain = 2F)) }
    coEvery { bookRepository.get(nextBook.id) } returns nextBook
    every { bookRepository.flow(nextBook.id) } returns flowOf(nextBook)
    currentBookStoreId.updateData { nextBook.id }
    player.setMediaItem(mediaItemProvider.mediaItem(nextBook))
    volumeGain.gain shouldBe Decibel(9F)

    releaseBookWrite.complete(Unit)
    writes.joinAll()
    runCurrent()
    globalVolumeGainStore.data.first() shouldBe 9F
    volumeGain.gain shouldBe Decibel(9F)
    currentBook.content.gain shouldBe 3F
  }

  @Test
  fun `playback parameters persist speed`() = scope.runTest {
    setMediaItems(listOf(chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null))))

    player.playbackParameters = PlaybackParameters(2F)
    advanceUntilIdle()

    coVerify { bookRepository.updateBook(bookId, match { it(currentBook.content).playbackSpeed == 2F }) }
  }

  @Test
  fun `seekForward does not clip`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 19_999, name = null),
          ChapterMark(startMs = 20_000, endMs = 30_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 19_999, name = null),
          ChapterMark(startMs = 20_000, endMs = 30_000, name = null),
        ),
      ),
    )

    seekTimeStore.updateData { 7 }

    player.prepare()
    awaitReady()
    player.shouldHavePosition(0, 0)

    player.seekForward()
    player.shouldHavePosition(0, 7_000)

    player.seekForward()
    player.shouldHavePosition(0, 14_000)

    player.seekForward()
    player.shouldHavePosition(0, 21_000)

    player.seekForward()
    player.shouldHavePosition(0, 28_000)

    player.seekForward()
    player.shouldHavePosition(1, 5_000)

    player.seekForward()
    player.shouldHavePosition(1, 12_000)
  }

  @Test
  fun `seekBack does not clip`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 4_999, name = null),
          ChapterMark(startMs = 5_000, endMs = 12_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 4_999, name = null),
          ChapterMark(startMs = 5_000, endMs = 12_001, name = null),
        ),
      ),
    )

    seekTimeStore.updateData { 5 }

    player.seekTo(1, 12_000)
    player.prepare()
    awaitReady()

    player.shouldHavePosition(1, 12_000)

    player.seekBack()
    player.shouldHavePosition(1, 7_000)

    player.seekBack()
    player.shouldHavePosition(1, 2_000)

    player.seekBack()
    player.shouldHavePosition(0, 9_000)

    player.seekBack()
    player.shouldHavePosition(0, 4_000)

    player.seekBack()
    player.shouldHavePosition(0, 0)
  }

  @Test
  fun `seekToNextMediaItem jumps to chapters`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
      ),
    )

    player.prepare()
    awaitReady()
    player.shouldHavePosition(0, 0)

    player.seekToNextMediaItem()
    player.shouldHavePosition(0, 12_000)

    player.seekToNextMediaItem()
    player.shouldHavePosition(1, 0)

    player.seekToNextMediaItem()
    player.shouldHavePosition(1, 12_000)

    player.seekToNextMediaItem()
    player.shouldHavePosition(1, 12_000)
  }

  @Test
  fun `seekToPreviousMediaItem jumps to chapters`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
      ),
    )

    player.seekTo(1, 18_000)
    player.prepare()
    awaitReady()
    player.shouldHavePosition(1, 18_000)

    player.seekToPreviousMediaItem()
    player.shouldHavePosition(1, 12_000)

    player.seekToPreviousMediaItem()
    player.shouldHavePosition(1, 0)

    player.seekToPreviousMediaItem()
    player.shouldHavePosition(0, 12_000)

    player.seekToPreviousMediaItem()
    player.shouldHavePosition(0, 0)
  }

  @Test
  fun `forceSeekToPrevious jumps to previous chapter when in the 2s window`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
      ),
    )

    player.seekTo(1, 13_000)
    player.prepare()
    awaitReady()
    player.shouldHavePosition(1, 13_000)

    player.forceSeekToPrevious()
    player.shouldHavePosition(1, 0)

    player.seekTo(1, 1_000)
    player.forceSeekToPrevious()
    player.shouldHavePosition(0, 12_000)
  }

  private fun TestScope.setMediaItems(chapters: List<Chapter>) {
    currentBook = book(chapters, bookId)
    bookFlow.value = currentBook
    player.setMediaItem(mediaItemProvider.mediaItem(currentBook))
    runCurrent()
  }

  @Test
  fun `lockscreen slider modes expose and seek the requested range`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 29_999, name = null),
          ChapterMark(startMs = 30_000, endMs = 60_000, name = null),
        ),
      ),
    )
    player.prepare()
    awaitReady()
    player.seekTo(45_000)

    val modeStore = MemoryDataStore(LockscreenSliderMode.AUDIOBOOK)
    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = ApplicationProvider.getApplicationContext(),
      modeStore = modeStore,
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = ChapterMarkChangeNotifier(),
      scope = backgroundScope,
    )
    runCurrent()

    lockscreenPlayer.currentPosition shouldBe 45_000
    lockscreenPlayer.duration shouldBe 60_000

    modeStore.updateData { LockscreenSliderMode.CHAPTER }
    runCurrent()

    lockscreenPlayer.currentPosition shouldBe 15_000
    lockscreenPlayer.duration shouldBe 29_999
    lockscreenPlayer.seekTo(10_000)
    player.shouldHavePosition(0, 40_000)

    modeStore.updateData { LockscreenSliderMode.DISABLED }
    runCurrent()

    lockscreenPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) shouldBe false
    lockscreenPlayer.isCurrentMediaItemSeekable shouldBe false
  }

  @Test
  fun `lockscreen secondary text switches from author to current chapter name`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 29_999, name = "First Section"),
          ChapterMark(startMs = 30_000, endMs = 60_000, name = "Later Section"),
        ),
      ),
    )
    player.prepare()
    awaitReady()
    player.seekTo(45_000)

    val secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR)
    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = ApplicationProvider.getApplicationContext(),
      modeStore = MemoryDataStore(LockscreenSliderMode.CHAPTER),
      secondaryTextModeStore = secondaryTextModeStore,
      chapterMarkChangeNotifier = chapterMarkChangeNotifier,
      scope = backgroundScope,
    )
    runCurrent()

    lockscreenPlayer.mediaMetadata.artist shouldBe currentBook.content.author

    secondaryTextModeStore.updateData { LockscreenSecondaryTextMode.CHAPTER }
    runCurrent()

    lockscreenPlayer.mediaMetadata.artist shouldBe "Later Section"

    player.seekTo(5_000)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    runCurrent()

    lockscreenPlayer.mediaMetadata.artist shouldBe "First Section"
  }

  @Test
  fun `audiobook lockscreen slider aggregates files and maps clamped seeks`() = scope.runTest {
    val chapters = listOf(
      chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null)),
      chapter(ChapterMark(startMs = 0, endMs = 20_000, name = null)),
      chapter(ChapterMark(startMs = 0, endMs = 30_000, name = null)),
    )
    setMediaItems(chapters)
    player.prepare()
    awaitReady()

    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = ApplicationProvider.getApplicationContext(),
      modeStore = MemoryDataStore(LockscreenSliderMode.AUDIOBOOK),
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = ChapterMarkChangeNotifier(),
      scope = backgroundScope,
    )
    runCurrent()

    player.seekTo(1, 5_000)
    player.shouldHavePosition(1, 5_000)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    lockscreenPlayer.currentPosition shouldBe 15_000
    lockscreenPlayer.duration shouldBe 60_000

    lockscreenPlayer.seekTo(9_000)
    player.shouldHavePosition(0, 9_000)

    lockscreenPlayer.seekTo(10_000)
    player.shouldHavePosition(1, 0)

    lockscreenPlayer.seekTo(35_000)
    player.shouldHavePosition(2, 5_000)

    lockscreenPlayer.seekTo(-1)
    player.shouldHavePosition(0, 0)

    lockscreenPlayer.seekTo(100_000)
    player.shouldHavePosition(2, 29_999)
  }

  @Test
  fun `audiobook slider does not remap seeks when current item has multiple periods`() = scope.runTest {
    periodCount = 2
    val chapters = listOf(
      chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null)),
      chapter(ChapterMark(startMs = 0, endMs = 20_000, name = null)),
    )
    setMediaItems(chapters)
    player.prepare()
    awaitReady()
    player.seekTo(1, 5_000)

    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = ApplicationProvider.getApplicationContext(),
      modeStore = MemoryDataStore(LockscreenSliderMode.AUDIOBOOK),
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = ChapterMarkChangeNotifier(),
      scope = backgroundScope,
    )
    runCurrent()

    lockscreenPlayer.currentPosition shouldBe 5_000
    lockscreenPlayer.duration shouldBe 20_000

    lockscreenPlayer.seekTo(15_000)
    player.shouldHavePosition(1, 15_000)
  }

  @Test
  fun `chapter slider does not clamp in-app seek to a later mark in the same file`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 29_999, name = "First Section"),
          ChapterMark(startMs = 30_000, endMs = 60_000, name = "Later Section"),
        ),
      ),
    )
    player.prepare()
    awaitReady()
    player.seekTo(5_000)

    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = ApplicationProvider.getApplicationContext(),
      modeStore = MemoryDataStore(LockscreenSliderMode.CHAPTER),
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = ChapterMarkChangeNotifier(),
      scope = backgroundScope,
    )
    runCurrent()

    lockscreenPlayer.duration shouldBe 29_999
    lockscreenPlayer.seekTo(0, 45_000)
    player.shouldHavePosition(0, 45_000)
  }

  @Test
  fun `audiobook slider metadata converts aggregate position to local chapter position`() = scope.runTest {
    val chapters = listOf(
      chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null)),
      chapter(ChapterMark(startMs = 0, endMs = 20_000, name = null)),
    )
    setMediaItems(chapters)
    player.prepare()
    awaitReady()
    player.seekTo(1, 5_000)

    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = ApplicationProvider.getApplicationContext(),
      modeStore = MemoryDataStore(LockscreenSliderMode.AUDIOBOOK),
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = ChapterMarkChangeNotifier(),
      scope = backgroundScope,
    )
    runCurrent()

    val snapshot = toLivePlaybackState(
      mediaId = lockscreenPlayer.currentMediaItem?.mediaId?.toMediaIdOrNull(),
      bookId = bookId,
      displayedPositionMs = lockscreenPlayer.currentPosition,
      mediaMetadata = lockscreenPlayer.mediaMetadata,
      isPlaying = lockscreenPlayer.isPlaying,
      playbackSpeed = lockscreenPlayer.playbackParameters.speed,
    ) ?: error("expected a live playback snapshot")

    snapshot.chapterId shouldBe chapters[1].id
    snapshot.positionMs shouldBe 5_000
  }

  @Test
  fun `chapter slider metadata converts displayed chapter position to source position`() = scope.runTest {
    val chapters = listOf(
      chapter(
        ChapterMark(startMs = 0, endMs = 29_999, name = null),
        ChapterMark(startMs = 30_000, endMs = 60_000, name = null),
      ),
    )
    setMediaItems(chapters)
    player.prepare()
    awaitReady()
    player.seekTo(45_000)

    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = ApplicationProvider.getApplicationContext(),
      modeStore = MemoryDataStore(LockscreenSliderMode.CHAPTER),
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = ChapterMarkChangeNotifier(),
      scope = backgroundScope,
    )
    runCurrent()

    lockscreenPlayer.currentPosition shouldBe 15_000
    val snapshot = toLivePlaybackState(
      mediaId = lockscreenPlayer.currentMediaItem?.mediaId?.toMediaIdOrNull(),
      bookId = bookId,
      displayedPositionMs = lockscreenPlayer.currentPosition,
      mediaMetadata = lockscreenPlayer.mediaMetadata,
      isPlaying = lockscreenPlayer.isPlaying,
      playbackSpeed = lockscreenPlayer.playbackParameters.speed,
    ) ?: error("expected a live playback snapshot")

    snapshot.chapterId shouldBe chapters.single().id
    snapshot.positionMs shouldBe 45_000
  }

  @Test
  fun `chapter slider previous refreshes paused metadata when displayed position stays zero`() = scope.runTest {
    val previousMark = ChapterMark(startMs = 0, endMs = 29_999, name = "Previous Section")
    val currentMark = ChapterMark(startMs = 30_000, endMs = 60_000, name = "Current Section")
    val chapter = chapter(previousMark, currentMark)
    setMediaItems(listOf(chapter))
    player.prepare()
    awaitReady()
    player.seekTo(currentMark.startMs)

    val lockscreenScope = CoroutineScope(backgroundScope.coroutineContext + Dispatchers.Main.immediate)
    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = ApplicationProvider.getApplicationContext(),
      modeStore = MemoryDataStore(LockscreenSliderMode.CHAPTER),
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = chapterMarkChangeNotifier,
      scope = lockscreenScope,
    )
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    lockscreenPlayer.isPlaying shouldBe false
    lockscreenPlayer.currentPosition shouldBe 0
    val initialSnapshot = lockscreenPlayer.livePlaybackState() ?: error("expected initial live playback state")
    initialSnapshot.positionMs shouldBe currentMark.startMs
    chapter.markForPosition(initialSnapshot.positionMs).name shouldBe currentMark.name

    val metadataChanges = mutableListOf<Pair<Long, LivePlaybackState>>()
    val deliveredEvents = mutableListOf<List<Int>>()
    lockscreenPlayer.addListener(
      object : Player.Listener {
        override fun onEvents(
          player: Player,
          events: Player.Events,
        ) {
          deliveredEvents += List(events.size()) { events.get(it) }
          if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
            player.livePlaybackState()?.let { snapshot ->
              metadataChanges += player.currentPosition to snapshot
            }
          }
        }
      },
    )

    player.forceSeekToPrevious()
    player.shouldHavePosition(0, previousMark.startMs)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    runCurrent()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val (displayedPosition, previousSnapshot) = metadataChanges.singleOrNull()
      ?: error("expected metadata change event, got $deliveredEvents")
    displayedPosition shouldBe 0
    previousSnapshot.positionMs shouldBe previousMark.startMs
    chapter.markForPosition(previousSnapshot.positionMs).name shouldBe previousMark.name
  }

  @Test
  fun `every position discontinuity notifies chapter mark observers`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(ChapterMark(startMs = 0, endMs = 60_000, name = "Only Section")),
      ),
    )
    player.prepare()
    awaitReady()
    player.seekTo(10_000)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    runCurrent()

    var notifications = 0
    backgroundScope.launch {
      chapterMarkChangeNotifier.flow.collect { notifications++ }
    }
    runCurrent()

    player.seekTo(9_000)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    runCurrent()

    notifications shouldBe 1
  }

  @Test
  fun `forceSeekToPrevious jumps to chapter start when outside the 2s window`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
      ),
    )

    player.seekTo(1, 15_000)
    player.prepare()
    awaitReady()
    player.shouldHavePosition(1, 15_000)

    player.forceSeekToPrevious()
    player.shouldHavePosition(1, 12_000)

    player.seekTo(1, 5_000)
    player.forceSeekToPrevious()
    player.shouldHavePosition(1, 0)
  }

  @Test
  fun `external lockscreen play must be repeated after sleep timer stops playback`() = scope.runTest {
    setMediaItems(listOf(chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null))))
    player.prepare()
    awaitReady()
    playbackIntentHolder.requireSleepResumeConfirmation()
    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = ApplicationProvider.getApplicationContext(),
      modeStore = MemoryDataStore(LockscreenSliderMode.CHAPTER),
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = chapterMarkChangeNotifier,
      scope = backgroundScope,
    )
    val controller = mockk<MediaSession.ControllerInfo> {
      every { packageName } returns "com.android.systemui"
    }
    lockscreenPlayer.attachTo(
      mockk {
        every { getControllerForCurrentRequest() } returns controller
      },
    )
    runCurrent()

    lockscreenPlayer.play()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    player.playWhenReady shouldBe false

    lockscreenPlayer.play()
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    player.playWhenReady shouldBe true
  }

  @Test
  fun `notification controller using our package still requires sleep confirmation`() = scope.runTest {
    setMediaItems(listOf(chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null))))
    player.prepare()
    awaitReady()
    playbackIntentHolder.requireSleepResumeConfirmation()
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = context,
      modeStore = MemoryDataStore(LockscreenSliderMode.CHAPTER),
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = chapterMarkChangeNotifier,
      scope = backgroundScope,
    )
    val controller = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
      context.packageName,
      android.os.Process.myPid(),
      android.os.Process.myUid(),
      1,
      1,
      true,
      android.os.Bundle().apply {
        putBoolean(androidx.media3.session.MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true)
      },
      true,
    )
    val session = MediaSession.Builder(context, lockscreenPlayer).build()
    try {
      session.isMediaNotificationController(controller) shouldBe true
      lockscreenPlayer.attachTo(
        mockk {
          every { getControllerForCurrentRequest() } returns controller
          every { isMediaNotificationController(controller) } answers { session.isMediaNotificationController(controller) }
        },
      )
      runCurrent()

      lockscreenPlayer.play()
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      player.playWhenReady shouldBe false

      lockscreenPlayer.play()
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      player.playWhenReady shouldBe true

      player.seekTo(1_000)
      player.shouldHavePosition(0, 1_000)
      lockscreenPlayer.pause()
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      player.playWhenReady shouldBe false
      lockscreenPlayer.play()
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      player.playWhenReady shouldBe true
    } finally {
      session.release()
    }
  }

  @Test
  fun `VoicePlus play resumes immediately after sleep timer stops playback`() = scope.runTest {
    setMediaItems(listOf(chapter(ChapterMark(startMs = 0, endMs = 10_000, name = null))))
    player.prepare()
    awaitReady()
    playbackIntentHolder.requireSleepResumeConfirmation()
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val lockscreenPlayer = LockscreenPlayer(
      voicePlayer = player,
      intentHolder = playbackIntentHolder,
      context = context,
      modeStore = MemoryDataStore(LockscreenSliderMode.CHAPTER),
      secondaryTextModeStore = MemoryDataStore(LockscreenSecondaryTextMode.AUTHOR),
      chapterMarkChangeNotifier = chapterMarkChangeNotifier,
      scope = backgroundScope,
    )
    val controller = mockk<MediaSession.ControllerInfo> {
      every { packageName } returns context.packageName
    }
    lockscreenPlayer.attachTo(
      mockk {
        every { getControllerForCurrentRequest() } returns controller
        every { isMediaNotificationController(controller) } returns false
      },
    )
    runCurrent()

    lockscreenPlayer.play()
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    player.playWhenReady shouldBe true
    playbackIntentHolder.confirmExternalResume(nowMs = 1_000L) shouldBe true
  }

  private fun chapter(vararg marks: ChapterMark): Chapter {
    return Chapter(
      id = ChapterId(UUID.randomUUID().toString()),
      name = "chapter",
      duration = marks.maxOf { it.endMs },
      fileLastModified = Instant.EPOCH,
      markData = marks.map {
        MarkData(it.startMs, it.name ?: "mark ")
      },
    )
  }

  private fun awaitReady() {
    TestPlayerRunHelper.runUntilPlaybackState(internalPlayer, Player.STATE_READY)
  }

  @IgnorableReturnValue
  private fun Player.shouldHavePosition(
    currentMediaItemIndex: Int,
    currentPosition: Long,
  ): Player {
    scope.advanceUntilIdle()
    this should havePosition(currentMediaItemIndex, currentPosition)
    return this
  }
}

private fun Player.livePlaybackState() = toLivePlaybackState(
  mediaId = currentMediaItem?.mediaId?.toMediaIdOrNull(),
  bookId = null,
  displayedPositionMs = currentPosition,
  mediaMetadata = mediaMetadata,
  isPlaying = isPlaying,
  playbackSpeed = playbackParameters.speed,
)

private fun havePosition(
  currentMediaItemIndex: Int,
  currentPosition: Long,
) = object : Matcher<Player> {
  override fun test(value: Player): MatcherResult {
    val actualCurrentMediaItemIndex = value.currentMediaItemIndex
    val actualCurrentPosition = value.currentPosition
    return MatcherResult(
      passed = actualCurrentMediaItemIndex == currentMediaItemIndex && actualCurrentPosition == currentPosition,
      failureMessageFn = {
        "position was ($actualCurrentMediaItemIndex,$actualCurrentPosition) but we expected ($currentMediaItemIndex,$currentPosition)"
      },
      negatedFailureMessageFn = { "position should not be ($actualCurrentMediaItemIndex,$actualCurrentPosition)" },
    )
  }
}
