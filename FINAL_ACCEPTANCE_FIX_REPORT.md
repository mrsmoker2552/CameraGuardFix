# CameraGuard final acceptance fix report

Based on the last supplied `CameraGuard_FINAL_Neumorphism_KotlinCompat_Fix.zip`.

## Fixed
- Main Map SOS moved into the native Main Map control row, directly above recenter; same 38dp size.
- Route screen right-side controls are one aligned 44dp rail: layers, recenter, SOS, route options, road alerts. The same layout is used while active navigation is running.
- HUD mini-map replaced with a native MapLibre roads-only mini-map using the same working vector tile pipeline as Main Map. It is GPS-centred, heading-up, ~2 km radius framing, includes active route line, and uses a radial 1 km-style cyan/fade treatment.
- Old WebView/Overpass mini-map is hidden so a failed road-network fetch cannot leave the rider with a blank mini-map.
- HUD GPS debug UI remains hidden.
- HUD SOS is now rendered inside the HUD content safe area at bottom-right, 44dp, above the bottom navigation.
- 3D scooter/rider scale increased from 1.42 to 2.12.
- Google Community sign-in no longer falls directly into deprecated GoogleSignIn on Android 16. It retries Credential Manager after clearing stale credential-provider state, using an unfiltered Google ID option. Legacy GoogleSignIn remains only for Android < 16.
- Existing Firebase, SOS, routing, camera-warning and Community backend contracts preserved.

## Validation performed here
- ZIP/source structure inspected.
- Kotlin delimiter/brace counts balanced for edited files.
- `kotlinc` parser pass produced no syntax/"expecting" errors in the edited Kotlin files (Android references are unresolved outside Gradle, as expected).
- Full Gradle/APK build could not be executed in this environment because the Gradle wrapper distribution cannot reach `services.gradle.org`.

Version: 1.6.9 (50).
