package voice.features.bookOverview.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voice.core.data.LibraryShelf
import voice.features.bookOverview.shelves.LibrarySection
import voice.core.strings.R as StringsR

@Composable
internal fun LibrarySection.title(): String = when {
  current -> stringResource(StringsR.string.book_header_current)
  id == LibraryShelf.OTHER -> stringResource(StringsR.string.library_other)
  else -> shelf!!.name
}

/** A quiet heading, with organisation controls kept out of the book tiles. */
@Composable
internal fun ShelfHeader(
  title: String,
  count: Int?,
  expanded: Boolean,
  onToggle: (() -> Unit)?,
  onMenu: (() -> Unit)?,
  modifier: Modifier = Modifier,
  dropActive: Boolean = false,
) {
  val expandedLabel = stringResource(if (expanded) StringsR.string.library_expanded else StringsR.string.library_collapsed)
  val container by animateColorAsState(
    if (dropActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    label = "Shelf drop target",
  )
  Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(container), verticalAlignment = Alignment.CenterVertically) {
    Row(
      Modifier.weight(1f).heightIn(min = if (onToggle == null && onMenu == null) 32.dp else 48.dp).then(
        if (onToggle ==
          null
        ) {
          Modifier
        } else {
          Modifier.semantics { stateDescription = expandedLabel }.clickable(role = Role.Button, onClick = onToggle)
        },
      ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        title,
        modifier = Modifier.weight(1f).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (count != null) {
        Text(
          pluralStringResource(StringsR.plurals.series_book_count, count, count),
          modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = 112.dp),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (onToggle != null) Icon(Icons.Outlined.ExpandMore, null, Modifier.rotate(if (expanded) 180f else 0f))
    }
    if (onMenu != null) {
      IconButton(onClick = onMenu) { Icon(Icons.Outlined.MoreHoriz, stringResource(StringsR.string.library_organise_shelf, title)) }
    }
  }
}
