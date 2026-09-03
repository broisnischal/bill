package np.bill.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches an APK from the releases page and hands it to the system installer.
 *
 * There is no silent install outside a device-owner app, so this does the part it can:
 * download with a progress the shopkeeper can watch, then open the installer. Android
 * refuses a package not signed with the key already on the phone, and that check is what
 * this leans on rather than a hash of our own.
 */
@Singleton
class ApkInstaller @Inject constructor(
  @ApplicationContext private val context: Context,
  client: OkHttpClient,
) {

  /**
   * The app's client without its interceptors. A release asset lives on GitHub, not on
   * our server, and arriving there with somebody else's bearer token is refused.
   */
  private val downloads: OkHttpClient =
    client.newBuilder().apply { interceptors().clear() }.build()

  /** Downloads to the cache, reporting 0..1. Older attempts are cleared out first. */
  suspend fun download(
    url: String,
    versionName: String,
    onProgress: (Float) -> Unit,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val directory = File(context.cacheDir, "updates").apply { mkdirs() }
      directory.listFiles()?.forEach(File::delete)
      val target = File(directory, "bill-$versionName.apk")

      downloads.newCall(Request.Builder().url(url).build()).execute().use { response ->
        val body = response.body
        require(response.isSuccessful && body != null) { "Download failed: ${response.code}" }

        val total = body.contentLength()
        body.byteStream().use { source ->
          target.outputStream().use { sink ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var written = 0L
            while (true) {
              val read = source.read(buffer)
              if (read == -1) break
              sink.write(buffer, 0, read)
              written += read
              if (total > 0) onProgress(written.toFloat() / total)
            }
          }
        }
      }
      target
    }
  }

  /** Android 8 and up will not install from an app the person has not allowed to. */
  fun canInstall(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
      context.packageManager.canRequestPackageInstalls()

  fun requestInstallPermission() = start(
    Intent(
      Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
      Uri.parse("package:${context.packageName}"),
    ),
  )

  fun install(file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    start(
      Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
  }

  /** The way out when the installer will not open. A browser can always fetch a file. */
  fun openInBrowser(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

  private fun start(intent: Intent) {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}
