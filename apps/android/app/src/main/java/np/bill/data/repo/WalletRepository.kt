package np.bill.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import np.bill.data.db.WalletBillEntity
import np.bill.data.db.WalletDao
import np.bill.data.net.ApiResult
import np.bill.data.net.BillApi
import np.bill.data.net.PublicBillResponse
import np.bill.data.net.SaveToWalletRequest
import np.bill.data.net.apiCall
import np.bill.util.Iso8601

/**
 * Customer mode.
 *
 * A shopper scans the QR on a bill they were handed, and the bill is filed on their
 * phone and against their account. The whole document is kept locally, so the wallet
 * reads with no network once a bill is in it.
 */
@Singleton
class WalletRepository @Inject constructor(
  private val api: BillApi,
  private val wallet: WalletDao,
  private val json: Json,
) {

  fun bills(): Flow<List<WalletBillEntity>> = wallet.bills()

  fun observe(token: String): Flow<WalletBillEntity?> = wallet.observe(token)

  fun spentSince(epochMillis: Long): Flow<Long> = wallet.spentSince(epochMillis)

  /** The token a scanned QR carries, whether it was a bare code or a bill.np link. */
  fun tokenFrom(scanned: String): String? {
    val candidate = scanned.trim().substringAfterLast('/').substringBefore('?')
    return if (TOKEN.matches(candidate)) candidate else null
  }

  suspend fun fetch(token: String): ApiResult<PublicBillResponse> = apiCall { api.publicBill(token) }

  /**
   * Files a scanned bill. It lands on the phone first so the shopper sees it even if the
   * account-side save has to wait for signal.
   */
  suspend fun save(token: String, bill: PublicBillResponse): ApiResult<Unit> {
    wallet.save(
      WalletBillEntity(
        shareToken = token,
        invoiceNumber = bill.invoice.invoiceNumber,
        sellerName = bill.seller.name,
        sellerPan = bill.seller.pan,
        sellerAddress = bill.seller.address,
        issuedAt = Iso8601.parse(bill.invoice.issuedAt) ?: System.currentTimeMillis(),
        miti = bill.invoice.miti,
        totalPaisa = bill.invoice.totalPaisa,
        status = bill.invoice.status,
        paymentMethod = bill.invoice.paymentMethod,
        payloadJson = json.encodeToString(PublicBillResponse.serializer(), bill),
      ),
    )

    return when (val response = apiCall { api.saveToWallet(SaveToWalletRequest(token)) }) {
      is ApiResult.Ok -> ApiResult.Ok(Unit)
      ApiResult.Offline -> ApiResult.Offline
      is ApiResult.Failed -> response
    }
  }

  fun decode(entity: WalletBillEntity): PublicBillResponse? = runCatching {
    json.decodeFromString(PublicBillResponse.serializer(), entity.payloadJson)
  }.getOrNull()

  suspend fun remove(token: String) = wallet.remove(token)

  private companion object {
    val TOKEN = Regex("^[a-f0-9]{32}$")
  }
}
