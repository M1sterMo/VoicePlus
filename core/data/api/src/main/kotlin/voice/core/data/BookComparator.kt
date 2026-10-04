package voice.core.data

import voice.core.common.comparator.NaturalOrderComparator

private val nameOrder = compareBy<Book, String>(NaturalOrderComparator.stringComparator) { it.content.name }
  .thenBy { it.id.value }

public enum class BookComparator(private val comparatorFunction: Comparator<Book>) : Comparator<Book> by comparatorFunction {

  ByLastPlayed(
    compareByDescending<Book> {
      it.content.lastPlayedAt
    }.then(nameOrder),
  ),
  ByName(
    nameOrder,
  ),
}
