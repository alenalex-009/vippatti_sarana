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

    // GNews API key — read from app/.env (git-ignored, real key) with the
    // .env.example placeholder as fallback so clean clones still build; the
    // app then reports an honest "key not configured" state instead of
    // fabricating news. Surfaces as BuildConfig.GNEWS_API_KEY — the key is
    // never hardcoded in Kotlin sources.
    val gnewsEnvFile = file(".env").takeIf { it.isFile } ?: file(".env.example")
    val gnewsApiKey = gnewsEnvFile.readLines()
        .firstOrNull { it.trim().startsWith("GNEWS_API_KEY=") }
        ?.substringAfter('=')
        ?.trim()
        ?.replace("\"", "")
        ?: "YOUR_GNEWS_API_KEY_HERE"
    buildConfigField("String", "GNEWS_API_KEY", "\"$gnewsApiKey\"")

    // NASA FIRMS MAP_KEY — free key for the official active-fire API
    // (https://firms.modaps.eosdis.nasa.gov/api/area/ -> "Get MAP Key").
    // Same .env convention: real key in app/.env (git-ignored), placeholder
    // fallback so clean clones build and the app honestly reports the fire
    // layer as unavailable until the key is configured.
    val firmsMapKey = gnewsEnvFile.readLines()
        .firstOrNull { it.trim().startsWith("FIRMS_MAP_KEY=") }
        ?.substringAfter('=')
        ?.trim()
        ?.replace("\"", "")
        ?: "YOUR_FIRMS_MAP_KEY_HERE"
    buildConfigField("String", "FIRMS_MAP_KEY", "\"$firmsMapKey\"")
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
            // Cap Gradle's default fork-every-class parallelism: resource-heavy
            // Robolectric compose tests are faster and stabler when they run in
            // a single forked JVM (they share the sandbox classloaders).
            it.maxParallelForks = 1
            it.forkEvery = 0
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
