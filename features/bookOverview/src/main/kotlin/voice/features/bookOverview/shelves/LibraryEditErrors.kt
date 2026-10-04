package voice.features.bookOverview.shelves

import androidx.annotation.StringRes
import voice.core.data.LibraryEditError
import voice.core.data.LibraryEditException
import voice.core.strings.R

@StringRes
internal fun Exception.libraryErrorMessage(): Int = when ((this as? LibraryEditException)?.reason) {
  LibraryEditError.EMPTY_NAME -> R.string.library_name_required
  LibraryEditError.DUPLICATE_SHELF -> R.string.library_shelf_name_exists
  LibraryEditError.DUPLICATE_SERIES -> R.string.library_series_name_exists
  LibraryEditError.CHOOSE_SHELF -> R.string.library_choose_shelf
  LibraryEditError.STALE_ORDER -> R.string.library_order_changed
  null -> R.string.library_save_failed
}
