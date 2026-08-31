package np.bill

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import np.bill.data.prefs.SessionStore
import np.bill.ui.BillApp
import np.bill.ui.theme.BillTheme
import np.bill.ui.theme.ThemeMode

/**
 * AppCompat rather than ComponentActivity for one reason: the per-app language switch
 * goes through AppCompatDelegate, which needs an AppCompat host to apply a locale on
 * Android 12 and below. Everything drawn inside is Compose.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

  @Inject lateinit var session: SessionStore

  private val themeMode: Flow<ThemeMode> get() = session.session.map { it.themeMode }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    requestHighestRefreshRate()
    setContent {
      val mode by themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
      BillTheme(mode = mode) {
        // A bill link scanned by the phone's own camera app opens straight into the
        // saved-bill screen rather than dropping the shopper on the home screen.
        BillApp(deepLinkToken = intent?.data?.lastPathSegment)
      }
    }
  }

  /**
   * Asks for the panel's fastest mode.
   *
   * A 120Hz phone does not necessarily give an app 120Hz: several manufacturers keep the
   * display at 60 unless something asks, and the result is an app that scrolls at half
   * the rate the hardware is capable of.
   */
  private fun requestHighestRefreshRate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val best = display?.supportedModes
      ?.filter { it.physicalWidth == display?.mode?.physicalWidth }
      ?.maxByOrNull { it.refreshRate }
      ?: return
    window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
  }
}
