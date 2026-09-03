package np.bill.ui.review

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.data.net.ApiResult
import np.bill.data.net.StoreDocumentDto
import np.bill.data.prefs.SessionStore
import np.bill.data.repo.AuthRepository
import np.bill.data.repo.DocumentRepository

data class ReviewState(
  val loading: Boolean = true,
  val status: String = "pending",
  val note: String? = null,
  val businessName: String = "",
  val pan: String = "",
  val documents: List<StoreDocumentDto> = emptyList(),
  /** Which slot is uploading, so only that row shows a spinner. */
  val uploading: String? = null,
  val error: String? = null,
) {
  fun documentFor(kind: String): StoreDocumentDto? = documents.firstOrNull { it.kind == kind }

  /** Nothing can be reviewed without it, so the screen says so until it is there. */
  val hasPan: Boolean get() = documentFor("pan") != null

  /**
   * Where the application stands, as three stages a shopkeeper can see the end of.
   *
   * "Waiting for your PAN certificate" is not a stage: it is the form not being finished
   * yet. Submitted means the paper is in, under review means a person has it, and
   * approved means the till opens. When review becomes automatic the middle stage keeps
   * its name and only its duration changes.
   */
  val stage: ReviewStage
    get() = when {
      status == "rejected" -> ReviewStage.RETURNED
      status == "approved" -> ReviewStage.APPROVED
      hasPan -> ReviewStage.UNDER_REVIEW
      else -> ReviewStage.INCOMPLETE
    }
}

enum class ReviewStage { INCOMPLETE, UNDER_REVIEW, APPROVED, RETURNED }

/**
 * The wait between registering and billing.
 *
 * A person looks at the PAN certificate before the shop can put that number on paper a
 * tax office will read. This screen is that wait made useful: it says where the business
 * stands, what is still missing, and what was asked for if it was refused.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
  private val documents: DocumentRepository,
  private val auth: AuthRepository,
  private val session: SessionStore,
) : ViewModel() {

  private val _state = MutableStateFlow(ReviewState())
  val state = _state.asStateFlow()

  init {
    refresh()
  }

  /**
   * Reads the store from the device first and then asks the server.
   *
   * The local copy is what the app was told last time, so the screen draws immediately
   * with no signal; the bootstrap call is what notices an approval that happened while
   * the phone was in a pocket.
   */
  fun refresh() {
    viewModelScope.launch {
      val current = session.current()
      _state.update {
        it.copy(
          loading = true,
          status = current.store?.status ?: "pending",
          note = current.store?.reviewNote,
          businessName = current.store?.name.orEmpty(),
          pan = current.store?.pan.orEmpty(),
        )
      }

      auth.bootstrap()
      val refreshed = session.current()
      _state.update {
        it.copy(
          loading = false,
          status = refreshed.store?.status ?: it.status,
          note = refreshed.store?.reviewNote,
          businessName = refreshed.store?.name ?: it.businessName,
          pan = refreshed.store?.pan ?: it.pan,
          documents = documents.list(),
        )
      }
    }
  }

  fun upload(kind: String, uri: Uri) {
    viewModelScope.launch {
      _state.update { it.copy(uploading = kind, error = null) }

      when (val result = documents.upload(kind, uri)) {
        is ApiResult.Ok -> _state.update { current ->
          current.copy(
            uploading = null,
            documents = current.documents.filterNot { it.kind == kind } + result.value,
            // Sending a new paper after a refusal puts the business back in the queue,
            // which the server does; reflect it here rather than waiting for a refresh.
            status = if (current.status == "rejected") "pending" else current.status,
            note = if (current.status == "rejected") null else current.note,
          )
        }

        ApiResult.Offline -> _state.update {
          it.copy(uploading = null, error = "No connection. The upload needs the internet.")
        }

        is ApiResult.Failed -> _state.update { it.copy(uploading = null, error = result.message) }
      }
    }
  }

  fun signOut(onSignedOut: () -> Unit) {
    viewModelScope.launch {
      auth.signOut()
      onSignedOut()
    }
  }
}
