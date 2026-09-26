# Google Sign-In Android 16 compatibility fix

Observed device error: `[16] Account reauth failed.` after Credential Manager Sign in with Google.

Audit findings:
- package name is `com.boss.cameraguard`.
- debug SHA-1 in Firebase matches the local debug keystore.
- Web OAuth client is present and used as `default_web_client_id`.
- The failure is therefore not explained by the previously checked package/SHA/Web-client configuration.
- There are current Android 16 reports where Credential Manager Google sign-in fails even with correct configuration; legacy GoogleSignIn continues to work as a compatibility workaround.

Changes:
- Credential Manager remains the primary path.
- On Android 16+ only, `[16] Account reauth failed` / `No credentials available` automatically falls back to legacy GoogleSignIn.
- Added `play-services-auth:21.6.0` for the fallback.
- Updated Google ID SDK from 1.1.1 to 1.2.1.
- Existing anonymous Firebase user linking is preserved.
- Added MainActivity activity-result bridge only for this fallback.
- No map/HUD/SOS/Community UI behavior was changed.

Build verification still requires Android Studio because this sandbox does not provide the user's Android SDK/Gradle environment.
