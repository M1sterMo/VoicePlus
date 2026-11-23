package voice.core.data.folders

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.documentfile.CachedDocumentFile

public interface AudiobookFolders {
  /** The configured folders VoicePlus may open. */
  public fun all(): Flow<Map<FolderType, List<DocumentFileWithUri>>>

  /** Configured folders whose access is gone, like a removed SD card or a provider that was reinstalled. */
  public fun withoutAccess(): Flow<Map<FolderType, List<Uri>>>

  public fun add(
    uri: Uri,
    type: FolderType,
  )

  public fun remove(
    uri: Uri,
    folderType: FolderType,
  )

  public suspend fun hasAnyFolders(): Boolean

  /**
   * Whether [bookId] currently lives under a configured audiobook folder, read against the LIVE folder set
   * (not a snapshot). The scanner consults this immediately before re-activating a book it found on disk, so a
   * scan that started before a folder was removed cannot resurrect that folder's books after the removal already
   * deactivated them — closing the remove-during-an-in-flight-scan race. Pure URI membership; performs no SAF IO.
   */
  public suspend fun isManaged(bookId: BookId): Boolean
}

public data class DocumentFileWithUri(
  val documentFile: CachedDocumentFile,
  val uri: Uri,
)
