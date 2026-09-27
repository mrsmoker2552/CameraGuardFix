plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.boss.cameraguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.boss.cameraguard"
        minSdk = 26
        targetSdk = 36
        versionCode = 54
        versionName = "1.6.13"
    }

    signingConfigs {
        getByName("debug") {
            // Fixed, checked-in debug key so every build - on any machine, and every CI run -
            // is signed with the SAME certificate/SHA-1. Without this, Gradle's normal behaviour
            // (auto-generating ~/.android/debug.keystore per machine if none exists) means a
            // fresh GitHub Actions runner signs with a brand-new random key every time, which
            // Google Sign-In then rejects with ApiException status 10 (DEVELOPER_ERROR) because
            // that SHA-1 was never registered in the Firebase project. This keystore's SHA-1
            // must be added once under Firebase Console > Project settings > your Android app >
            // Add fingerprint (see the repo README / the change report delivered with this fix).
            storeFile = file("ci-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Android 16 fallback: Credential Manager can return [16] Account reauth failed on some devices.
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")

    implementation("org.maplibre.gl:android-sdk:13.1.0")

    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    // Crash/ANR/non-fatal reporting for the same Firebase project already used above.
    implementation("com.google.firebase:firebase-crashlytics")
    // Rider Community Chat backend. Presence/cameras/road-reports keep using the existing
    // Realtime Database above (untouched); chat uses Firestore + Storage instead because
    // Storage Security Rules can call firestore.get()/exists() to verify a sender is an
    // authorized conversation member (including for groups) - Realtime Database has no
    // equivalent hook, so RTDB could not securely gate media access for group chats.
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    // Task<T>.await() for Firestore/Storage calls from coroutines.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    // Chat image thumbnails/avatars/full-screen viewer. No image-loading library existed
    // in the project before this feature.
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}