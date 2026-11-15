package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import voice.core.data.BookCharacter
import voice.core.data.BookId
import voice.core.data.LibraryPlacement

// A book in a series reads its series' shared list; any other book reads its own list.
private const val LIST_OF_BOOK = "seriesId = (SELECT seriesId FROM libraryPlacement WHERE bookId = :bookId) OR ownerBookId = :bookId"

@Dao
public interface BookCharacterDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  public suspend fun insert(character: BookCharacter)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  public suspend fun insert(characters: List<BookCharacter>)

  /** Inserts a new character into the list its book currently shows. */
  @Transaction
  public suspend fun add(character: BookCharacter) {
    val seriesId = seriesOf(character.bookId)
    insert(character.copy(seriesId = seriesId, ownerBookId = character.bookId.takeIf { seriesId == null }))
  }

  @Query("SELECT seriesId FROM libraryPlacement WHERE bookId = :bookId")
  public suspend fun seriesOf(bookId: BookId): String?

  @Update
  public suspend fun update(character: BookCharacter)

  // Room runs a list-parameter @Update as ONE transaction, so a reorder either lands whole or not
  // at all — a process death mid-reorder can't leave duplicate sortOrders behind.
  @Update
  public suspend fun update(characters: List<BookCharacter>)

  @Query("SELECT * FROM book_character WHERE $LIST_OF_BOOK ORDER BY sortOrder ASC, createdAt ASC")
  public fun charactersForBook(bookId: BookId): Flow<List<BookCharacter>>

  @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM book_character WHERE $LIST_OF_BOOK")
  public suspend fun nextSortOrder(bookId: BookId): Int

  @Query("SELECT COUNT(*) FROM book_character WHERE $LIST_OF_BOOK")
  public fun countForBook(bookId: BookId): Flow<Int>

  @Query("DELETE FROM book_character WHERE id = :id")
  public suspend fun delete(id: Long)

  @Query("DELETE FROM book_character WHERE id IN (:ids)")
  public suspend fun delete(ids: List<Long>)

  @Query("SELECT * FROM libraryPlacement")
  public suspend fun placements(): List<LibraryPlacement>

  @Query("SELECT * FROM book_character WHERE seriesId = :seriesId ORDER BY sortOrder ASC, createdAt ASC")
  public suspend fun seriesList(seriesId: String): List<BookCharacter>

  @Query(
    """SELECT c.* FROM book_character c JOIN libraryPlacement p ON p.bookId = c.ownerBookId
    WHERE p.seriesId IS NOT NULL ORDER BY p.seriesPosition, c.sortOrder, c.createdAt""",
  )
  public suspend fun ownListsOfSeriesMembers(): List<BookCharacter>

  @Query(
    "DELETE FROM book_character WHERE seriesId IS NOT NULL AND seriesId NOT IN " +
      "(SELECT seriesId FROM libraryPlacement WHERE seriesId IS NOT NULL)",
  )
  public suspend fun deleteListsOfEmptySeries()

  /**
   * Keeps character lists in step with series membership. Call in the transaction that changed the
   * placements from [before]: a book that left a series takes a full copy of its list, a series
   * without members loses its list, and a member's own characters join its series' list, keeping
   * one of each exact duplicate (same name and description).
   */
  @Transaction
  public suspend fun followSeries(before: List<LibraryPlacement> = emptyList()) {
    val seriesNow = placements().associate { it.bookId to it.seriesId }
    before.filter { it.seriesId != null && it.bookId in seriesNow && seriesNow[it.bookId] != it.seriesId }.forEach { left ->
      insert(seriesList(left.seriesId!!).map { it.copy(id = 0, seriesId = null, ownerBookId = BookId(left.bookId)) })
    }
    deleteListsOfEmptySeries()
    ownListsOfSeriesMembers().groupBy { seriesNow.getValue(it.ownerBookId!!.value)!! }.forEach { (seriesId, joining) ->
      val seen = mutableSetOf<Pair<String, String>>()
      val (kept, duplicates) = (seriesList(seriesId) + joining).partition { seen.add(it.name to it.description) }
      delete(duplicates.map { it.id })
      update(kept.mapIndexed { index, character -> character.copy(sortOrder = index, seriesId = seriesId, ownerBookId = null) })
    }
  }

  /** Turns the table, holding [current], back into [saved], matching rows by id. */
  @Transaction
  public suspend fun restore(
    current: List<BookCharacter>,
    saved: List<BookCharacter>,
  ) {
    delete(current.map { it.id } - saved.map { it.id }.toSet())
    insert(saved - current.toSet())
  }

  /** A restore found the book that [old] identified under the id [new]. */
  @Query("UPDATE book_character SET bookId = :new WHERE bookId = :old")
  public suspend fun reKeyOrigin(
    old: BookId,
    new: BookId,
  )

  @Query("SELECT * FROM book_character")
  public suspend fun all(): List<BookCharacter>

  // A change trigger for the snapshot writer: Room re-emits on any insert/update/delete to the table.
  @Query("SELECT COUNT(*) FROM book_character")
  public fun count(): Flow<Int>
}
