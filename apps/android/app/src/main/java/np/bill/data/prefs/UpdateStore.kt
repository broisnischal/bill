package np.bill.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import np.bill.data.net.AppReleaseResponse

private val Context.updates: DataStore<Preferences> by preferencesDataStore("updates")

/**
 * The last thing the server said about versions, and what the shopkeeper did about it.
 *
 * Kept apart from the session because it outlives one: whether this build is still
 * allowed to bill has nothing to do with who is signed in, and it has to survive a sign
 * out. Storing the answer at all is what lets a phone that has already been told it is
 * too old stay told once it loses signal.
 */
@Singleton
class UpdateStore @Inject constructor(@ApplicationContext private val context: Context) {

  suspend fun remember(release: AppReleaseResponse) {
    context.updates.edit { prefs ->
      prefs[VERSION_CODE] = release.versionCode
      prefs[VERSION_NAME] = release.versionName
      prefs[MINIMUM] = release.minimumVersionCode
      prefs[APK_URL] = release.apkUrl
      prefs[NOTES] = release.notes
      prefs[CHECKED_AT] = System.currentTimeMillis()
    }
  }

  suspend fun lastSeen(): AppReleaseResponse? {
    val prefs = context.updates.data.first()
    val versionCode = prefs[VERSION_CODE] ?: return null
    return AppReleaseResponse(
      versionName = prefs[VERSION_NAME].orEmpty(),
      versionCode = versionCode,
      minimumVersionCode = prefs[MINIMUM] ?: 0,
      apkUrl = prefs[APK_URL].orEmpty(),
      notes = prefs[NOTES].orEmpty(),
    )
  }

  suspend fun checkedAt(): Long = context.updates.data.first()[CHECKED_AT] ?: 0L

  /**
   * An optional update the shopkeeper said no to. Recorded against the version rather
   * than as a flag, so the next release asks again and this one stops asking.
   */
  suspend fun skip(versionCode: Int) {
    context.updates.edit { it[SKIPPED] = versionCode }
  }

  suspend fun skipped(): Int = context.updates.data.first()[SKIPPED] ?: 0

  private companion object {
    val VERSION_CODE = intPreferencesKey("version_code")
    val VERSION_NAME = stringPreferencesKey("version_name")
    val MINIMUM = intPreferencesKey("minimum_version_code")
    val APK_URL = stringPreferencesKey("apk_url")
    val NOTES = stringPreferencesKey("notes")
    val CHECKED_AT = longPreferencesKey("checked_at")
    val SKIPPED = intPreferencesKey("skipped_version_code")
  }
}
