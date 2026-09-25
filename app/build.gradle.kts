import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
alias(libs.plugins.android.application)
alias(libs.plugins.kotlin.compose)
alias(libs.plugins.google.devtools.ksp)
alias(libs.plugins.roborazzi)
alias(libs.plugins.secrets)
alias(libs.plugins.google.services)
}

android {
namespace = "com.example"
compileSdk { version = release(36) { minorApiLevel = 1 } }


defaultConfig {
    applicationId = "com.aistudio.vippattisarana.gaqgel"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // API keys — read from app/.env (git-ignored, real keys), falling back to
    // the .env.example placeholders so clean clones still build.
    //
    // THIS READ IS CONFIG-CACHE SAFE (real bug fixed 2026-09-24): the previous
    // raw file().takeIf{isFile} read ran at configuration time and Gradle could
    // not see .env appear/change, so a stale (placeholder) value was FROZEN
    // into BuildConfig and reused for every later build — the app shipped
    // "YOUR_GN...HERE" as a key and GNews answered HTTP 400. providers.*
    // registers the files as real configuration inputs, so any .env edit
    // invalidates the cache entry.
    //
    // Surfaces as BuildConfig.GNEWS_API_KEY / BuildConfig.FIRMS_MAP_KEY — keys
    // are never hardcoded in Kotlin sources.
    val envReal = layout.projectDirectory.file(".env")
    val envExample = layout.projectDirectory.file(".env.example")
    fun envValue(name: String, placeholder: String): String {
      val text = providers.fileContents(envReal).asText.getOrElse("")
        .ifBlank { providers.fileContents(envExample).asText.getOrElse("") }
      val raw = text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')
        ?.trim()
        ?.replace("\"", "")
        .orEmpty()
      // Guard: any non-ASCII or obviously-short value is a corrupted/placeholder
      // read — embed the honest placeholder instead of garbage that fails at runtime.
      return if (raw.isNotBlank() && raw == placeholder) placeholder
        else if (raw.length >= 20 && raw.all { it.code in 32..126 }) raw
        else placeholder
    }
    buildConfigField("String", "GNEWS_API_KEY", "\"" + envValue("GNEWS_API_KEY", "YOUR_GNEWS_API_KEY_HERE") + "\"")
    buildConfigField("String", "FIRMS_MAP_KEY", "\"" + envValue("FIRMS_MAP_KEY", "YOUR_FIRMS_MAP_KEY_HERE") + "\"")

}

signingConfigs {
    create("release") {
        val keystorePath =
            System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
    }

    create("debugConfig") {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
}

buildTypes {
    release {
        isCrunchPngs = false
        isMinifyEnabled = false
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        signingConfig = signingConfigs.getByName("release")
    }

    debug {
        // CI / clean clones may not have the repo-root debug.keystore (it is
        // git-ignored): fall back to AGP's auto-generated default debug store.
        signingConfig = if (file("${rootDir}/debug.keystore").exists())
            signingConfigs.getByName("debugConfig")
        else
            signingConfigs.getByName("debug")
    }
}

compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

buildFeatures {
    compose = true
    buildConfig = true
}

testOptions {
    unitTests {
        isIncludeAndroidResources = true

        // Robolectric NATIVE-graphics Compose rendering of full screens needs a
        // roomy heap — prevents Java heap space OOM in the responsive layout tests.
        all {
            it.maxHeapSize = "3g"
            // Robolectric NATIVE-Graphics Compose tests must each get a FRESH
            // JVM: with a shared fork, one class's never-idle composition
            // (LazyColumn prefetch spinning in the main looper) leaks into the
            // next class and trips AppNotIdleException — verified on both
            // origin/main (t_out.txt) and the merged branch: the same
            // Dispatches suite passes when run alone and fails in the full
            // suite under forkEvery=0. One fork per class keeps suites
            // isolated; maxParallelForks stays 1 for memory (8 GB dev boxes).
            it.maxParallelForks = 1
            it.forkEvery = 1
        }
    }
}

dependenciesInfo {
    includeInApk = false
    includeInBundle = true
}


}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
propertiesFileName = ".env"
defaultPropertiesFileName = ".env.example"
ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
// GNEWS/FIRMS keys are injected ABOVE via the config-cache-safe envValue() read.
// The plugin must NOT write its own copy of them: doing so overwrote the real key
// with a corrupted placeholder (the "Map data"-style truncation) and every request
// then died with GNews HTTP 400 / FIRMS empty layer.
ignoreList.add("GNEWS_API_KEY")
ignoreList.add("FIRMS_MAP_KEY")
}

googleServices {
missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

// Some unused dependencies are commented out below instead of being removed.
dependencies {
implementation(platform(libs.androidx.compose.bom))
implementation(platform(libs.firebase.bom))


// implementation(libs.accompanist.permissions)
implementation(libs.androidx.activity.compose)

// implementation(libs.androidx.camera.camera2)
// implementation(libs.androidx.camera.core)
// implementation(libs.androidx.camera.lifecycle)
// implementation(libs.androidx.camera.view)

implementation(libs.androidx.compose.material.icons.core)
implementation(libs.androidx.compose.material.icons.extended)
implementation(libs.androidx.compose.material3)
implementation(libs.androidx.compose.ui)
implementation(libs.androidx.compose.ui.graphics)
implementation(libs.androidx.compose.ui.tooling.preview)
implementation(libs.androidx.core.ktx)

// implementation(libs.androidx.datastore.preferences)

implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.lifecycle.viewmodel.compose)

// implementation(libs.androidx.navigation.compose)

implementation(libs.coil.compose)
implementation(libs.firebase.config)

// Uncomment to use Firestore:
// implementation(libs.firebase.firestore)

// Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
// Sign-In via Credential Manager:
// implementation(libs.firebase.auth)
// implementation(libs.androidx.credentials)
// implementation(libs.androidx.credentials.play.services)
// implementation(libs.googleid)

implementation(libs.kotlinx.coroutines.android)
implementation(libs.kotlinx.coroutines.core)
implementation(libs.okhttp)

// implementation(libs.play.services.location)

implementation("org.osmdroid:osmdroid-android:6.1.20")
implementation("androidx.preference:preference-ktx:1.2.1")

testImplementation(libs.androidx.compose.ui.test.junit4)
testImplementation(libs.androidx.core)

// Real org.json for plain-JVM unit tests
testImplementation("org.json:json:20240303")

testImplementation(libs.androidx.junit)
testImplementation(libs.junit)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.robolectric)
testImplementation(libs.roborazzi)
testImplementation(libs.roborazzi.compose)
testImplementation(libs.roborazzi.junit.rule)

androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
androidTestImplementation(libs.androidx.junit)
androidTestImplementation(libs.androidx.runner)

debugImplementation(libs.androidx.compose.ui.test.manifest)
debugImplementation(libs.androidx.compose.ui.tooling)


}
