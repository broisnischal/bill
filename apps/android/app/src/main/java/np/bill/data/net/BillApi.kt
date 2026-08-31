package np.bill.data.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BillApi {

  // Auth is Better Auth's own surface; the session token comes back in a header the
  // client stores and sends as a bearer from then on.
  @POST("api/auth/phone-number/send-otp")
  suspend fun sendOtp(@Body body: SendOtpRequest): Response<Unit>

  @POST("api/auth/phone-number/verify")
  suspend fun verifyOtp(@Body body: VerifyOtpRequest): Response<VerifyOtpResponse>

  @POST("api/auth/sign-out")
  suspend fun signOut(): Response<Unit>

  /**
   * Development only: the code the server would have sent by SMS. The route answers only
   * on a server with no SMS gateway configured, so this is a 404 anywhere real.
   */
  @GET("api/v1/dev/otp")
  suspend fun devOtp(@Query("phone") phone: String): Response<DevOtpResponse>

  @GET("api/v1/bootstrap")
  suspend fun bootstrap(): Response<BootstrapResponse>

  @POST("api/v1/store")
  suspend fun registerStore(@Body body: RegisterStoreRequest): Response<RegisterStoreResponse>

  @PATCH("api/v1/store")
  suspend fun updateStore(@Body body: StoreSettingsRequest): Response<RegisterStoreResponse>

  @POST("api/v1/devices")
  suspend fun registerDevice(@Body body: RegisterDeviceRequest): Response<RegisterDeviceResponse>

  @POST("api/v1/sync")
  suspend fun sync(@Body body: SyncRequest): Response<SyncResponse>

  @POST("api/v1/catalog")
  suspend fun upsertCatalog(@Body body: CatalogUpsertRequest): Response<kotlinx.serialization.json.JsonElement>

  @POST("api/v1/invoices/{id}/credit-note")
  suspend fun creditNote(@Path("id") id: String, @Body body: CreditNoteRequest): Response<CreditNoteResponse>

  @GET("api/v1/me")
  suspend fun myProfile(): Response<MyProfileResponse>

  @POST("api/v1/me")
  suspend fun saveProfile(@Body body: SaveProfileRequest): Response<MyProfileResponse>

  /** Resolves the card a shopper held up at the counter. Signed-in stores only. */
  @GET("api/v1/profiles/{token}")
  suspend fun scannedProfile(@Path("token") token: String): Response<ScannedProfileResponse>

  /** What a code belongs to, so the phone can say what it is about to let in. */
  @GET("api/v1/web-login/lookup")
  suspend fun lookupWebLogin(@Query("code") code: String): Response<WebLoginLookupResponse>

  @POST("api/v1/web-login?action=approve")
  suspend fun approveWebLogin(
    @Body body: WebLoginApproveRequest,
  ): Response<WebLoginApproveResponse>

  @GET("api/v1/bills/{token}")
  suspend fun publicBill(@Path("token") token: String): Response<PublicBillResponse>

  @POST("api/v1/wallet")
  suspend fun saveToWallet(@Body body: SaveToWalletRequest): Response<SaveToWalletResponse>
}
