package np.bill.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * "You tapped this — what did you want?"
 *
 * A row that does one thing when tapped will do the wrong thing for half the people who
 * tap it. Two labelled buttons cost one extra tap and remove the guessing, which matters
 * more here than saving the tap: the people using this are not going to learn that a long
 * press means something different.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
  title: String,
  primary: Pair<String, () -> Unit>,
  secondary: Pair<String, () -> Unit>? = null,
  subtitle: String? = null,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(
      topStart = Radius.sheet,
      topEnd = Radius.sheet,
    ),
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
      Text(title, style = MaterialTheme.typography.headlineSmall)
      subtitle?.let {
        Spacer(Modifier.height(2.dp))
        Text(
          it,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(20.dp))
      PrimaryButton(text = primary.first, onClick = primary.second)

      secondary?.let {
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = it.first, onClick = it.second)
      }
    }
  }
}
