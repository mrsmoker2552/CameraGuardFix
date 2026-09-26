# CameraGuard final HUD + Theme + Mini-map glow update

## Included
- Full 3D map HUD architecture retained: fixed scooter slightly below centre; map translates/rotates heading-up; route ribbon retained; scooter lean retained.
- Persistent Settings > Appearance Dark mode / Light mode retained.
- Theme mode is now also passed into the embedded HUD so HUD CSS and 3D map materials switch between dark and light palettes while warning colours remain semantic.
- Mini-map viewport radius changed to 2 km.
- Mini-map current position remains fixed at the exact centre; the map rotates heading-up according to the rider's filtered heading.
- Added cyan radial location glow with a maximum 1 km radius.
- Surrounding road alpha is masked by the same 1 km radial falloff: roads are strongest near the rider and smoothly disappear as the glow ends.
- The active navigation route is drawn after the road mask so it remains readable over the faded map context.
- Glow/fade applies only to the mini-map, not to the main 3D HUD map.

## Verification performed
- Extracted the HUD ES module and ran `node --check`: PASS.
- Confirmed the 2 km mini-map radius, 1 km glow radius, theme payload and 3D HUD theme hook are present in source.
- Full Android Gradle/APK build is not claimed here because this environment does not have a complete cached Android build toolchain and external Gradle dependency access is unavailable.
