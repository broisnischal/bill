package np.bill.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.core.geo.Nepal
import np.bill.ui.common.Field
import np.bill.ui.common.SegmentedChoice
import np.bill.ui.common.Hairline
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.PickerField
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.RomanizedField

/**
 * The registered business, after registration.
 *
 * Everything here prints on a bill, which is why it is editable at all — a shop that
 * moves or changes its phone number needs the next bill to say so. The PAN is the one
 * thing that is not: bills already issued carry it, and changing it would make the
 * series read as though it belonged to a different taxpayer.
 */
@Composable
fun BusinessSettingsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: BusinessSettingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) { viewModel.load() }

  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .imePadding()
      .padding(16.dp),
  ) {
    Field(
      value = state.name,
      onValueChange = viewModel::onName,
      label = stringResource(R.string.business_name),
    )

    RomanizedField(
      value = state.nameNepali,
      onValueChange = viewModel::onNameNepali,
      label = stringResource(R.string.business_name_nepali),
      romanize = state.romanize,
      onToggleRomanize = viewModel::onRomanize,
    )

    Field(
      value = state.pan,
      onValueChange = {},
      label = stringResource(R.string.pan),
      enabled = false,
      hint = stringResource(R.string.pan_locked),
    )

    Spacer(Modifier.height(8.dp))
    Hairline()
    Spacer(Modifier.height(16.dp))

    Field(
      value = state.address,
      onValueChange = viewModel::onAddress,
      label = stringResource(R.string.street_address),
    )
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
    Field(
      value = state.municipality,
      onValueChange = viewModel::onMunicipality,
      label = stringResource(R.string.local_level),
    )
    Row {
      PickerField(
        value = state.ward,
        options = Nepal.wards.map(Int::toString),
        onPick = viewModel::onWard,
        label = stringResource(R.string.ward),
        modifier = Modifier.width(120.dp),
      )
      Spacer(Modifier.width(12.dp))
      Field(
        value = state.phone,
        onValueChange = viewModel::onPhone,
        label = stringResource(R.string.business_phone),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(Modifier.height(8.dp))
    Hairline()
    Spacer(Modifier.height(16.dp))

    Field(
      value = state.invoicePrefix,
      onValueChange = viewModel::onInvoicePrefix,
      label = stringResource(R.string.invoice_prefix),
      hint = stringResource(R.string.invoice_prefix_hint),
    )
    Field(
      value = state.printFooterNote,
      onValueChange = viewModel::onFooter,
      label = stringResource(R.string.print_footer),
      singleLine = false,
      minLines = 2,
    )
    Field(
      value = state.bankDetails,
      onValueChange = viewModel::onBankDetails,
      label = stringResource(R.string.bank_details),
      singleLine = false,
      minLines = 2,
    )

    Spacer(Modifier.height(8.dp))
    Hairline()
    Spacer(Modifier.height(16.dp))

    // Hidden while VAT is switched off app-wide: a control that cannot change what a
    // bill charges is worse than no control.
    if (np.bill.BuildConfig.VAT_ENABLED) {
    Text(stringResource(R.string.taxpayer_type), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf(
        "pan" to stringResource(R.string.taxpayer_pan),
        "vat" to stringResource(R.string.taxpayer_vat),
      ),
      selected = state.taxpayerType,
      onSelect = viewModel::onTaxpayerType,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      stringResource(
        if (state.taxpayerType == "vat") R.string.taxpayer_vat_note else R.string.taxpayer_pan_note,
      ),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    }

    // CBMS is the IRD's real-time feed for VAT taxpayers. There is nothing to file with
    // it while the app charges no tax, so the whole block goes with VAT.
    if (np.bill.BuildConfig.VAT_ENABLED) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.cbms_title), style = MaterialTheme.typography.titleMedium)
        Text(
          stringResource(R.string.cbms_detail),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = state.cbmsEnabled,
        onCheckedChange = viewModel::onCbmsEnabled,
        enabled = state.taxpayerType == "vat",
      )
    }

    if (state.cbmsEnabled) {
      Spacer(Modifier.height(12.dp))
      Field(
        value = state.cbmsUsername,
        onValueChange = viewModel::onCbmsUsername,
        label = stringResource(R.string.cbms_username),
      )
      Field(
        value = state.cbmsPassword,
        onValueChange = viewModel::onCbmsPassword,
        label = stringResource(R.string.cbms_password),
        hint = stringResource(R.string.cbms_password_kept),
      )
    }
    }

    Spacer(Modifier.height(20.dp))
    PrimaryButton(
      text = stringResource(R.string.save),
      onClick = { viewModel.save(onBack) },
      enabled = state.valid,
      loading = state.saving,
    )

    Spacer(Modifier.height(12.dp))
    when {
      state.error != null -> Notice(state.error!!, tone = NoticeTone.ERROR)
      state.offline -> Notice(stringResource(R.string.register_needs_network), tone = NoticeTone.WARN)
      state.saved -> Notice(stringResource(R.string.saved))
    }
    Spacer(Modifier.height(40.dp))
  }
}
