package np.bill.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.core.geo.Nepal
import np.bill.ui.common.BsDateField
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.PickerField
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.RomanizedField

/**
 * The one form in the app.
 *
 * Only what the IRD requires on a printed bill is asked for. The parts that are closed
 * sets — province, district, ward, registration date — are picked rather than typed, and
 * the phone offers to fill the address in from where it is standing, because a shopkeeper
 * registering a shop is almost always standing in it.
 */
@Composable
fun RegisterBusinessScreen(
  onRegistered: () -> Unit,
  viewModel: RegisterViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  val locationLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted -> if (granted) viewModel.fillFromLocation() }

  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
  ) {
    Text(stringResource(R.string.register_title), style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text(
      stringResource(R.string.register_subtitle),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
      value = state.name,
      onValueChange = viewModel::onName,
      label = { Text(stringResource(R.string.business_name)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    RomanizedField(
      value = state.nameNepali,
      onValueChange = viewModel::onNameNepali,
      label = stringResource(R.string.business_name_nepali),
      romanize = state.romanize,
      onToggleRomanize = viewModel::onRomanize,
    )

    OutlinedTextField(
      value = state.pan,
      onValueChange = viewModel::onPan,
      label = { Text(stringResource(R.string.pan)) },
      placeholder = { Text(stringResource(R.string.pan_hint)) },
      singleLine = true,
      isError = state.pan.isNotEmpty() && state.pan.length != 9,
      supportingText = if (state.pan.isNotEmpty() && state.pan.length != 9) {
        { Text(stringResource(R.string.pan_invalid)) }
      } else {
        null
      },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.taxpayer_type), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row {
      FilterChip(
        selected = state.taxpayerType == "vat",
        onClick = { viewModel.onTaxpayerType("vat") },
        label = { Text(stringResource(R.string.taxpayer_vat)) },
      )
      Spacer(Modifier.width(8.dp))
      FilterChip(
        selected = state.taxpayerType == "pan",
        onClick = { viewModel.onTaxpayerType("pan") },
        label = { Text(stringResource(R.string.taxpayer_pan)) },
      )
    }

    Spacer(Modifier.height(16.dp))
    BsDateField(
      value = state.registrationDateBs,
      onValueChange = viewModel::onRegistrationDate,
      label = stringResource(R.string.registration_date),
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(20.dp))
    TextButton(
      onClick = {
        if (viewModel.canUseLocation()) {
          viewModel.fillFromLocation()
        } else {
          locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
      },
    ) {
      Icon(Icons.Filled.MyLocation, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text(stringResource(R.string.use_my_location))
    }

    state.locationMessage?.let {
      Notice(
        it,
        tone = if (state.locationFound) NoticeTone.INFO else NoticeTone.WARN,
      )
      Spacer(Modifier.height(12.dp))
    }

    OutlinedTextField(
      value = state.address,
      onValueChange = viewModel::onAddress,
      label = { Text(stringResource(R.string.street_address)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(12.dp))
    PickerField(
      value = state.province,
      options = Nepal.provinceNames,
      onPick = viewModel::onProvince,
      label = stringResource(R.string.province),
    )

    Spacer(Modifier.height(12.dp))
    PickerField(
      value = state.district,
      options = Nepal.districtsOf(state.province),
      onPick = viewModel::onDistrict,
      label = stringResource(R.string.district),
      searchable = true,
    )

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = state.municipality,
      onValueChange = viewModel::onMunicipality,
      label = { Text(stringResource(R.string.local_level)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(12.dp))
    PickerField(
      value = state.ward,
      options = Nepal.wards.map(Int::toString),
      onPick = viewModel::onWard,
      label = stringResource(R.string.ward),
    )

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = state.phone,
      onValueChange = viewModel::onPhone,
      label = { Text(stringResource(R.string.business_phone)) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Done,
      ),
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))
    PrimaryButton(
      text = stringResource(R.string.save_and_start),
      onClick = { viewModel.submit(onRegistered) },
      enabled = state.valid,
      loading = state.saving,
    )

    Spacer(Modifier.height(16.dp))
    when {
      state.error != null -> Notice(state.error!!, tone = NoticeTone.ERROR)
      state.offline -> Notice(stringResource(R.string.register_needs_network), tone = NoticeTone.WARN)
    }
    Spacer(Modifier.height(40.dp))
  }
}
