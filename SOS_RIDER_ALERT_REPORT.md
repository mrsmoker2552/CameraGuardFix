# CameraGuard SOS Rider Alert — implementation report

## Implemented
- Added a floating red SOS control on Main Map, Route and HUD.
- Pressing SOS publishes the authenticated rider's explicit emergency location to `sosAlerts/<uid>` for 10 minutes.
- All Community Mode riders subscribe to live SOS alerts.
- Incoming SOS events post a high-priority Android notification containing the rider name.
- Tapping the notification opens CameraGuard, switches to the Main Map and focuses the alert rider.
- Main Map and Route render the SOS location as a blinking red marker with the rider name visibly included in the marker graphic.
- HUD 3D map renders a pulsing red emergency ring and rider-name sprite at the SOS coordinates.
- Pressing the SOS button again while the user's SOS is active cancels it.
- Normal community presence remains privacy-rounded; exact coordinates are only published in the explicit short-lived SOS node.

## Firebase rules
`firebase-rules/realtime-database.rules.json` now includes `sosAlerts`, while preserving users, presence, admins, contributors, master cameras, OSM references, road reports and private presence.
The SOS rule permits authenticated riders to write/delete only their own SOS and permits reads only for users with Community Mode enabled.

## Verification
- HUD ES-module JavaScript syntax check: PASS.
- Realtime Database rules JSON syntax: PASS.
- Kotlin/Android full Gradle compilation: NOT VERIFIED in this environment because the Gradle wrapper distribution cannot be downloaded from services.gradle.org.

## Deployment requirement
The included Realtime Database rules file must be published to Firebase before SOS reads/writes will work in production.
