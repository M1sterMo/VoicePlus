package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.LibraryPlacement

/** Re-keys the rows of books whose files moved, see [voice.core.data.repo.BookMover]. */
@Dao
internal interface BookMoveDao {

  @Query("UPDATE bookmark2 SET bookId = :to, chapterId = :new WHERE bookId IN (:from) AND chapterId = :old")
  suspend fun moveBookmarks(
    from: List<BookId>,
    to: BookId,
    old: ChapterId,
    new: ChapterId,
  )

  @Query("UPDATE listening_event SET bookId = :to, chapterId = :new WHERE bookId IN (:from) AND chapterId = :old")
  suspend fun moveEvents(
    from: List<BookId>,
    to: BookId,
    old: ChapterId,
    new: ChapterId,
  )

  @Query("UPDATE listening_session SET bookId = :to, chapterId = :new WHERE bookId IN (:from) AND chapterId = :old")
  suspend fun moveSessions(
    from: List<BookId>,
    to: BookId,
    old: ChapterId,
    new: ChapterId,
  )

  @Query("UPDATE listening_session SET endChapterId = :new WHERE bookId = :to AND endChapterId = :old")
  suspend fun moveSessionEnds(
    to: BookId,
    old: ChapterId,
    new: ChapterId,
  )

  // a finish marked by hand, or a session in a chapter that's gone, still belongs to the book
  @Query("UPDATE listening_session SET bookId = :to WHERE bookId = :from")
  suspend fun moveRemainingSessions(
    from: BookId,
    to: BookId,
  )

  @Query("UPDATE OR REPLACE chapter_name_overrides SET bookId = :to, chapterId = :new WHERE bookId IN (:from) AND chapterId = :old")
  suspend fun moveOverrides(
    from: List<String>,
    to: String,
    old: String,
    new: String,
  )

  @Query("UPDATE book_character SET bookId = :to WHERE bookId = :from")
  suspend fun moveCharacters(
    from: BookId,
    to: BookId,
  )

  @Query("UPDATE book_character SET ownerBookId = :to WHERE ownerBookId = :from")
  suspend fun moveCharacterLists(
    from: BookId,
    to: BookId,
  )

  @Query("SELECT * FROM libraryPlacement WHERE bookId = :bookId")
  suspend fun placement(bookId: String): LibraryPlacement?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putPlacement(placement: LibraryPlacement)

  @Query("DELETE FROM libraryPlacement WHERE bookId = :bookId")
  suspend fun deletePlacement(bookId: String)
}
