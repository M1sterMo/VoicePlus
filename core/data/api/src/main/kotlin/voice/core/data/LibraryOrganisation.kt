package voice.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "libraryShelf")
public data class LibraryShelf(
  @PrimaryKey val id: String,
  val name: String,
  val position: Int,
  val sort: ShelfSort = ShelfSort.MANUAL,
  val expanded: Boolean = true,
) {
  public companion object {
    public const val OTHER: String = "other"
    public val Other: LibraryShelf = LibraryShelf(OTHER, "", Int.MAX_VALUE)
  }
}

@Serializable
public enum class ShelfSort { MANUAL, TITLE, AUTHOR, RECENTLY_ADDED }

@Serializable
@Entity(tableName = "librarySeries")
public data class LibrarySeries(
  @PrimaryKey val id: String,
  val name: String,
)

/** Organisation is deliberately separate from content2: playback and rescans cannot overwrite it. */
@Serializable
@Entity(tableName = "libraryPlacement")
public data class LibraryPlacement(
  @PrimaryKey val bookId: String,
  val shelfId: String = LibraryShelf.OTHER,
  val position: Int,
  val seriesId: String? = null,
  val seriesPosition: Int? = null,
) {
  public val itemId: String get() = seriesId?.let { "series:$it" } ?: "book:$bookId"
}

@Serializable
public data class LibraryOrganisation(
  val shelves: List<LibraryShelf> = listOf(LibraryShelf.Other),
  val series: List<LibrarySeries> = emptyList(),
  val placements: List<LibraryPlacement> = emptyList(),
) {
  public fun itemOrder(shelfId: String): List<String> = placements.filter { it.shelfId == shelfId }
    .sortedWith(compareBy<LibraryPlacement> { it.position }.thenBy { it.itemId }).map { it.itemId }.distinct()

  public fun validate() {
    require(shelves.count { it.id == LibraryShelf.OTHER } == 1)
    require(shelves.map { it.id }.distinct().size == shelves.size)
    require(series.map { it.id }.distinct().size == series.size)
    require(placements.map { it.bookId }.distinct().size == placements.size)
    val shelfIds = shelves.map { it.id }.toSet()
    val seriesIds = series.map { it.id }.toSet()
    require(placements.all { it.shelfId in shelfIds && (it.seriesId == null || it.seriesId in seriesIds) })
    placements.filter { it.seriesId != null }.groupBy { it.seriesId }.values.forEach { members ->
      require(members.map { it.shelfId to it.position }.distinct().size == 1)
      require(members.all { it.seriesPosition != null })
      require(members.map { it.seriesPosition }.distinct().size == members.size)
    }
  }
}

/** An undo token contains organisation only, never playback, chapters, covers or bookmarks. */
public data class LibraryChange(
  val before: LibraryOrganisation,
  val after: LibraryOrganisation,
)

public enum class LibraryEditError { EMPTY_NAME, DUPLICATE_SHELF, DUPLICATE_SERIES, CHOOSE_SHELF, STALE_ORDER }
public class LibraryEditException(public val reason: LibraryEditError) : IllegalArgumentException(reason.name)
