package np.bill.data.net

import java.io.IOException
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * What a call to the server came back with.
 *
 * The distinction that matters to this app is `Offline` against `Failed`: a bill that
 * could not be sent because there is no signal is normal and will go next time, while
 * one the server refused needs the shopkeeper to be told something.
 */
sealed interface ApiResult<out T> {
  data class Ok<T>(val value: T) : ApiResult<T>

  /** No usable network. Nothing is wrong; the outbox keeps the work. */
  data object Offline : ApiResult<Nothing>

  data class Failed(val code: String, val message: String, val status: Int) : ApiResult<Nothing>
}

private val errorJson = Json { ignoreUnknownKeys = true }

/** Turns a Retrofit response into a result, reading the server's error shape when present. */
inline fun <T> apiCall(block: () -> Response<T>): ApiResult<T> = try {
  val response = block()
  val body = response.body()
  when {
    response.isSuccessful && body != null -> ApiResult.Ok(body)
    response.isSuccessful -> ApiResult.Failed("empty_response", "The server sent nothing back", response.code())
    else -> parseError(response)
  }
} catch (error: IOException) {
  ApiResult.Offline
} catch (error: Exception) {
  ApiResult.Failed("client_error", error.message ?: "Something went wrong", 0)
}

fun parseError(response: Response<*>): ApiResult.Failed {
  val raw = runCatching { response.errorBody()?.string() }.getOrNull()
  val parsed = raw?.let {
    runCatching { errorJson.decodeFromString<ApiErrorBody>(it) }.getOrNull()
  }
  return ApiResult.Failed(
    code = parsed?.error?.code ?: "http_${response.code()}",
    message = parsed?.error?.message ?: defaultMessage(response.code()),
    status = response.code(),
  )
}

fun defaultMessage(status: Int): String = when (status) {
  401 -> "Sign in again to continue"
  403 -> "You do not have access to do that"
  404 -> "That was not found"
  in 500..599 -> "The server is having trouble. Try again shortly."
  else -> "That did not work"
}
