package voice.features.bookOverview.shelves

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import voice.core.data.BookId
import voice.core.data.LibraryOrganisation
import voice.core.data.LibraryShelf
import voice.core.data.ShelfSort
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.core.strings.R as StringsR

@Composable
internal fun ShelfLibraryBar(
  model: ShelfViewModel,
  organisation: LibraryOrganisation,
  onSearch: () -> Unit,
  onSettings: () -> Unit,
  onImport: () -> Unit,
  onAddToSeries: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  Column {
    TopAppBar(
      title = {
        Text(
          if (model.selecting) {
            pluralStringResource(
              StringsR.plurals.library_selected,
              model.selected.size,
              model.selected.size,
            )
          } else if (model.organising) {
            stringResource(StringsR.string.library_organise)
          } else {
            stringResource(StringsR.string.library_title)
          },
          maxLines = 1,
        )
      },
      navigationIcon = {
        if (model.selecting) {
          IconButton(onClick = model::cancelSelection) {
            Icon(Icons.Outlined.Close, stringResource(StringsR.string.library_cancel_selection))
          }
        }
      },
      actions = {
        if (model.selecting) {
          TextButton(onClick = model::moveSelection, enabled = model.selected.isNotEmpty() && !model.busy) {
            Text(
              if (model.selectionDestination ==
                null
              ) {
                stringResource(StringsR.string.library_move)
              } else {
                stringResource(StringsR.string.series_done_adding)
              },
            )
          }
        } else if (model.organising) {
          TextButton(onClick = model::finishOrganising) { Text(stringResource(StringsR.string.series_done_adding)) }
        } else {
          IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(StringsR.string.library_search)) }
          IconButton(onClick = { model.dialog = ShelfDialog.Filter }) {
            Icon(
              Icons.Outlined.FilterList,
              stringResource(StringsR.string.library_filter),
              tint = if (model.filter == ShelfFilter.ALL) {
                MaterialTheme.colorScheme.onSurfaceVariant
              } else {
                MaterialTheme.colorScheme.primary
              },
            )
          }
        }
        Box {
          IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, stringResource(StringsR.string.library_options)) }
          DropdownMenu(menu, onDismissRequest = { menu = false }) {
            fun action(block: () -> Unit): () -> Unit = {
              menu = false
              block()
            }
            if (model.selecting) {
              DropdownMenuItem(text = {
                Text(stringResource(StringsR.string.library_add_to_series))
              }, enabled = model.selected.isNotEmpty(), onClick = action(onAddToSeries))
            } else if (model.organising) {
              DropdownMenuItem(
                text = { Text(stringResource(StringsR.string.library_new_shelf)) },
                onClick = action {
                  model.dialog =
                    ShelfDialog.Name(null)
                },
              )
              DropdownMenuItem(
                text = { Text(stringResource(StringsR.string.library_reorder_shelves)) },
                enabled = organisation.shelves.size > 2,
                onClick = action {
                  model.beginOrder(
                    null,
                    organisation.shelves.filterNot {
                      it.id == LibraryShelf.OTHER
                    }.sortedBy { it.position }.map { it.id },
                  )
                },
              )
              DropdownMenuItem(text = {
                Text(stringResource(StringsR.string.library_select_books))
              }, onClick = action(model::beginSelection))
            } else {
              DropdownMenuItem(text = {
                Text(stringResource(StringsR.string.library_organise))
              }, onClick = action(model::beginOrganising))
              DropdownMenuItem(text = { Text(stringResource(StringsR.string.series_add_books)) }, onClick = action(onImport))
              DropdownMenuItem(text = { Text(stringResource(StringsR.string.action_settings)) }, onClick = action(onSettings))
            }
          }
        }
      },
    )
    if (model.organising) {
      Text(
        stringResource(StringsR.string.library_organise_hint),
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else if (model.selecting && model.selectionDestination != null) {
      Text(
        stringResource(
          StringsR.string.library_add_to_shelf,
          organisation.shelves.find {
            it.id == model.selectionDestination
          }?.name.orEmpty().ifEmpty { stringResource(StringsR.string.library_other) },
        ),
        Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
      )
    } else if (model.filter != ShelfFilter.ALL) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          if (model.filter ==
            ShelfFilter.COMPLETED
          ) {
            stringResource(StringsR.string.library_completed_filter)
          } else {
            stringResource(StringsR.string.library_not_started_filter)
          },
          Modifier.weight(1f),
          style = MaterialTheme.typography.labelLarge,
        )
        TextButton(onClick = { model.filter = ShelfFilter.ALL }) { Text(stringResource(StringsR.string.library_reset)) }
      }
    }
  }
}

@Composable
internal fun ShelfControls(
  model: ShelfViewModel,
  organisation: LibraryOrganisation,
  sections: List<LibrarySection>,
  books: Map<BookId, State<BookOverviewItemViewState>>,
  layout: BookOverviewLayoutMode,
  columns: Int,
) {
  val other = stringResource(StringsR.string.library_other)
  fun name(id: String?) = organisation.shelves.find { it.id == id }?.name.orEmpty().ifEmpty { other }
  val dialog = model.dialog
  if (dialog is ShelfDialog.Name) {
    AlertDialog(
      onDismissRequest = model::dismiss,
      title = {
        Text(
          if (dialog.id ==
            null
          ) {
            stringResource(StringsR.string.library_new_shelf)
          } else {
            stringResource(StringsR.string.library_rename_shelf)
          },
        )
      },
      text = {
        Column {
          OutlinedTextField(
            dialog.name,
            onValueChange = model::nameChanged,
            label = { Text(stringResource(StringsR.string.library_shelf_name)) },
            singleLine = true,
            isError =
            model.error != null,
            enabled = !model.busy,
          )
          model.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
      },
      confirmButton = {
        TextButton(onClick = model::saveName, enabled = dialog.name.isNotBlank() && !model.busy) {
          Text(
            if (dialog.id ==
              null
            ) {
              stringResource(StringsR.string.library_create)
            } else {
              stringResource(StringsR.string.character_save)
            },
          )
        }
      },
      dismissButton = {
        TextButton(onClick = model::dismiss, enabled = !model.busy) { Text(stringResource(StringsR.string.dialog_cancel)) }
      },
    )
  } else if (dialog is ShelfDialog.Remove) {
    AlertDialog(
      onDismissRequest = model::dismiss,
      title = { Text(stringResource(StringsR.string.library_remove_shelf_title, name(dialog.id))) },
      text = { Text(stringResource(StringsR.string.library_remove_shelf_message)) },
      confirmButton = {
        TextButton(onClick = {
          model.removeShelf(dialog.id)
        }, enabled = !model.busy) { Text(stringResource(StringsR.string.library_remove_shelf)) }
      },
      dismissButton = {
        TextButton(onClick = model::dismiss, enabled = !model.busy) { Text(stringResource(StringsR.string.dialog_cancel)) }
      },
    )
  } else if (dialog != null) {
    ModalBottomSheet(onDismissRequest = model::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        when (dialog) {
          is ShelfDialog.Menu -> {
            Text(name(dialog.id), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            ShelfAction(stringResource(StringsR.string.library_sort)) { model.dialog = ShelfDialog.Sort(dialog.id) }
            ShelfAction(
              if (model.filter ==
                ShelfFilter.ALL
              ) {
                stringResource(StringsR.string.library_rearrange)
              } else {
                stringResource(StringsR.string.library_show_all_rearrange)
              },
            ) {
              model.filter = ShelfFilter.ALL
              val all = librarySections(books, emptyList(), organisation, ShelfFilter.ALL).find { it.id == dialog.id }
              model.beginOrder(dialog.id, all?.entries.orEmpty().map { it.itemId })
            }
            ShelfAction(stringResource(StringsR.string.series_add_books)) { model.addToShelf(dialog.id) }
            if (dialog.id != LibraryShelf.OTHER) {
              ShelfAction(stringResource(StringsR.string.library_rename)) { model.dialog = ShelfDialog.Name(dialog.id, name(dialog.id)) }
              ShelfAction(stringResource(StringsR.string.library_remove_shelf)) { model.dialog = ShelfDialog.Remove(dialog.id) }
            }
          }
          is ShelfDialog.Sort -> {
            Text(
              stringResource(StringsR.string.library_sort_shelf, name(dialog.id)),
              Modifier.padding(16.dp),
              style = MaterialTheme.typography.titleLarge,
            )
            ShelfSort.entries.forEach { sort ->
              ShelfChoice(sort.label, organisation.shelves.find { it.id == dialog.id }?.sort == sort) { model.sort(dialog.id, sort) }
            }
          }
          is ShelfDialog.Move -> {
            Text(stringResource(StringsR.string.shelf_move), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            Text(
              stringResource(StringsR.string.library_series_move_together),
              Modifier.padding(horizontal = 16.dp),
              style = MaterialTheme.typography.bodyMedium,
            )
            organisation.shelves.sortedBy {
              it.position
            }.forEach { shelf -> ShelfAction(name(shelf.id), enabled = !model.busy) { model.moveTo(shelf.id) } }
          }
          ShelfDialog.Filter -> {
            Text(stringResource(StringsR.string.library_filter), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            listOf(
              ShelfFilter.ALL to stringResource(StringsR.string.library_all),
              ShelfFilter.NOT_STARTED to stringResource(StringsR.string.library_not_started),
              ShelfFilter.COMPLETED to stringResource(StringsR.string.book_header_completed),
            ).forEach { (filter, label) ->
              ShelfChoice(label, model.filter == filter) {
                model.filter = filter
                model.dismiss()
              }
            }
          }
        }
      }
    }
  }
  model.draft?.let { draft ->
    val allEntries = sections.flatMap { it.entries }.associateBy { it.itemId }
    ShelfOrderSheet(
      draft, organisation, allEntries, layout, columns, model.busy, model.error,
      onDismiss = model::dismiss, onSave = model::saveOrder, onMove = model::moveDraft,
    )
  }
}

@Composable
private fun ShelfOrderSheet(
  draft: ShelfOrderDraft,
  organisation: LibraryOrganisation,
  entries: Map<String, voice.features.bookOverview.series.LibraryEntry>,
  layout: BookOverviewLayoutMode,
  columns: Int,
  busy: Boolean,
  error: Int?,
  onDismiss: () -> Unit,
  onSave: () -> Unit,
  onMove: (String, Int) -> Unit,
) {
  val earlier = stringResource(StringsR.string.library_move_earlier)
  val later = stringResource(StringsR.string.library_move_later)
  val grid = rememberLazyGridState()
  val drag = rememberShelfDragState(grid) { from, to -> onMove(from, draft.ids.indexOf(to)) }
  var itemMenu by remember { mutableStateOf<String?>(null) }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    sheetGesturesEnabled = false,
    dragHandle = null,
  ) {
    BackHandler { onDismiss() }
    Column(Modifier.fillMaxHeight(.92f)) {
      Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(StringsR.string.dialog_cancel)) }
        Text(
          if (draft.shelfId ==
            null
          ) {
            stringResource(StringsR.string.library_reorder_shelves)
          } else {
            stringResource(StringsR.string.library_rearrange)
          },
          Modifier.weight(1f),
          style = MaterialTheme.typography.titleLarge,
        )
        TextButton(onClick = onSave, enabled = !busy) { Text(stringResource(StringsR.string.series_done_adding)) }
      }
      Text(
        stringResource(StringsR.string.library_reorder_hint),
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
      )
      error?.let { Text(stringResource(it), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
      BoxWithConstraints(Modifier.weight(1f)) {
        val readableColumns = (maxWidth / (100.dp * LocalDensity.current.fontScale)).toInt().coerceAtLeast(1)
        LazyVerticalGrid(
          columns = GridCells.Fixed(
            if (draft.shelfId == null ||
              layout == BookOverviewLayoutMode.List
            ) {
              1
            } else {
              columns.coerceAtMost(readableColumns)
            },
          ),
          state = grid,
          modifier = Modifier.fillMaxHeight().testTag("shelf-reorder-grid"),
          contentPadding = PaddingValues(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          itemsIndexed(draft.ids, key = { _, id -> id }) { index, id ->
            val moving = drag.dragged == id || drag.returning == id
            val positionLabel = stringResource(StringsR.string.series_position, index + 1, draft.ids.size)
            val itemTitle = entries[id]?.title ?: organisation.shelves.find { it.id == id }?.name.orEmpty()
            Box(
              Modifier.testTag("reorder:$id").then(if (moving) Modifier else Modifier.animateItem()).zIndex(if (moving) 1f else 0f)
                .graphicsLayer {
                  val offset = when (id) {
                    drag.dragged -> drag.offset
                    drag.returning -> drag.settle.value
                    else -> androidx.compose.ui.geometry.Offset.Zero
                  }
                  translationX = offset.x
                  translationY = offset.y
                  if (drag.dragged == id) {
                    scaleX = 1.035f
                    scaleY = 1.035f
                  }
                }
                .semantics {
                  contentDescription = itemTitle
                  stateDescription = positionLabel
                  customActions =
                    buildList {
                      if (!busy && index > 0) {
                        add(
                          CustomAccessibilityAction(earlier) {
                            onMove(id, index - 1)
                            true
                          },
                        )
                      }
                      if (!busy && index < draft.ids.lastIndex) {
                        add(
                          CustomAccessibilityAction(later) {
                            onMove(id, index + 1)
                            true
                          },
                        )
                      }
                    }
                }
                .pointerInput(id, busy) {
                  if (!busy) {
                    detectDragGesturesAfterLongPress(onDragStart = {
                      drag.start(id)
                    }, onDragEnd = drag::stop, onDragCancel = drag::stop) { change, delta ->
                      change.consume()
                      drag.drag(delta)
                    }
                  }
                },
            ) {
              if (draft.shelfId == null) {
                ListItem(
                  headlineContent = {
                    Text(organisation.shelves.find { it.id == id }?.name.orEmpty())
                  },
                  modifier = Modifier.clickable {
                    itemMenu =
                      id
                  },
                  trailingContent = { Icon(Icons.Outlined.MoreHoriz, null) },
                )
              } else {
                entries[id]?.let { entry ->
                  LibraryEntryTile(entry, layout, {
                    itemMenu = id
                  }, null, { _, _ -> itemMenu = id }, null, {}, null, null, null, false, emptySet(), {})
                }
              }
              DropdownMenu(itemMenu == id, onDismissRequest = { itemMenu = null }) {
                DropdownMenuItem(text = { Text(stringResource(StringsR.string.library_move_earlier)) }, enabled = index > 0, onClick = {
                  onMove(id, index - 1)
                  itemMenu =
                    null
                })
                DropdownMenuItem(text = {
                  Text(stringResource(StringsR.string.library_move_later))
                }, enabled = index < draft.ids.lastIndex, onClick = {
                  onMove(
                    id,
                    index + 1,
                  )
                  itemMenu = null
                })
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ShelfAction(
  text: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  ListItem(headlineContent = { Text(text) }, modifier = Modifier.clickable(enabled = enabled, onClick = onClick))
}

@Composable
private fun ShelfChoice(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  ListItem(headlineContent = {
    Text(text)
  }, modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick), trailingContent = {
    if (selected) Icon(Icons.Outlined.Check, null)
  })
}

private val ShelfSort.label: String @Composable get() = when (this) {
  ShelfSort.MANUAL -> stringResource(StringsR.string.library_my_order)
  ShelfSort.TITLE -> stringResource(StringsR.string.library_sort_title)
  ShelfSort.AUTHOR -> stringResource(StringsR.string.library_sort_author)
  ShelfSort.RECENTLY_ADDED -> stringResource(StringsR.string.library_recently_added)
}
