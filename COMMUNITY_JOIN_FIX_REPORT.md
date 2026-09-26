# CameraGuard Community Join Fix — v1.6.10

## Root cause
Google account selection was working, but CameraGuard starts with an anonymous Firebase session. If the selected Google account had already been used with CameraGuard during earlier tests, Firebase rejected `linkWithCredential()` with `FirebaseAuthUserCollisionException`. The app then stayed on the Join Community screen instead of switching to the existing Google Firebase account.

## Fix
`CameraGuardAuthManager.completeGoogleFirebaseSignIn()` now handles this expected collision by signing in with the selected Google credential instead of failing. After the account switch, the existing Community flow continues and recreates/updates the `communityMembers/{uid}` Firestore profile, then enables Community mode locally and in Realtime Database.

## Preserved
- Credential Manager Google account chooser
- Android 16 retry logic
- Google-linked Community requirement
- Firebase/Firestore/Realtime Database data contracts
- SOS, HUD, maps, warnings, routing and neumorphic UI

## Version
- versionCode: 51
- versionName: 1.6.10

## Build verification
A full Gradle build could not run in this environment because the Gradle wrapper attempted to reach `services.gradle.org`, which is not resolvable here. The edited source was patched directly against the latest acceptance-fix project.
