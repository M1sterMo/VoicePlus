package voice.features.widget.config

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import voice.core.common.rootGraph
import voice.core.ui.VoiceTheme
import voice.features.widget.R
import voice.features.widget.WidgetGraph
import voice.features.widget.WidgetUpdater
import java.util.Locale
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

class WidgetConfigActivity : ComponentActivity() {

  private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

  @Inject lateinit var configStore: WidgetConfigStore

  @Inject lateinit var widgetUpdater: WidgetUpdater

  @SuppressLint("InflateParams")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(Activity.RESULT_CANCELED)

    (rootGraph as WidgetGraph).inject(this)

    appWidgetId = intent.extras?.getInt(
      AppWidgetManager.EXTRA_APPWIDGET_ID,
      AppWidgetManager.INVALID_APPWIDGET_ID,
    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }

    // The default cover shape comes from the library layout, which is only readable asynchronously.
    lifecycleScope.launch {
      val savedCoverShape = configStore.getCoverShape(appWidgetId)
      setContent {
        VoiceTheme {
          Scaffold(
            topBar = {
              @OptIn(ExperimentalMaterial3Api::class)
              TopAppBar(title = { Text(stringResource(StringsR.string.widget_config_title)) })
            },
          ) { padding ->
            // Saveable: a rotation mid-adjustment would otherwise discard the unsaved values.
            var alpha by rememberSaveable { mutableStateOf(configStore.getAlpha(appWidgetId) / 255f) }
            var scale by rememberSaveable { mutableStateOf(configStore.getTextScale(appWidgetId)) }
            var coverShape by rememberSaveable { mutableStateOf(savedCoverShape) }
            var chapterButtons by rememberSaveable { mutableStateOf(configStore.getChapterButtons(appWidgetId)) }

            Column(
              modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Column(
                modifier = Modifier
                  .weight(1f)
                  .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                AndroidView(
                  modifier = Modifier.fillMaxWidth().height(120.dp),
                  factory = { ctx ->
                    LayoutInflater.from(ctx).inflate(R.layout.widget_preview, null, false).also { view ->
                      view.tag = PreviewViews(
                        background = view.findViewById(R.id.widgetBackground),
                        cover = view.findViewById<ImageView>(R.id.imageView).apply {
                          // The layout's clipToOutline attribute only applies from API 31.
                          clipToOutline = true
                        },
                        title = view.findViewById(R.id.title),
                        summary = view.findViewById(R.id.summary),
                        previousChapter = view.findViewById(R.id.previousChapter),
                        nextChapter = view.findViewById(R.id.nextChapter),
                      )
                    }
                  },
                  update = { view ->
                    val v = view.tag as PreviewViews
                    v.background.imageAlpha = (alpha * 255).roundToInt().coerceIn(0, 255)
                    v.title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.widget_title_size) * scale)
                    v.summary.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.widget_summary_size) * scale)
                    v.cover.doOnLayout { cover ->
                      cover.updateLayoutParams { width = (cover.height * coverShape.aspectRatio).roundToInt() }
                    }
                    v.previousChapter.isVisible = chapterButtons
                    v.nextChapter.isVisible = chapterButtons
                  },
                )

                Column {
                  Text(
                    stringResource(StringsR.string.widget_config_opacity, (alpha * 100).roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0f..1f)
                }

                Column {
                  Text(
                    stringResource(StringsR.string.widget_config_text_size, String.format(Locale.ROOT, "%.1f", scale)),
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.5f..2f)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(
                    stringResource(StringsR.string.widget_config_cover_shape),
                    style = MaterialTheme.typography.titleMedium,
                  )
                  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WidgetCoverShape.entries.forEachIndexed { index, shape ->
                      SegmentedButton(
                        selected = coverShape == shape,
                        onClick = { coverShape = shape },
                        shape = SegmentedButtonDefaults.itemShape(index, WidgetCoverShape.entries.size),
                      ) {
                        val label = when (shape) {
                          WidgetCoverShape.Square -> StringsR.string.widget_config_cover_square
                          WidgetCoverShape.Book -> StringsR.string.widget_config_cover_book
                        }
                        Text(stringResource(label))
                      }
                    }
                  }
                }

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = chapterButtons, role = Role.Switch, onValueChange = { chapterButtons = it })
                    .padding(vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    stringResource(StringsR.string.widget_config_chapter_buttons),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Switch(checked = chapterButtons, onCheckedChange = null)
                }
              }

              Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                  configStore.setAlpha(appWidgetId, (alpha * 255).roundToInt().coerceIn(0, 255))
                  configStore.setTextScale(appWidgetId, scale)
                  configStore.setCoverShape(appWidgetId, coverShape)
                  configStore.setChapterButtons(appWidgetId, chapterButtons)
                  widgetUpdater.update()
                  setResult(
                    Activity.RESULT_OK,
                    Intent().apply {
                      putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    },
                  )
                  finish()
                },
              ) {
                // Not "Add Widget": the widget is reconfigurable, so this same screen opens when
                // editing one that already exists.
                Text(stringResource(StringsR.string.widget_config_apply))
              }
            }
          }
        }
      }
    }
  }

  private data class PreviewViews(
    val background: ImageView,
    val cover: ImageView,
    val title: TextView,
    val summary: TextView,
    val previousChapter: View,
    val nextChapter: View,
  )
}
