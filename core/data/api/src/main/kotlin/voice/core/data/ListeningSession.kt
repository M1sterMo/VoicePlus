package voice.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
  tableName = "listening_session",
  indices = [
    Index(value = ["bookId"]),
    Index(value = ["startedAt"]),
  ],
)
public data class ListeningSession(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val bookId: BookId,
  val chapterId: ChapterId,
  val startedAt: Instant,
  val endedAt: Instant,
  val durationMs: Long,
  val startPositionMs: Long,
  val endPositionMs: Long,
  val endChapterId: ChapterId? = null,
  val endReason: Int? = null,
)

/** A finish marked by hand, not time spent listening. */
public val ListeningSession.isCompletionMark: Boolean
  get() = endReason == ListeningSessionEndReason.MarkedCompleted.id

/** Evidence the book was finished: playback reached the end, or the listener marked it completed. */
public val ListeningSession.isCompletion: Boolean
  get() = endReason == ListeningSessionEndReason.EndOfBook.id || isCompletionMark
