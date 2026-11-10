package voice.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "book_character")
public data class BookCharacter(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  /** The book the character was added in. It owns the character unless [seriesId] is set. */
  val bookId: BookId,
  val name: String,
  val description: String,
  @androidx.room.ColumnInfo(defaultValue = "0")
  val sortOrder: Int = 0,
  val createdAt: Instant,
  val updatedAt: Instant,
  /** The series sharing this character: every book in it reads and edits the same list. */
  val seriesId: String? = null,
)
