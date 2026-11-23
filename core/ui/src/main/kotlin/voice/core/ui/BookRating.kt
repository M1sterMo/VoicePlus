package voice.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import voice.core.strings.R as StringsR

/** Five tappable stars in the theme's primary colour. Tapping the current rating clears it. */
@Composable
fun RatingStars(
  rating: Int?,
  onRate: (Int?) -> Unit,
  modifier: Modifier = Modifier,
  starSize: Dp = 32.dp,
) {
  val state =
    rating?.let { pluralStringResource(StringsR.plurals.rating_label, it, it) } ?: stringResource(StringsR.string.rating_not_rated)
  Row(modifier) {
    (1..5).forEach { star ->
      val filled = star <= (rating ?: 0)
      IconButton(
        onClick = { onRate(if (star == rating) null else star) },
        // Each star carries the current rating, since TalkBack only reads the stars themselves.
        modifier = Modifier.semantics {
          selected = star == rating
          stateDescription = state
        },
      ) {
        Icon(
          if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
          contentDescription = stringResource(StringsR.string.rating_star, star),
          modifier = Modifier.size(starSize),
          tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
      }
    }
  }
}

/** The compact "★ 4" shown beside a book's status in lists. */
@Composable
fun RatingLabel(
  rating: Int,
  modifier: Modifier = Modifier,
  style: TextStyle = LocalTextStyle.current,
) {
  val description = pluralStringResource(StringsR.plurals.rating_label, rating, rating)
  Row(modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
    Icon(
      Icons.Filled.Star,
      contentDescription = null,
      modifier = Modifier.size(with(LocalDensity.current) { style.fontSize.toDp() }),
      tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.width(2.dp))
    Text("$rating", style = style, color = MaterialTheme.colorScheme.onSurface)
  }
}
