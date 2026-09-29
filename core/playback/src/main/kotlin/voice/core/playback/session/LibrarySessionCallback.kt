package voice.core.playback.session

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.ListeningEventType
import voice.core.data.MediaButtonClickAction
import voice.core.data.repo.BookRepository
import voice.core.data.repo.BookmarkRepo
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.MediaButtonDoubleClickHandlerStore
import voice.core.data.store.MediaButtonTripleClickHandlerStore
import voice.core.logging.api.Logger
import voice.core.playback.history.PlaybackIntentHolder
import voice.core.playback.player.VoicePlayer
import voice.core.playback.playstate.PositionUpdater
import voice.core.playback.session.search.BookSearchHandler
import voice.core.playback.session.search.BookSearchParser
import voice.core.strings.R as StringsR

@Inject
class LibrarySessionCallback(
  private val mediaItemProvider: MediaItemProvider,
  private val scope: CoroutineScope,
  private val player: VoicePlayer,
  private val bookSearchParser: BookSearchParser,
  private val bookSearchHandler: BookSearchHandler,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
  private val bookRepository: BookRepository,
  @MediaButtonDoubleClickHandlerStore
  private val doubleClickHandlerStore: DataStore<MediaButtonClickAction>,
  @MediaButtonTripleClickHandlerStore
  private val tripleClickHandlerStore: DataStore<MediaButtonClickAction>,
  private val bookmarkRepo: BookmarkRepo,
  private val intentHolder: PlaybackIntentHolder,
  private val positionUpdater: PositionUpdater,
  private val context: Context,
) : MediaLibrarySession.Callback {

  private var mediaButtonClickCount = 0
  private var mediaButtonClickJob: Job? = null

  override fun onAddMediaItems(
    mediaSession: MediaSession,
    controller: ControllerInfo,
    mediaItems: MutableList<MediaItem>,
  ): ListenableFuture<List<MediaItem>> {
    Logger.d("onAddMediaItems")
    return scope.future {
      mediaItems.map { item ->
        mediaItemProvider.item(item.mediaId) ?: item
      }
    }
  }

  override fun onSetMediaItems(
    mediaSession: MediaSession,
    controller: ControllerInfo,
    mediaItems: MutableList<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
  ): ListenableFuture<MediaItemsWithStartPosition> {
    Logger.d("onSetMediaItems(mediaItems.size=${mediaItems.size}, startIndex=$startIndex, startPosition=$startPositionMs)")
    val item = mediaItems.singleOrNull()
    return if (startIndex == C.INDEX_UNSET && startPositionMs == C.TIME_UNSET && item != null) {
      scope.future {
        onSetMediaItemsForSingleItem(item)
          ?: super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs).await()
      }
    } else {
      super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
    }
  }

  private suspend fun onSetMediaItemsForSingleItem(item: MediaItem): MediaItemsWithStartPosition? {
    val searchQuery = item.requestMetadata.searchQuery
    return if (searchQuery != null) {
      val search = bookSearchParser.parse(searchQuery, item.requestMetadata.extras)
      val searchResult = bookSearchHandler.handle(search) ?: return null
      currentBookStoreId.updateData { searchResult.id }
      mediaItemProvider.mediaItemsWithStartPosition(searchResult)
    } else {
      (item.mediaId.toMediaIdOrNull() as? MediaId.Book)?.let { bookId ->
        currentBookStoreId.updateData { bookId.id }
      }
      mediaItemProvider.mediaItemsWithStartPosition(item.mediaId)
    }
  }

  override fun onGetLibraryRoot(
    session: MediaLibrarySession,
    browser: ControllerInfo,
    params: LibraryParams?,
  ): ListenableFuture<LibraryResult<MediaItem>> {
    val mediaItem = if (params?.isRecent == true) {
      mediaItemProvider.recent() ?: mediaItemProvider.root()
    } else {
      mediaItemProvider.root()
    }
    Logger.d("onGetLibraryRoot(isRecent=${params?.isRecent == true}). Returning ${mediaItem.mediaId}")
    return Futures.immediateFuture(LibraryResult.ofItem(mediaItem, params))
  }

  override fun onGetItem(
    session: MediaLibrarySession,
    browser: ControllerInfo,
    mediaId: String,
  ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
    Logger.d("onGetItem(mediaId=$mediaId)")
    val item = mediaItemProvider.item(mediaId)
    if (item != null) {
      LibraryResult.ofItem(item, null)
    } else {
      LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }
  }

  override fun onGetChildren(
    session: MediaLibrarySession,
    browser: ControllerInfo,
    parentId: String,
    page: Int,
    pageSize: Int,
    params: LibraryParams?,
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
    Logger.d("onGetChildren for $parentId")
    val children = mediaItemProvider.children(parentId)
    if (children != null) {
      LibraryResult.ofItemList(children, params)
    } else {
      LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }
  }

  override fun onPlaybackResumption(
    mediaSession: MediaSession,
    controller: ControllerInfo,
    isForPlayback: Boolean,
  ): ListenableFuture<MediaItemsWithStartPosition> {
    Logger.d("onPlaybackResumption")
    return scope.future {
      val currentBook = currentBook()
      if (currentBook != null) {
        mediaItemProvider.mediaItemsWithStartPosition(currentBook)
      } else {
        throw UnsupportedOperationException()
      }
    }
  }

  private suspend fun currentBook(): Book? {
    val bookId = currentBookStoreId.data.first() ?: return null
    return bookRepository.get(bookId)
  }

  override fun onConnect(
    session: MediaSession,
    controller: ControllerInfo,
  ): ConnectionResult {
    Logger.d("onConnect to ${controller.packageName}")

    if (player.playbackState == Player.STATE_IDLE &&
      controller.packageName == "com.google.android.projection.gearhead"
    ) {
      Logger.d("onConnect to ${controller.packageName} and player is idle.")
      Logger.d("Preparing current book so it shows up as recently played")
      scope.launch {
        prepareCurrentBook()
      }
    }

    val connectionResult = super.onConnect(session, controller)
    val sessionCommands = connectionResult.availableSessionCommands
      .buildUpon()
      .add(SessionCommand(CustomCommand.CUSTOM_COMMAND_ACTION, Bundle.EMPTY))
      .build()
    return ConnectionResult.accept(
      sessionCommands,
      connectionResult.availablePlayerCommands,
    )
  }

  private suspend fun prepareCurrentBook() {
    val bookId = currentBookStoreId.data.first() ?: return
    val book = bookRepository.get(bookId) ?: return
    val item = mediaItemProvider.mediaItem(book)
    player.setMediaItem(item)
    player.prepare()
  }

  override fun onCustomCommand(
    session: MediaSession,
    controller: ControllerInfo,
    customCommand: SessionCommand,
    args: Bundle,
  ): ListenableFuture<SessionResult> {
    val command = CustomCommand.parse(customCommand, args)
      ?: return super.onCustomCommand(session, controller, customCommand, args)
    when (command) {
      CustomCommand.ForceSeekToNext -> {
        player.forceSeekToNext()
      }
      CustomCommand.ForceSeekToPrevious -> {
        player.forceSeekToPrevious()
      }
      CustomCommand.SeekBack -> {
        player.seekBack()
      }
      CustomCommand.SeekForward -> {
        player.seekForward()
      }
      is CustomCommand.SetSkipSilence -> {
        player.setSkipSilenceEnabled(command.skipSilence)
      }
      is CustomCommand.SetGain -> {
        player.setGain(command.gain)
      }
      is CustomCommand.PauseWithRewind -> {
        pauseWithRewind(player, intentHolder, command.rewindMs)
      }
      is CustomCommand.TagNextSeek -> {
        intentHolder.pendingSeekIntent = ListeningEventType.fromId(command.typeId)
      }
    }

    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
  }

  override fun onMediaButtonEvent(
    session: MediaSession,
    controller: ControllerInfo,
    intent: Intent,
  ): Boolean {
    val keyEvent = if (android.os.Build.VERSION.SDK_INT >= 33) {
      intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
    } ?: return false

    val ownsKeyCode = keyEvent.keyCode in HANDLED_MEDIA_KEY_CODES
    if (keyEvent.action != KeyEvent.ACTION_DOWN || keyEvent.repeatCount > 0) {
      // Consume rather than delegate: returning false hands repeats to media3's default handler,
      // which toggles play/pause per repeat — on top of the click this callback already counted.
      return ownsKeyCode
    }

    when (keyEvent.keyCode) {
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
        Logger.d("onMediaButtonEvent: PLAY_PAUSE/HEADSETHOOK")
        mediaButtonClickCount++
        mediaButtonClickJob?.cancel()
        mediaButtonClickJob = scope.launch {
          delay(MULTI_CLICK_WINDOW_MS)
          // Read and clear BEFORE any suspending call. A click arriving while this job is suspended
          // in a store read cancels it, and a reset left at the end would never run — the next
          // gesture would then start from a stale count and fire the wrong action.
          val clicks = mediaButtonClickCount
          mediaButtonClickCount = 0
          when (clicks) {
            2 -> {
              val action = doubleClickHandlerStore.data.first()
              Logger.d("Executing simulated double click action: $action")
              handleMediaButtonClickAction(action)
            }
            3 -> {
              val action = tripleClickHandlerStore.data.first()
              Logger.d("Executing simulated triple click action: $action")
              handleMediaButtonClickAction(action)
            }
            else -> {
              Logger.d("Executing: Toggle Play/Pause ($clicks clicks)")
              togglePlayPause()
            }
          }
        }
        return true
      }
      // Many earbuds translate double/triple taps into NEXT/PREVIOUS in firmware, so those
      // keycodes route through the user's configured click actions. Dedicated keyboard and car
      // controls must keep their literal direction, regardless of headset settings (#7, #20).
      KeyEvent.KEYCODE_MEDIA_NEXT -> {
        if (keyEvent.isFromHardwareKeyboard() ||
          session.isAutoCompanionController(controller) || session.isAutomotiveController(controller)
        ) {
          Logger.d("onMediaButtonEvent: NEXT (keyboard/car)")
          player.seekForward()
        } else {
          Logger.d("onMediaButtonEvent: NEXT")
          scope.launch {
            val action = doubleClickHandlerStore.data.first()
            Logger.d("Executing NEXT action ($action)")
            handleMediaButtonClickAction(action)
          }
        }
        return true
      }
      KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
        if (keyEvent.isFromHardwareKeyboard() ||
          session.isAutoCompanionController(controller) || session.isAutomotiveController(controller)
        ) {
          Logger.d("onMediaButtonEvent: PREVIOUS (keyboard/car)")
          player.seekBack()
        } else {
          Logger.d("onMediaButtonEvent: PREVIOUS")
          scope.launch {
            val action = tripleClickHandlerStore.data.first()
            Logger.d("Executing PREVIOUS action ($action)")
            handleMediaButtonClickAction(action)
          }
        }
        return true
      }
    }
    return super.onMediaButtonEvent(session, controller, intent)
  }

  // Headset/earbud events arrive from a virtual or non-alphabetic input device; a physical
  // keyboard reports an alphabetic one. Null device (relayed events) counts as headset.
  private fun KeyEvent.isFromHardwareKeyboard(): Boolean = device.isPhysicalAlphabeticKeyboard()

  private fun handleMediaButtonClickAction(action: MediaButtonClickAction) {
    when (action) {
      MediaButtonClickAction.SKIP_FORWARD -> player.seekForward()
      MediaButtonClickAction.SKIP_BACKWARD -> player.seekBack()
      MediaButtonClickAction.SKIP_FORWARD_CHAPTER -> player.forceSeekToNext()
      MediaButtonClickAction.SKIP_BACKWARD_CHAPTER -> player.forceSeekToPrevious()
      MediaButtonClickAction.QUICK_BOOKMARK -> scope.launch { createQuickBookmark() }
      MediaButtonClickAction.NONE -> Unit
    }
  }

  /**
   * Intercepting the play/pause key bypasses media3's own resumption path, which is what normally
   * loads the current book when the player is empty. Without this, the first headset press after a
   * reboot or swipe-away sets playWhenReady on an idle player and silently does nothing.
   */
  private suspend fun togglePlayPause() {
    if (player.isPlaying) {
      player.pause()
      return
    }
    if (!intentHolder.confirmExternalResume()) {
      showSleepResumeConfirmation()
      return
    }
    if (player.currentMediaItem == null) {
      prepareCurrentBook()
    }
    player.play()
  }

  private fun showSleepResumeConfirmation() {
    Toast.makeText(context, StringsR.string.sleep_resume_confirmation, Toast.LENGTH_SHORT).show()
  }

  private suspend fun createQuickBookmark() {
    // The bookmark is written from the book's persisted position, and with experimental playback
    // persistence enabled that is only flushed every 5 minutes — the bookmark would land minutes
    // behind the audio the user just heard.
    positionUpdater.flushPositionNow()
    val bookId = currentBookStoreId.data.first() ?: return
    val book = bookRepository.get(bookId) ?: return
    bookmarkRepo.addBookmarkAtBookPosition(book = book, title = null, setBySleepTimer = false)
    Logger.d("Quick bookmark created at current position")
    withContext(Dispatchers.Main) {
      Toast.makeText(context, StringsR.string.bookmark_added, Toast.LENGTH_SHORT).show()
    }
  }
}

internal fun pauseWithRewind(
  player: VoicePlayer,
  intentHolder: PlaybackIntentHolder,
  rewindMs: Long,
) {
  intentHolder.stoppedBySleepTimer = true
  intentHolder.requireSleepResumeConfirmation()
  player.pause()
  player.seekTo((player.currentPosition - rewindMs).coerceAtLeast(0L))
}

// Every single click waits this long so the click count can settle. Deliberately more generous than
// the platform's 300ms double-tap timeout: headset buttons are stiffer than a touchscreen, and a
// missed double-click runs the wrong action, while the cost of waiting is only play/pause latency.
private const val MULTI_CLICK_WINDOW_MS = 400L

private val HANDLED_MEDIA_KEY_CODES = setOf(
  KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
  KeyEvent.KEYCODE_HEADSETHOOK,
  KeyEvent.KEYCODE_MEDIA_NEXT,
  KeyEvent.KEYCODE_MEDIA_PREVIOUS,
)

internal fun InputDevice?.isPhysicalAlphabeticKeyboard(): Boolean =
  this != null && !isVirtual && keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
