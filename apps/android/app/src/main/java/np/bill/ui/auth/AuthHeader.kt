package np.bill.ui.auth

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The top of both sign-in steps, shared so the two land in the same place.
 *
 * Both screens used to centre themselves vertically, which put the heading at a height
 * that depended on how much was under it — so stepping from the number to the code moved
 * the title, and opening the keyboard moved it again. Anchored to the top instead, with
 * the same lead-in on both, the heading does not move once between typing a number and
 * typing the code that comes back.
 *
 * 64dp of it, because a heading hard against the status bar reads as a screen that was
 * cut off rather than one that starts there.
 */
@Composable
fun ColumnScope.AuthHeader(title: String, subtitle: String) {
  Spacer(Modifier.height(64.dp))
  Text(title, style = MaterialTheme.typography.displaySmall)
  Spacer(Modifier.height(8.dp))
  Text(
    subtitle,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Spacer(Modifier.height(28.dp))
}
