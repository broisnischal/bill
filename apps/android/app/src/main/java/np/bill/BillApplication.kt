package np.bill

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import np.bill.data.sync.SyncWorker

@HiltAndroidApp
class BillApplication : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(workerFactory)
      .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
      .build()

  override fun onCreate() {
    super.onCreate()
    // Three triggers, and all three are needed. The periodic sweep catches a till left
    // on a shelf; the one-shot after each bill is the usual path; and this one covers
    // the case that was actually losing time — a phone that billed all afternoon with no
    // signal, then walked into range and was opened. Without it those bills waited for
    // the next quarter-hour tick.
    SyncWorker.schedule(this)
    ProcessLifecycleOwner.get().lifecycle.addObserver(
      object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = SyncWorker.runNow(this@BillApplication)
      },
    )
  }
}
