package np.bill.ui.billing

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import np.bill.R
import np.bill.data.db.BillEntity
import np.bill.data.db.BillLineEntity
import np.bill.data.prefs.SessionStore
import np.bill.data.repo.BillingRepository
import np.bill.data.sync.SyncWorker
import np.bill.print.A4Invoice
import np.bill.print.PdfExporter
import np.bill.print.ReceiptRenderer
import np.bill.print.ThermalPrinter

data class BillDetailState(
  val loading: Boolean = true,
  val missing: Boolean = false,
  val bill: BillEntity? = null,
  val lines: List<BillLineEntity> = emptyList(),
  val qr: Bitmap? = null,
  val printing: Boolean = false,
  val message: String? = null,
  val messageIsError: Boolean = false,
)

/** The base a scanned QR resolves against. Customer mode also accepts the bare token. */
/**
 * Where a scanned or shared bill resolves.
 *
 * Built from the server this build talks to, not written out again: a QR printed on a
 * customer's receipt outlives the till, and one carrying a hostname that answers nothing
 * is a bill the buyer can never open.
 */
private val SHARE_BASE = np.bill.BuildConfig.API_BASE_URL.trimEnd('/') + "/b/"

@HiltViewModel
class BillDetailViewModel @Inject constructor(
  private val billing: BillingRepository,
  private val session: SessionStore,
  private val printer: ThermalPrinter,
  private val pdf: PdfExporter,
  private val application: Application,
) : ViewModel() {

  private val _state = MutableStateFlow(BillDetailState())
  val state = _state.asStateFlow()

  private val renderer = ReceiptRenderer()

  fun load(billId: String) {
    viewModelScope.launch {
      val loaded = billing.load(billId)
      if (loaded == null) {
        // Reachable when the app restores to a bill that is no longer on this device,
        // which is exactly when a blank screen is least helpful.
        _state.value = BillDetailState(loading = false, missing = true)
        return@launch
      }
      val (bill, lines) = loaded
      // The QR is drawn once, off the main thread, and kept: it never changes for a bill.
      val qr = withContext(Dispatchers.Default) {
        renderer.qr(SHARE_BASE + bill.shareToken, 512)
      }
      _state.value = BillDetailState(loading = false, bill = bill, lines = lines, qr = qr)
    }
  }

  /** The 80mm roll: what the customer is handed, QR and all. */
  private suspend fun receipt(): Bitmap? {
    val bill = _state.value.bill ?: return null
    val store = session.current().store ?: return null
    return withContext(Dispatchers.Default) {
      renderer.render(
        seller = ReceiptRenderer.Seller(
          name = store.name,
          nameNepali = store.nameNepali,
          pan = store.pan,
          vatRegistered = store.taxpayerType == "vat",
          address = listOfNotNull(
            store.address,
            store.ward?.let { "Ward $it" },
            store.municipality,
            store.district,
          ).joinToString(", "),
          phone = store.phone,
          footerNote = store.printFooterNote,
        ),
        bill = bill,
        lines = _state.value.lines,
        copyNumber = bill.printCount + 1,
        qrPayload = SHARE_BASE + bill.shareToken,
      )
    }
  }

  fun print(context: Context) {
    val bill = _state.value.bill ?: return
    viewModelScope.launch {
      _state.update { it.copy(printing = true, message = null) }

      val address = session.current().printerAddress
      if (address == null) {
        _state.update {
          it.copy(
            printing = false,
            message = application.getString(R.string.printer_none),
            messageIsError = true,
          )
        }
        return@launch
      }

      val bitmap = receipt()
      if (bitmap == null) {
        _state.update { it.copy(printing = false) }
        return@launch
      }

      when (val outcome = printer.print(address, bitmap)) {
        ThermalPrinter.Outcome.Printed -> {
          // The count is what turns the next copy into "Copy of Original (#2)".
          billing.countPrint(bill.id)
          load(bill.id)
          _state.update { it.copy(printing = false, message = null) }
        }
        ThermalPrinter.Outcome.NoPermission -> setError(R.string.printer_pair_hint)
        ThermalPrinter.Outcome.NoPrinter -> setError(R.string.printer_none)
        is ThermalPrinter.Outcome.Failed ->
          _state.update { it.copy(printing = false, message = outcome.message, messageIsError = true) }
      }
    }
  }

  /**
   * Sends the bill on.
   *
   * Two papers, because a Nepali shop uses both: the 80mm roll is what the customer was
   * handed and what gets forwarded on Viber, and the A4 sheet is the one that goes to an
   * accountant. Neither carries a QR on the A4 — the code belongs on the counter copy.
   */
  fun share(context: Context, format: PrintFormat = PrintFormat.RECEIPT_80MM) {
    viewModelScope.launch {
      val bill = _state.value.bill ?: return@launch
      val store = session.current().store ?: return@launch
      val name = bill.invoiceNumber.replace(Regex("[^\\w.-]"), "_")

      val uri = when (format) {
        PrintFormat.RECEIPT_80MM -> {
          val bitmap = receipt() ?: return@launch
          pdf.exportReceipt("$name-80mm.pdf", bitmap)
        }
        PrintFormat.A4 -> pdf.export(
          fileName = "$name-a4.pdf",
          input = A4Invoice.Input(
            store = store,
            bill = bill,
            lines = _state.value.lines,
            copyNumber = maxOf(bill.printCount, 1),
            enteredByName = session.current().phoneNumber.orEmpty(),
          ),
        )
      }

      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(Intent.createChooser(intent, null))
    }
  }

  enum class PrintFormat { RECEIPT_80MM, A4 }

  fun cancel(reason: String) {
    val bill = _state.value.bill ?: return
    viewModelScope.launch {
      billing.cancel(bill.id, reason)
      SyncWorker.runNow(application)
      load(bill.id)
    }
  }

  private fun setError(resource: Int) = _state.update {
    it.copy(printing = false, message = application.getString(resource), messageIsError = true)
  }
}
