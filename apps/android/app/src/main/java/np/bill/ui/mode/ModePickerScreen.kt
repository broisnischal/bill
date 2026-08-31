package np.bill.ui.mode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import np.bill.R

/**
 * The one fork in the app: a shopkeeper making bills, or a shopper keeping them. Asked
 * once, changeable in settings, and never asked again.
 */
@Composable
fun ModePickerScreen(
  onBusiness: (hasStore: Boolean) -> Unit,
  onCustomer: () -> Unit,
  viewModel: ModeViewModel = hiltViewModel(),
) {
  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(32.dp))

    ModeCard(
      icon = Icons.Outlined.Storefront,
      title = stringResource(R.string.mode_business),
      detail = stringResource(R.string.mode_business_detail),
      onClick = { viewModel.chooseBusiness(onBusiness) },
    )
    Spacer(Modifier.height(16.dp))
    ModeCard(
      icon = Icons.Outlined.QrCodeScanner,
      title = stringResource(R.string.mode_customer),
      detail = stringResource(R.string.mode_customer_detail),
      onClick = { viewModel.chooseCustomer(onCustomer) },
    )
  }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(36.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.size(16.dp))
      Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
          detail,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
