package np.bill.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import np.bill.data.net.StoreDto

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("session")

/** Which half of the app the person is using. A phone can be both, one at a time. */
enum class AppMode { BUSINESS, CUSTOMER }

data class Session(
  val token: String?,
  val userId: String?,
  val phoneNumber: String?,
  /**
   * The whole registered business, kept as it came from the server.
   *
   * A printed bill needs a dozen fields off it and has to print with no network, so the
   * record is stored whole rather than picked apart into preference keys that then have
   * to be kept in step with the server's schema.
   */
  val store: StoreDto?,
  val role: String?,
  val mode: AppMode,
  val deviceId: String,
  val deviceRegistered: Boolean,
  val catalogCursor: String?,
  val printerAddress: String?,
  val printerName: String?,
  val themeMode: np.bill.ui.theme.ThemeMode,
  /**
   * Whether a buyer typed straight onto a bill joins the customer list, and whether a
   * line typed by hand becomes a product.
   *
   * On by default. A shop that bills the same faces every week ends up retyping them
   * otherwise, and the catalogue that makes the next bill fast is exactly the one nobody
   * stops to build. Off is for the counter that serves mostly strangers, where saving
   * every walk-in buries the regulars.
   */
  val autoSaveCustomer: Boolean,
  val autoSaveProduct: Boolean,
) {
  val signedIn: Boolean get() = !token.isNullOrEmpty()
  val hasStore: Boolean get() = store != null

  /** Zero while VAT is switched off, whatever the store row says. See BuildConfig. */
  val vatRateBp: Int
    get() = if (np.bill.BuildConfig.VAT_ENABLED && store?.taxpayerType == "vat") {
      store.vatRateBp
    } else {
      0
    }
}

/**
 * Session and device identity.
 *
 * The device id is minted once on first launch and never changes: it is what number
 * leases are granted against, so a new id would mean a new block and a gap in the series.
 */
@Singleton
class SessionStore @Inject constructor(
  private val context: Context,
  private val secrets: SecretStore,
  private val json: Json,
) {

  /**
   * The last session read, kept in memory.
   *
   * Every HTTP request needs the token and the device id, and the OkHttp interceptor runs
   * on a background thread that must not block. Reading DataStore there would suspend
   * per request; this mirrors it into a plain field instead, updated as it changes.
   */
  @Volatile
  private var cached: Session? = null

  val session: Flow<Session> = context.dataStore.data.map { prefs ->
    Session(
      token = prefs[TOKEN]?.let(secrets::decrypt),
      userId = prefs[USER_ID],
      phoneNumber = prefs[PHONE],
      store = prefs[STORE_JSON]?.let { stored ->
        runCatching { json.decodeFromString(StoreDto.serializer(), stored) }.getOrNull()
      },
      role = prefs[ROLE],
      mode = prefs[MODE]?.let { runCatching { AppMode.valueOf(it) }.getOrNull() } ?: AppMode.BUSINESS,
      deviceId = prefs[DEVICE_ID] ?: "",
      deviceRegistered = prefs[DEVICE_REGISTERED] ?: false,
      catalogCursor = prefs[CATALOG_CURSOR],
      printerAddress = prefs[PRINTER],
      printerName = prefs[PRINTER_NAME],
      themeMode = prefs[THEME]?.let {
        runCatching { np.bill.ui.theme.ThemeMode.valueOf(it) }.getOrNull()
      } ?: np.bill.ui.theme.ThemeMode.SYSTEM,
      autoSaveCustomer = prefs[AUTO_SAVE_CUSTOMER] ?: true,
      autoSaveProduct = prefs[AUTO_SAVE_PRODUCT] ?: true,
    )
  }.onEach { cached = it }

  init {
    // One collector keeps the cache warm for the life of the process.
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { session.collect {} }
  }

  suspend fun current(): Session = cached ?: session.first()

  /** What the network layer needs, without suspending. Null until the first read lands. */
  fun cachedSession(): Session? = cached

  /** Returns the device id, minting one the first time the app runs. */
  suspend fun deviceId(): String {
    val existing = context.dataStore.data.first()[DEVICE_ID]
    if (!existing.isNullOrEmpty()) return existing
    val minted = UUID.randomUUID().toString()
    context.dataStore.edit { it[DEVICE_ID] = minted }
    return minted
  }

  suspend fun signIn(token: String, user: np.bill.data.net.AuthUser) {
    context.dataStore.edit { prefs ->
      prefs[TOKEN] = secrets.encrypt(token)
      prefs[USER_ID] = user.id
      user.phoneNumber?.let { prefs[PHONE] = it }
    }
  }

  /**
   * Keeps the registered business, so a bill printed with no network still carries the
   * shop's name, PAN, address and footer exactly as the web app would print them.
   */
  suspend fun setStore(store: StoreDto, role: String? = null) {
    context.dataStore.edit { prefs ->
      prefs[STORE_JSON] = json.encodeToString(StoreDto.serializer(), store)
      prefs[LAST_STORE_ID] = store.id
      role?.let { prefs[ROLE] = it }
    }
  }

  /**
   * The last business this device billed for, kept across sign-out.
   *
   * Sign-out clears the store record, which used to erase the only thing that could tell
   * a later sign-in that a *different* business had taken the phone over. The device then
   * kept the previous shop's number leases and wrote bills against them, and every one of
   * those bills was refused by the server: the numbers belonged to another store's
   * series. This value survives sign-out precisely so that comparison still works.
   */
  suspend fun lastStoreId(): String? = context.dataStore.data.first()[LAST_STORE_ID]

  suspend fun setAutoSaveCustomer(enabled: Boolean) {
    context.dataStore.edit { it[AUTO_SAVE_CUSTOMER] = enabled }
  }

  suspend fun setAutoSaveProduct(enabled: Boolean) {
    context.dataStore.edit { it[AUTO_SAVE_PRODUCT] = enabled }
  }

  suspend fun setThemeMode(mode: np.bill.ui.theme.ThemeMode) {
    context.dataStore.edit { it[THEME] = mode.name }
  }

  suspend fun setMode(mode: AppMode) {
    context.dataStore.edit { it[MODE] = mode.name }
  }

  suspend fun setDeviceRegistered(registered: Boolean) {
    context.dataStore.edit { it[DEVICE_REGISTERED] = registered }
  }

  suspend fun setCatalogCursor(cursor: String) {
    context.dataStore.edit { it[CATALOG_CURSOR] = cursor }
  }

  suspend fun setPrinter(address: String?, name: String? = null) {
    context.dataStore.edit { prefs ->
      if (address == null) {
        prefs.remove(PRINTER)
        prefs.remove(PRINTER_NAME)
      } else {
        prefs[PRINTER] = address
        name?.let { prefs[PRINTER_NAME] = it }
      }
    }
  }

  /**
   * Signing out clears the session but keeps the device id and anything still waiting to
   * sync: unsent bills belong to the shop, not to the phone's current session.
   */
  suspend fun signOut() {
    context.dataStore.edit { prefs ->
      prefs.remove(TOKEN)
      prefs.remove(USER_ID)
      prefs.remove(STORE_JSON)
      prefs.remove(ROLE)
      prefs.remove(DEVICE_REGISTERED)
    }
  }

  private companion object {
    val TOKEN = stringPreferencesKey("token")
    val USER_ID = stringPreferencesKey("user_id")
    val PHONE = stringPreferencesKey("phone")
    val STORE_JSON = stringPreferencesKey("store")
    val LAST_STORE_ID = stringPreferencesKey("last_store_id")
    val ROLE = stringPreferencesKey("role")
    val MODE = stringPreferencesKey("mode")
    val DEVICE_ID = stringPreferencesKey("device_id")
    val DEVICE_REGISTERED = booleanPreferencesKey("device_registered")
    val CATALOG_CURSOR = stringPreferencesKey("catalog_cursor")
    val PRINTER = stringPreferencesKey("printer")
    val PRINTER_NAME = stringPreferencesKey("printer_name")
    val THEME = stringPreferencesKey("theme")
    val AUTO_SAVE_CUSTOMER = booleanPreferencesKey("auto_save_customer")
    val AUTO_SAVE_PRODUCT = booleanPreferencesKey("auto_save_product")
  }
}
