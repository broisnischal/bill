package np.bill.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import np.bill.data.repo.SyncRepository

/**
 * Sync in the background.
 *
 * Two triggers: a one-shot the moment a bill is written, which lands immediately when
 * there is signal, and a periodic sweep that catches a till that has been offline for
 * hours. Both are constrained to a connected network, so a phone with no bars never
 * spends battery trying.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val sync: SyncRepository,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val outcome = sync.sync()
    return when {
      // No network is not a failure; WorkManager will run us again when there is one.
      outcome.offline -> Result.success()
      outcome.error != null -> Result.retry()
      else -> Result.success()
    }
  }

  companion object {
    private const val ONE_SHOT = "sync-now"
    private const val PERIODIC = "sync-periodic"

    private val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    /** Runs as soon as there is a network. Called after every bill. */
    fun runNow(context: Context) {
      WorkManager.getInstance(context).enqueueUniqueWork(
        ONE_SHOT,
        // Replacing an already queued run coalesces a burst of billing into one sync.
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<SyncWorker>()
          .setConstraints(constraints)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
          .build(),
      )
    }

    fun schedule(context: Context) {
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        PERIODIC,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
          .setConstraints(constraints)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
          .build(),
      )
    }
  }
}
