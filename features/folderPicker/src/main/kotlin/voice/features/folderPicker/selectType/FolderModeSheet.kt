package voice.features.folderPicker.selectType

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import voice.core.strings.R as StringsR

@Composable
internal fun FolderModeSheet(
  viewState: SelectFolderTypeViewState,
  onModeSelect: (FolderMode) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
    ) {
      Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = stringResource(StringsR.string.folder_mode_sheet_title, viewState.folderName),
        style = MaterialTheme.typography.headlineSmall,
      )
      Column(
        modifier = Modifier
          .padding(top = 24.dp)
          .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FolderMode.entries.forEach { mode ->
          FolderModeOption(
            mode = mode,
            viewState = viewState,
            onClick = { onModeSelect(mode) },
          )
        }
      }
    }
  }
}

@Composable
private fun FolderModeOption(
  mode: FolderMode,
  viewState: SelectFolderTypeViewState,
  onClick: () -> Unit,
) {
  val selected = mode == viewState.selectedMode
  val books = viewState.booksByMode?.get(mode).orEmpty()
  val colorScheme = MaterialTheme.colorScheme
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    shape = MaterialTheme.shapes.large,
    color = if (selected) colorScheme.secondaryContainer else colorScheme.surfaceContainerHigh,
    border = if (selected) BorderStroke(2.dp, colorScheme.primary) else null,
  ) {
    Row(
      modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RadioButton(
        selected = selected,
        onClick = null,
        modifier = Modifier.padding(horizontal = 12.dp),
      )
      Column(Modifier.weight(1F)) {
        if (mode == viewState.guessedMode) {
          GuessBadge()
        }
        Text(
          text = mode.optionTitle(viewState.folderName),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = mode.summary(books),
          style = MaterialTheme.typography.bodyMedium,
          color = colorScheme.onSurfaceVariant,
          maxLines = 2,
        )
        Text(
          modifier = Modifier.padding(top = 4.dp),
          text = pluralStringResource(StringsR.plurals.folder_mode_book_count, books.size, books.size),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Composable
private fun GuessBadge() {
  Surface(
    modifier = Modifier.padding(bottom = 6.dp),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.tertiary,
    contentColor = MaterialTheme.colorScheme.onTertiary,
  ) {
    Text(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
      text = stringResource(StringsR.string.folder_mode_guess),
      style = MaterialTheme.typography.labelMedium,
    )
  }
}

@Composable
private fun FolderMode.summary(books: List<SelectFolderTypeViewState.Book>): String {
  return when (this) {
    FolderMode.SingleBook -> stringResource(StringsR.string.folder_mode_single_summary)
    FolderMode.Audiobooks,
    FolderMode.Authors,
    -> {
      val examples = books.take(3).joinToString(separator = ", ") { it.name }
      if (books.size > 3) "$examples, …" else examples
    }
  }
}

@Composable
internal fun FolderMode.optionTitle(folderName: String): String {
  val res = when (this) {
    FolderMode.SingleBook -> StringsR.string.folder_mode_single_option
    FolderMode.Audiobooks -> StringsR.string.folder_mode_root_option
    FolderMode.Authors -> StringsR.string.folder_mode_author_option
  }
  return stringResource(res, folderName)
}
