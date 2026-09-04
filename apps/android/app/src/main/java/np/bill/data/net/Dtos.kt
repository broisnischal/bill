package np.bill.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire shapes for `/api/v1`. Field names match the server's JSON exactly; nothing is
 * renamed on the way in, so a change on either side shows up as a compile error here
 * rather than as a silently missing value on a bill.
 */

@Serializable
data class ApiErrorBody(val error: ApiErrorDetail)

@Serializable
data class ApiErrorDetail(val code: String, val message: String)

@Serializable
data class SendOtpRequest(val phoneNumber: String)

@Serializable
data class VerifyOtpRequest(val phoneNumber: String, val code: String)

@Serializable
data class VerifyOtpResponse(val status: Boolean, val token: String? = null, val user: AuthUser? = null)

@Serializable
data class AuthUser(
  val id: String,
  val name: String,
  val phoneNumber: String? = null,
)

@Serializable
data class DevOtpResponse(val phoneNumber: String, val code: String)

/**
 * The newest build the server is handing out, and the oldest it still talks to.
 *
 * `minimumVersionCode` is the difference between an update the shopkeeper can put off
 * and one that stops the app until it is taken.
 */
@Serializable
data class AppReleaseResponse(
  val versionName: String,
  val versionCode: Int,
  val minimumVersionCode: Int,
  val apkUrl: String,
  val notes: String = "",
)

@Serializable
data class BootstrapResponse(
  val user: AuthUser,
  val store: StoreDto? = null,
  /** What has been uploaded for review, so the onboarding knows what is still missing. */
  val documents: List<StoreDocumentDto> = emptyList(),
  val role: String? = null,
  val serverTime: String,
  val fiscalYear: String,
  val miti: String,
)

@Serializable
data class StoreDto(
  val id: String,
  val name: String,
  val nameNepali: String? = null,
  val tradeName: String? = null,
  val pan: String,
  val taxpayerType: String,
  val registrationDateBs: String,
  val businessType: String,
  val taxOffice: String? = null,
  val address: String,
  val ward: Int? = null,
  val municipality: String? = null,
  val district: String? = null,
  val province: String? = null,
  val phone: String? = null,
  val email: String? = null,
  val website: String? = null,
  val invoicePrefix: String = "",
  val vatRateBp: Int = 1300,
  val printFooterNote: String? = null,
  val bankDetails: String? = null,
  val cbmsEnabled: Boolean = false,
  /**
   * Where review has got to: pending, approved or rejected.
   *
   * Persisted with the rest of the store, so a till that opens with no signal still
   * knows whether it may bill rather than finding out when the server refuses a sync.
   */
  val status: String = "pending",
  /** Why it was refused, in words the shopkeeper reads. Null unless rejected. */
  val reviewNote: String? = null,
)

/** A paper the business has uploaded. The bytes never come down; only the fact. */
@Serializable
data class StoreDocumentDto(
  val id: String,
  val kind: String,
  val fileName: String? = null,
  val mimeType: String,
  val sizeBytes: Long,
)

@Serializable
data class RegisterStoreRequest(
  val name: String,
  val nameNepali: String? = null,
  val pan: String,
  val taxpayerType: String,
  val registrationDateBs: String,
  val businessType: String,
  val address: String,
  val ward: Int? = null,
  val municipality: String? = null,
  val district: String? = null,
  val province: String? = null,
  val phone: String? = null,
)

@Serializable
data class RegisterStoreResponse(val store: StoreDto, val role: String)

/**
 * What a shop can change after it is registered.
 *
 * The PAN is absent on purpose: bills already carry it, so it is fixed for the life of
 * the business.
 */
@Serializable
data class StoreSettingsRequest(
  val name: String,
  val nameNepali: String? = null,
  val taxpayerType: String,
  val address: String,
  val ward: Int? = null,
  val municipality: String? = null,
  val district: String? = null,
  val province: String? = null,
  val phone: String? = null,
  val invoicePrefix: String = "",
  val printFooterNote: String? = null,
  val bankDetails: String? = null,
  val cbmsEnabled: Boolean = false,
  val cbmsUsername: String? = null,
  val cbmsPassword: String? = null,
)

@Serializable
data class RegisterDeviceRequest(
  val id: String,
  val name: String,
  val platform: String = "android",
  val appVersion: String? = null,
)

@Serializable
data class RegisterDeviceResponse(val device: DeviceDto)

@Serializable
data class DeviceDto(val id: String, val name: String, val storeId: String)

@Serializable
data class LineDto(
  val itemId: String? = null,
  val description: String,
  val hsCode: String? = null,
  val unit: String,
  val quantityMilli: Long,
  val unitPricePaisa: Long,
  val discountPaisa: Long = 0,
  val vatApplicable: Boolean = true,
)

@Serializable
data class DeviceInvoiceDto(
  val id: String,
  val shareToken: String,
  val leaseId: String,
  val sequence: Int,
  val fiscalYear: String,
  val issuedAt: String,
  val queuedAt: String,
  val totalPaisa: Long,
  val paidAtIssuePaisa: Long = 0,
  val dueMiti: String? = null,
  val shopperLink: String? = null,
  val invoiceType: String,
  val customerId: String? = null,
  val buyerName: String,
  val buyerPan: String? = null,
  val buyerAddress: String? = null,
  val buyerPhone: String? = null,
  val paymentMethod: String,
  val notes: String? = null,
  val discountPaisa: Long = 0,
  val saveCustomer: Boolean = false,
  val lines: List<LineDto>,
)

@Serializable
data class CancellationDto(val invoiceId: String, val reason: String)

@Serializable
data class PaymentDto(
  val id: String,
  val invoiceId: String,
  val amountPaisa: Long,
  val method: String,
  val receivedAt: String,
  val miti: String,
  val note: String? = null,
)

@Serializable
data class PaymentResultDto(
  val id: String,
  val status: String,
  val message: String? = null,
)

@Serializable
data class SyncRequest(
  val invoices: List<DeviceInvoiceDto> = emptyList(),
  val cancellations: List<CancellationDto> = emptyList(),
  val payments: List<PaymentDto> = emptyList(),
  val want: Map<String, Int>? = null,
  val catalogSince: String? = null,
)

@Serializable
data class SyncResponse(
  val serverTime: String,
  val fiscalYear: String,
  val miti: String,
  val results: List<InvoiceResultDto> = emptyList(),
  val cancellations: List<CancellationResultDto> = emptyList(),
  val payments: List<PaymentResultDto> = emptyList(),
  val leases: List<LeaseDto> = emptyList(),
  val catalog: CatalogDto = CatalogDto(),
  val store: StoreDto? = null,
  /**
   * Where review has got to, which is what decides whether numbers were sent.
   *
   * The server has always returned this and the app has always thrown it away, so a till
   * whose business was still being reviewed knew only that it had no numbers and guessed
   * at why — out loud, to a shopkeeper, in the form of advice about their internet.
   */
  val review: SyncReviewDto? = null,
)

@Serializable
data class SyncReviewDto(val status: String, val note: String? = null)

@Serializable
data class InvoiceResultDto(
  val id: String,
  val status: String,
  val invoiceNumber: String? = null,
  val error: ApiErrorDetail? = null,
)

@Serializable
data class CancellationResultDto(
  val invoiceId: String,
  val status: String,
  val message: String? = null,
)

@Serializable
data class LeaseDto(
  val id: String,
  val fiscalYear: String,
  val invoiceType: String,
  val startSequence: Int,
  val endSequence: Int,
  val usedThrough: Int,
  val expiresAt: String,
)

@Serializable
data class CatalogDto(
  val items: List<ItemDto> = emptyList(),
  val customers: List<CustomerDto> = emptyList(),
)

@Serializable
data class ItemDto(
  val id: String,
  val name: String,
  val description: String? = null,
  val hsCode: String? = null,
  val sku: String? = null,
  val barcode: String? = null,
  val unit: String,
  val unitPricePaisa: Long,
  val stockThousandths: Long? = null,
  val tags: List<String> = emptyList(),
  val vatApplicable: Boolean,
  val active: Boolean,
  val updatedAt: String,
)

@Serializable
data class CustomerDto(
  val id: String,
  val name: String,
  val pan: String? = null,
  val address: String? = null,
  val phone: String? = null,
  val email: String? = null,
  val updatedAt: String,
)

@Serializable
data class CatalogUpsertRequest(val kind: String, val value: kotlinx.serialization.json.JsonElement)

@Serializable
data class ItemUpsert(
  val id: String? = null,
  val name: String,
  val description: String? = null,
  val hsCode: String? = null,
  val sku: String? = null,
  val barcode: String? = null,
  val unit: String = "pcs",
  val unitPricePaisa: Long,
  val stockThousandths: Long? = null,
  val tags: List<String> = emptyList(),
  val vatApplicable: Boolean = true,
  val active: Boolean = true,
)

@Serializable
data class CustomerUpsert(
  val id: String? = null,
  val name: String,
  val pan: String? = null,
  val address: String? = null,
  val phone: String? = null,
  val email: String? = null,
)

@Serializable
data class ItemResponse(val item: ItemDto)

@Serializable
data class CustomerResponse(val customer: CustomerDto)

/** What a shopper's phone gets back when it scans the QR printed on a bill. */
@Serializable
data class PublicBillResponse(
  val invoice: PublicInvoiceDto,
  val seller: SellerDto,
  val items: List<PublicLineDto>,
)

@Serializable
data class PublicInvoiceDto(
  val id: String,
  val invoiceNumber: String,
  val shareToken: String,
  val fiscalYear: String,
  val invoiceType: String,
  val buyerName: String,
  val buyerPan: String? = null,
  val issuedAt: String,
  val miti: String,
  val subTotalPaisa: Long,
  val discountPaisa: Long,
  val taxableAmountPaisa: Long,
  val nonTaxableAmountPaisa: Long,
  val vatRateBp: Int,
  val vatAmountPaisa: Long,
  val totalPaisa: Long,
  val amountInWords: String,
  val paymentMethod: String,
  val status: String,
)

@Serializable
data class SellerDto(
  val name: String,
  val nameNepali: String? = null,
  val pan: String,
  val taxpayerType: String,
  val address: String,
  val phone: String? = null,
)

@Serializable
data class PublicLineDto(
  val lineNo: Int,
  val description: String,
  val hsCode: String? = null,
  val unit: String,
  val quantityMilli: Long,
  val unitPricePaisa: Long,
  val discountPaisa: Long,
  val vatApplicable: Boolean,
  val lineTotalPaisa: Long,
)

@Serializable
data class ShopperProfileDto(
  val token: String,
  val name: String,
  val phone: String? = null,
  val pan: String? = null,
  val address: String? = null,
)

@Serializable
data class CardCodeDto(val code: String, val expiresAt: String)

@Serializable
data class MyProfileResponse(val profile: ShopperProfileDto, val card: CardCodeDto? = null)

@Serializable
data class SaveProfileRequest(
  val name: String,
  val phone: String? = null,
  val pan: String? = null,
  val address: String? = null,
)

@Serializable
data class ScannedProfileDto(
  val name: String,
  val phone: String? = null,
  val pan: String? = null,
  val address: String? = null,
  /** Signed handle the till keeps, so an offline bill can still name its buyer. */
  val link: String? = null,
)

@Serializable
data class ScannedProfileResponse(val profile: ScannedProfileDto)

@Serializable
data class WebLoginLookupResponse(val browser: String, val expiresAt: String)

@Serializable
data class WebLoginApproveRequest(val code: String, val approve: Boolean = true)

@Serializable
data class WebLoginApproveResponse(val approved: Boolean)

@Serializable
data class SaveToWalletRequest(val token: String)

@Serializable
data class SaveToWalletResponse(val saved: Boolean, val invoiceId: String)

@Serializable
data class CreditNoteRequest(val reason: String)

@Serializable
data class CreditNoteResponse(@SerialName("creditNote") val creditNote: PublicInvoiceDto)

@Serializable
data class StoreDocumentResponse(val document: StoreDocumentDto)

@Serializable
data class StoreDocumentsResponse(val documents: List<StoreDocumentDto> = emptyList())
