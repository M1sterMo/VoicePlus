package voice.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.io.File
import java.time.Instant

@Entity(tableName = "content2")
public data class BookContent(
  @PrimaryKey
  val id: BookId,
  val playbackSpeed: Float,
  val skipSilence: Boolean,
  val isActive: Boolean,
  val lastPlayedAt: Instant,
  val author: String?,
  val name: String,
  val addedAt: Instant,
  val chapters: List<ChapterId>,
  val currentChapter: ChapterId,
  val positionInChapter: Long,
  val cover: File?,
  @ColumnInfo(defaultValue = "0")
  val gain: Float,
  val genre: String?,
  val narrator: String?,
  val series: String?,
  val part: String?,
  @ColumnInfo(defaultValue = "0")
  val chapterNameOffset: Int = 0,
  /** User-chosen library grouping, independent of imported series metadata. */
  val seriesGroup: String? = null,
  /** User-chosen position in the group; null retains the imported part/title ordering. */
  val seriesOrder: Int? = null,
  /** Keeps a title chosen in Edit book from being replaced by a metadata re-scan. */
  @ColumnInfo(defaultValue = "0")
  val nameOverridden: Boolean = false,
  /**
   * The synopsis from a desc.txt or the file tags, refreshed whenever the book's tags are read again.
   * Null until the book has been looked at for one, which lets books added before synopses pick theirs up once.
   * Empty when there was none.
   */
  val synopsis: String? = null,
  /** A synopsis typed in Edit book. Wins over [synopsis] and survives re-scans; empty hides the file's. */
  val userSynopsis: String? = null,
  /** The listener's own 1–5 star rating; null when unrated. */
  val rating: Int? = null,
  /** Whether the end-of-book card has already asked for a rating, so it asks once per book. */
  @ColumnInfo(defaultValue = "0")
  val ratingPrompted: Boolean = false,
) {

  @Ignore
  val currentChapterIndex: Int = chapters.indexOf(currentChapter)

  init {
    require(currentChapter in chapters && positionInChapter >= 0) {
      "invalid data in $this"
    }
  }
}

/** What to show as this book's synopsis, or null when there is none. */
public val BookContent.shownSynopsis: String? get() = (userSynopsis ?: synopsis)?.takeIf { it.isNotBlank() }
