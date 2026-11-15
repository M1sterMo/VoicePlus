package voice.app.navigation

import voice.core.data.BookId
import voice.navigation.Destination

/**
 * The player shows the current book. When Up next starts the next book, or a back stack restored
 * after process death names a book that is no longer current, an open player moves to the current
 * book instead of acting on the one it opened with.
 */
internal fun MutableList<Destination.Compose>.showCurrentBook(current: BookId) {
  replaceAll { if (it is Destination.Playback && it.bookId != current) Destination.Playback(current) else it }
}
