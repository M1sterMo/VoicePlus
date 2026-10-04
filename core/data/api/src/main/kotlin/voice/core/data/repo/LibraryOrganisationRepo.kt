package voice.core.data.repo

import kotlinx.coroutines.flow.Flow
import voice.core.data.BookId
import voice.core.data.LibraryChange
import voice.core.data.LibraryOrganisation
import voice.core.data.ShelfSort

public interface LibraryOrganisationRepo {
  public fun flow(): Flow<LibraryOrganisation>
  public suspend fun get(): LibraryOrganisation

  @IgnorableReturnValue public suspend fun createShelf(name: String): LibraryChange

  @IgnorableReturnValue public suspend fun renameShelf(
    id: String,
    name: String,
  ): LibraryChange

  @IgnorableReturnValue public suspend fun removeShelf(id: String): LibraryChange

  @IgnorableReturnValue public suspend fun setSort(
    id: String,
    sort: ShelfSort,
  ): LibraryChange
  public suspend fun setExpanded(
    id: String,
    expanded: Boolean,
  )

  @IgnorableReturnValue public suspend fun reorderShelves(ids: List<String>): LibraryChange

  @IgnorableReturnValue public suspend fun move(
    books: Set<BookId>,
    shelfId: String,
  ): LibraryChange

  @IgnorableReturnValue public suspend fun reorder(
    shelfId: String,
    itemIds: List<String>,
  ): LibraryChange

  @IgnorableReturnValue public suspend fun saveSeries(
    id: String?,
    name: String,
    books: List<BookId>,
    shelfId: String? = null,
  ): LibraryChange

  @IgnorableReturnValue public suspend fun removeFromSeries(bookId: BookId): LibraryChange
  public suspend fun undo(change: LibraryChange): Boolean
}
