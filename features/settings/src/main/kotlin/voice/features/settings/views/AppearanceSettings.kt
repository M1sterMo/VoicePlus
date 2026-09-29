package voice.features.settings.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.core.data.GridMode
import voice.features.settings.SettingsListener
import voice.features.settings.SettingsViewState
import voice.core.strings.R as StringsR

@Composable
internal fun AppearanceSettingsRow(
  state: SettingsViewState,
  onClick: () -> Unit,
) {
  val layout = stringResource(state.gridMode.toLabelRes())
  val summary = if (state.gridMode == GridMode.BOOKS) {
    stringResource(StringsR.string.settings_appearance_books_summary, layout, state.booksPerRow)
  } else {
    layout
  }
  ListItem(
    modifier = Modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    leadingContent = {
      Icon(Icons.Outlined.Palette, contentDescription = null)
    },
    headlineContent = {
      Text(stringResource(StringsR.string.settings_section_appearance))
    },
    supportingContent = {
      Text(summary)
    },
    trailingContent = {
      Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    },
  )
}

@Composable
internal fun AppearanceSettingsSheet(
  state: SettingsViewState,
  listener: SettingsListener,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.padding(bottom = 24.dp)) {
      Text(
        text = stringResource(StringsR.string.settings_section_appearance),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.headlineSmall,
      )
      AppearanceSettings(state, listener)
      if (state.showDarkThemePref) {
        SettingsDivider()
        DarkThemeRow(state.useDarkTheme, listener::toggleDarkTheme)
      }
    }
  }
}

@Composable
internal fun AppearanceSettings(
  state: SettingsViewState,
  listener: SettingsListener,
) {
  Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(StringsR.string.pref_library_view), style = MaterialTheme.typography.titleSmall)
    Row(
      Modifier.fillMaxWidth().selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      listOf(GridMode.LIST, GridMode.GRID, GridMode.BOOKS).forEach { mode ->
        val selected = state.gridMode == mode
        Surface(
          selected = selected,
          onClick = { listener.setLibraryView(mode) },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
          shape = MaterialTheme.shapes.medium,
          color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
          border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          ),
        ) {
          Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
              stringResource(mode.toLabelRes()),
              style = MaterialTheme.typography.labelLarge,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }
    if (state.gridMode == GridMode.BOOKS) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          stringResource(StringsR.string.pref_books_per_row),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(2, 3).forEach { count ->
            val selected = state.booksPerRow == count
            val label = stringResource(StringsR.string.books_per_row_count, count)
            Surface(
              selected = selected,
              onClick = { listener.setBooksPerRow(count) },
              shape = MaterialTheme.shapes.medium,
              color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
              border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
              ),
              modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).semantics { contentDescription = label },
            ) {
              Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                Text(count.toString(), modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelLarge)
              }
            }
          }
        }
      }
    }
  }
}
