package voice.features.bookOverview.di

import androidx.compose.runtime.retain.RetainObserver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import voice.features.bookOverview.bottomSheet.BottomSheetViewModel
import voice.features.bookOverview.deleteBook.DeleteBookViewModel
import voice.features.bookOverview.editBook.EditBookViewModel
import voice.features.bookOverview.overview.BookOverviewViewModel
import voice.features.bookOverview.series.SeriesViewModel
import voice.features.bookOverview.shelves.ShelfViewModel

abstract class BookOverviewScope private constructor()

@GraphExtension(scope = BookOverviewScope::class)
interface BookOverviewGraph : RetainObserver {
  val bookOverviewViewModel: BookOverviewViewModel
  val editBookViewModel: EditBookViewModel
  val bottomSheetViewModel: BottomSheetViewModel
  val deleteBookViewModel: DeleteBookViewModel
  val seriesViewModel: SeriesViewModel
  val shelfViewModel: ShelfViewModel

  override fun onRetained() = Unit

  override fun onEnteredComposition() = Unit

  override fun onExitedComposition() = Unit

  override fun onRetired() {
    bookOverviewViewModel.onRetired()
    editBookViewModel.onRetired()
    bottomSheetViewModel.onRetired()
    deleteBookViewModel.onRetired()
    seriesViewModel.onRetired()
    shelfViewModel.onRetired()
  }

  override fun onUnused() {
    bookOverviewViewModel.onUnused()
    editBookViewModel.onUnused()
    bottomSheetViewModel.onUnused()
    deleteBookViewModel.onUnused()
    seriesViewModel.onUnused()
    shelfViewModel.onUnused()
  }

  @GraphExtension.Factory
  @ContributesTo(AppScope::class)
  interface Factory {
    fun create(): BookOverviewGraph

    @ContributesTo(AppScope::class)
    interface Provider {
      val bookOverviewGraphProviderFactory: Factory
    }
  }
}
