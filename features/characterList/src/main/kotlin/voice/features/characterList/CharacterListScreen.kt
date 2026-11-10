package voice.features.characterList

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface CharacterListGraph {
  val characterListViewModelFactory: CharacterListViewModel.Factory
}

@ContributesTo(AppScope::class)
interface CharacterListProvider {

  @Provides
  @IntoSet
  fun characterListNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.CharacterList> { key ->
    NavEntry(key) {
      CharacterListScreen(bookId = key.bookId)
    }
  }
}

@Composable
fun CharacterListScreen(bookId: BookId) {
  val viewModel = retain(bookId.value) {
    rootGraphAs<CharacterListGraph>().characterListViewModelFactory.create(bookId)
  }
  val viewState = viewModel.viewState()
  CharacterListScreen(
    viewState = viewState,
    onClose = viewModel::onClose,
    onAdd = viewModel::addCharacter,
    onUpdate = viewModel::updateCharacter,
    onDelete = viewModel::deleteCharacter,
  )
}

private sealed interface CharacterDialog {
  data object Add : CharacterDialog
  data class Edit(val item: CharacterItemViewState) : CharacterDialog
  data class DeleteConfirm(val item: CharacterItemViewState) : CharacterDialog
}

@Composable
internal fun CharacterListScreen(
  viewState: CharacterListViewState,
  onClose: () -> Unit,
  onAdd: (name: String, description: String) -> Unit,
  onUpdate: (id: Long, name: String, description: String, position: Int) -> Unit,
  onDelete: (id: Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var dialog by remember { mutableStateOf<CharacterDialog?>(null) }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(stringResource(StringsR.string.character_list))
            viewState.series?.let { series ->
              Text(
                text = pluralStringResource(StringsR.plurals.character_list_shared, series.bookCount, series.name, series.bookCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(StringsR.string.close))
          }
        },
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { dialog = CharacterDialog.Add }) {
        Icon(Icons.Default.Add, contentDescription = stringResource(StringsR.string.character_list_add))
      }
    },
  ) { paddingValues ->
    if (viewState.characters.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(StringsR.string.character_list_empty),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(contentPadding = paddingValues) {
        itemsIndexed(items = viewState.characters, key = { _, item -> item.id }) { index, character ->
          CharacterItem(
            character = character,
            onEdit = { dialog = CharacterDialog.Edit(character) },
          )
          if (index < viewState.characters.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
        item { Spacer(Modifier.size(80.dp)) }
      }
    }
  }

  when (val d = dialog) {
    CharacterDialog.Add -> {
      CharacterFormDialog(
        title = stringResource(StringsR.string.character_list_add),
        initialName = "",
        initialDescription = "",
        initialPosition = null,
        totalCharacters = viewState.characters.size,
        confirmLabel = stringResource(StringsR.string.add),
        onConfirm = { name, desc, _ ->
          onAdd(name, desc)
          dialog = null
        },
        onDismiss = { dialog = null },
      )
    }
    is CharacterDialog.Edit -> {
      CharacterFormDialog(
        title = stringResource(StringsR.string.character_edit),
        initialName = d.item.name,
        initialDescription = d.item.description,
        initialPosition = d.item.position,
        totalCharacters = viewState.characters.size,
        confirmLabel = stringResource(StringsR.string.character_save),
        onConfirm = { name, desc, position ->
          onUpdate(d.item.id, name, desc, position)
          dialog = null
        },
        onDelete = { dialog = CharacterDialog.DeleteConfirm(d.item) },
        onDismiss = { dialog = null },
      )
    }
    is CharacterDialog.DeleteConfirm -> {
      AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(stringResource(StringsR.string.character_delete_title)) },
        text = { Text(stringResource(StringsR.string.character_delete_confirmation)) },
        confirmButton = {
          TextButton(
            onClick = {
              onDelete(d.item.id)
              dialog = null
            },
          ) {
            Text(stringResource(StringsR.string.delete))
          }
        },
        dismissButton = {
          TextButton(onClick = { dialog = null }) {
            Text(stringResource(StringsR.string.dialog_cancel))
          }
        },
      )
    }
    null -> {}
  }
}

@Composable
private fun CharacterItem(
  character: CharacterItemViewState,
  onEdit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ListItem(
    modifier = modifier
      .fillMaxWidth()
      .clickable(
        onClickLabel = stringResource(StringsR.string.character_edit),
        onClick = onEdit,
      ),
    headlineContent = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = character.name,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        character.originBook?.let { book ->
          Spacer(Modifier.width(8.dp))
          OriginChip(book = book, openBook = character.fromOpenBook)
        }
      }
    },
    supportingContent = character.description.takeIf { it.isNotBlank() }?.let { description ->
      {
        Text(
          text = description,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    trailingContent = {
      IconButton(onClick = onEdit) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = stringResource(StringsR.string.character_edit),
        )
      }
    },
  )
}

@Composable
private fun OriginChip(
  book: Int,
  openBook: Boolean,
) {
  Surface(
    shape = CircleShape,
    color = if (openBook) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = if (openBook) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Text(
      text = stringResource(StringsR.string.character_origin_book, book),
      style = MaterialTheme.typography.labelMedium,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
    )
  }
}

@Composable
private fun CharacterFormDialog(
  title: String,
  initialName: String,
  initialDescription: String,
  initialPosition: Int?,
  totalCharacters: Int,
  confirmLabel: String,
  onConfirm: (name: String, description: String, position: Int) -> Unit,
  onDelete: (() -> Unit)? = null,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  var description by remember { mutableStateOf(initialDescription) }
  var positionText by remember { mutableStateOf(initialPosition?.toString() ?: "") }

  val position = positionText.toIntOrNull()?.coerceIn(1, maxOf(1, totalCharacters)) ?: (initialPosition ?: (totalCharacters + 1))

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(StringsR.string.character_name)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(stringResource(StringsR.string.character_description)) },
          minLines = 3,
          maxLines = 8,
          modifier = Modifier.fillMaxWidth(),
        )
        if (initialPosition != null) {
          Spacer(Modifier.size(12.dp))
          OutlinedTextField(
            value = positionText,
            onValueChange = { positionText = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(StringsR.string.character_position_label, totalCharacters)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Number,
              imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (onDelete != null) {
          Spacer(Modifier.size(8.dp))
          TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
          ) {
            Text(stringResource(StringsR.string.character_delete))
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(name, description, position) },
        enabled = name.isNotBlank(),
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.dialog_cancel))
      }
    },
  )
}
