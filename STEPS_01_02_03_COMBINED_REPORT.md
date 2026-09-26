# CameraGuard — Steps 01–03 consolidated source

## Step 01 — Main Map roads only
`RealMapView.kt` uses the `roadsOnly=true` branch to apply `applyBlackRoadsOnlyStyle`. Main Map base-map building/land-use/area fills, 3D extrusions, raster, circles, hillshade and unrelated symbols are hidden, while road lines and road-name symbols remain. MapLibre SDK annotations and CameraGuard app overlays are preserved. Route Map (`roadsOnly=false`) is not subject to this filtering.

## Step 02 — Smoker's Map logo
`CameraGuardApp.kt` shared `SmokerMapWordmark` now draws a shield and navigation-arrow icon with the Smoker's Map name; its icon makes a short Y-axis twist once per 5,000 ms animation cycle. Existing Main Map footer uses this component; the Route/Explore footer currently also uses it. A separately embedded WebView HUD logo is not changed by this step.

## Step 03 — Detailed Route Map
`RealMapView.kt` `applyFullMapTheme` contains the Step 03 color/contrast pass for roads and labels, building footprints and available 3D buildings, green spaces, water, rail/transit and POIs. It preserves data sources, layer visibility and zoom settings. The amount of detail depends on actual vector-tile contents.

## Validation and limitations
All three source modifications are present in this one project, and the ZIP packaging is CRC-checked. No Android SDK/Gradle APK build or device-level appearance/animation tests were performed. Map rendering, animated logo and Route map detail must be checked on a physical device. Step 04 has not started.
