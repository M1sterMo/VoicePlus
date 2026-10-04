package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions.TOKENIZER_UNICODE61
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import voice.core.data.BookContent
import voice.core.data.BookId

@Dao
public interface BookContentDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  public suspend fun insertContent(content: BookContent)

  @Query("INSERT OR IGNORE INTO libraryShelf (id, name, position, sort, expanded) VALUES ('other', '', 2147483647, 'MANUAL', 1)")
  public suspend fun ensureOtherShelf()

  @Query(
    "INSERT OR IGNORE INTO libraryPlacement (bookId, shelfId, position, seriesId, seriesPosition) SELECT :bookId, 'other', COALESCE(MAX(position), -1) + 1, NULL, NULL FROM libraryPlacement WHERE shelfId = 'other'",
  )
  public suspend fun ensurePlacement(bookId: String)

  @Query("SELECT EXISTS(SELECT 1 FROM libraryPlacement WHERE bookId = :bookId)")
  public suspend fun hasPlacement(bookId: String): Boolean

  @Transaction
  public suspend fun insert(content: BookContent) {
    insertContent(content)
    if (!hasPlacement(content.id.value)) {
      ensureOtherShelf()
      ensurePlacement(content.id.value)
    }
  }

  @Query("SELECT * FROM content2")
  public suspend fun all(): List<BookContent>

  @Query(
    """
  SELECT id
  FROM bookSearchFts
  WHERE bookSearchFts MATCH :query
  AND isActive = 1
    """,
  )
  public suspend fun search(query: String): List<BookId>
}

@Entity(tableName = "bookSearchFts")
@Fts4(
  contentEntity = BookContent::class,
  tokenizer = TOKENIZER_UNICODE61,
  notIndexed = ["id", "isActive"],
)
public data class BookSearchFts(
  val name: String,
  val author: String?,
  val genre: String?,
  val narrator: String?,
  val series: String?,
  val part: String?,
  val id: BookId,
  val isActive: Boolean,
)
