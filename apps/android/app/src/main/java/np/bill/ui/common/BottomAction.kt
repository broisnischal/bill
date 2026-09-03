package np.bill.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one action a screen exists for, pinned across the bottom.
 *
 * It replaced a floating button, which was the wrong shape for this app twice over: it
 * sat on top of the last row of every list, and a circle with a plus in it assumes the
 * person using it already knows what the plus does. A full-width labelled bar says what
 * will happen, is impossible to miss, and covers nothing.
 */
@Composable
fun BottomAction(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  loading: Boolean = false,
  secondary: (@Composable () -> Unit)? = null,
) {
  // Transparent: the tabs below it already float over the page, and a filled bar here
  // put a second horizon across the bottom of every list.
  Surface(modifier.fillMaxWidth(), color = androidx.compose.ui.graphics.Color.Transparent) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
      secondary?.invoke()
      PrimaryButton(text = text, onClick = onClick, enabled = enabled, loading = loading)
    }
  }
}
