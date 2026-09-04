package np.bill.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.core.geo.Nepal
import np.bill.ui.common.BsDateField
import np.bill.ui.common.Field
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.PickerField
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.RomanizedField
import np.bill.ui.common.SecondaryButton
import np.bill.ui.theme.Gutter
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/**
 * Registering the business, three questions at a time.
 *
 * It used to be ten fields on one page, which a shopkeeper reads as work. Nothing the IRD
 * needs has changed — the name, the PAN, the registration date and the address are all
 * still required — only how much of it is asked at once. What is on file after this is a
 * business waiting for review, not one that can bill.
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

  // Back steps through the questions before it leaves the form.
  androidx.activity.compose.BackHandler(enabled = state.step > 0) { viewModel.back() }

  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .imePadding()
      .padding(horizontal = Gutter),
  ) {
    Spacer(Modifier.height(12.dp))

    // Three marks, not a percentage: the point is that there are only three, and that
    // this is the second of them.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      repeat(RegisterState.STEPS) { index ->
        Box(
          Modifier
            .weight(1f)
            .height(4.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(
              if (index <= state.step) {
                LocalTokens.current.ink
              } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
              },
            ),
        )
      }
    }

    Spacer(Modifier.height(28.dp))

    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState()),
    ) {
      when (state.step) {
        0 -> {
          StepHeading(
            title = stringResource(R.string.step_name_title),
            detail = stringResource(R.string.step_name_detail),
          )
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
        }

        1 -> {
          StepHeading(
            title = stringResource(R.string.step_pan_title),
            detail = stringResource(R.string.step_pan_detail),
          )
          Field(
            value = state.pan,
            onValueChange = viewModel::onPan,
            label = stringResource(R.string.pan),
            placeholder = stringResource(R.string.pan_hint),
            error = if (state.pan.isNotEmpty() && state.pan.length < 9) {
              stringResource(R.string.pan_invalid)
            } else {
              null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          )
          BsDateField(
            value = state.registrationDateBs,
            onValueChange = viewModel::onRegistrationDate,
            label = stringResource(R.string.registration_date),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        else -> {
          StepHeading(
            title = stringResource(R.string.step_place_title),
            detail = stringResource(R.string.step_place_detail),
          )

          SecondaryButton(
            text = stringResource(R.string.use_my_location),
            onClick = {
              if (viewModel.canUseLocation()) {
                viewModel.fillFromLocation()
              } else {
                locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
              }
            },
          )
          state.locationMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(
              it,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(16.dp))

          Field(
            value = state.address,
            onValueChange = viewModel::onAddress,
            label = stringResource(R.string.street_address),
          )
          PickerField(
            value = state.province.ifBlank { null },
            options = Nepal.provinces.map { it.name },
            onPick = viewModel::onProvince,
            label = stringResource(R.string.province),
          )
          PickerField(
            value = state.district.ifBlank { null },
            options = Nepal.districtsOf(state.province),
            onPick = viewModel::onDistrict,
            label = stringResource(R.string.district),
            searchable = true,
          )
          Field(
            value = state.municipality,
            onValueChange = viewModel::onMunicipality,
            label = stringResource(R.string.local_level),
          )
          Row(verticalAlignment = Alignment.Top) {
            PickerField(
              value = state.ward.ifBlank { null },
              options = (1..35).map(Int::toString),
              onPick = viewModel::onWard,
              label = stringResource(R.string.ward),
              modifier = Modifier.width(132.dp),
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
        }
      }
    }

    when {
      state.error != null -> Notice(state.error!!, tone = NoticeTone.ERROR)
      state.offline -> Notice(stringResource(R.string.register_needs_network), tone = NoticeTone.WARN)
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (state.step > 0) {
        SecondaryButton(
          text = stringResource(R.string.back),
          onClick = viewModel::back,
          modifier = Modifier.weight(1f),
        )
      }
      PrimaryButton(
        text = if (state.step == RegisterState.STEPS - 1) {
          stringResource(R.string.save_and_start)
        } else {
          stringResource(R.string.continue_label)
        },
        onClick = {
          if (state.step == RegisterState.STEPS - 1) {
            viewModel.submit(onRegistered)
          } else {
            viewModel.next()
          }
        },
        enabled = state.stepDone(state.step),
        loading = state.saving,
        modifier = Modifier.weight(if (state.step > 0) 1.4f else 1f),
      )
    }
    Spacer(Modifier.height(20.dp))
  }
}

/** The question, and one line saying why it is being asked. */
@Composable
private fun StepHeading(title: String, detail: String) {
  Text(title, style = MaterialTheme.typography.displaySmall)
  Spacer(Modifier.height(8.dp))
  Text(
    detail,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Spacer(Modifier.height(24.dp))
}
