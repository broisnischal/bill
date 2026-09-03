package np.bill.ui.payments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import np.bill.R
import np.bill.data.repo.PaymentQrMethod
import np.bill.scan.CodeScanner
import np.bill.scan.ScanTarget
import np.bill.ui.common.Field
import np.bill.ui.common.FormSheet
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.SecondaryButton
import np.bill.ui.theme.Radius

/** How a shop can hand its code to the app. */
enum class AddQrRoute { CHOOSE, SCAN, NUMBER }

/**
 * Adding a payment QR.
 *
 * Scanning is offered first and deliberately. The shop already has a code on a card by
 * the till, and reading it captures exactly what the payment app put there, merchant
 * identifier and all, which is the only version that reliably moves money. Typing a
 * number is the fallback for a shop whose card has walked off, and a photograph is the
 * last resort, kept because some banks issue a code the camera cannot read off glossy
 * paper across a counter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddQrSheet(
  method: PaymentQrMethod,
  onScan: () -> Unit,
  onNumber: () -> Unit,
  onPhoto: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp),
    ) {
      Text(
        stringResource(R.string.qr_add_for, stringResource(method.labelRes())),
        style = MaterialTheme.typography.headlineSmall,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        stringResource(R.string.qr_add_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(Modifier.height(20.dp))
      PrimaryButton(text = stringResource(R.string.qr_add_scan), onClick = onScan)
      Spacer(Modifier.height(10.dp))
      SecondaryButton(text = stringResource(R.string.qr_add_number), onClick = onNumber)
      Spacer(Modifier.height(10.dp))
      SecondaryButton(text = stringResource(R.string.qr_add_photo), onClick = onPhoto)
    }
  }
}

/** The camera, full screen, handing back whatever the code says. */
@Composable
fun ScanQrScreen(onScanned: (String) -> Unit, onCancel: () -> Unit) {
  Dialog(
    onDismissRequest = onCancel,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(Modifier.fillMaxSize()) {
      CodeScanner(
        target = ScanTarget.QR,
        onScanned = onScanned,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/**
 * The number the wallet is registered to.
 *
 * What gets drawn is the number itself. Whether the customer's app opens straight into a
 * payment or just reads it back as text depends on that provider, so the hint says so
 * rather than letting a shop find out at the counter.
 */
@Composable
fun NumberQrDialog(
  method: PaymentQrMethod,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var value by remember { mutableStateOf("") }

  FormSheet(
    title = stringResource(R.string.qr_number_title, stringResource(method.labelRes())),
    onDismiss = onDismiss,
    heightFraction = 0.5f,
    action = {
      PrimaryButton(
        text = stringResource(R.string.save),
        onClick = { onConfirm(value.trim()) },
        enabled = value.trim().length >= 3,
      )
    },
  ) {
    Field(
      value = value,
      onValueChange = { value = it },
      label = stringResource(R.string.qr_number_label),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    Text(
      stringResource(R.string.qr_number_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

