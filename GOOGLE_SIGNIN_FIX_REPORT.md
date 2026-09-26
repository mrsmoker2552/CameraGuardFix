# CameraGuard Google Sign-In Fix Report

## Root cause
The Community screen uses an explicit **Continue with Google** button, but `CameraGuardAuthManager.signInWithGoogle()` was building a `GetGoogleIdOption` request. That option is intended for Credential Manager's general/bottom-sheet account selector. On some devices/accounts it can return `NoCredentialException` / **No credentials available**, including cases where the account needs re-authentication or the bottom-sheet flow is suppressed.

## Fix applied
- Replaced `GetGoogleIdOption` with `GetSignInWithGoogleOption` for the explicit Google button flow.
- Preserved use of `R.string.default_web_client_id` (web/server OAuth client ID).
- Preserved Firebase ID-token exchange and anonymous-user linking behavior.
- No changes to Community membership, Firestore, Realtime Database, SOS, maps, HUD, or camera-warning behavior.

## Configuration verified from project/Firebase screenshots
- Package: `com.boss.cameraguard`
- Debug SHA-1: `03:77:AF:FA:F3:64:E0:FD:9B:C8:65:8E:47:74:22:34:1A:9E:6F:B4`
- Debug SHA-256: `98:64:10:FD:D1:75:A1:71:83:5D:4A:49:BA:45:65:65:1A:FB:7C:AF:32:5B:C2:42:9A:2F:04:E7:5F:B1:B9:B6`
- `google-services.json` contains an Android OAuth client for that SHA-1 and a Web OAuth client (`client_type: 3`).
- Auth code reads `default_web_client_id`, so it uses the correct Web/server client type.

## Build verification
A local Gradle compile could not be executed in the sandbox because the Gradle wrapper tried to download Gradle from `services.gradle.org`, which is blocked in this environment. The source patch itself is narrow and follows the current Android Credential Manager Sign in with Google button flow.
