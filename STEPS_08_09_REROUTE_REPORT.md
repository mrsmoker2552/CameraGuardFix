# CameraGuard — Steps 08, 09 and route-change handling

Working tree: /mnt/data/cg_step04. This report is code-level status, not an APK or live navigation validation.

## Step 08 — Spoken route guidance
- Added `voice/NavigationVoiceSpeaker.kt`, a separate lifecycle-owned Android TextToSpeech instance for navigation instructions.
- Route tab reads `NavigationProgressTracker`'s next maneuver, remaining meters and off-route metric. For a fresh, accurate GPS fix it announces the real route maneuver at approximately 450 m, 120 m and 35 m thresholds. A route revision clears milestone history for the new route.
- It does not synthesize fake location or announce turns when GPS is stale, inaccurate or the rider is off the route.
- Language depends on the Android TTS engine's installed English voice; no third-party voice/online speech service is bundled. Camera-warning TTS is a separate existing engine.

## Step 09 — Mute/unmute
- Added route guidance volume on/off control inside the active navigation information card. Voice is enabled by default and mutable state survives screen recomposition.
- Muting stops queued navigation speech; ending navigation stops it as well. This control does not mute independent safety-camera warnings.

## Route changes and displayed blue polyline
- An active navigation card offers `Change destination`, keeping the existing route visible until a replacement is successfully calculated and selected.
- Automatic off-route detection requires two consecutive fresh/accurate fixes more than 55 m from the planned route. A new route is requested from the current GPS location, with a 4-second cooldown between attempts. No 8-second post-reroute sleep remains.
- MapLibre route polylines now update their points *in place* rather than removing the old line before drawing the new one. This is intended to eliminate the temporary blank-line frame on recalculation. Network failures preserve the old route instead of inventing a new one.

## Verification and limitations
- Updated files: CameraGuardApp.kt, RealMapView.kt, plus newly added NavigationVoiceSpeaker.kt.
- Static source assertions performed; a Gradle/Android SDK compile and Android device/navigation road test were not available in this session. Voice timing, actual reroute latency and MapLibre rendering must be verified on-device.
- Safety: never operate the app manually while riding; choose/change destinations while stopped.
