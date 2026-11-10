package voice.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import voice.core.data.BookId
import voice.navigation.Destination

class ShowCurrentBookTest {

  private val a = BookId("a")
  private val b = BookId("b")

  @Test
  fun `an open player moves to the book the queue started`() {
    val backStack = mutableListOf<Destination.Compose>(Destination.BookOverview, Destination.Playback(a), Destination.Bookmarks(a))
    backStack.showCurrentBook(b)
    assertEquals(listOf(Destination.BookOverview, Destination.Playback(b), Destination.Bookmarks(a)), backStack)
  }

  @Test
  fun `a player already on the current book stays`() {
    val backStack = mutableListOf<Destination.Compose>(Destination.BookOverview, Destination.Playback(a))
    backStack.showCurrentBook(a)
    assertEquals(listOf(Destination.BookOverview, Destination.Playback(a)), backStack)
  }
}
