package np.bill.ui.settings

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.core.geo.Nepal
import np.bill.data.net.ApiResult
import np.bill.data.net.StoreSettingsRequest
import np.bill.data.repo.StoreRepository
import np.bill.data.sync.SyncWorker

@Immutable
data class BusinessSettingsState(
  val name: String = "",
  val nameNepali: String = "",
  val pan: String = "",
  val taxpayerType: String = "vat",
  val address: String = "",
  val ward: String = "",
  val municipality: String = "",
  val district: String = "",
  val province: String = "",
  val phone: String = "",
  val invoicePrefix: String = "",
  val printFooterNote: String = "",
  val bankDetails: String = "",
  val cbmsEnabled: Boolean = false,
  val cbmsUsername: String = "",
  val cbmsPassword: String = "",
  val romanize: Boolean = true,
  val saving: Boolean = false,
  val saved: Boolean = false,
  val offline: Boolean = false,
  val error: String? = null,
) {
  val valid: Boolean get() = name.trim().length >= 2 && address.trim().length >= 2
}

@HiltViewModel
class BusinessSettingsViewModel @Inject constructor(
  private val store: StoreRepository,
  private val application: Application,
) : ViewModel() {

  private val _state = MutableStateFlow(BusinessSettingsState())
  val state = _state.asStateFlow()

  fun load() {
    viewModelScope.launch {
      val current = store.current() ?: return@launch
      _state.value = BusinessSettingsState(
        name = current.name,
        nameNepali = current.nameNepali.orEmpty(),
        pan = current.pan,
        taxpayerType = current.taxpayerType,
        address = current.address,
        ward = current.ward?.toString().orEmpty(),
        municipality = current.municipality.orEmpty(),
        district = current.district.orEmpty(),
        province = current.province.orEmpty(),
        phone = current.phone.orEmpty(),
        invoicePrefix = current.invoicePrefix,
        printFooterNote = current.printFooterNote.orEmpty(),
        bankDetails = current.bankDetails.orEmpty(),
        cbmsEnabled = current.cbmsEnabled,
      )
    }
  }

  fun onName(value: String) = edit { it.copy(name = value) }
  fun onNameNepali(value: String) = edit { it.copy(nameNepali = value) }
  fun onRomanize(on: Boolean) = edit { it.copy(romanize = on) }
  fun onAddress(value: String) = edit { it.copy(address = value) }
  fun onMunicipality(value: String) = edit { it.copy(municipality = value) }
  fun onWard(value: String) = edit { it.copy(ward = value) }
  fun onPhone(value: String) = edit { it.copy(phone = value.filter(Char::isDigit).take(10)) }
  fun onInvoicePrefix(value: String) = edit {
    // The prefix goes into a bill number, so it stays to what a number can carry.
    it.copy(invoicePrefix = value.filter { c -> c.isLetterOrDigit() || c == '-' }.take(10).uppercase())
  }
  fun onFooter(value: String) = edit { it.copy(printFooterNote = value) }
  fun onBankDetails(value: String) = edit { it.copy(bankDetails = value) }
  fun onCbmsEnabled(on: Boolean) = edit { it.copy(cbmsEnabled = on) }
  fun onCbmsUsername(value: String) = edit { it.copy(cbmsUsername = value) }
  fun onCbmsPassword(value: String) = edit { it.copy(cbmsPassword = value) }

  fun onDistrict(value: String) = edit {
    it.copy(district = value, province = Nepal.provinceOf(value) ?: it.province)
  }

  fun onProvince(value: String) = edit {
    val keep = Nepal.districtsOf(value).any { d -> d == it.district }
    it.copy(province = value, district = if (keep) it.district else "")
  }

  private fun edit(transform: (BusinessSettingsState) -> BusinessSettingsState) =
    _state.update { transform(it).copy(saved = false, error = null) }

  fun save(onSaved: () -> Unit) {
    val current = _state.value
    if (!current.valid) return

    viewModelScope.launch {
      _state.update { it.copy(saving = true, error = null, offline = false) }

      val request = StoreSettingsRequest(
        name = current.name.trim(),
        nameNepali = current.nameNepali.trim().ifBlank { null },
        taxpayerType = current.taxpayerType,
        address = current.address.trim(),
        ward = current.ward.toIntOrNull(),
        municipality = current.municipality.trim().ifBlank { null },
        district = current.district.ifBlank { null },
        province = current.province.ifBlank { null },
        phone = current.phone.ifBlank { null },
        invoicePrefix = current.invoicePrefix,
        printFooterNote = current.printFooterNote.trim().ifBlank { null },
        bankDetails = current.bankDetails.trim().ifBlank { null },
        cbmsEnabled = current.cbmsEnabled,
        cbmsUsername = current.cbmsUsername.trim().ifBlank { null },
        // Blank means "keep what is stored", which is why it is never sent back empty.
        cbmsPassword = current.cbmsPassword.ifBlank { null },
      )

      when (val result = store.updateSettings(request)) {
        is ApiResult.Ok -> {
          SyncWorker.runNow(application)
          _state.update { it.copy(saving = false, saved = true, cbmsPassword = "") }
          onSaved()
        }
        ApiResult.Offline -> _state.update { it.copy(saving = false, offline = true) }
        is ApiResult.Failed -> _state.update { it.copy(saving = false, error = result.message) }
      }
    }
  }
}
