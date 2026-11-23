package voice.core.scanner

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.core.common.DispatcherProvider
import voice.core.data.MediaScanWaiter
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.DocumentFileWithUri
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookRepository
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.logging.api.Logger
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.measureTime

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class MediaScanTrigger
internal constructor(
  private val audiobookFolders: AudiobookFolders,
  private val scanner: MediaScanner,
  private val coverScanner: CoverScanner,
  private val bookRepo: BookRepository,
  private val documentFileFactory: CachedDocumentFileFactory,
  private val dispatcherProvider: DispatcherProvider,
) : MediaScanWaiter {

  private val _scannerActive = MutableStateFlow(false)
  public val scannerActive: Flow<Boolean> = _scannerActive

  // SupervisorJob + handler so an exception in one scan (e.g. a SAF SecurityException or Room
  // error) is logged instead of cancelling the scope — otherwise every later scan would silently
  // no-op until the app restarts. The dispatcher is injected (not a hardcoded Dispatchers.IO) so a
  // test can run the scan on a single TestDispatcher and control exactly when it resumes relative to
  // a folder removal — the seam that makes the remove-during-scan race deterministically reproducible.
  private val scope = CoroutineScope(
    dispatcherProvider.io + SupervisorJob() +
      CoroutineExceptionHandler { _, throwable ->
        Logger.e(throwable, "Error while scanning for media")
      },
  )
  private val scanLock = Any()

  // The file phase only. Covers run in their own job, so a request while covers are looked for starts a
  // new scan instead of being folded into one that has already read the folders.
  private var scanningJob: Job? = null
  private var coverJob: Job? = null

  // One file scan at a time: a restart cancels the running scan, and the new one waits here until the old
  // one has fully unwound, however many restarts queue up behind it.
  private val scanMutex = Mutex()

  // A forced re-parse (the tag mode was toggled) carries over to the scans that restart it, until one finishes.
  @Volatile
  private var forceReParsePending = false

  @Volatile
  private var lastScanCompletedAt: Instant? = null

  // The folder configuration the last completed scan actually saw. Freshness is time AND
  // configuration, so a cooldown can never hide a folder the user just added, even if the scan that
  // [scanOnFolderChanges] starts for it is missed.
  @Volatile
  private var lastScanFolderKeys: Set<Pair<FolderType, Uri>>? = null

  // Test seam, mirroring ListeningEventRecorder.clock.
  internal var clock: () -> Instant = { Instant.now() }

  public fun scan(
    restartIfScanning: Boolean = false,
    forceReParse: Boolean = false,
    // The book overview triggers a scan on every entry into composition — i.e. every navigation
    // back to the library. Without a freshness window that is a full SAF re-walk (seconds of
    // progress bar) per navigation. Explicit scans (delete-rescan, restore, tag-mode toggle) pass
    // null or restart/force and are never skipped; only a scan that COMPLETED, over the SAME
    // folder set that is configured now, counts as fresh.
    skipIfCompletedWithin: Duration? = null,
  ) {
    val skipWindow = skipIfCompletedWithin.takeUnless { restartIfScanning || forceReParse }
    scanInternal(restartIfScanning = restartIfScanning, forceReParse = forceReParse, skipIfCompletedWithin = skipWindow)
  }

  // Returns the Job that will (or already does) run this scan. The check-then-launch is guarded so two
  // concurrent callers can't both read the same prior job and leak duplicate scans.
  @IgnorableReturnValue
  private fun scanInternal(
    restartIfScanning: Boolean,
    forceReParse: Boolean,
    skipIfCompletedWithin: Duration? = null,
  ): Job = synchronized(scanLock) {
    Logger.i("scanForFiles with restartIfScanning=$restartIfScanning, forceReParse=$forceReParse")
    val current = scanningJob
    if (current?.isActive == true && !restartIfScanning) {
      return@synchronized current
    }
    if (forceReParse) forceReParsePending = true
    // cancelled right away, so a restart that is itself restarted before it runs can't leave it scanning
    current?.cancel()
    val job = scope.launch {
      // The scan mutex makes this wait until the cancelled scan has unwound, so a restart can't leave the
      // flag stuck.
      scanMutex.withLock {
        val force = forceReParsePending
        val foldersWithUri = audiobookFolders.all().first()
        val folderKeys = foldersWithUri.keys()
        if (!force &&
          skipIfCompletedWithin != null &&
          isFresh(lastScanCompletedAt, clock(), skipIfCompletedWithin) &&
          folderKeys == lastScanFolderKeys
        ) {
          Logger.v("Skipping scan; last one completed within $skipIfCompletedWithin over the same folders")
          return@launch
        }
        // The covers are looked for again after this scan, over the books it finds.
        synchronized(scanLock) { coverJob }?.cancelAndJoin()
        _scannerActive.value = true
        try {
          measureTime {
            val folders: Map<FolderType, List<CachedDocumentFile>> = foldersWithUri
              .mapValues { (_, documentFilesWithUri) ->
                documentFilesWithUri.map {
                  documentFileFactory.create(it.documentFile.uri)
                }
              }
            scanner.scan(folders, force)
          }.also {
            Logger.i("scan took $it")
          }
          // Only a run that got this far refreshes the window; a cancelled or thrown scan must not
          // suppress the retry.
          lastScanCompletedAt = clock()
          lastScanFolderKeys = folderKeys
          if (force) forceReParsePending = false
        } finally {
          // Always clear the flag, even if the scan throws, so the progress indicator can't hang.
          _scannerActive.value = false
          // A failed scan still looks for the covers it cancelled; a restarted one leaves that to its successor.
          if (isActive) startCoverScan()
        }
      }
    }
    scanningJob = job
    job
  }

  private fun startCoverScan() = synchronized(scanLock) {
    coverJob?.cancel()
    coverJob = scope.launch {
      coverScanner.scan(bookRepo.all())
    }
  }

  /**
   * Scans again whenever an audiobook folder is added or removed, restarting a scan that is still reading
   * the old folders. Nothing else scans on a folder change: without this, a new folder would wait for the
   * library to be opened again, and a scan that was already running would drop it.
   */
  public fun scanOnFolderChanges() {
    scope.launch {
      audiobookFolders.all()
        .map { it.keys() }
        .distinctUntilChanged()
        .drop(1)
        .collect { scan(restartIfScanning = true) }
    }
  }

  /**
   * Starts a scan and suspends until it, its active-book reconcile inside [MediaScanner.scan] and its cover
   * search have finished; never observes the racy [scannerActive] flag. Always scans with
   * `restartIfScanning = true` so a restore's scan is not collapsed into an in-flight App-start / overview
   * scan by the re-entrancy guard — a guarantee of the interface, not a caller choice. If another scan
   * restarts this one (a folder change), it waits for that one instead, so it never returns before a scan
   * over the current folders has finished.
   */
  override suspend fun scanAndAwait() {
    scanInternal(restartIfScanning = true, forceReParse = false)
    while (true) {
      val (scan, covers) = synchronized(scanLock) { scanningJob to coverJob }
      scan?.join()
      covers?.join()
      if (synchronized(scanLock) { scanningJob === scan && coverJob === covers }) return
    }
  }
}

private fun Map<FolderType, List<DocumentFileWithUri>>.keys(): Set<Pair<FolderType, Uri>> {
  return flatMap { (type, files) -> files.map { type to it.uri } }.toSet()
}

// Pure so the cooldown window is testable without the scan rig.
internal fun isFresh(
  lastCompletedAt: Instant?,
  now: Instant,
  window: Duration,
): Boolean {
  if (lastCompletedAt == null) return false
  return java.time.Duration.between(lastCompletedAt, now).toMillis() < window.inWholeMilliseconds
}
