# Mini map implementation (2026-09-24)

Based on CameraGuard_HUD_4_Point_Fix.zip.

- 2D north/heading-up top-down mini map preserved; rider arrow centered and fully opaque.
- Mini map displays in HUD upper-left, border, background, rounded card and shadow overridden by final CSS; only road canvas has translucent edge fading.
- Projection adjusted to 5 km *radius* and OSM nearby-road request adjusted to 5,000 m. Network payload capped at 1,200 ways/26,000 vertices and may be incomplete in dense locations or if Overpass times out. Real-world 5 km complete coverage is not guaranteed by this HTTP endpoint; no fabricated roads are drawn.
- Matched current road blue, other roads muted; unrelated nearby roads no longer highlighted as current road.
- Route ON/OFF button is retained, accessible and functional; it toggles only the mini-map route ribbon, not live navigation.
- Route line is rendered from live route coordinates when available and toggle ON; no synthetic route is drawn. The native route state must clear routePoints when navigation ends; existing app behavior was not modified.
- Old compass element is hidden and redundant 1 km radius text is removed.
- No camera-warning thresholds, direction gating, routing or native map styles changed.
- Source-level and JS syntax validations performed; Android build, Overpass live coverage and device visual tests were not possible in this sandbox.
