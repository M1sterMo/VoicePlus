package voice.core.data.repo

import voice.core.data.BookId
import voice.core.data.ChapterId

/** Moves what the listener built up with a book to the id its files show up under after a rename or move. */
public interface BookMover {

  /**
   * The files of the books in [from] are now part of [to]; [chapters] maps their chapter ids to [to]'s.
   *
   * Bookmarks, listening events and sessions, and chapter name overrides of a mapped chapter move along.
   * [sameBook], the one of [from] that [to] is, also hands over its remaining sessions, its characters, its
   * place on the shelves and in up next. The current book follows when it was one of [playedHere].
   */
  public suspend fun move(
    from: List<BookId>,
    to: BookId,
    chapters: Map<ChapterId, ChapterId>,
    sameBook: BookId?,
    playedHere: List<BookId>,
  )
}
