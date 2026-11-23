package voice.core.data.folders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import voice.core.data.BookId
import voice.core.data.repo.BookContentRepo
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.logging.api.Logger
import kotlin.coroutines.cancellation.CancellationException

// App-scoped, so a grant taken in one screen shows in every flow of the others.
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class AudiobookFoldersImpl
internal constructor(
  @RootAudiobookFoldersStore
  private val rootAudioBookFoldersStore: DataStore<Set<@JvmSuppressWildcards Uri>>,
  @SingleFolderAudiobookFoldersStore
  private val singleFolderAudiobookFoldersStore: DataStore<Set<@JvmSuppressWildcards Uri>>,
  @SingleFileAudiobookFoldersStore
  private val singleFileAudiobookFoldersStore: DataStore<Set<@JvmSuppressWildcards Uri>>,
  @AuthorAudiobookFoldersStore
  private val authorAudiobookFoldersStore: DataStore<Set<@JvmSuppressWildcards Uri>>,
  private val context: Context,
  private val cachedDocumentFileFactory: CachedDocumentFileFactory,
  private val persistedUriPermissions: PersistedUriPermissions,
  private val bookContentRepo: BookContentRepo,
) : AudiobookFolders {

  private val scope = MainScope()

  // Grants live outside the stores, so a grant taken again for a folder that is already configured would change
  // nothing the flows below see.
  private val grantsChanged = MutableStateFlow(0)

  public override fun all(): Flow<Map<FolderType, List<DocumentFileWithUri>>> {
    val flows = FolderType.entries
      .map { folderType ->
        combine(dataStore(folderType).data, grantsChanged) { uris, _ ->
          val persistedUris = persistedUriPermissions.persistedUris()
          val documentFiles = uris
            .filter {
              it in persistedUris
            }
            .map { uri ->
              DocumentFileWithUri(
                documentFile = uri.toDocumentFile(folderType),
                uri = uri,
              )
            }
          folderType to documentFiles
        }
      }
    return combine(flows) { it.toMap() }
  }

  public override fun withoutAccess(): Flow<Map<FolderType, List<Uri>>> {
    val flows = FolderType.entries.map { folderType ->
      combine(dataStore(folderType).data, grantsChanged) { uris, _ ->
        val persistedUris = persistedUriPermissions.persistedUris()
        folderType to uris.filter { it !in persistedUris }
      }
    }
    return combine(flows) { it.toMap() }
  }

  private fun Uri.toDocumentFile(folderType: FolderType): CachedDocumentFile {
    val uri = when (folderType) {
      FolderType.SingleFile -> this
      FolderType.SingleFolder,
      FolderType.Root,
      FolderType.Author,
      -> {
        DocumentsContract.buildDocumentUriUsingTree(
          this,
          DocumentsContract.getTreeDocumentId(this),
        )
      }
    }
    return cachedDocumentFileFactory.create(uri)
  }

  public override fun add(
    uri: Uri,
    type: FolderType,
  ) {
    takePermission(uri)
    grantsChanged.update { it + 1 }
    scope.launch {
      dataStore(type).updateData {
        it + uri
      }
      // A folder has one type, so adding it again with another type moves it instead of finding its books
      // twice. The other stores are edited directly: remove() would release the grant just taken and
      // deactivate the folder's books. Added first, so a scan in between never misses its books.
      FolderType.entries
        .filter { it != type }
        .forEach { otherType -> dataStore(otherType).updateData { it - uri } }
    }
  }

  // One at a time: asking for both takes nothing when the picker only granted reading, and all() drops a
  // folder without a persisted grant.
  private fun takePermission(uri: Uri) {
    val resolver = context.contentResolver
    try {
      resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
      Logger.w("Could not take the read permission for $uri")
      return
    }
    try {
      resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    } catch (_: SecurityException) {
      Logger.w("Could only take the read permission for $uri")
    }
  }

  public override fun remove(
    uri: Uri,
    folderType: FolderType,
  ) {
    try {
      context.contentResolver.releasePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    } catch (_: SecurityException) {
      Logger.w("Could not release uri permission for $uri")
    }
    scope.launch {
      dataStore(folderType).updateData { folders ->
        folders - uri
      }
      // Deactivate the books that came from the removed folder, so the library reflects the removal
      // immediately — scoped to that folder's books via pure URI membership, no scanner reconcile.
      // Best-effort: a DB failure is logged, not propagated, so it can't crash the app or leave the
      // folder half-removed; the next overview scan reconciles regardless (and a book that also sits
      // under a separately-configured overlapping grant simply re-activates on that scan).
      try {
        bookContentRepo.all()
          .filter { it.isActive && FolderMembership.isBookUnderFolder(it.id.value.toUri(), uri, folderType) }
          // update, not put: a scan may write the same book meanwhile, and a put of this stale copy would undo it
          .forEach { book -> bookContentRepo.update(book.id) { it.copy(isActive = false) } }
      } catch (e: CancellationException) {
        throw e
      } catch (t: Throwable) {
        Logger.e(t, "Failed to deactivate books for removed folder $uri")
      }
    }
  }

  private fun dataStore(type: FolderType): DataStore<Set<Uri>> {
    return when (type) {
      FolderType.SingleFile -> singleFileAudiobookFoldersStore
      FolderType.SingleFolder -> singleFolderAudiobookFoldersStore
      FolderType.Root -> rootAudioBookFoldersStore
      FolderType.Author -> authorAudiobookFoldersStore
    }
  }

  public override suspend fun hasAnyFolders(): Boolean {
    return FolderType.entries.any {
      dataStore(it).data.first().isNotEmpty()
    }
  }

  public override suspend fun isManaged(bookId: BookId): Boolean {
    val bookUri = bookId.toUri()
    // Read the LIVE configured URIs per type (not a cached snapshot) so a scan that started before a
    // folder was removed sees the post-removal truth: an unconfigured folder yields no match here, so the
    // scanner refuses to re-activate the removed folder's books. No persisted-permission / DocumentFile
    // filtering — pure URI membership, mirroring the deactivation path in [remove].
    return FolderType.entries.any { folderType ->
      dataStore(folderType).data.first().any { folderUri ->
        FolderMembership.isBookUnderFolder(bookUri, folderUri, folderType)
      }
    }
  }
}
