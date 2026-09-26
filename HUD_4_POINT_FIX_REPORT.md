# CameraGuard HUD – four-point renderer patch (2026-09-24)

This package is based on `CameraGuard_Updated_PropertyFactory_Fix.zip` and preserves the previous PropertyFactory correction. Changed application file: `app/src/main/assets/hud/index.html` only.

1. **Fixed scooter / centre anchor:** The 3D bike stays at the world origin (x=0, z=0) for every GPS update. The current road centreline is translated to the nearest mapped road point below this anchor. The 3D camera looks at the rider instead of a distant road point. User-controlled orbit/zoom is retained.
2. **Road bends:** The 3D centreline uses short, segment-limited quadratic corner fillets instead of global Catmull–Rom interpolation, reducing harsh OSM vertex corners and preventing spline overshoot at real junctions. This visual-only interpolation does not revise the routing graph.
3. **Blue route:** Road surface, lane edges and the blue route ribbon are generated from the same corner-smoothed centreline; no separately interpolated route geometry.
4. **Smoothness:** Road and blue-line vertex buffers transition over 720 ms with eased interpolation when consecutive GPS updates use the same road-data source. The rider remains anchored, not teleported between sampled spline points. A source change to or from a visual fallback is not interpolated, to avoid presenting fallback as navigable road.

## Limits / verification

- All four HUD inline scripts passed a Node JavaScript syntax check, including the module script. A static source sanity check verified the anchor/camera/transition functions.
- No Android SDK/build environment or real scooter/GPS playback was available here. Therefore APK build, WebGL device compatibility, real-world road shape, and live visual smoothness are **not yet verified**. A device test on straight segments, tight bends, roundabouts, route transitions, and GPS loss is required.
- Camera-warning eligibility/thresholds, routes, and Firebase functionality were not changed.
