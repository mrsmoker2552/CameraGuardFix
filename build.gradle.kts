plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    // Crashes/ANRs on rider devices were previously invisible unless someone reported them.
    // Uses the same Firebase project as auth/Firestore below - no new secrets needed.
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}
