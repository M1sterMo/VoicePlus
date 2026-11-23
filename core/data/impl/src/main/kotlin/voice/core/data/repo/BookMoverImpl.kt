package voice.core.data.repo

import androidx.datastore.core.DataStore
import androidx.room.RoomDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.repo.internals.dao.BookMoveDao
import voice.core.data.repo.internals.transaction
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.UpNextStore

@ContributesBinding(AppScope::class)
public class BookMoverImpl
internal constructor(
  private val appDb: RoomDatabase,
  private val dao: BookMoveDao,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  @UpNextStore
  private val upNextStore: DataStore<List<BookId>>,
) : BookMover {

  public override suspend fun move(
    from: List<BookId>,
    to: BookId,
    chapters: Map<ChapterId, ChapterId>,
    sameBook: BookId?,
    playedHere: List<BookId>,
  ) {
    val others = from.filter { it != to }
    if (others.isEmpty()) return
    appDb.transaction {
      chapters.forEach { (old, new) ->
        dao.moveBookmarks(others, to, old, new)
        dao.moveEvents(others, to, old, new)
        dao.moveSessions(others, to, old, new)
        dao.moveOverrides(from = others.map { it.value }, to = to.value, old = old.value, new = new.value)
      }
      if (sameBook != null && sameBook != to) {
        dao.moveRemainingSessions(sameBook, to)
      }
      // once every session is here: one can end in a chapter before the one it started in
      chapters.forEach { (old, new) -> dao.moveSessionEnds(to, old, new) }
      if (sameBook != null && sameBook != to) {
        dao.moveCharacters(sameBook, to)
        dao.moveCharacterLists(sameBook, to)
        // its shelf, series and position, in place of the one the new book got when it was added
        dao.placement(sameBook.value)?.let { placement ->
          dao.deletePlacement(sameBook.value)
          dao.putPlacement(placement.copy(bookId = to.value))
        }
      }
    }
    if (playedHere.isNotEmpty()) {
      currentBookStore.updateData { current -> if (current in playedHere) to else current }
    }
    if (sameBook != null && sameBook != to) {
      upNextStore.updateData { queue ->
        if (sameBook !in queue) queue else queue.map { if (it == sameBook) to else it }.distinct()
      }
    }
  }
}
