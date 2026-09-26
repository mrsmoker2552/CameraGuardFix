# CameraGuard — current Android source project

Open this project folder (containing `settings.gradle.kts`) with Android Studio. The app module is `app/`; the package is `com.boss.cameraguard`.

## Current components kept intact
- Main UI, MapLibre maps, rider/community/chat, login, Firebase integration, GPS location service and navigation.
- WebView 3D HUD: `app/src/main/assets/hud/index.html` and its *required* local Three.js module `three.module.js`.
- Real OSM road data and connected-road rendering logic, plus a clearly marked **visual-only fallback road** when verified road geometry is missing. A visually present road in fallback mode is **not** proof of road alignment or a real navigation route.
- Live camera repository, direction-aware warning engine, DrivingService, native warning card, sound and settings. Real-world camera coverage and warning delivery have **not** been verified by this cleanup.
- Camera, GPS and warning diagnostics; Firebase rules shipped separately in `firebase-rules/` for manual review/deployment.

## Build and validation
Use the included Gradle wrapper in Android Studio with Internet access for initial dependencies and the Android SDK. The required Firebase `app/google-services.json` is kept unchanged. **No Android APK build, Firebase emulator test, or on-scooter verification was performed during this cleanup.**

## On-road verification
1. While parked, inspect Settings, map, route, HUD GPS DEBUG and test alerts.
2. With the phone securely mounted, have a passenger/observer record whether the HUD ever enters `VISUAL ROAD ONLY` or misaligns at junctions/bends; do not operate the phone while riding.
3. Test a known correctly directed speed camera and red-light camera. Confirm visual and audible alerts at configured distances (defaults 300 m and 150 m). Check opposing-direction cameras do not warn. If an alert is missing, export a privacy-redacted diagnostic log **when parked**; do not change directional filtering solely to force an alert.
4. Review `STEP_6_DEPLOYMENT_README.md` before changing live Firebase rules: the documented privacy migration was not verified or deployed by this cleanup.

## Cleanup scope
Only obsolete root changelogs/handoffs and five demonstrably unused prototype Kotlin files were removed. All remaining production source/configuration files (including Gradle wrapper, Three.js, Firebase settings/rules and the active HUD, road and warning engines) are byte-identical to the original supplied ZIP.
