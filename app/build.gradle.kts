import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

// Release signing: from environment variables (CI) or from keystore.properties
// in the project root (local builds, never committed). Without either, the
// release APK is built unsigned.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotEmpty() } ?: keystoreProperties.getProperty(prop)

val releaseStoreFile = signingValue("KAARTSCAN_KEYSTORE_FILE", "storeFile")

// CI builds get increasing version codes, so a newer APK installs over an older one
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// The version name comes from the git tags: "1.9" for a build of tag v1.9,
// "1.9-3-gabc1234" for the third commit after it. CI sets it in the
// environment; locally git describe is asked directly.
fun gitVersionName(): String? = try {
    val p = ProcessBuilder("git", "describe", "--tags", "--match", "v*", "--always", "--dirty")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val out = p.inputStream.bufferedReader().readText().trim()
    if (p.waitFor() == 0 && out.isNotEmpty()) out.removePrefix("v") else null
} catch (e: Exception) {
    null
}

val appVersionName = System.getenv("KAARTSCAN_VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: gitVersionName()
    ?: "dev"

android {
    namespace = "it.snel.kaartscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.snel.kaartscan"
        minSdk = 24
        targetSdk = 35
        versionCode = buildNumber
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("KAARTSCAN_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KAARTSCAN_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KAARTSCAN_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // a separate app next to the release ("Kaartscan debug"), which
            // updates itself to the newest debug build
            applicationIdSuffix = ".debug"
            // the release key when available, so every CI debug build
            // installs over the previous one
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric screenshot tests (docs/screenshots) need resources
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // bundled model: works offline, no Play Services download needed
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation("junit:junit:4.13.2")
    // real org.json for plain JVM unit tests (android.jar only has stubs)
    testImplementation("org.json:json:20240303")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
