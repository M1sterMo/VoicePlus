package voice.features.folderPicker.selectType

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.ui.VoiceTheme
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Origin
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface SelectFolderTypeGraph {
  val selectFolderTypeViewModelFactory: SelectFolderTypeViewModel.Factory
}

@ContributesTo(AppScope::class)
interface SelectFolderTypeProvider {

  @Provides
  @IntoSet
  fun selectFolderTypeNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.SelectFolderType> { key ->
    NavEntry(key) {
      SelectFolderType(
        uri = key.uri,
        origin = key.origin,
      )
    }
  }
}

@Composable
fun SelectFolderType(
  uri: Uri,
  origin: Origin,
) {
  val context = LocalContext.current
  val viewModel = retain(uri.toString(), origin.name) {
    rootGraphAs<SelectFolderTypeGraph>().selectFolderTypeViewModelFactory
      .create(
        uri = uri,
        origin = origin,
        documentFile = DocumentFile.fromTreeUri(context, uri)!!,
      )
  }
  SelectFolderType(
    viewState = viewModel.viewState(),
    onModeSelect = viewModel::selectMode,
    onAddClick = viewModel::add,
    onBackClick = viewModel::onCloseClick,
  )
}

@Composable
private fun SelectFolderType(
  viewState: SelectFolderTypeViewState,
  onModeSelect: (FolderMode) -> Unit,
  onAddClick: () -> Unit,
  onBackClick: () -> Unit,
) {
  var showModes by rememberSaveable { mutableStateOf(false) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = {},
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(StringsR.string.close),
            )
          }
        },
      )
    },
    bottomBar = {
      ReviewActions(
        viewState = viewState,
        onAddClick = onAddClick,
        onChangeClick = { showModes = true },
        onChooseAnotherFolder = onBackClick,
      )
    },
  ) { contentPadding ->
    ReviewContent(contentPadding = contentPadding, viewState = viewState)
  }
  if (showModes) {
    FolderModeSheet(
      viewState = viewState,
      onModeSelect = {
        onModeSelect(it)
        showModes = false
      },
      onDismiss = { showModes = false },
    )
  }
}

@Composable
private fun ReviewContent(
  contentPadding: PaddingValues,
  viewState: SelectFolderTypeViewState,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = contentPadding,
  ) {
    item(key = "header") {
      ReviewHeader(viewState)
    }
    if (viewState.loading) {
      item(key = "loading") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
    } else {
      items(viewState.books) { book ->
        ListItem(
          headlineContent = { Text(book.name) },
          supportingContent = { Text(book.details()) },
        )
      }
    }
  }
}

@Composable
private fun ReviewHeader(viewState: SelectFolderTypeViewState) {
  val empty = !viewState.loading && viewState.books.isEmpty()
  Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
    Text(
      text = when {
        viewState.loading -> stringResource(StringsR.string.folder_review_loading, viewState.folderName)
        empty -> stringResource(StringsR.string.folder_review_empty_title, viewState.folderName)
        else -> pluralStringResource(StringsR.plurals.folder_review_title, viewState.books.size, viewState.books.size)
      },
      style = MaterialTheme.typography.headlineMedium,
    )
    if (!viewState.loading) {
      Text(
        modifier = Modifier.padding(top = 8.dp),
        text = if (empty) {
          stringResource(StringsR.string.folder_review_empty_subtitle)
        } else {
          viewState.selectedMode.optionTitle(viewState.folderName)
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SelectFolderTypeViewState.Book.details(): String {
  return listOfNotNull(
    author,
    pluralStringResource(StringsR.plurals.folder_type_file_count, fileCount, fileCount),
  ).joinToString(separator = " · ")
}

@Composable
private fun ReviewActions(
  viewState: SelectFolderTypeViewState,
  onAddClick: () -> Unit,
  onChangeClick: () -> Unit,
  onChooseAnotherFolder: () -> Unit,
) {
  Surface {
    Column(
      modifier = Modifier
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val buttonModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
      if (!viewState.loading && viewState.books.isEmpty()) {
        Button(modifier = buttonModifier, onClick = onChooseAnotherFolder) {
          Text(stringResource(StringsR.string.folder_add_action_folder))
        }
      } else {
        Button(modifier = buttonModifier, onClick = onAddClick, enabled = !viewState.loading) {
          Text(pluralStringResource(StringsR.plurals.folder_review_action_add, viewState.books.size, viewState.books.size))
        }
        TextButton(modifier = Modifier.fillMaxWidth(), onClick = onChangeClick, enabled = !viewState.loading) {
          Text(stringResource(StringsR.string.folder_review_action_change))
        }
      }
    }
  }
}

@Preview
@Composable
private fun SelectFolderTypePreview() {
  val books = listOf(
    SelectFolderTypeViewState.Book(name = "Dune", author = "Frank Herbert", fileCount = 24),
    SelectFolderTypeViewState.Book(name = "Hyperion", author = null, fileCount = 31),
  )
  VoiceTheme {
    SelectFolderType(
      viewState = SelectFolderTypeViewState(
        folderName = "Audiobooks",
        selectedMode = FolderMode.Audiobooks,
        guessedMode = FolderMode.Audiobooks,
        booksByMode = FolderMode.entries.associateWith { books },
      ),
      onModeSelect = {},
      onAddClick = {},
      onBackClick = {},
    )
  }
}
