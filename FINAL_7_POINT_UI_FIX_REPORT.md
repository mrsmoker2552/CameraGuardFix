# CameraGuard — Final 7-point UI fix report

Applied to `CameraGuard_FINAL_Community_GoogleSignIn_Android16_Fallback.zip`.

1. Main Map SOS moved to the same right edge as recenter, directly above it.
2. Main Map SOS size set to 38dp, matching the recenter button.
3. Route right-side controls normalized to 44dp and aligned on the same right edge. Map Layers moved into the same vertical control stack; SOS is aligned immediately above it.
4. HUD mini-map reliability improved: nearby OSM request radius reduced from 5km to ~2.2km to match the 2km viewport and reduce dense-area Overpass timeouts; empty fetches retry after ~30 seconds; map/road contrast increased while retaining real OSM-only geometry.
5. HUD GPS DEBUG control/panel hidden from the rider UI. Diagnostic code remains internal for troubleshooting.
6. HUD SOS normalized to 44dp and moved to bottom-right above bottom navigation.
7. Settings CameraGuard Account/login card removed. Existing Community Google sign-in remains available from Community. The old New Log / Export Log controls were also removed from Settings UI; diagnostics backend is unchanged.

Safety logic, camera warning eligibility/distances, routing, Firebase/SOS data contracts, and Community backend were not changed.

Validation: ZIP/source integrity checked. Full Gradle compile could not be executed in this sandbox because the Gradle wrapper could not resolve `services.gradle.org` (UnknownHostException).
