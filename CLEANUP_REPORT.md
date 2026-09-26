# CameraGuard lightweight ZIP — cleanup report

- Historical root-level changelogs, version notes, test reports and outdated handoff documents were removed. They contained obsolete implementation and version descriptions, so rely on the current source and `README.md` instead.
- Deleted inactive source files: `app/location/LocationTracker.kt` (outside `app/src/main` and duplicated there), `alerts/WarningEngine.kt` (unused early prototype), `model/CameraPoint.kt` (unused early model), `ui/NativeHudScreen.kt` and `ui/NativeScooter3D.kt` (unreferenced alternative native HUD; live HUD uses `assets/hud/index.html` and Three.js).
- Kept all active navigation/map, location, camera detection + alerts, WebView HUD, diagnostics, rider/community/chat, authentication, Firebase rules/config, and Gradle build files unchanged.
- Preserved `STEP_6_DEPLOYMENT_README.md` for its important warning against deploying untested Firebase privacy rules.
- Validation: ZIP archive integrity, original-versus-retained file hash equality, references to removed classes, JSON/XML parsing. An actual Android build/on-device test was not available; removal is based on static references only.
