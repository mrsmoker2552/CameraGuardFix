# CameraGuard Google ID Kotlin compatibility fix

## Build failure fixed
The failing build referenced `GetSignInWithGoogleOption` / `GoogleIdTokenCredential` from `googleid:1.2.1`. The app itself uses Kotlin 2.2.20, while the newer Google ID artifact was built with newer Kotlin metadata and Android Studio rejected it as binary-incompatible.

## Change
- `com.google.android.libraries.identity.googleid:googleid:1.2.1` -> `1.1.1`
- Kept `GetSignInWithGoogleOption`; Google introduced this API in 1.1.0, so 1.1.1 still supports the explicit Google button flow.
- Kept the Android 16 legacy Google Sign-In fallback (`play-services-auth:21.6.0`).
- Kept Firebase/OAuth IDs and `google-services.json` unchanged.
- Bumped app version to 1.6.8 (49).

No map, HUD, SOS, Community, camera-warning or neumorphism behavior was intentionally changed in this fix.
