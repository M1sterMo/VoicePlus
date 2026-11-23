package voice.features.listeningStats

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import coil.compose.AsyncImage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs
import voice.core.ui.BookshelfCover
import voice.core.ui.RatingStars
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@ContributesTo(AppScope::class)
interface ListeningStatsGraph {
  val listeningStatsViewModel: ListeningStatsViewModel
}

@ContributesTo(AppScope::class)
interface ListeningStatsProvider {

  @Provides
  @IntoSet
  fun listeningStatsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.ListeningStatistics> { key ->
    NavEntry(key) {
      ListeningStatsScreen()
    }
  }
}

@Composable
fun ListeningStatsScreen() {
  val viewModel = retain { rootGraphAs<ListeningStatsGraph>().listeningStatsViewModel }
  // Null until the first aggregation lands: rendering Empty here would flash "No listening data yet"
  // at a user who has years of it.
  val viewState = viewModel.viewState() ?: return
  val scope = rememberCoroutineScope()
  ListeningStatsScreen(
    viewState = viewState,
    onClose = viewModel::onClose,
    onChangeFinishDate = { book, date -> scope.launch { viewModel.changeFinishDate(book, date) } },
    onRate = { book, rating -> scope.launch { viewModel.rate(book, rating) } },
  )
}

@Composable
internal fun ListeningStatsScreen(
  viewState: ListeningStatsViewState,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  onChangeFinishDate: (FinishedBookStats, LocalDate) -> Unit = { _, _ -> },
  onRate: (FinishedBookStats, Int?) -> Unit = { _, _ -> },
) {
  var selectedBook by remember { mutableStateOf<FinishedBookStats?>(null) }
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(StringsR.string.listening_stats)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(StringsR.string.close))
          }
        },
      )
    },
  ) { paddingValues ->
    if (viewState.totalLifetimeMs == 0L && viewState.booksInLibrary == 0) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(StringsR.string.listening_stats_no_data),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      return@Scaffold
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (viewState.totalLifetimeMs > 0L) {
          TotalListening(viewState)
          Recently(viewState)
        }

        // The trophy shelf comes before the charts: it is what this screen is visited for.
        if (viewState.finishedBooks.isNotEmpty()) {
          FinishedBooks(
            books = viewState.finishedBooks,
            jacket = viewState.bookJackets,
            onBookClick = { selectedBook = it },
          )
        }

        if (viewState.totalLifetimeMs > 0L) {
          LastTwelveMonths(viewState)
          Records(viewState)
        }

        if (viewState.booksInLibrary > 0) {
          LibraryProgress(viewState)
        }

        if (viewState.totalLifetimeMs > 0L) {
          viewState.firstListeningDateLabel?.let { date ->
            Text(
              text = stringResource(StringsR.string.listening_stats_since, date),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth(),
              textAlign = TextAlign.Center,
            )
          }
        }

        Spacer(modifier = Modifier.size(12.dp))
      }
    }
  }
  selectedBook?.let { book ->
    FinishedBookDetailsSheet(
      book = book,
      jacket = viewState.bookJackets,
      onDismiss = { selectedBook = null },
      onChangeFinishDate = { date ->
        onChangeFinishDate(book, date)
        selectedBook = null
      },
      onRate = { onRate(book, it) },
    )
  }
}

@Composable
private fun TotalListening(viewState: ListeningStatsViewState) {
  val totalMs = viewState.totalLifetimeMs
  val days = totalMs / 86_400_000
  val hours = (totalMs % 86_400_000) / 3_600_000
  val minutes = (totalMs % 3_600_000) / 60_000

  Column {
    Text(
      text = stringResource(StringsR.string.listening_stats_total_lifetime),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Max)
        .padding(top = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      UnitTile(days.toString(), stringResource(StringsR.string.listening_stats_unit_days), Modifier.weight(1f))
      UnitTile(hours.toString(), stringResource(StringsR.string.listening_stats_unit_hours), Modifier.weight(1f))
      UnitTile(minutes.toString(), stringResource(StringsR.string.listening_stats_unit_minutes), Modifier.weight(1f))
    }
    Text(
      text = listOfNotNull(
        formatDuration(totalMs),
        pluralStringResource(
          StringsR.plurals.listening_stats_books_finished,
          viewState.booksCompleted,
          viewState.booksCompleted,
        ).takeIf { viewState.booksCompleted > 0 },
      ).joinToString(" · "),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 10.dp),
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun UnitTile(
  value: String,
  unit: String,
  modifier: Modifier = Modifier,
  valueStyle: TextStyle = MaterialTheme.typography.displaySmall,
  supporting: String? = null,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
  Card(
    modifier = modifier.fillMaxHeight(),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(vertical = 14.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = value,
        style = valueStyle,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        // Long values ("200h 15m") shrink to fit a narrow tile instead of being cut off.
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = valueStyle.fontSize),
      )
      Text(
        text = unit,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
        textAlign = TextAlign.Center,
      )
      supporting?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
      }
    }
  }
}

@Composable
private fun Recently(viewState: ListeningStatsViewState) {
  Column {
    SectionTitle(stringResource(StringsR.string.listening_stats_recently))
    // Every tile takes the tallest one's height, so the week's change line does not make it stand out.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Max)
        .padding(top = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val style = MaterialTheme.typography.titleLarge
      UnitTile(formatDuration(viewState.todayMs), stringResource(StringsR.string.listening_stats_today), Modifier.weight(1f), style)
      UnitTile(
        value = formatDuration(viewState.thisWeekMs),
        unit = stringResource(StringsR.string.listening_stats_this_week),
        modifier = Modifier.weight(1f),
        valueStyle = style,
        supporting = viewState.weekChangePercent?.let { change ->
          when {
            change > 0 -> stringResource(StringsR.string.listening_stats_week_delta_more, change)
            change < 0 -> stringResource(StringsR.string.listening_stats_week_delta_less, -change)
            else -> null
          }
        },
      )
      UnitTile(
        formatDuration(viewState.thisMonthMs),
        stringResource(StringsR.string.listening_stats_this_month),
        Modifier.weight(1f),
        style,
      )
    }
  }
}

@Composable
private fun LastTwelveMonths(viewState: ListeningStatsViewState) {
  val data = viewState.monthlyData
  if (data.isEmpty()) return
  // Not keyed on data: a session closing while the screen is open changes the current month's value
  // and must not snap the selection away from the bar the user is inspecting.
  var selectedIndex by remember { mutableIntStateOf(data.lastIndex) }
  val selectedPoint = data[selectedIndex.coerceIn(0, data.lastIndex)]

  Column {
    SectionTitle(stringResource(StringsR.string.listening_stats_last_12_months))
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ),
      shape = MaterialTheme.shapes.extraLarge,
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(StringsR.string.listening_stats_activity),
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = stringResource(
              StringsR.string.listening_stats_selected_period,
              selectedPoint.label,
              formatDuration(selectedPoint.valueMs),
            ),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp),
          )
        }
        InteractiveBarChart(
          data = data,
          selectedIndex = selectedIndex,
          onSelect = { selectedIndex = it },
          modifier = Modifier.padding(top = 16.dp),
        )
      }
    }
  }
}

@Composable
private fun InteractiveBarChart(
  data: List<ChartDataPoint>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val maxValue = data.maxOfOrNull { it.valueMs }?.coerceAtLeast(1L) ?: 1L

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(160.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    data.forEachIndexed { index, point ->
      val selected = index == selectedIndex
      val heightFraction by animateFloatAsState(
        targetValue = (point.valueMs.toFloat() / maxValue).coerceAtLeast(0.04f),
        animationSpec = spring(),
        label = "Listening bar height",
      )
      val description = stringResource(
        StringsR.string.listening_stats_selected_period,
        point.label,
        formatDuration(point.valueMs),
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .clickable(
            role = Role.Button,
            onClickLabel = stringResource(StringsR.string.listening_stats_select_period),
            onClick = { onSelect(index) },
          )
          .semantics(mergeDescendants = true) {
            contentDescription = description
            this.selected = selected
          },
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.BottomCenter,
        ) {
          if (point.valueMs == 0L) {
            // A month without listening is a dot, not a stub bar pretending to be data.
            Box(
              modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)),
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxWidth(0.58f)
                .fillMaxHeight(heightFraction)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(
                  if (selected) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                  },
                ),
            )
          }
        }
        Text(
          text = point.label,
          style = MaterialTheme.typography.labelSmall,
          color = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun Records(viewState: ListeningStatsViewState) {
  val longestStreakLabel = stringResource(StringsR.string.listening_stats_longest_streak)
  val records = listOf(
    pluralStringResource(StringsR.plurals.listening_stats_streak_days, viewState.longestStreak, viewState.longestStreak) to
      if (viewState.currentStreak == viewState.longestStreak && viewState.currentStreak > 0) {
        "$longestStreakLabel ${stringResource(StringsR.string.listening_stats_streak_now)}"
      } else {
        longestStreakLabel
      },
    pluralStringResource(StringsR.plurals.listening_stats_streak_days, viewState.currentStreak, viewState.currentStreak) to
      stringResource(StringsR.string.listening_stats_current_streak),
    formatDuration(viewState.longestDayMs) to
      dated(stringResource(StringsR.string.listening_stats_longest_day), viewState.longestDayLabel),
    formatDuration(viewState.biggestMonthMs) to
      dated(stringResource(StringsR.string.listening_stats_biggest_month), viewState.biggestMonthLabel),
    (viewState.bestDayOfWeek ?: "—") to stringResource(StringsR.string.listening_stats_best_day_of_week),
    formatDuration(viewState.avgSessionMs) to stringResource(StringsR.string.listening_stats_avg_session),
  )
  Column {
    SectionTitle(stringResource(StringsR.string.listening_stats_records))
    Column(
      modifier = Modifier.padding(top = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      records.chunked(2).forEach { pair ->
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          pair.forEach { (value, label) -> RecordTile(value, label, Modifier.weight(1f).fillMaxHeight()) }
        }
      }
    }
  }
}

@Composable
private fun dated(
  label: String,
  date: String?,
): String = if (date == null) label else stringResource(StringsR.string.listening_stats_selected_period, label, date)

@Composable
private fun RecordTile(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
      )
    }
  }
}

@Composable
private fun FinishedBooks(
  books: List<FinishedBookStats>,
  jacket: Boolean,
  onBookClick: (FinishedBookStats) -> Unit,
) {
  var showAll by remember { mutableStateOf(false) }
  val visibleBooks = if (showAll) books else books.take(RECENT_FINISHED_BOOKS)
  // The list is newest first, so grouping keeps years in order and undated books last.
  val yearCounts = books.groupingBy { it.finishedDate?.year }.eachCount()
  Column {
    SectionTitle(stringResource(StringsR.string.listening_stats_finished))
    visibleBooks.groupBy { it.finishedDate?.year }.forEach { (year, group) ->
      Text(
        text = if (year == null) {
          stringResource(StringsR.string.listening_stats_undated)
        } else {
          val count = yearCounts.getValue(year)
          pluralStringResource(StringsR.plurals.listening_stats_year_books, count, year.toString(), count)
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
      )
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
      ) {
        Spacer(Modifier.height(6.dp))
        group.forEach { book ->
          FinishedBookRow(
            book = book,
            jacket = jacket,
            onClick = { onBookClick(book) },
          )
        }
        Spacer(Modifier.height(6.dp))
      }
    }
    if (books.size > RECENT_FINISHED_BOOKS) {
      TextButton(
        onClick = { showAll = !showAll },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          if (showAll) {
            stringResource(StringsR.string.listening_stats_show_fewer)
          } else {
            stringResource(StringsR.string.listening_stats_show_all, books.size)
          },
        )
      }
    }
  }
}

@Composable
private fun FinishedBookRow(
  book: FinishedBookStats,
  jacket: Boolean,
  onClick: () -> Unit,
) {
  val days = book.daysToFinish()
  val detailParts = listOfNotNull(
    formatDuration(book.listenedMs).takeIf { book.listenedMs > 0 },
    days?.let { pluralStringResource(StringsR.plurals.listening_stats_streak_days, it, it) },
  )
  val details = detailParts.joinToString(" · ")
  val finishedOn = book.finishedDate?.let { stringResource(StringsR.string.book_finished_on, it.format(monthDayFormatter())) }
  val rating = book.rating?.let { pluralStringResource(StringsR.plurals.rating_label, it, it) }
  // Screen readers hear the title first, then this: when, how long, and the rating.
  val spoken = (listOfNotNull(finishedOn) + detailParts + listOfNotNull(rating)).joinToString(", ")
  val locale = LocalConfiguration.current.locales[0]
  val month = remember(book.finishedDate, locale) {
    book.finishedDate?.month?.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale)?.uppercase(locale)
  }
  // One line each and a fixed height, rated or not, so the list reads as an even timeline.
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .padding(start = 12.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // The finish date as a small calendar mark: the day, and the month under it. Each shrinks rather
    // than wraps, so long month names and large text keep the row one height.
    Column(
      modifier = Modifier.width(34.dp).clearAndSetSemantics {},
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (book.finishedDate != null && month != null) {
        val day = MaterialTheme.typography.titleMedium
        val monthStyle = MaterialTheme.typography.labelSmall
        Text(
          book.finishedDate.dayOfMonth.toString(),
          style = day,
          maxLines = 1,
          autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = day.fontSize),
        )
        Text(
          month,
          style = monthStyle,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = monthStyle.fontSize),
        )
      }
    }
    // Sized by the cover alone: the rating tag overhangs it without widening the row.
    Box {
      StatsCover(book, jacket, width = if (jacket) 30.dp else 44.dp, compact = true)
      // The rating rides on the cover's corner, in the finished medal's colours, so no line ever wraps for it.
      book.rating?.let {
        Text(
          text = "★$it",
          modifier = Modifier
            .matchParentSize()
            .wrapContentSize(Alignment.BottomEnd, unbounded = true)
            .offset(x = 6.dp, y = 5.dp)
            .clearAndSetSemantics {}
            // A ring in the card's colour around the tag, outside it so it never covers the text.
            .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
            .padding(2.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }
    Column(Modifier.weight(1f).padding(start = 4.dp)) {
      Text(
        text = book.name,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = details,
        modifier = Modifier.semantics { contentDescription = spoken },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * A finished book's cover in the library's look, like the player's: a 2:3 book jacket in Books, and
 * otherwise a square like the Grid and List covers, which suits audiobook art.
 */
@Composable
private fun StatsCover(
  book: FinishedBookStats,
  jacket: Boolean,
  width: Dp,
  compact: Boolean = false,
) {
  if (jacket) {
    BookshelfCover(book.bookId, book.cover, Modifier.width(width).testTag("finished-cover:jacket"), compact = compact)
  } else {
    AsyncImage(
      modifier = Modifier
        .testTag("finished-cover:square")
        .size(width)
        .clip(if (compact) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.medium),
      model = book.cover?.file,
      placeholder = painterResource(id = UiR.drawable.album_art),
      error = painterResource(id = UiR.drawable.album_art),
      contentScale = ContentScale.Crop,
      contentDescription = null,
    )
  }
}

@Composable
private fun FinishedBookDetailsSheet(
  book: FinishedBookStats,
  jacket: Boolean,
  onDismiss: () -> Unit,
  onChangeFinishDate: (LocalDate) -> Unit,
  onRate: (Int?) -> Unit,
) {
  var pickingDate by remember { mutableStateOf(false) }
  // The sheet holds a snapshot of the book; show a new rating straight away.
  var rating by remember(book) { mutableStateOf(book.rating) }
  val speedFormat = remember { DecimalFormat("0.##") }
  if (pickingDate) {
    FinishDatePicker(
      initial = book.finishedDate ?: LocalDate.now(),
      after = book.finishDateFloor,
      onPick = {
        pickingDate = false
        onChangeFinishDate(it)
      },
      onDismiss = { pickingDate = false },
    )
  }
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
          .padding(bottom = 32.dp),
      ) {
        ListItem(
          // The sheet's own colour, not a lighter box behind the title.
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          headlineContent = {
            Text(
              text = book.name,
              style = MaterialTheme.typography.titleLarge,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          },
          supportingContent = book.finishedDateLabel?.let { date ->
            {
              Text(
                text = stringResource(StringsR.string.book_finished_on, date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          },
          leadingContent = { StatsCover(book, jacket, width = if (jacket) 56.dp else 72.dp) },
        )
        RatingStars(
          rating = rating,
          onRate = {
            rating = it
            onRate(it)
          },
          modifier = Modifier.padding(start = 4.dp),
        )
        if (book.sessionCount > 0 || book.durationMs > 0) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(IntrinsicSize.Max)
              .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            // The sheet is the page's tile colour, so its tiles take the tonal accent instead.
            val tile: @Composable (String, String) -> Unit = { value, label ->
              UnitTile(
                value = value,
                unit = label,
                modifier = Modifier.weight(1f),
                valueStyle = MaterialTheme.typography.titleLarge,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
              )
            }
            if (book.sessionCount > 0) tile(formatDuration(book.listenedMs), stringResource(StringsR.string.listening_stats_listening))
            if (book.durationMs > 0) tile(formatDuration(book.durationMs), stringResource(StringsR.string.listening_stats_book_length))
            if (book.sessionCount > 0) {
              book.daysToFinish()?.let { days ->
                tile(
                  pluralStringResource(StringsR.plurals.listening_stats_streak_days, days, days),
                  stringResource(StringsR.string.listening_stats_start_to_finish),
                )
              }
              tile(book.sessionCount.toString(), stringResource(StringsR.string.listening_stats_sessions))
            }
          }
        }
        if (book.dailyMs.size > 1) {
          SectionTitle(
            stringResource(StringsR.string.listening_stats_day_by_day),
            Modifier.padding(top = 20.dp, bottom = 8.dp),
          )
          DayBars(book)
        }
        if (book.sessionCount > 0 || book.completionCount != null) {
          StatsCard {
            val rows = buildList {
              if (book.sessionCount > 0) {
                add(stringResource(StringsR.string.listening_stats_longest_session) to formatDuration(book.longestSessionMs))
                add(stringResource(StringsR.string.listening_stats_avg_session) to formatDuration(book.listenedMs / book.sessionCount))
                add(stringResource(StringsR.string.playback_speed) to "${speedFormat.format(book.playbackSpeed)}×")
              }
              book.firstListenedDateLabel?.let { add(stringResource(StringsR.string.listening_stats_first_listened) to it) }
              // An unknown count (a finish from before tracking) is left out rather than guessed.
              book.completionCount?.let { count ->
                add(
                  stringResource(StringsR.string.listening_stats_completed) to
                    pluralStringResource(StringsR.plurals.listening_stats_completion_count, count, count),
                )
              }
            }
            rows.forEachIndexed { index, (label, value) ->
              if (index > 0) RowDivider()
              StatRow(label, value)
            }
          }
        }
        if (book.canChangeFinishDate) {
          TextButton(
            onClick = { pickingDate = true },
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 12.dp),
          ) {
            Text(stringResource(StringsR.string.listening_stats_change_finish_date))
          }
        }
      }
    }
  }
}

@Composable
private fun DayBars(book: FinishedBookStats) {
  val max = book.dailyMs.max().coerceAtLeast(1L)
  val primary = MaterialTheme.colorScheme.primary
  val idle = MaterialTheme.colorScheme.outlineVariant
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(96.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      book.dailyMs.forEach { ms ->
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight((ms.toFloat() / max).coerceAtLeast(0.06f))
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (ms > 0) primary else idle),
        )
      }
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      val formatter = monthDayFormatter()
      listOfNotNull(book.readStartDate, book.finishedDate).forEach {
        Text(it.format(formatter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishDatePicker(
  initial: LocalDate,
  after: LocalDate?,
  onPick: (LocalDate) -> Unit,
  onDismiss: () -> Unit,
) {
  // DatePicker counts in UTC midnights, so convert at UTC to keep the calendar day intact.
  val state = rememberDatePickerState(
    initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    selectableDates = object : SelectableDates {
      override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return date <= LocalDate.now() && (after == null || date > after)
      }
    },
  )
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
        },
      ) { Text(stringResource(StringsR.string.listening_stats_set_date)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(StringsR.string.dialog_cancel)) }
    },
  ) {
    DatePicker(state)
  }
}

/** Days from the first listen to the finish, counting both; null when either end is unknown. */
private fun FinishedBookStats.daysToFinish(): Int? {
  val start = readStartDate ?: return null
  val end = finishedDate ?: return null
  return (end.toEpochDay() - start.toEpochDay() + 1).toInt().takeIf { it > 0 }
}

/** "Nov 8" in the reader's locale: the year is already given by the group heading. */
@Composable
private fun monthDayFormatter(): DateTimeFormatter {
  val locale = LocalConfiguration.current.locales[0]
  return remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }
}

@Composable
private fun LibraryProgress(viewState: ListeningStatsViewState) {
  val progress = (viewState.booksCompleted.toFloat() / viewState.booksInLibrary).coerceIn(0f, 1f)

  Column {
    SectionTitle(stringResource(StringsR.string.listening_stats_library))
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      shape = MaterialTheme.shapes.large,
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(StringsR.string.listening_stats_library_completed),
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text = pluralStringResource(
              StringsR.plurals.listening_stats_library_progress,
              viewState.booksInLibrary,
              viewState.booksCompleted,
              viewState.booksInLibrary,
            ),
            style = MaterialTheme.typography.labelLarge,
          )
        }
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .height(8.dp)
            .clip(CircleShape),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
      }
    }
  }
}

@Composable
private fun StatsCard(content: @Composable () -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      content()
    }
  }
}

@Composable
private fun RowDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(vertical = 8.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}

@Composable
private fun StatRow(
  label: String,
  value: String,
  supporting: String? = null,
  badge: String? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Column(
      modifier = Modifier.padding(start = 12.dp),
      horizontalAlignment = Alignment.End,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = value,
          style = MaterialTheme.typography.labelLarge,
          textAlign = TextAlign.End,
        )
        badge?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(start = 6.dp),
          )
        }
      }
      supporting?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun SectionTitle(
  title: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    modifier = modifier,
  )
}

fun formatDuration(ms: Long): String {
  val hours = ms / 3_600_000
  val minutes = (ms % 3_600_000) / 60_000
  val seconds = (ms % 60_000) / 1_000
  return when {
    hours > 0 -> "${hours}h ${minutes}m"
    minutes > 0 -> "${minutes}m ${seconds}s"
    seconds > 0 -> "${seconds}s"
    else -> "0m"
  }
}

private const val RECENT_FINISHED_BOOKS = 5
