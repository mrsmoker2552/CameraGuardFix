# Step 04 — Route Map layers (IN PROGRESS)

Baseline: CameraGuard_Steps_01_02_03_Combined.zip. All Step 01–03 source files retained.

Implemented in working copy (NOT packaged as final ZIP):
- RouteExploreScreen has a Route-only Layers button with toggle controls for buildings, available 3D building layers, places/POIs, transit/rail, and parks/water.
- MapLibre RealMapView accepts RouteMapLayers and toggles the visibility of *existing* named vector-style layers in-place. It does not call setStyle when a toggle changes, and skips the Main Map roadsOnly=true mode and CameraGuard annotation layers.
- Affected files: app/src/main/java/com/boss/cameraguard/map/RealMapView.kt and app/src/main/java/com/boss/cameraguard/ui/CameraGuardApp.kt.

Not yet implemented or verified:
- Satellite imagery, live traffic, Street View, air quality or other Google Maps-specific data layers. These require suitable authorized data providers; base OpenFreeMap vector tiles alone do not provide them.
- Exact layer IDs/availability vary with remote map style; phone test required for all toggles.
- Active navigation's separate Route view has not yet received equivalent controls.
- Android APK build/real-device check not run; no Android SDK identified in environment.

This step is NOT COMPLETE and Step 05 must not start yet. No new ZIP until the user's final 22-step request is complete or they explicitly request an intermediate one.
