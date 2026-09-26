# CameraGuard — steps 10–22 source-change report

Baseline: CameraGuard_Steps_01_to_09_Updated.zip. All original baseline project files preserved. Modified files: HUD `index.html`, `CameraGuardApp.kt`, `CurrentRoadRepository.kt`.

| No. | Implementation in this source ZIP |
|---|---|
| 10 | HUD mini-map map scale set to **1 km radius**, including matching nearby-road-network fetch radius of 1050 m. Actual road coverage depends on OSM/Overpass. |
| 11 | Map canvas and arrow confined to a circular viewport with a radial soft outer fade. |
| 12 | The GPS-matched current road is highlighted yellow, with a yellow soft halo. |
| 13 | Roads in the live OSM road network intersecting the actual 300 m GPS-centred circle get yellow styling; drawing is clipped to that circle, which follows each new valid GPS update. No artificial roads are added. |
| 14 | User arrow is centred and heading-up; actual map geometry rotates in the opposite direction. When GPS is unavailable, the arrow is grey and road highlights are not invented. |
| 15 | HUD button now toggles state rather than calling an inert callback. Full HUD mode hides the bottom tabs, and EXIT HUD brings them back. |
| 16 | Existing textured 3D asphalt/curved lane surfaces retained; paired emissive street-light models follow each reprojected visual road curve. Street-light placement is illustrative, not actual OSM lamp coordinates. |
| 17 | A 3D roadside camera is rendered only if the native warning engine selected a target, its position is near the live real road geometry, and it is ahead in the HUD viewport. Housing color/size/signage varies by camera type; red-light uses visible signal lenses. Visual object positions are approximate and do not alter engine warnings or confirm actual hardware mounting location. Cameras with insufficient geometry are deliberately not faked. |
| 18 | HUD 3D scooter remains snapped to the visual road centreline even in the labelled visual-only fallback, without representing the display as a real GPS location or inventing camera warnings. |
| 19 | Lane-motion animation begins only for a current GPS fix (up to 8 seconds old) with filtered GPS speed >= 12 km/h; stale/unavailable GPS freezes it. Warning calculations unchanged. |
| 20 | Main Map, Route Tab and native HUD warning banners now use the same horizontal padding, 82 dp row height and 17 dp corner radius, based on the single active native warning target; HTML warning remains hidden behind native overlay. |
| 21 | Camera direction compass accepts drag gestures in addition to taps and compass quick-select options; existing Save Verified Fix persists the selected bearing via the existing save callback. |
| 22 | HUD bottom-left shield/arrow mark has a 5-second short twist, matching the existing Compose Smoker’s Map branding on Main Map/Route Tab. Logo is moved above the native warning while one is displayed. |

## Verification and limitations

- All 4 inline HUD JavaScript blocks were syntax-checked with Node.js.
- Android APK compilation **could not be performed** in this container: Android SDK and cached Gradle distribution are not available, and the Gradle wrapper cannot reach `services.gradle.org`. The project ZIP is **source code**, not an APK.
- Phone/Scooter field testing is outstanding, including 3D rendering/visibility, actual direction dragging, voice guidance, live camera warnings and GPS loss behaviour. All 13 steps above are implemented at the source-code level only; end-to-end operation has not been verified.
- Step 4 from the previous ZIP remains **partial**. This release does not claim to supply satellite, live traffic or Street View layers.
- Roadside camera placement, road geometry and GPS map-matching depend on actual data; visual fallback never feeds or changes the warning engine.
