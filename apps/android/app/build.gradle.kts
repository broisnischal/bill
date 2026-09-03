plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

/**
 * One file describes a release: `release.json` beside this module.
 *
 * Gradle stamps the APK from it and the Worker serves the same file to installs that ask
 * whether they are out of date, so the version a phone reports and the version it is
 * measured against cannot drift. It is read through `providers` rather than
 * `File.readText` so that bumping it invalidates the configuration cache instead of
 * quietly shipping the previous number.
 */
@Suppress("UNCHECKED_CAST")
val release = groovy.json.JsonSlurper().parseText(
  providers.fileContents(rootProject.layout.projectDirectory.file("release.json")).asText.get(),
) as Map<String, Any>

/**
 * Which key signs the release build.
 *
 * Android refuses an update signed by a different key than the one already on the phone,
 * so this is what makes an in-app update possible at all. CI writes the upload keystore
 * out of a secret and sets these variables; a machine without them falls back to the
 * debug key, which installs locally and can never update a phone carrying a real build.
 */
val uploadKeystore = providers.environmentVariable("ANDROID_KEYSTORE_FILE")

android {
  namespace = "np.bill"
  compileSdk = 35

  defaultConfig {
    applicationId = "np.bill"
    // Android 7.0. Covers the cheap handsets a small shop actually bills on.
    minSdk = 24
    targetSdk = 35
    versionCode = (release["versionCode"] as Number).toInt()
    versionName = release["versionName"] as String

    /**
     * VAT is off across the app for now.
     *
     * Every shop this reaches first holds a PAN and is not registered for VAT, and a
     * bill charging 13% on their behalf overcharges their customer and claims a
     * registration they do not have. A store row can still say it is VAT-registered —
     * several do, from before this — and the flag makes that inert until VAT is turned
     * back on deliberately. The server has the same switch in src/lib/tax/vat.ts, and
     * the two have to move together.
     */
    buildConfigField("Boolean", "VAT_ENABLED", "false")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Only the languages the app is translated into ship, which keeps the APK small.
    resourceConfigurations += setOf("en", "ne")
  }

  signingConfigs {
    // Release is signed with the debug key here so a minified build can be installed and
    // measured on a real phone. A real release replaces this with the upload key.
    create("measure") {
      storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }

    create("upload") {
      if (uploadKeystore.isPresent) {
        storeFile = file(uploadKeystore.get())
        storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
        keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
        keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
      }
    }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      // `adb reverse tcp:3000 tcp:3000` puts the dev server on the phone's own localhost,
      // so a USB-connected handset and an emulator both use the same URL.
      buildConfigField("String", "API_BASE_URL", "\"http://localhost:3000/\"")
      buildConfigField("Boolean", "OTP_AUTOFILL", "true")
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      buildConfigField("String", "API_BASE_URL", "\"https://bill.jotko.workers.dev/\"")
      /**
       * Fills the code in from the server instead of waiting for an SMS.
       *
       * This is on because there is no SMS gateway yet and the build is being handed to
       * people to try. It does nothing on its own: the route it calls answers only for
       * the numbers named in the server's OTP_DEBUG_PHONES, so a stranger holding this
       * APK gets a 404 for their own number and cannot sign in as anybody. Adding a
       * number to that list is what hands out an account, and the flag here is only what
       * saves the person on it from reading the code off a server log.
       */
      buildConfigField("Boolean", "OTP_AUTOFILL", "true")
      signingConfig = signingConfigs.getByName(if (uploadKeystore.isPresent) "upload" else "measure")
    }

    /**
     * Release, but pointed at the dev server and installable alongside the debug build.
     *
     * A debug build runs Compose without R8, without a baseline profile and with
     * debuggable on, and is several times slower to lay out a list. Judging how the app
     * feels on a debug build measures the toolchain, not the app.
     */
    create("measure") {
      initWith(getByName("release"))
      applicationIdSuffix = ".measure"
      matchingFallbacks += listOf("release")
      buildConfigField("String", "API_BASE_URL", "\"https://bill.jotko.workers.dev/\"")
      /**
       * Fills the code in from the server instead of waiting for an SMS.
       *
       * This is on because there is no SMS gateway yet and the build is being handed to
       * people to try. It does nothing on its own: the route it calls answers only for
       * the numbers named in the server's OTP_DEBUG_PHONES, so a stranger holding this
       * APK gets a 404 for their own number and cannot sign in as anybody. Adding a
       * number to that list is what hands out an account, and the flag here is only what
       * saves the person on it from reading the code off a server log.
       */
      buildConfigField("Boolean", "OTP_AUTOFILL", "true")
      signingConfig = signingConfigs.getByName("measure")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    // Room and the BS calendar use java.time on API levels that predate it.
    isCoreLibraryDesugaringEnabled = true
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
  }
}

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)
  implementation(libs.androidx.navigation.compose)
  debugImplementation(libs.androidx.compose.ui.tooling)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.hilt.work)
  implementation(libs.androidx.hilt.navigation.compose)
  ksp(libs.androidx.hilt.compiler)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  implementation(libs.androidx.datastore.preferences)

  implementation(libs.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  debugImplementation(libs.okhttp.logging)
  // The measure build is release-shaped but still talks to the dev server, so it keeps
  // the logging interceptor the debug build has.
  "measureImplementation"(libs.okhttp.logging)
  // Release names the interceptor too, inside a `BuildConfig.DEBUG` branch R8 removes
  // before it ever runs. The symbol still has to resolve to compile, so the release
  // variant needs the type without shipping the library.
  releaseCompileOnly(libs.okhttp.logging)

  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)
  implementation(libs.mlkit.barcode)
  implementation(libs.zxing.core)
  implementation(libs.image.cropper)

  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
}
