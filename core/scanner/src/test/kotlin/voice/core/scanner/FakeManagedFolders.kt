package voice.core.scanner

import android.net.Uri
import androidx.core.net.toFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import voice.core.data.BookId
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.DocumentFileWithUri
import voice.core.data.folders.FolderType
import voice.core.documentfile.FileBasedDocumentFile
import java.io.File

/**
 * In-memory [AudiobookFolders] for the scanner tests. Backs the LIVE folder set the scanner now consults via
 * [isManaged] before re-activating a found book.
 *
 * Membership is file-path-prefix logic over `file://` URIs (the test surface uses [FileBasedDocumentFile]),
 * not the production SAF documentId algebra of `FolderMembership` (which only matches real SAF tree URIs). This
 * is faithful for the property under test — "active book ⊆ currently-configured folder" — because that property
 * is about coordination/ordering (does the gate read live state?), and the production URI predicate is proven
 * separately by FolderMembershipTest. State is mutable and synchronously settable so a test can [remove] a folder
 * between two steps of a scan and have the very next [isManaged] read reflect it.
 */
internal class FakeManagedFolders : AudiobookFolders {

  private val state = MutableStateFlow<Map<FolderType, Set<Uri>>>(emptyMap())

  fun set(
    type: FolderType,
    uris: List<Uri>,
  ) {
    state.value = state.value + (type to uris.toSet())
  }

  override fun all(): Flow<Map<FolderType, List<DocumentFileWithUri>>> {
    return state.map { byType ->
      byType.mapValues { (_, uris) ->
        uris.map { uri ->
          DocumentFileWithUri(documentFile = FileBasedDocumentFile(uri.toFile()), uri = uri)
        }
      }
    }
  }

  override fun withoutAccess(): Flow<Map<FolderType, List<Uri>>> = state.map { byType -> byType.mapValues { emptyList() } }

  override fun add(
    uri: Uri,
    type: FolderType,
  ) {
    state.value = state.value + (type to (state.value[type].orEmpty() + uri))
  }

  override fun remove(
    uri: Uri,
    folderType: FolderType,
  ) {
    state.value = state.value + (folderType to (state.value[folderType].orEmpty() - uri))
  }

  override suspend fun hasAnyFolders(): Boolean = state.value.values.any { it.isNotEmpty() }

  override suspend fun isManaged(bookId: BookId): Boolean {
    val bookFile = bookId.toUri().toFile().absoluteFile
    return state.value.entries.any { (type, uris) ->
      uris.any { folderUri ->
        val folderFile = folderUri.toFile().absoluteFile
        when (type) {
          FolderType.SingleFile, FolderType.SingleFolder -> bookFile == folderFile
          // File-ancestry walk rather than String.startsWith so the comparison is OS-separator-agnostic
          // (Windows uses '\\'); this is the equivalent of the production "book is under this folder" test.
          FolderType.Root, FolderType.Author -> bookFile == folderFile || bookFile.isUnder(folderFile)
        }
      }
    }
  }

  private fun File.isUnder(ancestor: File): Boolean {
    var parent = parentFile
    while (parent != null) {
      if (parent == ancestor) return true
      parent = parent.parentFile
    }
    return false
  }
}
