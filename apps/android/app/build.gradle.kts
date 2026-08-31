plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

android {
  namespace = "np.bill"
  compileSdk = 35

  defaultConfig {
    applicationId = "np.bill"
    // Android 7.0. Covers the cheap handsets a small shop actually bills on.
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0.0"

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
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      // `adb reverse tcp:3000 tcp:3000` puts the dev server on the phone's own localhost,
      // so a USB-connected handset and an emulator both use the same URL.
      buildConfigField("String", "API_BASE_URL", "\"http://localhost:3000/\"")
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      buildConfigField("String", "API_BASE_URL", "\"https://bill.np/\"")
      signingConfig = signingConfigs.getByName("measure")
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
      buildConfigField("String", "API_BASE_URL", "\"http://localhost:3000/\"")
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

  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)
  implementation(libs.mlkit.barcode)
  implementation(libs.zxing.core)

  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
}
