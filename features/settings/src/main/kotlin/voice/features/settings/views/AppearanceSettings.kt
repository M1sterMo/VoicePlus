package voice.features.settings.views

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import voice.core.data.GridMode
import voice.core.data.ThemeColor
import voice.core.data.ThemeMode
import voice.core.ui.isDarkTheme
import voice.core.ui.voiceColorScheme
import voice.features.settings.SettingsListener
import voice.features.settings.SettingsViewState
import voice.core.strings.R as StringsR

@Composable
internal fun AppearanceSettingsRow(
  state: SettingsViewState,
  onClick: () -> Unit,
) {
  val layout = stringResource(state.gridMode.toLabelRes())
  val librarySummary = if (state.gridMode == GridMode.BOOKS) {
    stringResource(StringsR.string.settings_appearance_books_summary, layout, state.booksPerRow)
  } else {
    layout
  }
  val theme = stringResource(state.themeMode.toLabelRes())
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
      Text(stringResource(StringsR.string.settings_appearance_summary, theme, librarySummary))
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
    Column(
      Modifier
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(StringsR.string.settings_section_appearance),
        modifier = Modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.headlineSmall,
      )
      SectionTitle(StringsR.string.appearance_theme)
      OptionRow {
        ThemeMode.entries.forEach { mode ->
          OptionTile(
            selected = state.themeMode == mode,
            onClick = { listener.setThemeMode(mode) },
            modifier = Modifier.weight(1F),
          ) {
            Text(stringResource(mode.toLabelRes()), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
          }
        }
      }
      SectionTitle(StringsR.string.appearance_colour)
      Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val dark = isDarkTheme()
        ThemeColor.entries.forEach { color ->
          ColourSwatch(
            color = voiceColorScheme(color, dark).primary,
            label = stringResource(color.toLabelRes()),
            selected = state.themeColor == color,
            onClick = { listener.setThemeColor(color) },
          )
        }
      }
      SectionTitle(StringsR.string.pref_library_view)
      LibraryViewSettings(state, listener)
      ListItem(
        modifier = Modifier.toggleable(
          value = state.coverGlow,
          role = Role.Switch,
          onValueChange = listener::setCoverGlow,
        ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(StringsR.string.appearance_cover_glow)) },
        supportingContent = { Text(stringResource(StringsR.string.appearance_cover_glow_summary)) },
        trailingContent = { Switch(checked = state.coverGlow, onCheckedChange = null) },
      )
    }
  }
}

@Composable
private fun LibraryViewSettings(
  state: SettingsViewState,
  listener: SettingsListener,
) {
  OptionRow {
    listOf(GridMode.LIST, GridMode.GRID, GridMode.BOOKS).forEach { mode ->
      OptionTile(
        selected = state.gridMode == mode,
        onClick = { listener.setLibraryView(mode) },
        modifier = Modifier.weight(1F),
      ) {
        Column(
          Modifier.padding(4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          LibraryViewPicture(mode)
          Text(stringResource(mode.toLabelRes()), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
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
          val label = stringResource(StringsR.string.books_per_row_count, count)
          OptionTile(
            selected = state.booksPerRow == count,
            onClick = { listener.setBooksPerRow(count) },
            modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = label },
          ) {
            Text(count.toString(), modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }
}

/** A thumbnail of the library layout: list rows, a 2×2 grid or book spines. */
@Composable
private fun LibraryViewPicture(mode: GridMode) {
  Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
    when (mode) {
      GridMode.LIST -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            PictureBlock(10.dp, 10.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
              PictureBlock(28.dp, 3.dp)
              PictureBlock(18.dp, 3.dp)
            }
          }
        }
      }
      GridMode.GRID -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) { PictureBlock(18.dp, 18.dp) }
          }
        }
      }
      GridMode.BOOKS, GridMode.FOLLOW_DEVICE -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { PictureBlock(10.dp, 40.dp) }
      }
    }
  }
}

@Composable
private fun PictureBlock(
  width: Dp,
  height: Dp,
) {
  Box(Modifier.size(width, height).background(LocalContentColor.current.copy(alpha = 0.4F), RoundedCornerShape(2.dp)))
}

@Composable
private fun ColourSwatch(
  color: Color,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Box(
    Modifier
      .size(48.dp)
      .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
      .clip(CircleShape)
      .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
      .semantics { contentDescription = label }
      .padding(6.dp),
  ) {
    Box(Modifier.matchParentSize().background(color, CircleShape))
  }
}

@Composable
private fun SectionTitle(text: Int) {
  Text(stringResource(text), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun OptionRow(content: @Composable RowScope.() -> Unit) {
  Row(
    Modifier.fillMaxWidth().selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
  )
}

@Composable
private fun OptionTile(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    selected = selected,
    onClick = onClick,
    modifier = modifier.heightIn(min = 48.dp),
    shape = MaterialTheme.shapes.medium,
    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(
      if (selected) 2.dp else 1.dp,
      if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    ),
  ) {
    Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
      content()
    }
  }
}

private fun ThemeMode.toLabelRes(): Int = when (this) {
  ThemeMode.SYSTEM -> StringsR.string.appearance_theme_system
  ThemeMode.LIGHT -> StringsR.string.appearance_theme_light
  ThemeMode.DARK -> StringsR.string.appearance_theme_dark
}

private fun ThemeColor.toLabelRes(): Int = when (this) {
  ThemeColor.DYNAMIC -> if (Build.VERSION.SDK_INT >= 31) {
    StringsR.string.appearance_colour_wallpaper
  } else {
    StringsR.string.appearance_colour_default
  }
  ThemeColor.TEAL -> StringsR.string.appearance_colour_teal
  ThemeColor.GREEN -> StringsR.string.appearance_colour_green
  ThemeColor.ORANGE -> StringsR.string.appearance_colour_orange
  ThemeColor.ROSE -> StringsR.string.appearance_colour_rose
  ThemeColor.BLUE -> StringsR.string.appearance_colour_blue
}
