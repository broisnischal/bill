package np.bill.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.core.geo.Nepal
import np.bill.core.nepali.BsDate
import np.bill.data.net.ApiResult
import np.bill.data.net.RegisterStoreRequest
import np.bill.data.repo.AuthRepository
import np.bill.data.sync.SyncWorker

@androidx.compose.runtime.Immutable
data class RegisterState(
  val name: String = "",
  val nameNepali: String = "",
  val pan: String = "",
  val taxpayerType: String = "vat",
  val registrationDateBs: String = "",
  val address: String = "",
  val ward: String = "",
  val municipality: String = "",
  val district: String = "",
  val province: String = "",
  val phone: String = "",
  val romanize: Boolean = true,
  val saving: Boolean = false,
  val offline: Boolean = false,
  val error: String? = null,
  val locationMessage: String? = null,
  val locationFound: Boolean = false,
) {
  val valid: Boolean
    get() = name.trim().length >= 2 &&
      pan.length == 9 &&
      address.trim().length >= 2 &&
      BsDate.parse(registrationDateBs) != null
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
  private val auth: AuthRepository,
  private val location: np.bill.device.LocationHint,
  private val application: android.app.Application,
) : ViewModel() {

  private val _state = MutableStateFlow(RegisterState())
  val state = _state.asStateFlow()

  fun onName(value: String) = _state.update { it.copy(name = value, error = null) }
  fun onNameNepali(value: String) = _state.update { it.copy(nameNepali = value) }
  fun onPan(value: String) = _state.update { it.copy(pan = value.filter(Char::isDigit).take(9), error = null) }
  fun onTaxpayerType(value: String) = _state.update { it.copy(taxpayerType = value) }
  fun onRegistrationDate(value: String) = _state.update { it.copy(registrationDateBs = value.take(10)) }
  fun onAddress(value: String) = _state.update { it.copy(address = value) }
  fun onWard(value: String) = _state.update { it.copy(ward = value.filter(Char::isDigit).take(2)) }
  fun onMunicipality(value: String) = _state.update { it.copy(municipality = value) }
  fun onRomanize(on: Boolean) = _state.update { it.copy(romanize = on) }

  /** Picking a district fills the province too, since one determines the other. */
  fun onDistrict(value: String) = _state.update {
    it.copy(district = value, province = Nepal.provinceOf(value) ?: it.province)
  }

  /** Changing province clears a district that no longer belongs to it. */
  fun onProvince(value: String) = _state.update {
    val keepDistrict = Nepal.districtsOf(value).any { d -> d == it.district }
    it.copy(province = value, district = if (keepDistrict) it.district else "")
  }

  fun canUseLocation(): Boolean = location.hasPermission()

  /**
   * Fills the address from where the phone is standing. Everything it writes stays
   * editable: a geocoder that puts a shop one street over is a nuisance, not an error.
   */
  fun fillFromLocation() {
    viewModelScope.launch {
      _state.update { it.copy(locationMessage = null) }
      val suggestion = location.suggest()
      if (suggestion == null) {
        _state.update {
          it.copy(
            locationFound = false,
            locationMessage = application.getString(np.bill.R.string.location_unavailable),
          )
        }
        return@launch
      }

      _state.update { current ->
        current.copy(
          address = suggestion.address?.takeIf { current.address.isBlank() } ?: current.address,
          municipality = suggestion.municipality?.takeIf { current.municipality.isBlank() }
            ?: current.municipality,
          district = suggestion.district ?: current.district,
          province = suggestion.province ?: current.province,
          locationFound = true,
          locationMessage = application.getString(np.bill.R.string.location_filled),
        )
      }
    }
  }
  fun onPhone(value: String) = _state.update { it.copy(phone = value.filter(Char::isDigit).take(10)) }

  fun submit(onRegistered: () -> Unit) {
    val current = _state.value
    if (!current.valid) return

    viewModelScope.launch {
      _state.update { it.copy(saving = true, error = null, offline = false) }

      val request = RegisterStoreRequest(
        name = current.name.trim(),
        nameNepali = current.nameNepali.trim().ifBlank { null },
        pan = current.pan,
        taxpayerType = current.taxpayerType,
        registrationDateBs = current.registrationDateBs,
        businessType = "sole_proprietorship",
        address = current.address.trim(),
        ward = current.ward.toIntOrNull(),
        municipality = current.municipality.trim().ifBlank { null },
        district = current.district.trim().ifBlank { null },
        province = current.province.trim().ifBlank { null },
        phone = current.phone.ifBlank { null },
      )

      when (val result = auth.registerStore(request)) {
        is ApiResult.Ok -> {
          // The first sync registers the till and fetches the numbers it will print from,
          // so the shop can go offline straight after setting up.
          SyncWorker.runNow(application)
          _state.update { it.copy(saving = false) }
          onRegistered()
        }
        ApiResult.Offline -> _state.update { it.copy(saving = false, offline = true) }
        is ApiResult.Failed -> _state.update { it.copy(saving = false, error = result.message) }
      }
    }
  }
}
