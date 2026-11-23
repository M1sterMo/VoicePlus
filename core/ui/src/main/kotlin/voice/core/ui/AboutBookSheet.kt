package voice.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.repo.LibraryOrganisationRepo
import voice.core.data.seriesNameOf
import voice.core.data.shownSynopsis
import voice.core.strings.R as StringsR

@Immutable
data class AboutBookState(
  val id: BookId,
  val title: String,
  val author: String?,
  val narrator: String?,
  val cover: ImmutableFile?,
  val rating: Int?,
  val series: String?,
  val part: String?,
  val genre: String?,
  val length: String,
  val synopsis: String?,
)

/** The book's About state, kept current as it is rated, edited or re-scanned; null once it is gone. */
fun aboutBookState(
  repo: BookRepository,
  organisation: LibraryOrganisationRepo,
  id: BookId,
): Flow<AboutBookState?> = combine(repo.flow(id), organisation.flow()) { book, library ->
  book?.aboutBookState(library.seriesNameOf(id) ?: book.content.series?.takeIf { it.isNotBlank() })
}

private fun Book.aboutBookState(series: String?): AboutBookState = AboutBookState(
  id = id,
  title = content.name,
  author = content.author,
  narrator = content.narrator?.takeIf { it.isNotBlank() && it != content.author },
  cover = content.cover?.let(::ImmutableFile),
  rating = content.rating,
  series = series,
  part = content.part?.takeIf { series != null && it.isNotBlank() },
  genre = content.genre?.takeIf { it.isNotBlank() },
  length = formatTime(duration),
  synopsis = content.shownSynopsis,
)

/** The book's details, its star rating and its synopsis, from the library or the player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBookSheet(
  state: AboutBookState,
  onDismiss: () -> Unit,
  onRate: (Int?) -> Unit,
  /** Opens Edit book; null where the editor is not available, which hides the edit actions. */
  onEdit: (() -> Unit)?,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(
      Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BookshelfCover(state.id, state.cover, Modifier.width(96.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(state.title, style = MaterialTheme.typography.titleLarge)
          state.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
          state.narrator?.let {
            Text(
              stringResource(StringsR.string.about_book_narrator, it),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (onEdit != null) {
          IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(StringsR.string.edit_book_title))
          }
        }
      }
      RatingStars(state.rating, onRate)
      val series = state.series?.let { name ->
        state.part?.let { "$name · ${stringResource(StringsR.string.series_volume, it)}" } ?: name
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOfNotNull(series, state.genre, state.length).forEach { Chip(it) }
      }
      Synopsis(state.synopsis, onEdit)
    }
  }
}

@Composable
private fun Chip(text: String) {
  Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
    Text(
      text,
      Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

@Composable
private fun Synopsis(
  synopsis: String?,
  onEdit: (() -> Unit)?,
) {
  if (synopsis == null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        stringResource(StringsR.string.about_book_no_synopsis),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (onEdit != null) TextButton(onClick = onEdit) { Text(stringResource(StringsR.string.about_book_add_synopsis)) }
    }
    return
  }
  var expanded by rememberSaveable(synopsis) { mutableStateOf(false) }
  var overflows by rememberSaveable(synopsis) { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
    Text(
      synopsis,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
      overflow = TextOverflow.Ellipsis,
      onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
    )
    if (overflows || expanded) {
      TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) StringsR.string.about_book_less else StringsR.string.more))
      }
    }
  }
}

private const val COLLAPSED_LINES = 6
