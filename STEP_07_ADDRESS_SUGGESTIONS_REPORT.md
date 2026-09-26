# Step 07 — Route-tab address suggestions (code implementation)

Base: `/mnt/data/cg_step04`, retaining Steps 01–06, in-progress Step 04 layers and the map-camera blinking mitigation. No ZIP/APK generated in this step. Step 08 not started.

## Problem found
`RouteExploreScreen` (the Route tab before starting navigation) only searched on explicit Search/keyboard submission. It did not invoke the existing `RoutePlannerRepository.suggestPlaces()` while typing, nor render autocomplete suggestions. `NavigationScreen` had a separate suggestion UI already, but requested remote results after just 90 ms, which can cause unnecessary network calls and rate limiting.

## Changes
- `app/src/main/java/com/boss/cameraguard/ui/CameraGuardApp.kt`: Added a dedicated debounced (`450 ms`) autocomplete effect for the Route tab, with request cancellation when the query changes; requests run on `Dispatchers.IO`. Suggestions render beneath the input, above the existing five full-width category buttons. Tapping a suggestion sets the selected destination, preserves map pin / Directions flow and hides the keyboard. Explicit Search continues to use the existing search path, with offline/no-results feedback and optional full-address search. Suggestion selection suppresses a redundant refetch. Increased existing `NavigationScreen` live suggestion debounce from 90 to 450 ms.
- `app/src/main/java/com/boss/cameraguard/data/RoutePlannerRepository.kt`: Photon suggestion descriptions include street + house number when available, and town/village fallback when city is missing.

## Verification and limits
Source-level checks confirm binding, debouncing, suggestions display/click, background IO, cancellation, existing manual search, category layout, address formatting and INTERNET permission. No Android SDK/Gradle Android build or device/network functional test performed. Public Photon availability/coverage and Nominatim explicit-search availability cannot be guaranteed; real address results require internet and source map data. Step 04 remains incomplete pending additional provider decisions; its status is unchanged.
