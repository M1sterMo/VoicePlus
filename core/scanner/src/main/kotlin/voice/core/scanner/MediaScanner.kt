package voice.core.scanner

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.audioFileCount
import voice.core.data.fitIntoOneBook
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.isAudioFile
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.store.ExcludedBooksStore
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.walk
import voice.core.logging.api.Logger

@Inject
internal class MediaScanner(
  private val contentRepo: BookContentRepo,
  private val chapterParser: ChapterParser,
  private val bookParser: BookParser,
  private val deviceHasPermissionBug: DeviceHasStoragePermissionBug,
  private val audiobookFolders: AudiobookFolders,
  @ExcludedBooksStore
  private val excludedBooksStore: DataStore<Set<String>>,
  private val chapterRepo: ChapterRepo,
  private val progressCarryOver: ProgressCarryOver,
) {

  suspend fun scan(
    folders: Map<FolderType, List<CachedDocumentFile>>,
    forceReParse: Boolean = false,
  ) {
    val excludedIds = excludedBooksStore.data.first()
    val files = folders.flatMap { (folderType, files) ->
      when (folderType) {
        FolderType.SingleFile, FolderType.SingleFolder -> {
          files
        }
        FolderType.Root -> {
          files.flatMap { file ->
            file.children
          }
        }
        FolderType.Author -> {
          files.flatMap { folder ->
            folder.children.flatMap { author ->
              if (author.isFile) {
                listOf(author)
              } else {
                author.children
              }
            }
          }
        }
      }
    }

    // Books found by this scan that are STILL under a currently-configured folder. Gating the bulk reconcile
    // on LIVE membership (not just this scan's stale folder snapshot) means a folder removed mid-scan can't
    // have its books bulk-re-activated here — closing the window where an early-return or cancellation before
    // the per-book loop (below) would otherwise leave them active with no later scan able to clear them.
    // Excluded books are omitted so they stay inactive even when their files are found.
    val foundIds = files.map { BookId(it.uri) }.filter { it.value !in excludedIds }
    val activeIds = mutableListOf<BookId>()
    for (id in foundIds) {
      if (audiobookFolders.isManaged(id)) activeIds += id
    }
    // A scan that finds no files, or one where a configured folder couldn't be listed, can't tell
    // the books it missed from deleted ones. Reconciling then would deactivate the books under that
    // folder and blank them from the library, so only reconcile when the scan looks healthy. A
    // folder that lists no entries was emptied, like when its books moved to another folder, so it
    // doesn't hold reconciling back. A partial failure deeper inside a folder is not detected.
    val aConfiguredFolderIsUnlisted = folders.values.flatten().any { !it.childrenListed }
    val reconciles = files.isNotEmpty() && !aConfiguredFolderIsUnlisted
    // Before the books found again are reconciled, so they can be told apart from the ones that moved. A scan
    // that can't reconcile can't tell a moved book from an unreadable one, so only books gone before count.
    val previousBooks = PreviousBooks(
      library = contentRepo.all().filter { reconciles || !it.isActive },
      scanned = foundIds.toSet(),
      excluded = excludedIds,
      chapterRepo = chapterRepo,
    )
    if (reconciles) {
      contentRepo.setAllInactiveExcept(activeIds)
    } else if (files.isNotEmpty()) {
      Logger.w("Skipping reconciliation: a configured folder could not be listed.")
    }

    val probeFile = folders.values.flatten().findProbeFile()
    if (probeFile != null) {
      if (deviceHasPermissionBug.checkForBugAndSet(probeFile)) {
        Logger.w("Device has permission bug, aborting scan! Probed $probeFile")
        return
      }
    }

    files
      .sortedBy { it.audioFileCount() }
      .forEach { file ->
        scan(file, excludedIds, forceReParse, previousBooks)
      }
  }

  private fun List<CachedDocumentFile>.findProbeFile(): CachedDocumentFile? {
    return asSequence().flatMap { it.walk() }
      .firstOrNull { child ->
        child.isAudioFile() && child.uri.authority == "com.android.externalstorage.documents"
      }
  }

  private suspend fun scan(
    file: CachedDocumentFile,
    excludedIds: Set<String>,
    forceReParse: Boolean,
    previousBooks: PreviousBooks,
  ) {
    if (BookId(file.uri).value in excludedIds) return

    val parseResult = chapterParser.parse(file, forceReParse)
    val chapters = parseResult.chapters
    if (chapters.isEmpty()) {
      // A folder that still lists entries but holds no audio lost its files for real, like after
      // deleting them with a file manager, so the book can't play and leaves the library. An empty
      // or unreadable folder is left alone: that is what a dropped permission or an unmounted volume
      // looks like, and its books must not be blanked. Progress stays stored either way.
      if (file.isDirectory && file.children.isNotEmpty() && file.audioFileCount() == 0) {
        contentRepo.update(BookId(file.uri)) { it.copy(isActive = false) }
      }
      return
    }
    val chapterIds = chapters.map { it.id }
    if (!chapterIds.fitIntoOneBook()) {
      Logger.e("Skipping $file, its ${chapters.size} chapters are too many for one book")
      return
    }

    val content = bookParser.parseAndStore(
      chapters = chapters,
      file = file,
      firstChapterMetadata = parseResult.firstChapterMetadata,
      forceReParse = forceReParse,
    )

    // Gate activation on the LIVE folder set rather than blindly forcing isActive=true. This scan may have
    // snapshotted its folder list (in MediaScanTrigger) before the user removed the folder; without this
    // check, the slow per-book loop would re-activate the removed folder's books AFTER remove() already
    // deactivated them, leaving zombies the empty-scan reconcile guard never clears. Consulting live
    // membership here makes a re-activation unable to outlive the removal under any interleaving.
    val isManaged = audiobookFolders.isManaged(BookId(file.uri))
    contentRepo.update(content.id) { current ->
      val currentChapterGone = current.currentChapter !in chapterIds
      val updated = current.copy(
        chapters = chapterIds,
        currentChapter = if (currentChapterGone) chapterIds.first() else current.currentChapter,
        positionInChapter = if (currentChapterGone) 0 else current.positionInChapter,
        isActive = isManaged,
      )
      validateIntegrity(updated, chapters)
      updated
    }
    // a removed folder's book doesn't take over anyone's progress
    if (isManaged) progressCarryOver.carryOver(content.id, previousBooks)
  }
}
