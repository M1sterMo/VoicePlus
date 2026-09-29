package voice.core.data.repo.internals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import voice.core.data.LibraryOrganisation
import voice.core.data.LibraryPlacement
import voice.core.data.LibrarySeries
import voice.core.data.LibraryShelf

@Dao
public interface LibraryOrganisationDao {
  // Observe all three tables, including shelf-only edits. The value is just an invalidation signal.
  @Query("SELECT id FROM libraryShelf UNION SELECT id FROM librarySeries UNION SELECT bookId FROM libraryPlacement")
  public fun changes(): Flow<List<String>>

  @Query("SELECT * FROM libraryShelf ORDER BY position, id")
  public suspend fun shelves(): List<LibraryShelf>

  @Query("SELECT * FROM librarySeries ORDER BY id")
  public suspend fun series(): List<LibrarySeries>

  @Query("SELECT * FROM libraryPlacement ORDER BY bookId")
  public suspend fun placements(): List<LibraryPlacement>

  @Transaction
  public suspend fun get(): LibraryOrganisation {
    val shelves = shelves()
    if (shelves.isEmpty()) ensureOtherShelf()
    return LibraryOrganisation(shelves.ifEmpty { listOf(LibraryShelf.Other) }, series(), placements())
  }

  @Query("INSERT OR IGNORE INTO libraryShelf (id, name, position, sort, expanded) VALUES ('other', '', 2147483647, 'MANUAL', 1)")
  public suspend fun ensureOtherShelf()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  public suspend fun putShelves(shelves: List<LibraryShelf>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  public suspend fun putSeries(series: List<LibrarySeries>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  public suspend fun putPlacements(placements: List<LibraryPlacement>)

  @Query("DELETE FROM libraryShelf WHERE id IN (:ids)")
  public suspend fun deleteShelves(ids: List<String>)

  @Query("DELETE FROM librarySeries WHERE id IN (:ids)")
  public suspend fun deleteSeries(ids: List<String>)

  @Query("DELETE FROM libraryPlacement WHERE bookId IN (:ids)")
  public suspend fun deletePlacements(ids: List<String>)

  @Transaction
  public suspend fun replace(
    before: LibraryOrganisation,
    after: LibraryOrganisation,
  ) {
    after.validate()
    deleteShelves(before.shelves.map { it.id } - after.shelves.map { it.id }.toSet())
    deleteSeries(before.series.map { it.id } - after.series.map { it.id }.toSet())
    deletePlacements(before.placements.map { it.bookId } - after.placements.map { it.bookId }.toSet())
    putShelves(after.shelves.filter { it !in before.shelves })
    putSeries(after.series.filter { it !in before.series })
    putPlacements(after.placements.filter { it !in before.placements })
  }
}
