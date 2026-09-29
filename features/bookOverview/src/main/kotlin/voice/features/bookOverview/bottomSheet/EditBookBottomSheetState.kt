package voice.features.bookOverview.bottomSheet

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.NotStarted
import androidx.compose.material.icons.outlined.Title
import androidx.compose.ui.graphics.vector.ImageVector
import voice.core.strings.R as StringsR

internal data class EditBookBottomSheetState(val items: List<BottomSheetItem>)

enum class BottomSheetItem(
  @StringRes val titleRes: Int,
  val icon: ImageVector,
) {
  EditBook(StringsR.string.edit_book_title, Icons.Outlined.Title),
  Series(StringsR.string.series_edit, Icons.AutoMirrored.Outlined.LibraryBooks),
  ManageSeries(StringsR.string.series_manage, Icons.AutoMirrored.Outlined.LibraryBooks),
  MoveToShelf(StringsR.string.shelf_move, Icons.AutoMirrored.Outlined.LibraryBooks),
  MoveSeriesToShelf(StringsR.string.shelf_move_series, Icons.AutoMirrored.Outlined.LibraryBooks),
  RemoveFromSeries(StringsR.string.series_remove_member, Icons.AutoMirrored.Outlined.LibraryBooks),
  DeleteBook(StringsR.string.delete_book_bottom_sheet_title, Icons.Outlined.Delete),
  BookCategoryMarkAsNotStarted(StringsR.string.mark_as_not_started, Icons.Outlined.HourglassEmpty),
  BookCategoryMarkAsCurrent(StringsR.string.mark_as_current, Icons.Outlined.NotStarted),
  BookCategoryMarkAsCompleted(StringsR.string.mark_as_completed, Icons.Outlined.Done),
}
