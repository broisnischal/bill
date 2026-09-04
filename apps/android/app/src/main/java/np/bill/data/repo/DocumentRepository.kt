package np.bill.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import np.bill.data.net.ApiResult
import np.bill.data.net.BillApi
import np.bill.data.net.StoreDocumentDto
import np.bill.data.net.apiCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The papers a business uploads for review.
 *
 * A photograph of a PAN certificate, taken on the same phone that will print the bills.
 * Read into memory and posted as the body: these are a page or two at a couple of
 * megabytes, and streaming them would buy nothing but a longer code path.
 *
 * Nothing is queued for later. Uploading is part of signing up, which already needs the
 * network for the OTP, so failing loudly here is better than a shop believing it has sent
 * a document that is sitting in an outbox.
 */
@Singleton
class DocumentRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val api: BillApi,
) {

  /** What may be sent. A reviewer has to be able to read it. */
  val allowedTypes = arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf")

  suspend fun upload(kind: String, uri: Uri): ApiResult<StoreDocumentDto> =
    withContext(Dispatchers.IO) {
      val resolver = context.contentResolver
      // The cropper hands back a file it wrote itself, and a provider that was not asked
      // to declare a type answers null. Falling back to the extension keeps a cropped
      // photograph from being refused as an unreadable file the shop never chose.
      val mimeType = resolver.getType(uri)
        ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString())
          ?.lowercase()
          ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        ?: "application/octet-stream"

      if (mimeType !in allowedTypes) {
        return@withContext ApiResult.Failed(
          "unsupported_type",
          "Send a photo or a PDF of the page.",
          415,
        )
      }

      val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        ?: return@withContext ApiResult.Failed("unreadable", "That file could not be read.", 0)

      if (bytes.size > MAX_BYTES) {
        return@withContext ApiResult.Failed(
          "too_large",
          "That file is over 8 MB. A photo of the page is enough.",
          413,
        )
      }

      when (
        val result = apiCall {
          api.uploadStoreDocument(
            kind = kind,
            fileName = displayName(uri),
            contentType = mimeType,
            body = bytes.toRequestBody(mimeType.toMediaType()),
          )
        }
      ) {
        is ApiResult.Ok -> ApiResult.Ok(result.value.document)
        ApiResult.Offline -> ApiResult.Offline
        is ApiResult.Failed -> result
      }
    }

  suspend fun list(): List<StoreDocumentDto> =
    when (val result = apiCall { api.storeDocuments() }) {
      is ApiResult.Ok -> result.value.documents
      else -> emptyList()
    }

  /** The name the picker showed, so a reviewer sees what the shop thought it sent. */
  private fun displayName(uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
  }.getOrNull()

  private companion object {
    /** Matches the server. A phone photo of a certificate is one to three megabytes. */
    const val MAX_BYTES = 8 * 1024 * 1024
  }
}
