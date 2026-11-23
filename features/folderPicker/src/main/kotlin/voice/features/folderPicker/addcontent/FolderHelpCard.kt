package voice.features.folderPicker.addcontent

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import voice.core.logging.api.Logger
import voice.core.ui.VoiceTheme
import voice.core.strings.R as StringsR

/** Why the add screen offers help: the folder picker was closed without a folder, or there is none. */
internal enum class FolderHelp {
  PickerClosed,
  NoPicker,
}

internal enum class FolderHelpAnswer(@StringRes val label: Int) {
  NoFiles(StringsR.string.folder_add_help_answer_no_files),
  NotFound(StringsR.string.folder_add_help_answer_not_found),
  OtherApp(StringsR.string.folder_add_help_answer_other_app),
}

/** Tips for a listener who didn't get their audiobooks in. The answer only picks the tip; nothing is sent anywhere. */
@Composable
internal fun FolderHelpCard(
  help: FolderHelp,
  answer: FolderHelpAnswer?,
  onAnswer: (FolderHelpAnswer) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(Modifier.animateContentSize().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Outlined.Lightbulb,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(16.dp))
        Text(
          modifier = Modifier
            .weight(1F)
            .semantics { heading() },
          text = stringResource(
            when (help) {
              FolderHelp.PickerClosed -> StringsR.string.folder_add_help_title
              FolderHelp.NoPicker -> StringsR.string.folder_add_no_picker_title
            },
          ),
          style = MaterialTheme.typography.titleMedium,
        )
      }
      when (help) {
        FolderHelp.PickerClosed -> {
          Spacer(Modifier.height(8.dp))
          Column(Modifier.selectableGroup()) {
            FolderHelpAnswer.entries.forEach { option ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .selectable(selected = option == answer, role = Role.RadioButton, onClick = { onAnswer(option) }),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(selected = option == answer, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
              }
            }
          }
          when (answer) {
            FolderHelpAnswer.NoFiles -> NoFilesText()
            FolderHelpAnswer.NotFound -> {
              // older versions of Android still let apps open the Download folder
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HelpText(StringsR.string.folder_add_help_download)
              }
              HelpText(StringsR.string.folder_add_help_computer)
            }
            FolderHelpAnswer.OtherApp -> HelpText(StringsR.string.folder_add_help_other_app)
            null -> Unit
          }
        }
        FolderHelp.NoPicker -> HelpText(StringsR.string.folder_add_no_picker_message)
      }
    }
  }
}

@Composable
private fun NoFilesText() {
  val text = stringResource(StringsR.string.folder_add_help_no_files)
  val linkColor = MaterialTheme.colorScheme.primary
  val uriHandler = LocalUriHandler.current
  val linkedText = remember(text, linkColor, uriHandler) {
    buildAnnotatedString {
      append(text)
      // translations keep the name, some with an ending
      val start = text.indexOf(LIBRIVOX)
      if (start >= 0) {
        addLink(
          LinkAnnotation.Clickable(
            tag = LIBRIVOX,
            styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
          ) {
            // a device without a browser can't open it, which is no reason to crash
            runCatching { uriHandler.openUri("https://librivox.org") }
              .onFailure { Logger.w(it, "Could not open LibriVox") }
          },
          start = start,
          end = start + LIBRIVOX.length,
        )
      }
    }
  }
  Text(
    modifier = Modifier.padding(top = 8.dp),
    text = linkedText,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

private const val LIBRIVOX = "LibriVox"

@Composable
private fun HelpText(@StringRes text: Int) {
  Text(
    modifier = Modifier.padding(top = 8.dp),
    text = stringResource(text),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
@Preview
private fun FolderHelpCardPreview() {
  VoiceTheme {
    Column {
      FolderHelpCard(help = FolderHelp.PickerClosed, answer = FolderHelpAnswer.NotFound, onAnswer = {})
      Spacer(Modifier.height(16.dp))
      FolderHelpCard(help = FolderHelp.NoPicker, answer = null, onAnswer = {})
    }
  }
}
