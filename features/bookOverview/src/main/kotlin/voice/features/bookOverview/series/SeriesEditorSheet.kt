package voice.features.bookOverview.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@Composable
internal fun SeriesEditorSheet(
  state: SeriesEditorState,
  onDismiss: () -> Unit,
  onNameChange: (String) -> Unit,
  onSuggestion: (String) -> Unit,
  onToggle: (BookId) -> Unit,
  onMove: (BookId, Int) -> Unit,
  onSave: () -> Unit,
  onUngroup: () -> Unit,
  onShelfChange: (String) -> Unit = {},
) {
  var adding by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable { mutableStateOf("") }
  var confirmUngroup by rememberSaveable { mutableStateOf(false) }
  val editing = state.originalName != null
  val listState = rememberLazyListState()
  val reorder = rememberSeriesReorderState(listState, onMove)
  val focusManager = LocalFocusManager.current
  LaunchedEffect(adding) {
    focusManager.clearFocus()
    if (!adding) listState.scrollToItem(0)
  }
  ModalBottomSheet(
    onDismissRequest = { if (adding) adding = false else onDismiss() },
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    sheetGesturesEnabled = false,
    dragHandle = null,
  ) {
    BackHandler(enabled = adding) { adding = false }
    Column(
      Modifier.fillMaxHeight(0.92f).imePadding().padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (adding) {
          IconButton(onClick = { adding = false }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(StringsR.string.series_back_to_editor))
          }
        }
        Text(
          stringResource(
            if (adding) {
              StringsR.string.series_add_books
            } else if (editing) {
              StringsR.string.series_manage
            } else {
              StringsR.string.series_edit
            },
          ),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f),
        )
        if (!adding) {
          IconButton(onClick = onDismiss, enabled = !state.saving) {
            Icon(Icons.Outlined.Close, stringResource(StringsR.string.close))
          }
        }
      }
      if (adding) {
        Text(
          stringResource(
            StringsR.string.library_books_move_to,
            state.organisation.shelves.find { it.id == state.destination }?.name?.ifEmpty { stringResource(StringsR.string.library_other) }
              ?: stringResource(StringsR.string.library_chosen_shelf),
          ),
          style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          label = { Text(stringResource(StringsR.string.series_search_books)) },
          modifier = Modifier.fillMaxWidth(),
        )
        val matches = state.books.filter {
          (it.isActive || it.id in state.selected) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.author.orEmpty().contains(query, ignoreCase = true))
        }
        LazyColumn(Modifier.weight(1f).testTag("series-picker"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          if (matches.isEmpty()) item { Text(stringResource(StringsR.string.series_no_matches), Modifier.padding(16.dp)) }
          items(matches, key = { it.id.value }) { book ->
            val selected = book.id in state.selected
            Row(
              Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                .toggleable(selected, role = Role.Checkbox, onValueChange = { onToggle(book.id) })
                .padding(8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              EditorCover(book)
              Column(Modifier.weight(1f)) {
                Text(book.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.seriesGroup != null && book.seriesGroup != state.originalName) {
                  Text(
                    stringResource(StringsR.string.series_move_from, book.seriesGroup.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }
              Checkbox(selected, onCheckedChange = null)
            }
          }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(pluralStringResource(StringsR.plurals.series_book_count, state.selected.size, state.selected.size), Modifier.weight(1f))
          Button(onClick = { adding = false }) { Text(stringResource(StringsR.string.series_done_adding)) }
        }
      } else {
        var choosingShelf by remember { mutableStateOf(false) }
        OutlinedTextField(
          value = state.name,
          onValueChange = onNameChange,
          singleLine = true,
          enabled = !state.saving,
          label = { Text(stringResource(StringsR.string.series_name)) },
          modifier = Modifier.fillMaxWidth(),
        )
        Box {
          TextButton(onClick = { choosingShelf = true }, enabled = state.seriesId == null && !state.saving) {
            Text(
              stringResource(
                StringsR.string.library_series_shelf,
                state.organisation.shelves.find {
                  it.id == state.destination
                }?.name?.ifEmpty { stringResource(StringsR.string.library_other) }
                  ?: stringResource(StringsR.string.library_choose_shelf),
              ),
            )
          }
          DropdownMenu(choosingShelf, onDismissRequest = { choosingShelf = false }) {
            state.organisation.shelves.forEach { shelf ->
              DropdownMenuItem(text = { Text(shelf.name.ifEmpty { stringResource(StringsR.string.library_other) }) }, onClick = {
                onShelfChange(shelf.id)
                choosingShelf =
                  false
              })
            }
          }
        }
        if (state.error !=
          null
        ) {
          Text(stringResource(state.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        val suggestions = state.suggestions.filter { name -> name != state.name || state.books.any { it.seriesGroup == name } }
        if (!editing && suggestions.isNotEmpty()) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suggestions, key = { it }) { name ->
              SuggestionChip(onClick = { onSuggestion(name) }, label = { Text(name, maxLines = 1) }, enabled = !state.saving)
            }
          }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(StringsR.string.series_book_order), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
          TextButton(onClick = {
            adding = true
            query = ""
          }, enabled = !state.saving) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(StringsR.string.series_add_books), Modifier.padding(start = 8.dp))
          }
        }
        Text(
          stringResource(StringsR.string.series_reorder_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
          Modifier.weight(1f).testTag("series-books"),
          state = listState,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (state.selected.isEmpty()) {
            item {
              Text(stringResource(StringsR.string.series_empty), Modifier.padding(vertical = 16.dp))
            }
          }
          itemsIndexed(state.selectedBooks, key = { _, book -> book.id.value }) { index, book ->
            val active = reorder.draggedId == book.id
            val returning = reorder.returningId == book.id
            val motion = when {
              active -> Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offset }
              returning -> Modifier.zIndex(1f).graphicsLayer { translationY = reorder.returnOffset.value }
              else -> Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
            }
            var menu by remember { mutableStateOf(false) }
            val up = stringResource(StringsR.string.series_move_up)
            val down = stringResource(StringsR.string.series_move_down)
            val remove = stringResource(StringsR.string.series_remove_book)
            val position = stringResource(StringsR.string.series_position, index + 1, state.selected.size)
            Row(
              Modifier.then(motion).testTag("series-order:${book.id.value}").fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                .semantics(mergeDescendants = true) {
                  stateDescription = position
                  if (!state.saving) {
                    customActions = buildList {
                      if (index > 0) {
                        add(
                          CustomAccessibilityAction(up) {
                            onMove(book.id, index - 1)
                            true
                          },
                        )
                      }
                      if (index < state.selected.lastIndex) {
                        add(
                          CustomAccessibilityAction(down) {
                            onMove(book.id, index + 1)
                            true
                          },
                        )
                      }
                      add(
                        CustomAccessibilityAction(remove) {
                          onToggle(book.id)
                          true
                        },
                      )
                    }
                  }
                }.padding(8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                "${index + 1}",
                Modifier.width(24.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              EditorCover(book)
              Text(
                book.name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Box {
                IconButton(
                  onClick = { menu = true },
                  enabled = !state.saving,
                  modifier = Modifier.testTag("series-reorder:${book.id.value}").pointerInput(reorder, book.id, state.saving) {
                    if (!state.saving) {
                      detectDragGestures(
                        onDragStart = { reorder.start(book.id) },
                        onDrag = { change, amount ->
                          change.consume()
                          reorder.drag(amount.y)
                        },
                        onDragEnd = reorder::stop,
                        onDragCancel = reorder::stop,
                      )
                    }
                  },
                ) {
                  Icon(Icons.Outlined.DragHandle, stringResource(StringsR.string.series_reorder_book, book.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                  DropdownMenuItem(text = { Text(up) }, enabled = index > 0, onClick = {
                    menu = false
                    onMove(book.id, index - 1)
                  })
                  DropdownMenuItem(text = { Text(down) }, enabled = index < state.selected.lastIndex, onClick = {
                    menu = false
                    onMove(
                      book.id,
                      index + 1,
                    )
                  })
                  DropdownMenuItem(text = { Text(remove) }, onClick = {
                    menu = false
                    onToggle(book.id)
                  })
                }
              }
            }
          }
        }
        if (state.failed) Text(stringResource(StringsR.string.series_save_error), color = MaterialTheme.colorScheme.error)
        Row(
          Modifier.fillMaxWidth().padding(bottom = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (editing) {
            TextButton(onClick = {
              confirmUngroup = true
            }, enabled = !state.saving) { Text(stringResource(StringsR.string.series_ungroup)) }
          } else {
            Text(pluralStringResource(StringsR.plurals.series_book_count, state.selected.size, state.selected.size))
          }
          Button(onClick = onSave, enabled = state.canSave && reorder.draggedId == null) {
            Text(stringResource(StringsR.string.series_save))
          }
        }
      }
    }
  }
  if (confirmUngroup) {
    AlertDialog(
      modifier = Modifier.testTag("series-ungroup-confirmation"),
      onDismissRequest = { confirmUngroup = false },
      title = { Text(stringResource(StringsR.string.series_ungroup)) },
      text = { Text(stringResource(StringsR.string.series_ungroup_hint)) },
      confirmButton = {
        TextButton(onClick = {
          confirmUngroup = false
          onUngroup()
        }) { Text(stringResource(StringsR.string.series_ungroup)) }
      },
      dismissButton = { TextButton(onClick = { confirmUngroup = false }) { Text(stringResource(StringsR.string.dialog_cancel)) } },
    )
  }
}

@Composable
private fun EditorCover(book: BookContent) {
  AsyncImage(
    model = book.cover,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    placeholder = painterResource(UiR.drawable.album_art),
    error = painterResource(UiR.drawable.album_art),
    modifier = Modifier.size(40.dp, 56.dp).clip(MaterialTheme.shapes.extraSmall),
  )
}
