package np.bill.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import np.bill.data.net.ApiResult
import np.bill.data.net.BillApi
import np.bill.data.net.StoreDto
import np.bill.data.net.StoreSettingsRequest
import np.bill.data.prefs.SessionStore
import np.bill.data.net.apiCall

/**
 * The registered business, as the device knows it.
 *
 * Reads come from the copy stored with the session, so the settings screen opens with no
 * network. Writing needs one: the shop's details print on every bill from every till, and
 * the server is where they are agreed.
 */
@Singleton
class StoreRepository @Inject constructor(
  private val api: BillApi,
  private val session: SessionStore,
) {

  suspend fun current(): StoreDto? = session.current().store

  suspend fun updateSettings(request: StoreSettingsRequest): ApiResult<StoreDto> =
    when (val result = apiCall { api.updateStore(request) }) {
      is ApiResult.Ok -> {
        session.setStore(result.value.store)
        ApiResult.Ok(result.value.store)
      }
      ApiResult.Offline -> ApiResult.Offline
      is ApiResult.Failed -> result
    }
}
