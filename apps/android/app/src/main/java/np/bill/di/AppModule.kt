package np.bill.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import np.bill.BuildConfig
import np.bill.data.db.BillDatabase
import np.bill.data.net.BillApi
import np.bill.data.prefs.SessionStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  fun json(): Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  }

  @Provides
  @Singleton
  fun database(@ApplicationContext context: Context): BillDatabase =
    Room.databaseBuilder(context, BillDatabase::class.java, "bill.db")
      // A schema change ships with a migration; there is no destructive fallback,
      // because the database can be holding bills that have not reached the server.
      .addMigrations(
        BillDatabase.MIGRATION_1_2,
        BillDatabase.MIGRATION_2_3,
        BillDatabase.MIGRATION_3_4,
        BillDatabase.MIGRATION_4_5,
        BillDatabase.MIGRATION_5_6,
        BillDatabase.MIGRATION_6_7,
        BillDatabase.MIGRATION_7_8,
      )
      .build()

  @Provides
  fun billDao(database: BillDatabase) = database.bills()

  @Provides
  fun leaseDao(database: BillDatabase) = database.leases()

  @Provides
  fun catalogDao(database: BillDatabase) = database.catalog()

  @Provides
  fun walletDao(database: BillDatabase) = database.wallet()

  @Provides
  fun storeDataDao(database: BillDatabase) = database.storeData()

  @Provides
  @Singleton
  fun sessionStore(
    @ApplicationContext context: Context,
    secrets: np.bill.data.prefs.SecretStore,
    json: Json,
  ) = SessionStore(context, secrets, json)

  @Provides
  @Singleton
  fun okHttp(session: SessionStore): OkHttpClient = OkHttpClient.Builder()
    // A till on a 2G connection needs patience, but not so much that the UI hangs on it.
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .addInterceptor { chain ->
      // Read from the in-memory mirror: this runs on OkHttp's thread and must not block.
      val current = session.cachedSession()
      val request = chain.request().newBuilder().apply {
        current?.token?.takeIf(String::isNotEmpty)?.let { header("Authorization", "Bearer $it") }
        // Every call carries the till's identity, so a bill traces back to the phone.
        current?.deviceId?.takeIf(String::isNotEmpty)?.let { header("x-device-id", it) }
        header("accept", "application/json")
      }.build()
      chain.proceed(request)
    }
    .apply {
      if (BuildConfig.DEBUG) {
        addInterceptor(
          okhttp3.logging.HttpLoggingInterceptor()
            .setLevel(okhttp3.logging.HttpLoggingInterceptor.Level.BASIC),
        )
      }
    }
    .build()

  @Provides
  @Singleton
  fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
    .baseUrl(BuildConfig.API_BASE_URL)
    .client(client)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

  @Provides
  @Singleton
  fun api(retrofit: Retrofit): BillApi = retrofit.create(BillApi::class.java)
}
