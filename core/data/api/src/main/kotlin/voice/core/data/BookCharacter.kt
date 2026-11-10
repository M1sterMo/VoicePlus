package voice.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "book_character")
public data class BookCharacter(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  /** The book the character was added in. Copies keep it, so it can differ from [ownerBookId]. */
  val bookId: BookId,
  val name: String,
  val description: String,
  @androidx.room.ColumnInfo(defaultValue = "0")
  val sortOrder: Int = 0,
  val createdAt: Instant,
  val updatedAt: Instant,
  /** The series sharing this character: every book in it reads and edits the same list. */
  val seriesId: String? = null,
  /** The book whose own list holds the character; null exactly when [seriesId] is set. */
  val ownerBookId: BookId? = bookId.takeIf { seriesId == null },
)
