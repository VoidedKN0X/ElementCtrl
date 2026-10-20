plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vknxworks.elementctrl"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vknxworks.elementctrl"
        minSdk = 33
        targetSdk = 35
        // CI passes the run number so every build installs over the previous one.
        // Bump both to publish a new release (see CHANGELOG.md).
        versionCode = 35
        versionName = "1.2.15"

        // The Rust SDK ships a native library per CPU type (~60 MB each). Phones running Android 13+
        // are 64-bit ARM, so only that one is packaged; add "x86_64" here to run on an emulator.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Release signing from env (GitHub secrets). Local builds fall back to the debug key; CI must never do that.
    val ksPath = System.getenv("SIGNING_KEYSTORE_PATH")
    val onCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
    val signingReady = !ksPath.isNullOrBlank() && file(ksPath).exists() &&
        !System.getenv("SIGNING_STORE_PASSWORD").isNullOrBlank() &&
        !System.getenv("SIGNING_KEY_ALIAS").isNullOrBlank() &&
        !System.getenv("SIGNING_KEY_PASSWORD").isNullOrBlank()
    if (onCi && !signingReady) {
        throw GradleException("Release signing values are missing on CI; refusing to sign the release with the debug key.")
    }
    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
                ?: if (onCi) throw GradleException("No release signing config on CI") else signingConfigs.getByName("debug")
        }
    }

    packaging {
        // Compress the big native SDK library inside the APK so the download stays smaller.
        jniLibs { useLegacyPackaging = true }
        resources { excludes += setOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Matrix: the same Rust SDK bindings Element X uses (sync, timeline, end-to-end encryption, verification).
    implementation("org.matrix.rustcomponents:sdk-android:26.10.15")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("org.rustls:rustls-platform-verifier:0.2.0@aar")

    // Frosted-glass blur behind the floating composer island, as in the other Ctrl apps.
    implementation("dev.chrisbanes.haze:haze:1.6.0")

    // Push notifications through ntfy or any other UnifiedPush distributor.
    implementation("org.unifiedpush.android:connector:3.3.5")
    // App lock with fingerprint.
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    val coil = "3.1.0"
    implementation("io.coil-kt.coil3:coil-compose:$coil")
    // Plays animated GIFs and stickers other people send.
    implementation("io.coil-kt.coil3:coil-gif:$coil")
}
