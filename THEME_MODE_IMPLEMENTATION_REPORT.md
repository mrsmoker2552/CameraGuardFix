# CameraGuard Dark / Light Theme implementation

Implemented in Settings with persistent `AppThemeMode.DARK` / `AppThemeMode.LIGHT`.

- Theme selection persists in SharedPreferences.
- MainActivity applies the selected Material3 color scheme at the root.
- CameraGuard custom palette is dynamic, so existing app surfaces/text/borders follow the selected mode.
- Settings contains an Appearance card with Dark mode and Light mode choices.
- Main roads-only map and full route/explore map repaint in place for light/dark mode without reloading the MapLibre style.
- Chat palette values were made dynamic so message surfaces update too.
- Existing semantic warning colors remain independent for safety visibility.
- No camera warning distances or warning-engine logic changed.

Verification note: source-level edits completed. Full Gradle compile cannot run in this environment because the Gradle distribution is not locally cached and network access to services.gradle.org is unavailable.
