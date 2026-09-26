# Step 06 — Remove “Tap a place” card

**Status:** Code change completed in the existing `cg_step04` working tree (retains Step 01–03 combined and Step 04/05 edits). No Step 07 changes made.

**Modified file:** `app/src/main/java/com/boss/cameraguard/ui/CameraGuardApp.kt`

Removed the conditional bottom-center “Tap a place · Hold anywhere to drop a pin” hint from `RouteExploreScreen`. It previously appeared when there was no selected place and no search result. Existing map tap/long-press destination interactions, selected-place details, search results, navigation controls and Smoker’s Map logo remain unchanged.

**Checks:** Exact occurrence and one-line-only replacement verified; string absent after change; destination tap handler and place details still present. Android Gradle build and on-device visual testing not performed. Step 04 (all requested map data layers) remains in progress. No ZIP generated yet.
