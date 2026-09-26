# CameraGuard — Main Map blinking mitigation (working copy)

Baseline: existing /mnt/data/cg_step04 working copy containing Steps 01–06 (Step 04 still in progress). No next numbered step started. Changed only `app/src/main/java/com/boss/cameraguard/map/RealMapView.kt`.

## Root cause identified in source
`latestRender` called `moveToCurrentLocation` on every >=2 m GPS change or >=5 degree heading change while following. `moveToCurrentLocation` rebuilt a camera position at zoom 18.2 and tilt 48 degrees on every such update, so ordinary stationary GPS drift or updates while zoomed out repeatedly moved/zoomed/rotated the map. 700-ms animations could restart with closely spaced updates. This explains a plausible camera-jumping source of reported map blinking; video alone cannot establish that no other flicker source exists.

## Applied change
- Follow camera movement only when an initial fix is needed, or GPS reports actual motion at >=1.5 m/s and movement clears max(5 m, 1.5 x reported accuracy), or valid moving heading changes >=8 degrees.
- Throttle subsequent camera-follow commands to no more than one per 850 ms.
- Preserve the user's existing zoom and tilt during ordinary GPS-follow updates rather than resetting to 18.2 / 48 degrees; initial follow and an explicit recenter still reset default framing.
- Synchronize recenter bookkeeping so the same GPS reading does not initiate an immediate redundant follow animation.
- Map style and camera/rider marker rendering logic left unchanged. No CameraWarningRuntime or warning range code changed.

## Verification and limits
Code-level pattern checks complete; Android SDK/Gradle APK build and physical phone/route/zoom test not performed. This targets camera-follow jitter; tile download delays, remote style failures, GL surface resets or camera-marker refreshes require a device log if blinking continues. Step 07 not started. No intermediate ZIP generated.
