package voice.core.scanner

import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.repo.BookMover

/** Records the moves the scanner asks for; the moving itself is tested with the database in core:data:impl. */
internal class RecordingBookMover : BookMover {

  data class Move(
    val from: List<BookId>,
    val to: BookId,
    val chapters: Map<ChapterId, ChapterId>,
    val sameBook: BookId?,
    val playedHere: List<BookId>,
  )

  val moves = mutableListOf<Move>()

  override suspend fun move(
    from: List<BookId>,
    to: BookId,
    chapters: Map<ChapterId, ChapterId>,
    sameBook: BookId?,
    playedHere: List<BookId>,
  ) {
    moves += Move(from, to, chapters, sameBook, playedHere)
  }
}
