package np.bill.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import np.bill.ui.theme.Radius

/**
 * A sheet with a form in it.
 *
 * Every sheet in the app had the same fault: the content grew until the field being typed
 * into and the button that saves it were both underneath the keyboard. You could type
 * without seeing it, and you could not reach Save without dismissing the keyboard first.
 *
 * This is a fixed frame instead. The title stays put, the fields scroll between it and
 * the action, and the action sits above the keyboard where a thumb already is. Sheets
 * that fit are still only as tall as they need to be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
  title: String,
  onDismiss: () -> Unit,
  action: @Composable () -> Unit,
  subtitle: String? = null,
  /** How much of the screen a full sheet may take. Lowered for short forms. */
  heightFraction: Float = 0.86f,
  content: @Composable ColumnScope.() -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    // The sheet handles the keyboard inset itself; letting Compose do it as well pads
    // the content twice and leaves a gap the size of the keyboard.
    contentWindowInsets = { WindowInsets(0) },
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .fillMaxHeight(heightFraction)
        .imePadding(),
    ) {
      Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        subtitle?.let {
          Spacer(Modifier.height(2.dp))
          Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(
        Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp),
        content = content,
      )

      Hairline()
      Box(Modifier.padding(20.dp).navigationBarsPadding()) { action() }
    }
  }
}
