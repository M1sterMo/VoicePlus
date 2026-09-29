package voice.core.data.repo.internals.migrations

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import voice.core.common.comparator.NaturalOrderComparator
import java.util.UUID

/** Preserve the old name-based series once, then use stable identities for every organisation edit. */
internal class Migration68 : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    db.execSQL("INSERT INTO libraryShelf (id, name, position, sort, expanded) VALUES ('other', '', 2147483647, 'MANUAL', 1)")
    data class Row(
      val id: String,
      val name: String,
      val group: String?,
      val order: Int?,
      val part: String?,
    )
    val books = buildList {
      db.query("SELECT id, name, seriesGroup, seriesOrder, part FROM content2").use { cursor ->
        while (cursor.moveToNext()) {
          add(
            Row(
              cursor.getString(0),
              cursor.getString(1),
              cursor.getString(2),
              if (cursor.isNull(3)) null else cursor.getInt(3),
              cursor.getString(4),
            ),
          )
        }
      }
    }
    val groups = books.groupBy { it.group?.let { name -> "series:$name" } ?: "book:${it.id}" }.values
      .sortedWith(
        compareBy<List<Row>, String>(NaturalOrderComparator.stringComparator) {
          it.first().group ?: it.first().name
        }.thenBy { it.first().id },
      )
    groups.forEachIndexed { position, members ->
      val name = members.first().group
      val seriesId = name?.let { UUID.randomUUID().toString() }
      if (seriesId != null) db.execSQL("INSERT INTO librarySeries (id, name) VALUES (?, ?)", arrayOf(seriesId, name))
      members.sortedWith(
        compareBy<Row, Int?>(nullsLast()) { it.order }
          .thenBy(nullsLast(NaturalOrderComparator.stringComparator)) { it.part?.takeIf(String::isNotBlank) }
          .thenBy(NaturalOrderComparator.stringComparator) { it.name }.thenBy { it.id },
      )
        .forEachIndexed { seriesPosition, book ->
          db.execSQL(
            "INSERT INTO libraryPlacement (bookId, shelfId, position, seriesId, seriesPosition) VALUES (?, 'other', ?, ?, ?)",
            arrayOf<Any?>(book.id, position, seriesId, seriesPosition.takeIf { seriesId != null }),
          )
        }
    }
  }
}
