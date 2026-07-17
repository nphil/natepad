plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.natepad.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.natepad.app"
        minSdk = 26
        targetSdk = 35
        // CI passes -PversionCode / -PversionName; defaults are for local builds
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Committed keystore: keeps the APK signature identical across CI runs so
            // Obtainium/sideload updates install without signature-mismatch errors.
            // Update-continuity only — this app is distributed via our own GitHub Releases.
            storeFile = file("natepad-release.keystore")
            storePassword = "natepad-release-2026"
            keyAlias = "natepad"
            keyPassword = "natepad-release-2026"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Minification disabled: Bouncy Castle + kotlinx.serialization rely on
            // reflection; an over-aggressive shrink would break PGP at runtime
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Window / Adaptive
    implementation(libs.androidx.window)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)

    // Bouncy Castle
    implementation(libs.bouncycastle.bcpg)
    implementation(libs.bouncycastle.bcprov)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Biometric
    implementation(libs.androidx.biometric)

    // Security / EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // QR code generation + scanning
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
